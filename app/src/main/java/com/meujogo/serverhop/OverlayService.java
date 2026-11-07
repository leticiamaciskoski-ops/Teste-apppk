package com.meujogo.serverhop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Painel flutuante (sobreposição de tela): fica por cima do Roblox e troca de servidor sem sair do
 * jogo. Pode ficar aberto como painel ou recolhido em uma bolha. Precisa da permissão "exibir sobre
 * outros apps" e roda como serviço em primeiro plano (com uma notificação para desligar).
 */
public class OverlayService extends Service {

    public static final String ACTION_STOP = "com.meujogo.serverhop.action.STOP";
    private static final String CHANNEL = "overlay";
    private static final int NOTIF_ID = 31;

    /** O app consulta isto para mostrar se o painel está ligado. */
    public static volatile boolean running;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout panel;
    private FrameLayout bubble;
    private TextView tvStatus;
    private TextView tvResult;
    private TextView chipEmpty;
    private TextView chipBusy;
    private TextView btnHop;
    private Config cfg;
    private boolean added;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (!added) {
            showOverlay();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (added && root != null) {
            try {
                wm.removeView(root);
            } catch (RuntimeException ignored) {
                // já removido
            }
        }
        added = false;
        running = false;
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Notificação do serviço em primeiro plano
    // ------------------------------------------------------------------

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Painel flutuante",
                NotificationManager.IMPORTANCE_LOW));

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, OverlayService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent pStop = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_hop)
                .setContentTitle("Server Hop ligado")
                .setContentText("Painel flutuante ativo. Toque para abrir o app.")
                .setContentIntent(pOpen)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat_hop), "Desligar", pStop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    // ------------------------------------------------------------------
    // Janela flutuante
    // ------------------------------------------------------------------

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        cfg = ConfigStore.load(this);
        buildViews();

        lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = cfg.overlayX;
        lp.y = cfg.overlayY;
        clampPosition();
        try {
            wm.addView(root, lp);
            added = true;
        } catch (RuntimeException e) {
            stopSelf();
        }
    }

    private void buildViews() {
        root = new FrameLayout(this);

        // ---- painel aberto ----
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));
        panel.setBackground(Ui.shape(this, 0xF2141E2E, Ui.STROKE, 1, 18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "≡  Server Hop", 14, Ui.ACCENT, true);
        title.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        attachDrag(title, false);
        TextView btnMin = headButton("–", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOpen(false);
            }
        });
        TextView btnClose = headButton("✕", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(btnMin, Ui.params(this, Ui.dp(this, 36), Ui.dp(this, 32), 6, 0, 0, 0));
        head.addView(btnClose, Ui.params(this, Ui.dp(this, 36), Ui.dp(this, 32), 6, 0, 0, 0));
        panel.addView(head);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipEmpty = Ui.chip(this, HopMode.EMPTY.label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(0);
            }
        });
        chipBusy = Ui.chip(this, HopMode.BUSY.label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(1);
            }
        });
        chipEmpty.setTextSize(13);
        chipBusy.setTextSize(13);
        chips.addView(chipEmpty, Ui.params(this, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 4, 0));
        ((LinearLayout.LayoutParams) chipEmpty.getLayoutParams()).weight = 1f;
        chips.addView(chipBusy, Ui.params(this, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 0, 0, 0));
        ((LinearLayout.LayoutParams) chipBusy.getLayoutParams()).weight = 1f;
        panel.addView(chips, Ui.params(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));

        btnHop = Ui.button(this, "Trocar de servidor", Ui.ACCENT, Ui.ON_ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doHop();
            }
        });
        panel.addView(btnHop, Ui.params(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));

        tvResult = Ui.text(this, "", 13, Ui.TEXT, true);
        panel.addView(tvResult, Ui.params(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        tvStatus = Ui.text(this, "Pronto.", 12, Ui.MUTED, false);
        panel.addView(tvStatus, Ui.params(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 2, 0, 0));

        root.addView(panel, new FrameLayout.LayoutParams(Ui.dp(this, 252), ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- bolha recolhida ----
        bubble = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xF20B1A2E);
        circle.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        bubble.setBackground(circle);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_bubble);
        bubble.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52), Gravity.CENTER));
        attachDrag(bubble, true);
        root.addView(bubble, new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        paintChips();
        applyOpen(cfg.overlayOpen == 1);
    }

    private TextView headButton(String label, View.OnClickListener l) {
        TextView t = Ui.text(this, label, 16, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.shape(this, Ui.CHIP_OFF, 0, 0, 10));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------
    // Estado e ações
    // ------------------------------------------------------------------

    private void setOpen(boolean open) {
        cfg.overlayOpen = open ? 1 : 0;
        applyOpen(open);
        ConfigStore.save(this, cfg);
        if (added) {
            clampPosition();
            wm.updateViewLayout(root, lp);
        }
    }

    private void applyOpen(boolean open) {
        panel.setVisibility(open ? View.VISIBLE : View.GONE);
        bubble.setVisibility(open ? View.GONE : View.VISIBLE);
    }

    private void setMode(int mode) {
        cfg.mode = mode;
        ConfigStore.save(this, cfg);
        paintChips();
        tvStatus.setText("Tipo: " + cfg.hopMode().label + " (" + cfg.hopMode().describe(ServerHop.DEFAULT_CAP) + ")");
    }

    private void paintChips() {
        Ui.setChip(chipEmpty, this, cfg.mode == 0);
        Ui.setChip(chipBusy, this, cfg.mode == 1);
    }

    private void doHop() {
        // recarrega: o modo/jogo pode ter sido mudado na tela do app
        cfg = ConfigStore.load(this);
        paintChips();
        tvResult.setText("");
        HopController.hop(getApplicationContext(), cfg.placeId, cfg.hopMode(), cfg.maxPages, false,
                new HopController.Listener() {
                    @Override
                    public void onStatus(String msg) {
                        if (tvStatus != null) {
                            tvStatus.setText(msg);
                        }
                    }

                    @Override
                    public void onPicked(ServerHop.Server s) {
                        if (tvResult != null) {
                            tvResult.setText(ServerHop.describe(s));
                        }
                    }
                });
    }

    // ------------------------------------------------------------------
    // Arrastar
    // ------------------------------------------------------------------

    private void attachDrag(View handle, final boolean tapOpens) {
        final int slop = Ui.dp(this, 8);
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (lp == null || !added) {
                    return false;
                }
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!moved && Math.abs(dx) + Math.abs(dy) > slop) {
                            moved = true;
                        }
                        if (moved) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            clampPosition();
                            wm.updateViewLayout(root, lp);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            cfg.overlayX = lp.x;
                            cfg.overlayY = lp.y;
                            ConfigStore.save(OverlayService.this, cfg);
                        } else if (tapOpens) {
                            setOpen(true);
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    /** Mantém o painel dentro da tela (o celular pode ter girado). */
    private void clampPosition() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = root.getWidth() > 0 ? root.getWidth() : Ui.dp(this, 56);
        int h = root.getHeight() > 0 ? root.getHeight() : Ui.dp(this, 56);
        int maxX = Math.max(0, dm.widthPixels - w);
        int maxY = Math.max(0, dm.heightPixels - h);
        lp.x = Math.max(0, Math.min(lp.x, maxX));
        lp.y = Math.max(0, Math.min(lp.y, maxY));
    }
}
