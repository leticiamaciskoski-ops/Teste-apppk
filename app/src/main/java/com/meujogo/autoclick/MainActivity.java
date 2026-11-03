package com.meujogo.autoclick;

import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tela de configuração: parâmetros do teste + atalhos para ativar o serviço e mostrar o painel. */
public class MainActivity extends Activity {

    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private TextView tvService;
    private TextView tvEstimate;
    private TextView tvGame;
    private TextView tvHop;
    private TextView tvOverlay;
    private EditText etGame;
    private Config cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = ConfigStore.load(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        root.addView(text("Autoclick Pesca", 22, true));
        root.addView(text("Ferramenta de teste do seu minigame: segura para lançar, espera o sinal de fisgada "
                + "(lendo a cor de um pixel) e dá os toques rápidos, repetindo sem parar.", 14, false));

        root.addView(text("Passo a passo", 16, true));
        root.addView(text("1) Toque em \"Abrir ajustes de Acessibilidade\" e ative \"Autoclick Pesca\".\n"
                + "   (Android 13+: se estiver bloqueado, vá em Informações do app > menu ⋮ > \"Permitir configurações restritas\".)\n"
                + "2) Ajuste os números abaixo e toque em Salvar.\n"
                + "3) Abra o seu jogo. No painel flutuante, toque em Calibrar e siga os 3 passos.\n"
                + "4) Use Testar para conferir a detecção do sinal e depois Iniciar.", 13, false));

        tvService = text("", 14, true);
        root.addView(tvService);

        root.addView(button("Abrir ajustes de Acessibilidade", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        root.addView(button("Mostrar painel flutuante", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutoClickService s = AutoClickService.instance;
                if (s == null) {
                    Toast.makeText(MainActivity.this, "Ative o serviço nos ajustes de acessibilidade primeiro.",
                            Toast.LENGTH_LONG).show();
                } else {
                    s.showPanel();
                }
            }
        }));

        root.addView(text("Parâmetros", 16, true));
        for (int i = 0; i < Config.EDITABLE.length; i++) {
            root.addView(text(Config.LABELS[i], 13, false));
            EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
            et.setSingleLine(true);
            fields.put(Config.EDITABLE[i], et);
            root.addView(et);
        }

        tvEstimate = text("", 13, false);
        root.addView(tvEstimate);
        root.addView(text("Server hop (Roblox)", 16, true));
        root.addView(text("Troca de servidor direto pelo app (ou pelo painel flutuante), escolhendo servidores "
                + "quase vazios. O padrão é o Steal An Egg.", 13, false));
        tvGame = text("", 13, true);
        root.addView(tvGame);
        etGame = new EditText(this);
        etGame.setInputType(InputType.TYPE_CLASS_TEXT);
        etGame.setSingleLine(true);
        etGame.setHint("Outro jogo? Cole o link ou o número");
        root.addView(etGame);
        root.addView(button("Verificar e usar este jogo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resolveGame();
            }
        }));
        for (int i = 0; i < Config.HOP_EDITABLE.length; i++) {
            root.addView(text(Config.HOP_LABELS[i], 13, false));
            EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
            et.setSingleLine(true);
            fields.put(Config.HOP_EDITABLE[i], et);
            root.addView(et);
        }
        root.addView(button("Testar server hop agora", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hopNow();
            }
        }));
        tvHop = text("", 13, false);
        root.addView(tvHop);
        tvOverlay = text("", 13, true);
        root.addView(tvOverlay);
        root.addView(button("Permitir exibir sobre outros apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        root.addView(text("Recomendado: sem essa permissão o Android pode impedir o painel flutuante de abrir o "
                + "Roblox em segundo plano. O servidor mais vazio costuma ter 1 jogador (a lista do Roblox vem dos "
                + "menores para os maiores); o Roblox limita as consultas, por isso o app reaproveita a lista por 45 s.",
                12, false));

        root.addView(button("Salvar configuração", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        }));
        root.addView(text("Dica de balanceamento: aumente \"Variação aleatória do segurar\" para ver quanta imprecisão "
                + "a zona perfeita tolera; aumente o intervalo entre toques até estourar a janela para achar o "
                + "limite de toques por segundo que o seu minigame exige.", 12, false));

        setContentView(scroll);
        loadFields();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshGameInfo();
        tvOverlay.setText(Settings.canDrawOverlays(this)
                ? "Exibir sobre outros apps: PERMITIDO" : "Exibir sobre outros apps: não permitido");
        tvService.setText(AutoClickService.instance != null
                ? "Serviço: ATIVO" : "Serviço: desativado (ative nos ajustes de acessibilidade)");
    }

    private void loadFields() {
        for (Map.Entry<String, EditText> e : fields.entrySet()) {
            e.getValue().setText(String.valueOf(cfg.get(e.getKey())));
        }
        updateEstimate();
    }

    private void save() {
        for (Map.Entry<String, EditText> e : fields.entrySet()) {
            String s = e.getValue().getText().toString().trim();
            if (s.length() > 0) {
                try {
                    cfg.set(e.getKey(), Integer.parseInt(s));
                } catch (NumberFormatException ignored) {
                    // mantém o valor anterior
                }
            }
        }
        // preserva a calibração feita pelo serviço enquanto a tela estava aberta
        Config stored = ConfigStore.load(this);
        for (String k : new String[]{"holdX", "holdY", "tapX", "tapY", "detectX", "detectY", "detectColor"}) {
            cfg.set(k, stored.get(k));
        }
        ConfigStore.save(this, cfg);
        loadFields();
        AutoClickService s = AutoClickService.instance;
        if (s != null) {
            s.reloadConfig();
        }
        Toast.makeText(this, "Salvo.", Toast.LENGTH_SHORT).show();
    }

    private void updateEstimate() {
        long est = TapPlan.estimateWindowMs(cfg.taps, cfg.spacingMs, cfg.tapDurMs,
                GestureDescription.getMaxStrokeCount(), 60);
        int batches = TapPlan.batchSizes(cfg.taps, GestureDescription.getMaxStrokeCount()).length;
        tvEstimate.setText("Janela estimada dos toques: ~" + est + " ms em " + batches + " gesto(s) "
                + "(o jogo exige " + cfg.tapWindowMs + " ms)" + (est > cfg.tapWindowMs ? "  ← ESTOURA" : "  ← cabe"));
    }

    private void refreshGameInfo() {
        tvGame.setText("Jogo atual: " + cfg.placeName + " (Place ID " + cfg.placeId + ")");
    }

    /** Aceita link ou número (lugar ou universo) e descobre o lugar raiz do jogo pela API do Roblox. */
    private void resolveGame() {
        final long id = ServerHop.parseId(etGame.getText().toString());
        if (id <= 0) {
            Toast.makeText(this, "Cole o link do jogo (roblox.com/games/...) ou só o número.", Toast.LENGTH_LONG).show();
            return;
        }
        tvHop.setText("Verificando o jogo...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    RobloxApi.GameInfo gi = RobloxApi.resolveGame(id);
                    if (gi == null) {
                        msg = "Não encontrei esse jogo. Confira o link ou o número.";
                    } else {
                        cfg.placeId = gi.rootPlaceId;
                        cfg.placeName = gi.name;
                        ConfigStore.save(MainActivity.this, cfg);
                        msg = "Ok: " + gi.name + " (Place ID " + gi.rootPlaceId + ")";
                    }
                } catch (RobloxApi.ApiException e) {
                    msg = HopController.explain(e);
                } catch (IOException e) {
                    msg = HopController.explain(e);
                } catch (JSONException e) {
                    msg = HopController.explain(e);
                }
                final String shown = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHop.setText(shown);
                        refreshGameInfo();
                        AutoClickService s = AutoClickService.instance;
                        if (s != null) {
                            s.reloadConfig();
                        }
                    }
                });
            }
        }).start();
    }

    private void hopNow() {
        save();
        HopController.hop(this, cfg, new HopController.Listener() {
            @Override
            public void onStatus(String msg) {
                tvHop.setText(msg);
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, dp(8), 0, dp(2));
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }
}
