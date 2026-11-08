package com.meujogo.serverhop;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Motor da pesca automática (serviço de acessibilidade).
 *
 * Ciclo: PEGAR A VARA no inventário (toques no slot) -> SEGURAR (lançar) -> esperar o sinal lendo a cor
 * de um pixel -> dar os toques rápidos -> repetir.
 *
 * Usa dispatchGesture (pegar a vara, segurar, tocar) e takeScreenshot (ler a cor, Android 11+).
 * A interface fica no painel flutuante (OverlayService); aqui só há a calibração em tela cheia.
 */
public class FishService extends AccessibilityService {

    public static volatile FishService instance;

    /** O painel flutuante recebe um aviso a cada mudança de estado (na thread principal). */
    public interface Listener {
        void onUpdate();
    }

    /** Área do painel flutuante na tela, para não cobrir os pontos calibrados. */
    public interface AvoidProvider {
        Rect rect();
    }

    public static volatile Listener listener;
    public static volatile AvoidProvider avoid;

    private interface BitmapCb {
        void onBitmap(Bitmap b);

        void onFail(int code);
    }

    private static final int CAL_EQUIP = 0;
    private static final int CAL_HOLD = 1;
    private static final int CAL_TAP = 2;
    private static final int CAL_DETECT = 3;  // tocar no ponto onde o sinal aparece
    private static final int CAL_SIGNAL = 4;  // lançar de verdade e capturar a cor quando o sinal aparecer

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
    private int baseFrac;       // % da cor do sinal visto SEM o sinal, medido a cada ciclo
    private int hits;           // leituras seguidas com o sinal
    private int testBase = 100; // menor % visto no modo teste (o fundo)
    private String step = "";   // o que o ciclo está fazendo agora
    private String testLine = "";

    private FrameLayout calibView;
    private TextView calLabel;
    private WindowManager.LayoutParams calLp;
    private LinearLayout calBtnView;
    private int[] calSteps;
    private int calIdx;

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        bornAt = SystemClock.uptimeMillis();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        cfg = ConfigStore.load(this);
        addLog("Acessibilidade conectada.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopAll();
        removeCalibration();
        instance = null;
        notifyUi();
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

    // ------------------------------------------------------------------
    // API usada pelo painel
    // ------------------------------------------------------------------

    public boolean isRunning() {
        return running;
    }

    public boolean isTesting() {
        return testing;
    }

    /** Linha de estado: o que o ciclo está fazendo. */
    public String phaseLine() {
        if (testing) {
            return "Modo teste";
        }
        if (!running) {
            return "Parado";
        }
        return "Ciclo " + stats.cycles + " · " + step;
    }

    public String statsText() {
        return testing ? testLine : stats.summary();
    }

    public List<String> recentLog(int n) {
        int from = Math.max(0, log.size() - n);
        return new ArrayList<>(log.subList(from, log.size()));
    }

    public void reloadConfig() {
        cfg = ConfigStore.load(this);
    }

    public void start() {
        cfg = ConfigStore.load(this);
        if (!cfg.fishCalibrated()) {
            addLog("Calibre a pesca primeiro"
                    + (cfg.equipOn == 1 ? " (vara, segurar, toques e sinal)." : " (segurar, toques e sinal)."));
            return;
        }
        if (covers(cfg.holdX, cfg.holdY) || covers(cfg.tapX, cfg.tapY) || covers(cfg.detectX, cfg.detectY)
                || (cfg.equipOn == 1 && covers(cfg.equipX, cfg.equipY))) {
            addLog("O painel cobre um ponto calibrado. Mova o painel ou recolha-o.");
            return;
        }
        testing = false;
        running = true;
        token++;
        stats.reset();
        long est = TapPlan.estimateWindowMs(cfg.taps, cfg.spacingMs, cfg.tapDurMs,
                GestureDescription.getMaxStrokeCount(), 60);
        addLog("Iniciado. Toques ~" + est + " ms (jogo: " + cfg.tapWindowMs + ")");
        if (cfg.equipOn == 1) {
            addLog("Pegar a vara: " + cfg.equipTaps + " toques no slot, a cada " + cfg.equipEvery + " ciclo(s).");
        }
        startCycle(token);
        tick(token);
    }

    public void stopAll() {
        boolean was = running || testing;
        running = false;
        testing = false;
        token++;
        h.removeCallbacksAndMessages(null);
        if (was) {
            addLog("Parado.");
        }
        notifyUi();
    }

    private void tick(final int tok) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (tok != token || !running) {
                    return;
                }
                notifyUi();
                tick(tok);
            }
        }, 1000);
    }

    private void notifyUi() {
        Listener l = listener;
        if (l != null) {
            l.onUpdate();
        }
    }

    private void addLog(String msg) {
        long s = (SystemClock.uptimeMillis() - bornAt) / 1000;
        log.add(String.format(Locale.US, "%02d:%02d ", s / 60, s % 60) + msg);
        while (log.size() > 40) {
            log.remove(0);
        }
        notifyUi();
    }

    private boolean alive(int tok) {
        return tok == token && running;
    }

    /** Toques no slot da vara no inventário; depois espera ela aparecer na mão. */
    private void equipRod(final int tok) {
        step = "pegando a vara";
        addLog("Pegando a vara: " + cfg.equipTaps + " toques no slot (" + cfg.equipX + "," + cfg.equipY + ")");
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (int i = 0; i < cfg.equipTaps; i++) {
            b.addStroke(new GestureDescription.StrokeDescription(
                    point(cfg.equipX, cfg.equipY), (long) i * cfg.equipSpacingMs, 30));
        }
        boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription d) {
                afterEquip(tok);
            }

            @Override
            public void onCancelled(GestureDescription d) {
                stats.cancelled++;
                addLog("Toques na vara cancelados pelo sistema; seguindo mesmo assim.");
                afterEquip(tok);
            }
        }, null);
        if (!ok) {
            addLog("Não foi possível tocar no slot da vara.");
            afterEquip(tok);
        }
        notifyUi();
    }

    private void afterEquip(final int tok) {
        if (!alive(tok)) {
            return;
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                beginHold(tok);
            }
        }, cfg.equipPauseMs);
    }

    // ------------------------------------------------------------------
    // Um ciclo de pesca: segurar -> esperar sinal -> tocar
    // ------------------------------------------------------------------

    private void startCycle(final int tok) {
        if (!alive(tok)) {
            return;
        }
        stats.cycles++;
        if (cfg.equipOn == 1 && (stats.cycles - 1) % cfg.equipEvery == 0) {
            equipRod(tok);
        } else {
            beginHold(tok);
        }
    }

    /** Mede o fundo (sem o sinal) e só então segura. Assim o sinal é detectado por mudança, não por cor fixa. */
    private void beginHold(final int tok) {
        if (!alive(tok)) {
            return;
        }
        step = "medindo o fundo";
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                if (alive(tok)) {
                    baseFrac = fraction(b);
                }
                b.recycle();
                afterBase(tok);
            }

            @Override
            public void onFail(int code) {
                baseFrac = 0;
                afterBase(tok);
            }
        });
    }

    private void afterBase(int tok) {
        if (!alive(tok)) {
            return;
        }
        if (!ColorMatch.detectable(baseFrac, cfg.detectMinPct)) {
            addLog("AVISO: " + baseFrac + "% da área já tem a cor do sinal sem ele. Não dá para detectar aqui: "
                    + "recalibre num ponto mais exclusivo do sinal.");
        } else if (baseFrac > 0) {
            addLog("Fundo com " + baseFrac + "% da cor do sinal; o sinal precisa passar de " + (baseFrac + cfg.detectMinPct) + "%.");
        }
        doHold(tok);
    }

    private void doHold(final int tok) {
        hits = 0;
        int jitter = cfg.holdJitterMs > 0 ? rng.nextInt(2 * cfg.holdJitterMs + 1) - cfg.holdJitterMs : 0;
        long hold = Math.max(50, cfg.holdMs + jitter);
        hold = Math.min(hold, GestureDescription.getMaxGestureDuration());
        step = "lançando";
        addLog("Ciclo " + stats.cycles + ": segurando " + hold + " ms");

        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(point(cfg.holdX, cfg.holdY), 0, hold))
                .build();
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription d) {
                if (!alive(tok)) {
                    return;
                }
                waitStart = SystemClock.uptimeMillis();
                step = "esperando o peixe";
                pollSignal(tok);
            }

            @Override
            public void onCancelled(GestureDescription d) {
                if (!alive(tok)) {
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
        if (!alive(tok)) {
            return;
        }
        long waited = SystemClock.uptimeMillis() - waitStart;
        if (waited > cfg.maxWaitMs) {
            stats.timeouts++;
            addLog("Sinal não detectado em " + waited + " ms. Próximo ciclo.");
            scheduleNext(tok, cfg.resultDelayMs);
            return;
        }
        if (waited < cfg.ignoreMs) {
            laterPoll(tok, Math.min(cfg.pollMs, cfg.ignoreMs - waited + 20));
            return;
        }
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                boolean live = alive(tok);
                boolean raw = live && ColorMatch.signalHit(fraction(b), baseFrac, cfg.detectMinPct);
                b.recycle();
                if (!live) {
                    return;
                }
                hits = raw ? hits + 1 : 0;
                if (hits >= cfg.detectConfirm) {
                    onSignal(tok);
                } else {
                    laterPoll(tok, cfg.pollMs);
                }
            }

            @Override
            public void onFail(int code) {
                if (alive(tok)) {
                    laterPoll(tok, 400);
                }
            }
        });
    }

    private void laterPoll(final int tok, long delay) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                pollSignal(tok);
            }
        }, delay);
    }

    private void onSignal(final int tok) {
        step = "puxando";
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
        if (!alive(tok)) {
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
                if (!alive(tok)) {
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
                if (!alive(tok)) {
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
        step = "descansando";
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                startCycle(tok);
            }
        }, delayMs);
        notifyUi();
    }

    // ------------------------------------------------------------------
    // Leitura de cor
    // ------------------------------------------------------------------

    /** Fator entre pixels da captura e pixels da tela (normalmente 1). */
    private float scale(int bitmapSize, boolean horizontal) {
        try {
            Rect r = wm.getMaximumWindowMetrics().getBounds();
            int real = horizontal ? r.width() : r.height();
            return real > 0 ? bitmapSize / (float) real : 1f;
        } catch (RuntimeException e) {
            return 1f;
        }
    }

    /** % de pixels com a cor do sinal ao redor do ponto de detecção. */
    private int fraction(final Bitmap b) {
        int cx = Math.round(cfg.detectX * scale(b.getWidth(), true));
        int cy = Math.round(cfg.detectY * scale(b.getHeight(), false));
        return ColorMatch.fraction(new ColorMatch.Pixels() {
            @Override
            public int get(int x, int y) {
                return b.getPixel(x, y);
            }
        }, b.getWidth(), b.getHeight(), cx, cy, cfg.boxRadius, cfg.detectColor, cfg.tolerance);
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

    public void toggleTest() {
        if (testing) {
            stopAll();
            return;
        }
        cfg = ConfigStore.load(this);
        if (cfg.detectX < 0) {
            addLog("Calibre a pesca primeiro.");
            return;
        }
        stopAll();
        testing = true;
        token++;
        testLine = "Lendo a cor... deixe o sinal APAGADO uns segundos, depois faça o sinal aparecer.";
        testBase = 100;
        addLog("Modo teste: veja se o sinal vira DETECTADO.");
        testLoop(token);
        notifyUi();
    }

    private void testLoop(final int tok) {
        if (tok != token || !testing) {
            return;
        }
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                boolean live = tok == token && testing;
                if (live) {
                    int f = fraction(b);
                    testBase = Math.min(testBase, f);
                    int need = Math.min(100, testBase + cfg.detectMinPct);
                    int cx = Math.max(0, Math.min(b.getWidth() - 1, Math.round(cfg.detectX * scale(b.getWidth(), true))));
                    int cy = Math.max(0, Math.min(b.getHeight() - 1, Math.round(cfg.detectY * scale(b.getHeight(), false))));
                    boolean hit = ColorMatch.signalHit(f, testBase, cfg.detectMinPct);
                    testLine = "Pixel " + ColorMatch.hex(b.getPixel(cx, cy)) + " | sinal " + ColorMatch.hex(cfg.detectColor)
                            + "\nCor do sinal na área: " + f + "% (fundo ~" + testBase + "%, precisa " + need + "%)"
                            + (ColorMatch.detectable(testBase, cfg.detectMinPct) ? "" : "\nSem como detectar: fundo parecido com o sinal")
                            + "\n-> " + (hit ? "DETECTADO" : "não detectado");
                    notifyUi();
                }
                b.recycle();
                if (live) {
                    laterTest(tok, 400);
                }
            }

            @Override
            public void onFail(int code) {
                if (tok == token && testing) {
                    laterTest(tok, 500);
                }
            }
        });
    }

    private void laterTest(final int tok, long delay) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                testLoop(tok);
            }
        }, delay);
    }

    // ------------------------------------------------------------------
    // Calibração em tela cheia
    // ------------------------------------------------------------------

    /** Passos: slot da vara (se ligado), segurar, tocar, sinal. */
    public void startCalibration() {
        stopAll();
        removeCalibration();
        cfg = ConfigStore.load(this);
        calSteps = cfg.equipOn == 1
                ? new int[]{CAL_EQUIP, CAL_HOLD, CAL_TAP, CAL_DETECT, CAL_SIGNAL}
                : new int[]{CAL_HOLD, CAL_TAP, CAL_DETECT, CAL_SIGNAL};
        calIdx = 0;
        calibView = new FrameLayout(this);
        calLabel = new TextView(this);
        calLabel.setTextSize(14);
        calLabel.setTextColor(0xFFFFFFFF);
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
        calLp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wm.addView(calibView, calLp);
        updateCalLabel();
    }

    private void updateCalLabel() {
        if (calLabel == null || calSteps == null) {
            return;
        }
        String n = calSteps.length > 1 ? (calIdx + 1) + "/" + calSteps.length + "  " : "";
        String msg;
        switch (calSteps[Math.min(calIdx, calSteps.length - 1)]) {
            case CAL_HOLD:
                msg = "Toque no ponto onde o jogo deve ser SEGURADO (área de lançar).";
                break;
            case CAL_TAP:
                msg = "Toque no ponto onde o jogo recebe os TOQUES rápidos (puxar o peixe).";
                break;
            case CAL_EQUIP:
                msg = "Toque no SLOT DA VARA no inventário/hotbar (onde você toca para pegar a vara).";
                break;
            case CAL_DETECT:
                msg = "Toque no ponto EXATO onde o sinal de fisgada aparece (ex.: o '!'), mesmo que ele ainda não esteja na tela.";
                break;
            default:
                msg = "Agora LANCE a vara normalmente (a tela do jogo está liberada). Quando o sinal aparecer, "
                        + "toque em CAPTURAR SINAL. Se o sinal for rápido demais, use o botão com a outra mão.";
                break;
        }
        calLabel.setText(n + msg);
    }

    private void onCalibTouch(final int x, final int y) {
        int step = calSteps[calIdx];
        switch (step) {
            case CAL_HOLD:
                cfg.holdX = x;
                cfg.holdY = y;
                break;
            case CAL_TAP:
                cfg.tapX = x;
                cfg.tapY = y;
                break;
            case CAL_EQUIP:
                cfg.equipX = x;
                cfg.equipY = y;
                break;
            default:
                cfg.detectX = x;
                cfg.detectY = y;
                break;
        }
        calIdx++;
        updateCalLabel();
        if (calSteps[calIdx] == CAL_SIGNAL) {
            enterSignalStep();
        }
    }

    /** Libera os toques para o jogo e mostra só um botão pequeno para capturar a cor do sinal. */
    private void enterSignalStep() {
        calLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(calibView, calLp);
        calBtnView = new LinearLayout(this);
        calBtnView.setOrientation(LinearLayout.HORIZONTAL);
        calBtnView.setBackgroundColor(0xEE000000);
        calBtnView.setPadding(dp(6), dp(6), dp(6), dp(6));
        TextView cap = signalButton("CAPTURAR SINAL", 0xFF2DD4A8, 0xFF04201A, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureReference(cfg.detectX, cfg.detectY);
            }
        });
        TextView cancel = signalButton("Cancelar", 0xFF1B283B, 0xFFFFFFFF, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeCalibration();
                addLog("Calibração cancelada.");
            }
        });
        calBtnView.addView(cap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        calBtnView.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // fica longe do ponto do sinal: metade de cima da tela -> botão embaixo, e vice-versa
        int screenH = getResources().getDisplayMetrics().heightPixels;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(260), ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        boolean signalIsUp = cfg.detectY < screenH / 2;
        lp.gravity = (signalIsUp ? Gravity.BOTTOM : Gravity.TOP) | Gravity.END;
        lp.x = dp(8);
        lp.y = dp(signalIsUp ? 60 : 110);
        wm.addView(calBtnView, lp);
    }

    private TextView signalButton(String label, int bg, int fg, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(fg);
        t.setBackgroundColor(bg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(12), dp(6), dp(12));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(p);
        return t;
    }

    private void captureReference(final int x, final int y) {
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                int cx = Math.max(0, Math.min(b.getWidth() - 1, Math.round(x * scale(b.getWidth(), true))));
                int cy = Math.max(0, Math.min(b.getHeight() - 1, Math.round(y * scale(b.getHeight(), false))));
                cfg.detectColor = b.getPixel(cx, cy) | 0xFF000000;
                int f = fraction(b);
                b.recycle();
                removeCalibration();
                saveKeys("equipX", "equipY", "holdX", "holdY", "tapX", "tapY", "detectX", "detectY", "detectColor");
                addLog("Pesca calibrada. Sinal (" + x + "," + y + ") cor " + ColorMatch.hex(cfg.detectColor)
                        + " (" + f + "% da área).");
                if (f < cfg.detectMinPct) {
                    addLog("AVISO: o sinal ocupa só " + f + "% da área. Diminua o raio ou a % mínima, ou toque no centro do sinal.");
                }
                addLog("Use 'Testar': deixe o sinal apagado, depois faça ele aparecer.");
            }

            @Override
            public void onFail(int code) {
                removeCalibration();
                addLog("Não consegui capturar a tela (código " + code + "). Calibre de novo.");
            }
        });
    }

    /** Salva só as chaves alteradas, sem sobrescrever o que o app mudou nesse meio tempo. */
    private void saveKeys(String... keys) {
        cfg = ConfigStore.update(this, cfg, keys);
    }

    private void removeCalibration() {
        if (calibView != null && wm != null) {
            try {
                wm.removeView(calibView);
            } catch (RuntimeException ignored) {
                // já removido
            }
        }
        if (calBtnView != null && wm != null) {
            try {
                wm.removeView(calBtnView);
            } catch (RuntimeException ignored) {
                // já removido
            }
        }
        calBtnView = null;
        calibView = null;
        calLabel = null;
        notifyUi();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private boolean covers(int x, int y) {
        AvoidProvider a = avoid;
        Rect r = a == null ? null : a.rect();
        if (r == null) {
            return false;
        }
        int m = dp(4);
        return x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m;
    }

    private static Path point(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
