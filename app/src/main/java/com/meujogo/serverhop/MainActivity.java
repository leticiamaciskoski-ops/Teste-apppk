package com.meujogo.serverhop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Tela principal, em três seções: Server Hop, Pesca (rota automática) e Janelas (painel flutuante
 * e tela dividida).
 */
public class MainActivity extends Activity {

    private static final String[] ROUTE_KEYS = {"walkSec", "fishSec", "backSec", "pauseSec", "walkJitterPct", "joyRadiusDp"};
    private static final String[] ROUTE_LABELS = {
            "Andar até a poça (segundos)", "Pescar por (segundos; 330 = 5 min 30)", "Andar ao sair da poça (segundos)",
            "Pausa depois de andar (segundos)", "Variação aleatória da caminhada (± %)",
            "Distância de arrasto do joystick (dp)"};
    private static final String[] FISH_KEYS = {"holdMs", "holdJitterMs", "taps", "spacingMs", "tapDurMs", "tapWindowMs",
            "tolerance", "pollMs", "maxWaitMs", "reactionMs", "resultDelayMs"};
    private static final String[] FISH_LABELS = {
            "Segurar para lançar (ms)", "Variação aleatória do segurar (± ms)", "Quantidade de toques",
            "Intervalo entre toques (ms)", "Duração de cada toque (ms)", "Janela do jogo para os toques (ms)",
            "Tolerância de cor (0-255)", "Intervalo de leitura da tela (ms, mín. 340)",
            "Tempo máximo esperando o sinal (ms)", "Atraso antes dos toques (ms)", "Pausa entre ciclos (ms)"};
    private static final int[] ANGLES = {0, 45, 90, 135, 180, 225, 270, 315};
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private Config cfg;
    private final List<LinearLayout> sections = new ArrayList<>();
    private final List<TextView> navChips = new ArrayList<>();
    private int section;

    // hop
    private TextView tvStatus;
    private TextView tvResult;
    private TextView tvHistory;
    private TextView btnHop;
    private TextView chipEmpty;
    private TextView chipBusy;
    private TextView tvModeHint;
    private TextView tvGame;
    private EditText etGame;
    // pesca
    private TextView tvA11y;
    private TextView chipRouteOn;
    private TextView chipRouteOff;
    private TextView chipStartSpawn;
    private TextView chipStartPond;
    private final TextView[] walkChips = new TextView[8];
    private final TextView[] backChips = new TextView[8];
    private TextView tvRouteSummary;
    private TextView tvCalib;
    private final List<EditText> fields = new ArrayList<>();
    private final List<String> fieldKeys = new ArrayList<>();
    // janelas
    private TextView tvOverlayState;
    private TextView btnOverlay;
    private TextView tvWindow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = ConfigStore.load(this);
        section = b == null ? 0 : b.getInt("section", 0);
        build();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("section", section);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = ConfigStore.load(this);
        loadFields();
        refresh();
    }

    @Override
    protected void onPause() {
        saveFields();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        saveFields();
        build();
        loadFields();
        refresh();
    }

    @Override
    public void onMultiWindowModeChanged(boolean in, Configuration c) {
        super.onMultiWindowModeChanged(in, c);
        refresh();
    }

    // ------------------------------------------------------------------
    // Construção
    // ------------------------------------------------------------------

    private void build() {
        sections.clear();
        navChips.clear();
        fields.clear();
        fieldKeys.clear();

        ScrollView scroll = new ScrollView(this);
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
        int width = getResources().getConfiguration().screenWidthDp > 600
                ? Ui.dp(this, 560) : ViewGroup.LayoutParams.MATCH_PARENT;
        outer.addView(col, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.text(this, "Server Hop", 26, Ui.TEXT, true));
        head.addView(Ui.text(this, "Troca de servidor e pesca automática, sobre o jogo", 14, Ui.MUTED, false));
        col.addView(head, Ui.params(this, -1, -2, 4, 4, 4, 12));

        // navegação
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Server hop", "Pesca", "Janelas"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = Ui.chip(this, names[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveFields();
                    section = idx;
                    showSection();
                }
            });
            LinearLayout.LayoutParams p = Ui.params(this, 0, -2, i == 0 ? 0 : 4, 0, i == 2 ? 0 : 4, 0);
            p.weight = 1f;
            nav.addView(t, p);
            navChips.add(t);
        }
        col.addView(nav, Ui.params(this, -1, -2, 0, 0, 0, 12));

        sections.add(buildHop());
        sections.add(buildFish());
        sections.add(buildWindows());
        for (LinearLayout s : sections) {
            col.addView(s, new LinearLayout.LayoutParams(-1, -2));
        }
        col.addView(Ui.text(this,
                "Usa só a lista pública de servidores do Roblox e gestos de acessibilidade no seu aparelho. "
                        + "Use por sua conta: não é afiliado ao Roblox, e o Roblox pode limitar consultas (cerca de 3 por minuto).",
                11, Ui.MUTED, false), Ui.params(this, -1, -2, 4, 8, 4, 8));
        setContentView(scroll);
        showSection();
    }

    private void showSection() {
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).setVisibility(i == section ? View.VISIBLE : View.GONE);
            Ui.setChip(navChips.get(i), this, i == section);
        }
    }

    // ---- Server hop ----

    private LinearLayout buildHop() {
        LinearLayout s = column();

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
        s.addView(c1, gap());

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
        c2.addView(Ui.text(this,
                "O Roblox não informa nível nem tempo de conta dos jogadores, então não dá para filtrar \"iniciantes\". "
                        + "Servidores com status desconhecido (sem fps/ping) ou ruins (fps < 30, ping > 500 ms) são sempre ignorados.",
                12, Ui.WARN, false), Ui.params(this, -1, -2, 0, 10, 0, 0));
        s.addView(c2, gap());

        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Jogo", 16, Ui.TEXT, true));
        tvGame = Ui.text(this, "", 14, Ui.MUTED, false);
        c3.addView(tvGame, Ui.params(this, -1, -2, 0, 6, 0, 0));
        etGame = field("Link do jogo ou número (Place ID)", false);
        etGame.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        c3.addView(etGame, Ui.params(this, -1, -2, 0, 10, 0, 0));
        c3.addView(Ui.button(this, "Usar este jogo", Ui.CHIP_OFF, Ui.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useGame();
            }
        }), Ui.params(this, -1, -2, 0, 10, 0, 0));
        s.addView(c3, gap());
        return s;
    }

    // ---- Pesca ----

    private LinearLayout buildFish() {
        LinearLayout s = column();

        // acessibilidade
        LinearLayout c0 = Ui.card(this);
        c0.addView(Ui.text(this, "Acessibilidade", 16, Ui.TEXT, true));
        tvA11y = Ui.text(this, "", 13, Ui.MUTED, true);
        c0.addView(tvA11y, Ui.params(this, -1, -2, 0, 6, 0, 0));
        c0.addView(Ui.text(this, "A pesca toca, segura e arrasta o joystick por gestos de acessibilidade e lê a cor de "
                + "um pixel da tela. Nada é coletado nem enviado.", 12, Ui.MUTED, false), Ui.params(this, -1, -2, 0, 4, 0, 0));
        c0.addView(Ui.button(this, "Abrir ajustes de acessibilidade", Ui.ACCENT, Ui.ON_ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    tvStatus.setText("Abra Configurações > Acessibilidade.");
                }
            }
        }), Ui.params(this, -1, -2, 0, 10, 0, 0));
        c0.addView(Ui.button(this, "Não consigo ativar / bloqueado pelo Android", Ui.CHIP_OFF, Ui.WARN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        helpRestricted();
                    }
                }), Ui.params(this, -1, -2, 0, 8, 0, 0));
        s.addView(c0, gap());

        // rota
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "Rota automática", 16, Ui.TEXT, true));
        c1.addView(Ui.text(this, "Anda até a poça, pesca pelo tempo definido, anda para sair e anda de novo "
                + "até a poça, em repetição.", 12, Ui.MUTED, false), Ui.params(this, -1, -2, 0, 4, 0, 0));
        c1.addView(chipRow(new String[]{"Rota ligada", "Só pescar parado"}, 0, new TextView[2], true),
                Ui.params(this, -1, -2, 0, 10, 0, 0));
        chipRouteOn = (TextView) ((LinearLayout) c1.getChildAt(c1.getChildCount() - 1)).getChildAt(0);
        chipRouteOff = (TextView) ((LinearLayout) c1.getChildAt(c1.getChildCount() - 1)).getChildAt(1);
        c1.addView(chipRow(new String[]{"Começo longe da poça", "Já estou na poça"}, 1, new TextView[2], true),
                Ui.params(this, -1, -2, 0, 8, 0, 0));
        chipStartSpawn = (TextView) ((LinearLayout) c1.getChildAt(c1.getChildCount() - 1)).getChildAt(0);
        chipStartPond = (TextView) ((LinearLayout) c1.getChildAt(c1.getChildCount() - 1)).getChildAt(1);

        c1.addView(Ui.text(this, "Direção até a poça", 13, Ui.TEXT, true), Ui.params(this, -1, -2, 0, 14, 0, 0));
        c1.addView(arrowRow(walkChips, true), Ui.params(this, -1, -2, 0, 6, 0, 0));
        c1.addView(Ui.text(this, "Direção ao sair da poça", 13, Ui.TEXT, true), Ui.params(this, -1, -2, 0, 12, 0, 0));
        c1.addView(arrowRow(backChips, false), Ui.params(this, -1, -2, 0, 6, 0, 0));
        c1.addView(Ui.text(this, "As direções valem para o personagem na tela do jogo; se a câmera girar, a direção real "
                + "muda. Ajuste os segundos testando.", 11, Ui.MUTED, false), Ui.params(this, -1, -2, 0, 8, 0, 0));
        for (int i = 0; i < ROUTE_KEYS.length; i++) {
            addField(c1, ROUTE_KEYS[i], ROUTE_LABELS[i]);
        }
        tvRouteSummary = Ui.text(this, "", 12, Ui.ACCENT, true);
        c1.addView(tvRouteSummary, Ui.params(this, -1, -2, 0, 12, 0, 0));
        s.addView(c1, gap());

        // calibração
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Calibração", 16, Ui.TEXT, true));
        tvCalib = Ui.text(this, "", 13, Ui.MUTED, false);
        c2.addView(tvCalib, Ui.params(this, -1, -2, 0, 6, 0, 0));
        c2.addView(Ui.text(this, "Calibre e inicie pelo painel flutuante, por cima do jogo (aba Pesca): "
                + "1) Calibrar pesca, 2) Calibrar joystick, 3) Testar, 4) Iniciar.", 12, Ui.MUTED, false),
                Ui.params(this, -1, -2, 0, 6, 0, 0));
        c2.addView(Ui.button(this, "Ligar painel flutuante", Ui.CHIP_OFF, Ui.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                section = 2;
                showSection();
                toggleOverlay(true);
            }
        }), Ui.params(this, -1, -2, 0, 10, 0, 0));
        s.addView(c2, gap());

        // tempos da pesca
        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Ajustes da pesca", 16, Ui.TEXT, true));
        for (int i = 0; i < FISH_KEYS.length; i++) {
            addField(c3, FISH_KEYS[i], FISH_LABELS[i]);
        }
        s.addView(c3, gap());

        s.addView(Ui.button(this, "Salvar ajustes", Ui.ACCENT, Ui.ON_ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFields();
                loadFields();
                refresh();
                tvCalib.setText(tvCalib.getText() + "\nAjustes salvos.");
            }
        }), gap());
        return s;
    }

    private LinearLayout chipRow(String[] labels, final int which, TextView[] out, boolean two) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.chip(this, labels[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveFields();
                    if (which == 0) {
                        cfg.routeOn = idx == 0 ? 1 : 0;
                        cfg = ConfigStore.update(MainActivity.this, cfg, "routeOn");
                    } else {
                        cfg.startAtPond = idx;
                        cfg = ConfigStore.update(MainActivity.this, cfg, "startAtPond");
                    }
                    refresh();
                }
            });
            t.setTextSize(13);
            LinearLayout.LayoutParams p = Ui.params(this, 0, -2, i == 0 ? 0 : 4, 0, i == labels.length - 1 ? 0 : 4, 0);
            p.weight = 1f;
            row.addView(t, p);
        }
        return row;
    }

    private LinearLayout arrowRow(final TextView[] store, final boolean walk) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < ANGLES.length; i++) {
            final int angle = ANGLES[i];
            TextView t = Ui.chip(this, ARROWS[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveFields();
                    if (walk) {
                        boolean linked = cfg.backAngle == Route.opposite(cfg.walkAngle);
                        cfg.walkAngle = angle;
                        if (linked) {
                            cfg.backAngle = Route.opposite(angle); // sair = voltar pelo caminho
                        }
                        cfg = ConfigStore.update(MainActivity.this, cfg, "walkAngle", "backAngle");
                    } else {
                        cfg.backAngle = angle;
                        cfg = ConfigStore.update(MainActivity.this, cfg, "backAngle");
                    }
                    refresh();
                }
            });
            t.setTextSize(16);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            LinearLayout.LayoutParams p = Ui.params(this, 0, -2, i == 0 ? 0 : 2, 0, i == 7 ? 0 : 2, 0);
            p.weight = 1f;
            row.addView(t, p);
            store[i] = t;
        }
        return row;
    }

    private void addField(LinearLayout parent, String key, String label) {
        parent.addView(Ui.text(this, label, 12, Ui.MUTED, false), Ui.params(this, -1, -2, 0, 10, 0, 0));
        EditText e = field("", true);
        parent.addView(e, Ui.params(this, -1, -2, 0, 4, 0, 0));
        fields.add(e);
        fieldKeys.add(key);
    }

    private EditText field(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5C6F8A);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        if (number) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        e.setBackground(Ui.shape(this, Ui.CHIP_OFF, Ui.STROKE, 1, 12));
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        return e;
    }

    // ---- Janelas ----

    private LinearLayout buildWindows() {
        LinearLayout s = column();

        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.text(this, "Painel flutuante", 16, Ui.TEXT, true));
        c4.addView(Ui.text(this, "Fica por cima do Roblox, com abas Hop e Pesca: troque de servidor e controle a "
                + "pesca sem voltar para este app.", 13, Ui.MUTED, false), Ui.params(this, -1, -2, 0, 4, 0, 0));
        tvOverlayState = Ui.text(this, "", 13, Ui.MUTED, true);
        c4.addView(tvOverlayState, Ui.params(this, -1, -2, 0, 8, 0, 0));
        btnOverlay = Ui.button(this, "", Ui.ACCENT, Ui.ON_ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOverlay(false);
            }
        });
        c4.addView(btnOverlay, Ui.params(this, -1, -2, 0, 10, 0, 0));
        s.addView(c4, gap());

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
        s.addView(c5, gap());
        return s;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams gap() {
        return Ui.params(this, -1, -2, 0, 0, 0, 12);
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    private void loadFields() {
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).setText(String.valueOf(cfg.get(fieldKeys.get(i))));
        }
    }

    private void saveFields() {
        if (fields.isEmpty() || cfg == null) {
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            String t = fields.get(i).getText().toString().trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                cfg.set(fieldKeys.get(i), Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
                // mantém o valor anterior
            }
        }
        cfg.sanitize();
        cfg = ConfigStore.update(this, cfg, fieldKeys.toArray(new String[0]));
    }

    private void refresh() {
        if (tvStatus == null) {
            return;
        }
        // hop
        Ui.setChip(chipEmpty, this, cfg.mode == 0);
        Ui.setChip(chipBusy, this, cfg.mode == 1);
        tvModeHint.setText(cfg.hopMode() == HopMode.EMPTY
                ? "Vazio: servidores com 1 a 2 jogadores (na prática, quase sempre 1)."
                : "Movimentado: servidores quase cheios, com mais gente. É o mais perto de \"mais iniciantes\" que a lista permite.");
        tvGame.setText("Atual: " + cfg.placeName + "  ·  " + cfg.placeId);

        // pesca
        boolean a11y = FishService.instance != null;
        tvA11y.setText(a11y ? "Serviço: ligado" : "Serviço: desligado");
        tvA11y.setTextColor(a11y ? Ui.ACCENT : Ui.WARN);
        Ui.setChip(chipRouteOn, this, cfg.routeOn == 1);
        Ui.setChip(chipRouteOff, this, cfg.routeOn == 0);
        Ui.setChip(chipStartSpawn, this, cfg.startAtPond == 0);
        Ui.setChip(chipStartPond, this, cfg.startAtPond == 1);
        for (int i = 0; i < ANGLES.length; i++) {
            Ui.setChip(walkChips[i], this, cfg.walkAngle == ANGLES[i]);
            Ui.setChip(backChips[i], this, cfg.backAngle == ANGLES[i]);
        }
        tvRouteSummary.setText(cfg.routeOn == 1
                ? (cfg.startAtPond == 1 ? "Ciclo: " : "Ciclo: andar " + cfg.walkSec + " s " + Route.compass(cfg.walkAngle) + " → ")
                + "pescar " + Route.clock(cfg.fishSec * 1000L) + " → andar " + cfg.backSec + " s "
                + Route.compass(cfg.backAngle) + " → andar " + cfg.walkSec + " s " + Route.compass(cfg.walkAngle)
                + " → pescar de novo..."
                : "Rota desligada: só pesca parado, sem andar.");
        tvCalib.setText("Pesca: " + (cfg.fishCalibrated() ? "calibrada" : "NÃO calibrada")
                + "\nJoystick: " + (cfg.joystickCalibrated() ? "calibrado" : "NÃO calibrado"));

        // janelas
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
        tvWindow.setText(isInMultiWindowMode()
                ? "Este app já está em tela dividida/janela. O Roblox abrirá no outro lado."
                : "Para dividir: abra os apps recentes, toque no ícone do Server Hop e escolha \"Dividir tela\". "
                + "Depois use o botão abaixo para abrir o Roblox no outro lado. (Nem todo celular/jogo aceita.)");

        List<String> h = HopController.history();
        StringBuilder sb = new StringBuilder();
        for (String x : h) {
            sb.append(sb.length() == 0 ? "Recentes:\n" : "\n").append(x);
        }
        tvHistory.setText(sb.toString());
        tvHistory.setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setMode(int mode) {
        cfg.mode = mode;
        cfg = ConfigStore.update(this, cfg, "mode");
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
                        cfg = ConfigStore.updatePlace(MainActivity.this, cfg);
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

    private void toggleOverlay(boolean onlyTurnOn) {
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
            if (onlyTurnOn) {
                return;
            }
            stopService(new Intent(this, OverlayService.class));
            OverlayService.running = false;
            refresh();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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

    private void helpRestricted() {
        new AlertDialog.Builder(this)
                .setTitle("Ativar a acessibilidade")
                .setMessage("Se o Play Protect bloquear a instalação: toque em \"Mais detalhes\" > \"Instalar mesmo assim\".\n\n"
                        + "Se a acessibilidade aparecer cinza/bloqueada (\"configuração restrita\"):\n"
                        + "1. Configurações > Apps > Server Hop.\n"
                        + "2. Toque nos três pontinhos (canto superior direito).\n"
                        + "3. Toque em \"Permitir configurações restritas\".\n"
                        + "4. Volte em Configurações > Acessibilidade > Apps instalados > Server Hop e ative.\n\n"
                        + "Se o item dos três pontinhos não aparecer, tente ativar a acessibilidade uma vez (vai falhar) "
                        + "e abra Apps > Server Hop de novo.")
                .setPositiveButton("Entendi", null)
                .show();
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
