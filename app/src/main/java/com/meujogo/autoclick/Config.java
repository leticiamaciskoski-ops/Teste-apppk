package com.meujogo.autoclick;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configuração do autoclick (Java puro, sem Android, para poder ser testada na JVM). */
public class Config {

    // Pontos calibrados (coordenadas de tela, em pixels). -1 = ainda não calibrado.
    public int holdX = -1, holdY = -1;
    public int tapX = -1, tapY = -1;
    public int detectX = -1, detectY = -1;
    public int detectColor = 0; // ARGB lido na calibração

    // Tempos e parâmetros (editáveis na tela inicial do app)
    public int holdMs = 1300;        // quanto tempo segurar para lançar (acerte a zona perfeita)
    public int holdJitterMs = 0;     // sorteia holdMs +/- isso, para testar a tolerância da zona perfeita
    public int taps = 13;            // quantidade de toques
    public int spacingMs = 25;       // intervalo entre o início de cada toque
    public int tapDurMs = 12;        // duração de cada toque
    public int tapWindowMs = 500;    // janela do jogo (só para avisos e relatório)
    public int tolerance = 40;       // diferença máxima por canal RGB para considerar "mesma cor"
    public int boxRadius = 3;        // raio (px) da área de busca ao redor do ponto de detecção
    public int pollMs = 350;         // intervalo entre leituras de tela (o Android limita a ~333 ms)
    public int maxWaitMs = 15000;    // desiste do ciclo se o sinal não aparecer
    public int reactionMs = 0;       // atraso artificial entre detectar e tocar
    public int resultDelayMs = 1800; // pausa entre o fim dos toques e o próximo lançamento

    public static final String[] ALL_KEYS = {
            "holdX", "holdY", "tapX", "tapY", "detectX", "detectY", "detectColor",
            "holdMs", "holdJitterMs", "taps", "spacingMs", "tapDurMs", "tapWindowMs",
            "tolerance", "boxRadius", "pollMs", "maxWaitMs", "reactionMs", "resultDelayMs"
    };

    /** Campos mostrados na tela de configuração, com seus rótulos. */
    public static final String[] EDITABLE = {
            "holdMs", "holdJitterMs", "taps", "spacingMs", "tapDurMs", "tapWindowMs",
            "tolerance", "pollMs", "maxWaitMs", "reactionMs", "resultDelayMs"
    };
    public static final String[] LABELS = {
            "Segurar para lançar (ms)", "Variação aleatória do segurar (± ms)", "Quantidade de toques",
            "Intervalo entre toques (ms)", "Duração de cada toque (ms)", "Janela do jogo para os toques (ms)",
            "Tolerância de cor (0-255)", "Intervalo de leitura da tela (ms, mín. 340)",
            "Tempo máximo esperando o sinal (ms)", "Atraso antes dos toques (ms)", "Pausa entre ciclos (ms)"
    };

    public boolean calibrated() {
        return holdX >= 0 && holdY >= 0 && tapX >= 0 && tapY >= 0 && detectX >= 0 && detectY >= 0;
    }

    public int get(String key) {
        switch (key) {
            case "holdX": return holdX;
            case "holdY": return holdY;
            case "tapX": return tapX;
            case "tapY": return tapY;
            case "detectX": return detectX;
            case "detectY": return detectY;
            case "detectColor": return detectColor;
            case "holdMs": return holdMs;
            case "holdJitterMs": return holdJitterMs;
            case "taps": return taps;
            case "spacingMs": return spacingMs;
            case "tapDurMs": return tapDurMs;
            case "tapWindowMs": return tapWindowMs;
            case "tolerance": return tolerance;
            case "boxRadius": return boxRadius;
            case "pollMs": return pollMs;
            case "maxWaitMs": return maxWaitMs;
            case "reactionMs": return reactionMs;
            case "resultDelayMs": return resultDelayMs;
            default: throw new IllegalArgumentException(key);
        }
    }

    public void set(String key, int v) {
        switch (key) {
            case "holdX": holdX = v; break;
            case "holdY": holdY = v; break;
            case "tapX": tapX = v; break;
            case "tapY": tapY = v; break;
            case "detectX": detectX = v; break;
            case "detectY": detectY = v; break;
            case "detectColor": detectColor = v; break;
            case "holdMs": holdMs = v; break;
            case "holdJitterMs": holdJitterMs = v; break;
            case "taps": taps = v; break;
            case "spacingMs": spacingMs = v; break;
            case "tapDurMs": tapDurMs = v; break;
            case "tapWindowMs": tapWindowMs = v; break;
            case "tolerance": tolerance = v; break;
            case "boxRadius": boxRadius = v; break;
            case "pollMs": pollMs = v; break;
            case "maxWaitMs": maxWaitMs = v; break;
            case "reactionMs": reactionMs = v; break;
            case "resultDelayMs": resultDelayMs = v; break;
            default: throw new IllegalArgumentException(key);
        }
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : ALL_KEYS) {
            m.put(k, get(k));
        }
        return m;
    }

    public void fromMap(Map<String, Integer> m) {
        for (String k : ALL_KEYS) {
            Integer v = m.get(k);
            if (v != null) {
                set(k, v);
            }
        }
        sanitize();
    }

    /** Coloca valores absurdos dentro de limites seguros. */
    public void sanitize() {
        holdMs = clamp(holdMs, 50, 50000);
        holdJitterMs = clamp(holdJitterMs, 0, holdMs - 50);
        taps = clamp(taps, 1, 200);
        spacingMs = clamp(spacingMs, 1, 1000);
        tapDurMs = clamp(tapDurMs, 1, 1000);
        tapWindowMs = clamp(tapWindowMs, 1, 60000);
        tolerance = clamp(tolerance, 0, 255);
        boxRadius = clamp(boxRadius, 0, 20);
        pollMs = clamp(pollMs, 340, 60000);
        maxWaitMs = clamp(maxWaitMs, 1000, 600000);
        reactionMs = clamp(reactionMs, 0, 60000);
        resultDelayMs = clamp(resultDelayMs, 0, 600000);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
