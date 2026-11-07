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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Motor da pesca automática (serviço de acessibilidade).
 *
 * Rota: ANDAR até a poça (joystick) -> PESCAR por fishSec (segurar, esperar o sinal lendo a cor
 * de um pixel, dar os toques rápidos, repetir) -> ANDAR para sair -> ANDAR de novo até a poça -> ...
 *
 * Usa dispatchGesture (segurar, tocar, arrastar o joystick) e takeScreenshot (ler a cor, Android 11+).
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

    private static final int CAL_HOLD = 0;
    private static final int CAL_TAP = 1;
    private static final int CAL_JOY = 2;
    private static final int CAL_DETECT = 3;

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
    private Route.Phase phase = Route.Phase.FISH;
    private long phaseEnd;      // uptime em que a fase termina (Long.MAX_VALUE = sem fim)
    private int fishRounds;
    private String testLine = "";

    private FrameLayout calibView;
    private TextView calLabel;
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

    /** Linha de estado: fase atual e tempo restante. */
    public String phaseLine() {
        if (testing) {
            return "Modo teste";
        }
        if (!running) {
            return "Parado";
        }
        if (cfg.routeOn == 1 && phaseEnd != Long.MAX_VALUE) {
            long left = phaseEnd - SystemClock.uptimeMillis();
            return phase.label + " · restam " + Route.clock(left);
        }
        return phase.label;
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
        boolean route = cfg.routeOn == 1;
        if (!cfg.fishCalibrated()) {
            addLog("Calibre a pesca primeiro (3 pontos).");
            return;
        }
        if (route && !cfg.joystickCalibrated()) {
            addLog("Calibre o joystick (ou desligue a rota nos ajustes).");
            return;
        }
        if (covers(cfg.holdX, cfg.holdY) || covers(cfg.tapX, cfg.tapY) || covers(cfg.detectX, cfg.detectY)
                || (route && covers(cfg.joyX, cfg.joyY))) {
            addLog("O painel cobre um ponto calibrado. Mova o painel ou recolha-o.");
            return;
        }
        testing = false;
        running = true;
        token++;
        stats.reset();
        fishRounds = 0;
        long est = TapPlan.estimateWindowMs(cfg.taps, cfg.spacingMs, cfg.tapDurMs,
                GestureDescription.getMaxStrokeCount(), 60);
        addLog("Iniciado. Toques ~" + est + " ms (jogo: " + cfg.tapWindowMs + ")");
        if (route) {
            addLog("Rota: andar " + cfg.walkSec + " s " + Route.compass(cfg.walkAngle) + ", pescar "
                    + Route.clock(cfg.fishSec * 1000L) + ", sair " + cfg.backSec + " s "
                    + Route.compass(cfg.backAngle) + ", repetir.");
        }
        enterPhase(token, Route.first(route, cfg.startAtPond == 1));
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

    // ------------------------------------------------------------------
    // Fases da rota
    // ------------------------------------------------------------------

    private boolean alive(int tok) {
        return tok == token && running;
    }

    private void enterPhase(final int tok, Route.Phase p) {
        if (!alive(tok)) {
            return;
        }
        phase = p;
        boolean route = cfg.routeOn == 1;
        switch (p) {
            case FISH:
                phaseEnd = route ? SystemClock.uptimeMillis() + cfg.fishSec * 1000L : Long.MAX_VALUE;
                fishRounds++;
                addLog(route ? "Pescando por " + Route.clock(cfg.fishSec * 1000L) + " (rodada " + fishRounds + ")"
                        : "Pescando.");
                startCycle(tok);
                break;
            case WALK_TO:
                walk(tok, p, cfg.walkAngle, cfg.walkSec);
                break;
            default:
                walk(tok, p, cfg.backAngle, cfg.backSec);
                break;
        }
        notifyUi();
    }

    /** Anda arrastando o dedo do centro do joystick até a borda e mantendo ali. */
    private void walk(final int tok, final Route.Phase p, int angle, int sec) {
        final long ms = Route.jitter(sec * 1000L, cfg.walkJitterPct, rng);
        phaseEnd = SystemClock.uptimeMillis() + ms + 150;
        final int radius = Math.round(cfg.joyRadiusDp * getResources().getDisplayMetrics().density);
        final int[] t = Route.joystickTarget(cfg.joyX, cfg.joyY, radius, angle);
        addLog(p.label + ": " + Route.compass(angle) + " por " + (ms / 100) / 10.0 + " s");

        Path p1 = new Path();
        p1.moveTo(cfg.joyX, cfg.joyY);
        p1.lineTo(t[0], t[1]);
        final GestureDescription.StrokeDescription s1 =
                new GestureDescription.StrokeDescription(p1, 0, 150, true);
        boolean ok = dispatchGesture(new GestureDescription.Builder().addStroke(s1).build(),
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription d) {
                        if (!alive(tok)) {
                            return;
                        }
                        GestureDescription.StrokeDescription s2 = s1.continueStroke(point(t[0], t[1]), 0, ms, false);
                        boolean ok2 = dispatchGesture(new GestureDescription.Builder().addStroke(s2).build(),
                                new GestureResultCallback() {
                                    @Override
                                    public void onCompleted(GestureDescription d2) {
                                        afterWalk(tok, p);
                                    }

                                    @Override
                                    public void onCancelled(GestureDescription d2) {
                                        retryPhase(tok, p, "Caminhada cancelada pelo sistema.");
                                    }
                                }, null);
                        if (!ok2) {
                            retryPhase(tok, p, "Não foi possível manter o joystick.");
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription d) {
                        retryPhase(tok, p, "Caminhada cancelada pelo sistema.");
                    }
                }, null);
        if (!ok) {
            retryPhase(tok, p, "Não foi possível mexer no joystick (acessibilidade sem gestos?).");
        }
    }

    private void afterWalk(final int tok, final Route.Phase p) {
        if (!alive(tok)) {
            return;
        }
        notifyUi();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                enterPhase(tok, Route.next(p, cfg.routeOn == 1));
            }
        }, cfg.pauseSec * 1000L);
    }

    private void retryPhase(final int tok, final Route.Phase p, String why) {
        if (!alive(tok)) {
            return;
        }
        stats.cancelled++;
        addLog(why + " Tentando de novo.");
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                enterPhase(tok, p);
            }
        }, 1500);
    }

    // ------------------------------------------------------------------
    // Um ciclo de pesca: segurar -> esperar sinal -> tocar
    // ------------------------------------------------------------------

    private void startCycle(final int tok) {
        if (!alive(tok)) {
            return;
        }
        if (cfg.routeOn == 1 && SystemClock.uptimeMillis() >= phaseEnd) {
            addLog("Tempo de pesca acabou.");
            enterPhase(tok, Route.next(Route.Phase.FISH, true));
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
                if (!alive(tok)) {
                    return;
                }
                waitStart = SystemClock.uptimeMillis();
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
        grab(new BitmapCb() {
            @Override
            public void onBitmap(Bitmap b) {
                boolean live = alive(tok);
                boolean hit = live && bestDiff(b) <= cfg.tolerance;
                b.recycle();
                if (!live) {
                    return;
                }
                if (hit) {
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
        testLine = "Lendo a cor...";
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
                    int d = bestDiff(b);
                    int cx = Math.max(0, Math.min(b.getWidth() - 1, cfg.detectX));
                    int cy = Math.max(0, Math.min(b.getHeight() - 1, cfg.detectY));
                    testLine = "Pixel " + ColorMatch.hex(b.getPixel(cx, cy)) + " | alvo "
                            + ColorMatch.hex(cfg.detectColor) + "\nDiferença " + d + " (tol. " + cfg.tolerance + ") -> "
                            + (d <= cfg.tolerance ? "DETECTADO" : "não detectado");
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

    /** joystickOnly = só o centro do joystick; senão os 3 pontos da pesca (segurar, tocar, sinal). */
    public void startCalibration(boolean joystickOnly) {
        stopAll();
        removeCalibration();
        cfg = ConfigStore.load(this);
        calSteps = joystickOnly ? new int[]{CAL_JOY} : new int[]{CAL_HOLD, CAL_TAP, CAL_DETECT};
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
            case CAL_JOY:
                msg = "Toque no CENTRO do joystick: o ponto da tela onde você põe o dedo para andar.";
                break;
            default:
                msg = "Deixe o jogo chegar na fisgada e toque SOBRE o sinal (ex.: o '!'). "
                        + "O sinal precisa ficar visível por ~1 s; um pisca-pisca muito rápido não é detectável.";
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
            case CAL_JOY:
                cfg.joyX = x;
                cfg.joyY = y;
                saveKeys("joyX", "joyY");
                removeCalibration();
                addLog("Joystick calibrado em (" + x + "," + y + ").");
                return;
            default:
                removeCalibration(); // tira a camada para ela não aparecer na captura
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        captureReference(x, y);
                    }
                }, 250);
                return;
        }
        calIdx++;
        updateCalLabel();
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
                saveKeys("holdX", "holdY", "tapX", "tapY", "detectX", "detectY", "detectColor");
                addLog("Pesca calibrada. Sinal (" + cx + "," + cy + ") cor " + ColorMatch.hex(cfg.detectColor));
                addLog("Use 'Testar' para conferir a detecção.");
            }

            @Override
            public void onFail(int code) {
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
