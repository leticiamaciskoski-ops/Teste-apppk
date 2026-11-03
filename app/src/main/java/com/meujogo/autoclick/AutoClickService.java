package com.meujogo.autoclick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/**
 * Autoclick para testar o minigame de pesca.
 *
 * Ciclo: SEGURAR (holdMs) -> esperar o sinal de fisgada (lendo a cor de um pixel) ->
 * dar N toques rápidos -> pausa -> repete. Mede a espera do jogo e o tempo real dos toques.
 *
 * Usa a API de acessibilidade: dispatchGesture (segurar/tocar) e takeScreenshot (ler a cor, Android 11+).
 */
public class AutoClickService extends AccessibilityService {

    public static volatile AutoClickService instance;

    private interface BitmapCb {
        void onBitmap(Bitmap b);

        void onFail(int code);
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rng = new Random();
    private final RunStats stats = new RunStats();
    private final ArrayList<String> log = new ArrayList<>();
    private Config cfg = new Config();
    private WindowManager wm;

    private boolean running;
    private boolean testing;
    private int token;          // muda a cada parar/iniciar: callbacks antigos se descartam
    private long waitStart;
    private long bornAt;

    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView tvStatus;
    private TextView tvLog;
    private Button btnRun;
    private Button btnTest;
    private Button btnHop;
    private Button btnCollapse;
    private TextView tvTitle;
    private TextView tvHop;
    private LinearLayout body;
    private boolean collapsed;

    private FrameLayout calibView;
    private TextView calLabel;
    private int calStep;

    // ------------------------------------------------------------------
    // Ciclo de vida do serviço
    // ------------------------------------------------------------------

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        bornAt = SystemClock.uptimeMillis();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        cfg = ConfigStore.load(this);
        showPanel();
        addLog("Serviço conectado. Calibre os 3 pontos.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopAll();
        removeCalibration();
        removePanel();
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // não usamos eventos
    }

    @Override
    public void onInterrupt() {
        stopAll();
    }

    /** Chamado pela tela de configuração depois de salvar. */
    public void reloadConfig() {
        cfg = ConfigStore.load(this);
        addLog("Configuração recarregada.");
    }

    // ------------------------------------------------------------------
    // Painel flutuante
    // ------------------------------------------------------------------

    public void showPanel() {
        if (panel != null || wm == null) {
            return;
        }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xE6101820);
        panel.setPadding(dp(8), dp(6), dp(8), dp(8));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        tvTitle = label("≡ Autoclick Pesca (arraste aqui)", 12, 0xFFFFC857);
        tvTitle.setOnTouchListener(dragListener);
        btnCollapse = button("–", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCollapsed();
            }
        });
        titleRow.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(btnCollapse, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(titleRow);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        tvStatus = label("", 11, 0xFFBFD7EA);
        body.addView(tvStatus);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        btnRun = button("Iniciar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    stopAll();
                } else {
                    start();
                }
            }
        });
        Button btnCal = button("Calibrar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCalibration();
            }
        });
        btnTest = button("Testar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTest();
            }
        });
        Button btnHide = button("X", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAll();
                removePanel();
            }
        });
        row.addView(btnRun, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        row.addView(btnCal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        row.addView(btnTest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        row.addView(btnHide, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        body.addView(row);

        btnHop = button("Server hop", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doHop();
            }
        });
        body.addView(btnHop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvHop = label("", 11, 0xFFFFC857);
        body.addView(tvHop);

        tvLog = label("", 10, 0xFF9FB6CC);
        body.addView(tvLog);
        panel.addView(body);

        panelLp = new WindowManager.LayoutParams(
                dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = dp(8);
        panelLp.y = dp(60);
        wm.addView(panel, panelLp);
        refreshUi();
    }

    private void removePanel() {
        if (panel != null && wm != null) {
            try {
                wm.removeView(panel);
            } catch (RuntimeException ignored) {
                // já removido
            }
        }
        panel = null;
        tvStatus = null;
        tvLog = null;
        btnRun = null;
        btnTest = null;
        btnHop = null;
        btnCollapse = null;
        tvTitle = null;
        tvHop = null;
        body = null;
        collapsed = false;
    }

    private final View.OnTouchListener dragListener = new View.OnTouchListener() {
        private int startX;
        private int startY;
        private float downX;
        private float downY;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (panelLp == null || panel == null) {
                return false;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = panelLp.x;
                    startY = panelLp.y;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panelLp.x = startX + (int) (e.getRawX() - downX);
                    panelLp.y = startY + (int) (e.getRawY() - downY);
                    wm.updateViewLayout(panel, panelLp);
                    return true;
                default:
                    return true;
            }
        }
    };

    private void refreshUi() {
        if (panel == null) {
            return;
        }
        btnRun.setText(running ? "Parar" : "Iniciar");
        btnTest.setText(testing ? "Parar teste" : "Testar");
        btnHop.setText("Server hop (" + ServerHop.describe(cfg.hopMin, cfg.hopMax) + ")");
        if (!testing) {
            tvStatus.setText(stats.summary());
        }
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, log.size() - 6);
        for (int i = from; i < log.size(); i++) {
            sb.append(log.get(i)).append('\n');
        }
        tvLog.setText(sb.toString());
    }

    /** Recolhe o painel para uma barrinha (para não cobrir o jogo) ou expande de novo. */
    private void toggleCollapsed() {
        if (panel == null || body == null || panelLp == null) {
            return;
        }
        collapsed = !collapsed;
        body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        btnCollapse.setText(collapsed ? "+" : "–");
        tvTitle.setText(collapsed ? "≡ Pesca" : "≡ Autoclick Pesca (arraste aqui)");
        panelLp.width = collapsed ? dp(120) : dp(300);
        wm.updateViewLayout(panel, panelLp);
    }

    /** Server hop: escolhe um servidor com poucos jogadores e abre o Roblox nele. */
    private void doHop() {
        if (cfg.placeId <= 0) {
            addLog("Defina o jogo (Place ID) na tela do app.");
            return;
        }
        HopController.hop(this, cfg, new HopController.Listener() {
            @Override
            public void onStatus(String msg) {
                if (tvHop != null) {
                    tvHop.setText(msg);
                }
                addLog(msg);
            }
        });
    }

    private void addLog(String msg) {
        long s = (SystemClock.uptimeMillis() - bornAt) / 1000;
        log.add(String.format(Locale.US, "%02d:%02d ", s / 60, s % 60) + msg);
        while (log.size() > 40) {
            log.remove(0);
        }
        refreshUi();
    }

    // ------------------------------------------------------------------
    // Iniciar / parar
    // ------------------------------------------------------------------

    private void start() {
        if (!cfg.calibrated()) {
            addLog("Calibre os 3 pontos primeiro.");
            return;
        }
        if (covers(cfg.holdX, cfg.holdY) || covers(cfg.tapX, cfg.tapY) || covers(cfg.detectX, cfg.detectY)) {
            addLog("O painel cobre um ponto calibrado. Arraste o painel para outro lugar.");
            return;
        }
        testing = false;
        running = true;
        token++;
        long est = TapPlan.estimateWindowMs(cfg.taps, cfg.spacingMs, cfg.tapDurMs,
                GestureDescription.getMaxStrokeCount(), 60);
        addLog("Iniciado. Janela estimada dos toques ~" + est + " ms (jogo: " + cfg.tapWindowMs + ")");
        startCycle(token);
    }

    private void stopAll() {
        boolean was = running || testing;
        running = false;
        testing = false;
        token++;
        h.removeCallbacksAndMessages(null);
        if (was) {
            addLog("Parado.");
        }
        refreshUi();
    }

    // ------------------------------------------------------------------
    // Um ciclo: segurar -> esperar sinal -> tocar
    // ------------------------------------------------------------------

    private void startCycle(final int tok) {
        if (tok != token || !running) {
            return;
        }
        int jitter = cfg.holdJitterMs > 0 ? rng.nextInt(2 * cfg.holdJitterMs + 1) - cfg.holdJitterMs : 0;
        long hold = Math.max(50, cfg.holdMs + jitter);
        hold = Math.min(hold, GestureDescription.getMaxGestureDuration());
        stats.cycles++;
        addLog("Ciclo " + stats.cycles + ": segurando " + hold + " ms");

        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(point(cfg.holdX, cfg.holdY), 0, hold))
                .build();
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription d) {
                if (tok != token || !running) {
                    return;
                }
                waitStart = SystemClock.uptimeMillis();
                pollSignal(tok);
            }

            @Override
            public void onCancelled(GestureDescription d) {
                if (tok != token || !running) {
                    return;
                }
                stats.cancelled++;
                addLog("Gesto de segurar cancelado pelo sistema; tentando de novo.");
                scheduleNext(tok, 1000);
            }
        }, null);
        if (!ok) {
            addLog("Não foi possível despachar o gesto (serviço sem permissão de gestos?).");
            scheduleNext(tok, 1500);
        }
    }

    private void pollSignal(final int tok) {
        if (tok != token || !running) {
            return;
        }
        long waited = SystemClock.uptimeMillis() - waitStart;
        if (waited > cfg.maxWaitMs) {
            stats.timeouts++;
            addLog("Sinal não detectado em " + waited + " ms. Próximo ciclo.");
            scheduleNext(tok, cfg.resultDelayMs);
            return;
        }
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                boolean live = tok == token && running;
                boolean hit = live && matches(b);
                b.recycle();
                if (!live) {
                    return;
                }
                if (hit) {
                    onSignal(tok);
                } else {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            pollSignal(tok);
                        }
                    }, cfg.pollMs);
                }
            }

            @Override
            public void onFail(int code) {
                if (tok != token || !running) {
                    return;
                }
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pollSignal(tok);
                    }
                }, 400);
            }
        });
    }

    private void onSignal(final int tok) {
        long waited = SystemClock.uptimeMillis() - waitStart;
        stats.recordWait(waited);
        addLog("Sinal detectado após " + waited + " ms de espera.");
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendTaps(tok, 0, 0);
            }
        }, cfg.reactionMs);
    }

    /** Envia os toques em lotes (o Android limita os traços por gesto). */
    private void sendTaps(final int tok, final int done, final long firstStart) {
        if (tok != token || !running) {
            return;
        }
        final int total = cfg.taps;
        final int n = Math.min(total - done, GestureDescription.getMaxStrokeCount());
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (int i = 0; i < n; i++) {
            b.addStroke(new GestureDescription.StrokeDescription(
                    point(cfg.tapX, cfg.tapY), (long) i * cfg.spacingMs, cfg.tapDurMs));
        }
        final long start = done == 0 ? SystemClock.uptimeMillis() : firstStart;
        boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription d) {
                if (tok != token || !running) {
                    return;
                }
                int nd = done + n;
                if (nd >= total) {
                    long span = SystemClock.uptimeMillis() - start;
                    boolean fits = stats.recordTaps(span, cfg.tapWindowMs);
                    addLog(total + " toques em ~" + span + " ms (janela " + cfg.tapWindowMs + ": "
                            + (fits ? "OK" : "ESTOUROU") + ")");
                    scheduleNext(tok, cfg.resultDelayMs);
                } else {
                    sendTaps(tok, nd, start);
                }
            }

            @Override
            public void onCancelled(GestureDescription d) {
                if (tok != token || !running) {
                    return;
                }
                stats.cancelled++;
                addLog("Toques cancelados pelo sistema.");
                scheduleNext(tok, 1000);
            }
        }, null);
        if (!ok) {
            addLog("Não foi possível despachar os toques.");
            scheduleNext(tok, 1500);
        }
    }

    private void scheduleNext(final int tok, long delayMs) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                startCycle(tok);
            }
        }, delayMs);
        refreshUi();
    }

    // ------------------------------------------------------------------
    // Leitura de cor
    // ------------------------------------------------------------------

    private boolean matches(final Bitmap b) {
        int d = bestDiff(b);
        return d <= cfg.tolerance;
    }

    private int bestDiff(final Bitmap b) {
        return ColorMatch.bestDiff(new ColorMatch.Pixels() {
            @Override
            public int get(int x, int y) {
                return b.getPixel(x, y);
            }
        }, b.getWidth(), b.getHeight(), cfg.detectX, cfg.detectY, cfg.boxRadius, cfg.detectColor);
    }

    /** Captura a tela (Android 11+; o sistema limita a ~3 capturas por segundo). */
    private void grab(final BitmapCb cb) {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult r) {
                Bitmap sw = null;
                HardwareBuffer hb = r.getHardwareBuffer();
                try {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                    if (hw != null) {
                        sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hw.recycle();
                    }
                } finally {
                    hb.close();
                }
                if (sw == null) {
                    cb.onFail(-1);
                } else {
                    cb.onBitmap(sw);
                }
            }

            @Override
            public void onFailure(int errorCode) {
                cb.onFail(errorCode);
            }
        });
    }

    // ------------------------------------------------------------------
    // Modo teste: mostra ao vivo se o sinal está sendo detectado
    // ------------------------------------------------------------------

    private void toggleTest() {
        if (testing) {
            stopAll();
            return;
        }
        if (cfg.detectX < 0) {
            addLog("Calibre o ponto de detecção primeiro.");
            return;
        }
        stopAll();
        testing = true;
        token++;
        addLog("Modo teste: observe se o sinal vira DETECTADO.");
        testLoop(token);
        refreshUi();
    }

    private void testLoop(final int tok) {
        if (tok != token || !testing) {
            return;
        }
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                boolean live = tok == token && testing;
                if (live && tvStatus != null) {
                    int d = bestDiff(b);
                    int cx = Math.max(0, Math.min(b.getWidth() - 1, cfg.detectX));
                    int cy = Math.max(0, Math.min(b.getHeight() - 1, cfg.detectY));
                    tvStatus.setText("Pixel " + ColorMatch.hex(b.getPixel(cx, cy)) + " | alvo "
                            + ColorMatch.hex(cfg.detectColor) + "\nDiferença mín. " + d + " (tol. " + cfg.tolerance + ") -> "
                            + (d <= cfg.tolerance ? "DETECTADO" : "não detectado"));
                }
                b.recycle();
                if (live) {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            testLoop(tok);
                        }
                    }, 400);
                }
            }

            @Override
            public void onFail(int code) {
                if (tok != token || !testing) {
                    return;
                }
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        testLoop(tok);
                    }
                }, 500);
            }
        });
    }

    // ------------------------------------------------------------------
    // Calibração: 3 toques (segurar, tocar, sinal)
    // ------------------------------------------------------------------

    private void startCalibration() {
        stopAll();
        removeCalibration();
        calStep = 0;
        calibView = new FrameLayout(this);
        calLabel = label("", 14, 0xFFFFFFFF);
        calLabel.setBackgroundColor(0xEE000000);
        calLabel.setPadding(dp(10), dp(10), dp(10), dp(10));
        calibView.addView(calLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        calibView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    onCalibTouch((int) e.getRawX(), (int) e.getRawY());
                }
                return true;
            }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wm.addView(calibView, lp);
        updateCalLabel();
    }

    private void updateCalLabel() {
        String[] msgs = {
                "1/3  Toque no ponto onde o jogo deve ser SEGURADO (área de lançar).",
                "2/3  Toque no ponto onde o jogo recebe os TOQUES rápidos (puxar o peixe).",
                "3/3  Deixe o jogo chegar na fisgada e toque SOBRE o sinal (ex.: o '!'). "
                        + "O sinal precisa ficar visível por ~1 s; um pisca-pisca muito rápido não é detectável."
        };
        if (calLabel != null) {
            calLabel.setText(msgs[Math.min(calStep, 2)]);
        }
    }

    private void onCalibTouch(final int x, final int y) {
        switch (calStep) {
            case 0:
                cfg.holdX = x;
                cfg.holdY = y;
                calStep = 1;
                updateCalLabel();
                break;
            case 1:
                cfg.tapX = x;
                cfg.tapY = y;
                calStep = 2;
                updateCalLabel();
                break;
            default:
                removeCalibration(); // tira a camada para ela não aparecer na captura
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        captureReference(x, y);
                    }
                }, 250);
                break;
        }
    }

    private void captureReference(final int x, final int y) {
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                int cx = Math.max(0, Math.min(b.getWidth() - 1, x));
                int cy = Math.max(0, Math.min(b.getHeight() - 1, y));
                cfg.detectX = cx;
                cfg.detectY = cy;
                cfg.detectColor = b.getPixel(cx, cy) | 0xFF000000;
                b.recycle();
                ConfigStore.save(AutoClickService.this, cfg);
                addLog("Calibrado. Segurar (" + cfg.holdX + "," + cfg.holdY + "), toques (" + cfg.tapX + ","
                        + cfg.tapY + "), sinal (" + cx + "," + cy + ") cor " + ColorMatch.hex(cfg.detectColor));
                addLog("Use 'Testar' para conferir a detecção antes de iniciar.");
            }

            @Override
            public void onFail(int code) {
                addLog("Não consegui capturar a tela (código " + code + "). Calibre de novo.");
            }
        });
    }

    private void removeCalibration() {
        if (calibView != null && wm != null) {
            try {
                wm.removeView(calibView);
            } catch (RuntimeException ignored) {
                // já removido
            }
        }
        calibView = null;
        calLabel = null;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private boolean covers(int x, int y) {
        if (panel == null) {
            return false;
        }
        int[] loc = new int[2];
        panel.getLocationOnScreen(loc);
        int m = dp(4);
        return x >= loc[0] - m && x <= loc[0] + panel.getWidth() + m
                && y >= loc[1] - m && y <= loc[1] + panel.getHeight() + m;
    }

    private static Path point(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, float sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setOnClickListener(l);
        return b;
    }
}
