package com.meujogo.serverhop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Tela principal: trocar de servidor, escolher o tipo, o jogo, painel flutuante e tela dividida. */
public class MainActivity extends Activity {

    private Config cfg;
    private TextView tvStatus;
    private TextView tvResult;
    private TextView tvHistory;
    private TextView btnHop;
    private TextView chipEmpty;
    private TextView chipBusy;
    private TextView tvModeHint;
    private TextView tvGame;
    private TextView tvOverlayState;
    private TextView btnOverlay;
    private TextView tvWindow;
    private EditText etGame;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = ConfigStore.load(this);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = ConfigStore.load(this);
        refresh();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
        refresh();
    }

    @Override
    public void onMultiWindowModeChanged(boolean in, Configuration c) {
        super.onMultiWindowModeChanged(in, c);
        refresh();
    }

    // ------------------------------------------------------------------
    // Construção da tela
    // ------------------------------------------------------------------

    private void build() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 16);
        outer.setPadding(pad, pad, pad, pad);
        scroll.addView(outer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int w = getResources().getConfiguration().screenWidthDp;
        int width = w > 600 ? Ui.dp(this, 560) : ViewGroup.LayoutParams.MATCH_PARENT;
        outer.addView(col, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- cabeçalho ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.text(this, "Server Hop", 26, Ui.TEXT, true));
        head.addView(Ui.text(this, "Troque de servidor sem sair do jogo", 14, Ui.MUTED, false));
        col.addView(head, Ui.params(this, -1, -2, 4, 4, 4, 14));

        // ---- cartão: trocar de servidor ----
        LinearLayout c1 = Ui.card(this);
        btnHop = Ui.button(this, "Trocar de servidor", Ui.ACCENT, Ui.ON_ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hop(false);
            }
        });
        btnHop.setTextSize(18);
        btnHop.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        c1.addView(btnHop, Ui.params(this, -1, -2, 0, 0, 0, 0));
        tvResult = Ui.text(this, "", 15, Ui.ACCENT, true);
        c1.addView(tvResult, Ui.params(this, -1, -2, 0, 12, 0, 0));
        tvStatus = Ui.text(this, "Pronto. Escolha o tipo e toque no botão.", 13, Ui.MUTED, false);
        c1.addView(tvStatus, Ui.params(this, -1, -2, 0, 4, 0, 0));
        tvHistory = Ui.text(this, "", 12, Ui.MUTED, false);
        c1.addView(tvHistory, Ui.params(this, -1, -2, 0, 10, 0, 0));
        col.addView(c1, Ui.params(this, -1, -2, 0, 0, 0, 12));

        // ---- cartão: tipo de servidor ----
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Tipo de servidor", 16, Ui.TEXT, true));
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
        LinearLayout.LayoutParams pe = Ui.params(this, 0, -2, 0, 0, 6, 0);
        pe.weight = 1f;
        LinearLayout.LayoutParams pb = Ui.params(this, 0, -2, 6, 0, 0, 0);
        pb.weight = 1f;
        chips.addView(chipEmpty, pe);
        chips.addView(chipBusy, pb);
        c2.addView(chips, Ui.params(this, -1, -2, 0, 10, 0, 0));
        tvModeHint = Ui.text(this, "", 13, Ui.MUTED, false);
        c2.addView(tvModeHint, Ui.params(this, -1, -2, 0, 10, 0, 0));
        TextView note = Ui.text(this,
                "O Roblox não informa nível nem tempo de conta dos jogadores, então não dá para filtrar \"iniciantes\". "
                        + "Servidores com status desconhecido (sem fps/ping) ou ruins (fps < 30, ping > 500 ms) são sempre ignorados.",
                12, Ui.WARN, false);
        c2.addView(note, Ui.params(this, -1, -2, 0, 10, 0, 0));
        col.addView(c2, Ui.params(this, -1, -2, 0, 0, 0, 12));

        // ---- cartão: jogo ----
        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Jogo", 16, Ui.TEXT, true));
        tvGame = Ui.text(this, "", 14, Ui.MUTED, false);
        c3.addView(tvGame, Ui.params(this, -1, -2, 0, 6, 0, 0));
        etGame = new EditText(this);
        etGame.setHint("Link do jogo ou número (Place ID)");
        etGame.setHintTextColor(0xFF5C6F8A);
        etGame.setTextColor(Ui.TEXT);
        etGame.setTextSize(14);
        etGame.setSingleLine(true);
        etGame.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etGame.setBackground(Ui.shape(this, Ui.CHIP_OFF, Ui.STROKE, 1, 12));
        etGame.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        c3.addView(etGame, Ui.params(this, -1, -2, 0, 10, 0, 0));
        c3.addView(Ui.button(this, "Usar este jogo", Ui.CHIP_OFF, Ui.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useGame();
            }
        }), Ui.params(this, -1, -2, 0, 10, 0, 0));
        col.addView(c3, Ui.params(this, -1, -2, 0, 0, 0, 12));

        // ---- cartão: painel flutuante ----
        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.text(this, "Painel flutuante", 16, Ui.TEXT, true));
        c4.addView(Ui.text(this, "Fica por cima do Roblox: troque de servidor sem voltar para este app.", 13,
                Ui.MUTED, false), Ui.params(this, -1, -2, 0, 4, 0, 0));
        tvOverlayState = Ui.text(this, "", 13, Ui.MUTED, true);
        c4.addView(tvOverlayState, Ui.params(this, -1, -2, 0, 8, 0, 0));
        btnOverlay = Ui.button(this, "", Ui.ACCENT, Ui.ON_ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOverlay();
            }
        });
        c4.addView(btnOverlay, Ui.params(this, -1, -2, 0, 10, 0, 0));
        col.addView(c4, Ui.params(this, -1, -2, 0, 0, 0, 12));

        // ---- cartão: tela dividida ----
        LinearLayout c5 = Ui.card(this);
        c5.addView(Ui.text(this, "Tela dividida", 16, Ui.TEXT, true));
        tvWindow = Ui.text(this, "", 13, Ui.MUTED, false);
        c5.addView(tvWindow, Ui.params(this, -1, -2, 0, 4, 0, 0));
        c5.addView(Ui.button(this, "Abrir Roblox ao lado", Ui.CHIP_OFF, Ui.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                splitScreen();
            }
        }), Ui.params(this, -1, -2, 0, 10, 0, 0));
        col.addView(c5, Ui.params(this, -1, -2, 0, 0, 0, 12));

        col.addView(Ui.text(this,
                "Usa só a lista pública de servidores do Roblox. Use por sua conta: o Roblox pode limitar consultas "
                        + "(cerca de 3 por minuto) e não é afiliado a este app.", 11, Ui.MUTED, false),
                Ui.params(this, 4, 0, 4, 8, 4, 8));

        setContentView(scroll);
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    private void refresh() {
        if (tvStatus == null) {
            return;
        }
        Ui.setChip(chipEmpty, this, cfg.mode == 0);
        Ui.setChip(chipBusy, this, cfg.mode == 1);
        HopMode m = cfg.hopMode();
        tvModeHint.setText(m == HopMode.EMPTY
                ? "Vazio: servidores com 1 a 2 jogadores (na prática, quase sempre 1)."
                : "Movimentado: servidores quase cheios, com mais gente. É o mais perto de \"mais iniciantes\" que a lista permite.");
        tvGame.setText("Atual: " + cfg.placeName + "  ·  " + cfg.placeId);

        boolean perm = Settings.canDrawOverlays(this);
        if (!perm) {
            tvOverlayState.setText("Permissão: não concedida");
            tvOverlayState.setTextColor(Ui.WARN);
            btnOverlay.setText("Permitir exibir sobre outros apps");
        } else if (OverlayService.running) {
            tvOverlayState.setText("Painel: ligado");
            tvOverlayState.setTextColor(Ui.ACCENT);
            btnOverlay.setText("Desligar painel flutuante");
        } else {
            tvOverlayState.setText("Painel: desligado");
            tvOverlayState.setTextColor(Ui.MUTED);
            btnOverlay.setText("Ligar painel flutuante");
        }

        boolean multi = isInMultiWindowMode();
        tvWindow.setText(multi
                ? "Este app já está em tela dividida/janela. O Roblox abrirá no outro lado."
                : "Para dividir: abra os apps recentes, toque no ícone do Server Hop e escolha \"Dividir tela\". "
                + "Depois use o botão abaixo para abrir o Roblox no outro lado. (Nem todo celular/jogo aceita.)");

        List<String> h = HopController.history();
        StringBuilder sb = new StringBuilder();
        for (String s : h) {
            sb.append(sb.length() == 0 ? "Recentes:\n" : "\n").append(s);
        }
        tvHistory.setText(sb.toString());
        tvHistory.setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setMode(int mode) {
        cfg.mode = mode;
        ConfigStore.save(this, cfg);
        refresh();
    }

    private void hop(boolean adjacent) {
        tvResult.setText("");
        btnHop.setAlpha(0.6f);
        HopController.hop(getApplicationContext(), cfg.placeId, cfg.hopMode(), cfg.maxPages,
                adjacent && isInMultiWindowMode(), new HopController.Listener() {
                    @Override
                    public void onStatus(String msg) {
                        tvStatus.setText(msg);
                        btnHop.setAlpha(HopController.isBusy() ? 0.6f : 1f);
                    }

                    @Override
                    public void onPicked(ServerHop.Server s) {
                        tvResult.setText(ServerHop.describe(s));
                        refresh();
                    }
                });
    }

    private void useGame() {
        final String raw = etGame.getText().toString();
        final long id = ServerHop.parseId(raw);
        if (id <= 0) {
            tvStatus.setText("Não entendi. Cole o link do jogo ou o número.");
            return;
        }
        tvStatus.setText("Procurando o jogo...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    final RobloxApi.GameInfo g = RobloxApi.resolveGame(id);
                    if (g == null) {
                        msg = "Jogo não encontrado.";
                    } else {
                        cfg.placeId = g.rootPlaceId;
                        cfg.placeName = g.name;
                        ConfigStore.save(MainActivity.this, cfg);
                        msg = "Jogo definido: " + g.name;
                    }
                } catch (Exception e) {
                    msg = HopController.explain(e);
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText(m);
                        refresh();
                    }
                });
            }
        }, "resolve-game").start();
    }

    // ------------------------------------------------------------------
    // Painel flutuante
    // ------------------------------------------------------------------

    private void toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                tvStatus.setText("Abra Configurações > Apps > Server Hop > Exibir sobre outros apps.");
            }
            return;
        }
        if (OverlayService.running) {
            stopService(new Intent(this, OverlayService.class));
            OverlayService.running = false;
            refresh();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        startForegroundService(new Intent(this, OverlayService.class));
        tvStatus.setText("Painel ligado. Abra o Roblox: ele fica por cima.");
        tvOverlayState.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 400);
    }

    // ------------------------------------------------------------------
    // Tela dividida
    // ------------------------------------------------------------------

    private void splitScreen() {
        if (isInMultiWindowMode()) {
            hop(true);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Primeiro divida a tela")
                .setMessage("O Android só deixa o próprio sistema dividir a tela:\n\n"
                        + "1. Abra os apps recentes.\n"
                        + "2. Toque no ícone do Server Hop no topo do cartão.\n"
                        + "3. Escolha \"Dividir tela\" (ou \"Abrir em janela\").\n"
                        + "4. Volte aqui e toque de novo em \"Abrir Roblox ao lado\".")
                .setPositiveButton("Entendi", null)
                .show();
    }
}
