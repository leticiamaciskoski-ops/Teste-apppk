package com.meujogo.serverhop;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** Configuração do app (Java puro, para poder ser testada na JVM). Todos os campos int abaixo são salvos. */
public class Config {

    /** Steal An Egg: lugar raiz do universo 10563114921 (confirmado pela API do Roblox). */
    public static final long DEFAULT_PLACE_ID = 107778070777162L;
    public static final String DEFAULT_PLACE_NAME = "Steal An Egg";

    public long placeId = DEFAULT_PLACE_ID;
    public String placeName = DEFAULT_PLACE_NAME;

    // ---- server hop ----
    /** Índice do HopMode (0 = vazio, 1 = movimentado). */
    public int mode = 0;
    /** Páginas de 100 servidores a consultar por busca (o Roblox limita a ~3 consultas por minuto). */
    public int maxPages = 2;

    // ---- painel flutuante ----
    public int overlayX = 24;
    public int overlayY = 240;
    public int overlayOpen = 1;
    /** Aba do painel: 0 = Hop, 1 = Pesca. */
    public int overlayTab = 0;

    // ---- pesca: pontos calibrados (pixels de tela; -1 = não calibrado) ----
    public int holdX = -1, holdY = -1;
    public int tapX = -1, tapY = -1;
    public int detectX = -1, detectY = -1;
    public int detectColor = 0; // ARGB lido na calibração

    // ---- pesca: tempos ----
    public int holdMs = 1300;        // quanto tempo segurar para lançar
    public int holdJitterMs = 0;     // sorteia holdMs +/- isso
    public int taps = 13;
    public int spacingMs = 25;
    public int tapDurMs = 12;
    public int tapWindowMs = 500;    // janela do jogo (só para avisos e relatório)
    public int tolerance = 40;       // diferença máxima por canal RGB
    public int boxRadius = 3;
    public int pollMs = 350;         // o Android limita capturas a ~3 por segundo
    public int maxWaitMs = 15000;
    public int reactionMs = 0;
    public int resultDelayMs = 1800;
    public int detectMinPct = 15;    // % de pixels da área que precisam virar a cor do sinal (acima do fundo)
    public int ignoreMs = 800;       // depois de soltar, ignora o sinal por esse tempo (a tela ainda está mudando)
    public int detectConfirm = 1;    // leituras seguidas que confirmam o sinal (1 = mais rápido)

    // ---- pegar a vara no inventário (antes de pescar) ----
    public int equipOn = 1;          // 1 = toca no slot da vara antes de pescar
    public int equipX = -1, equipY = -1; // slot da vara no inventário/hotbar
    public int equipTaps = 3;        // quantos toques no slot
    public int equipSpacingMs = 150; // intervalo entre esses toques
    public int equipPauseMs = 500;   // espera depois, para a vara aparecer na mão
    public int equipEvery = 1;       // pegar a vara a cada N ciclos de pesca

    public static final String[] INT_KEYS = {
            "mode", "maxPages", "overlayX", "overlayY", "overlayOpen", "overlayTab",
            "holdX", "holdY", "tapX", "tapY", "detectX", "detectY", "detectColor",
            "holdMs", "holdJitterMs", "taps", "spacingMs", "tapDurMs", "tapWindowMs",
            "tolerance", "boxRadius", "pollMs", "maxWaitMs", "reactionMs", "resultDelayMs",
            "detectMinPct", "ignoreMs", "detectConfirm",
            "equipOn", "equipX", "equipY", "equipTaps", "equipSpacingMs", "equipPauseMs", "equipEvery"
    };

    public HopMode hopMode() {
        return HopMode.fromIndex(mode);
    }

    public boolean fishCalibrated() {
        return holdX >= 0 && holdY >= 0 && tapX >= 0 && tapY >= 0 && detectX >= 0 && detectY >= 0
                && (equipOn == 0 || (equipX >= 0 && equipY >= 0));
    }

    private static Field field(String key) {
        for (String k : INT_KEYS) {
            if (k.equals(key)) {
                try {
                    return Config.class.getField(key);
                } catch (NoSuchFieldException e) {
                    break;
                }
            }
        }
        throw new IllegalArgumentException(key);
    }

    public int get(String key) {
        try {
            return field(key).getInt(this);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(key);
        }
    }

    public void set(String key, int v) {
        try {
            field(key).setInt(this, v);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(key);
        }
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : INT_KEYS) {
            m.put(k, get(k));
        }
        return m;
    }

    public void fromMap(Map<String, Integer> m) {
        for (String k : INT_KEYS) {
            Integer v = m.get(k);
            if (v != null) {
                set(k, v);
            }
        }
        sanitize();
    }

    /** Coloca valores absurdos dentro de limites seguros. */
    public void sanitize() {
        mode = clamp(mode, 0, HopMode.values().length - 1);
        maxPages = clamp(maxPages, 1, 5);
        overlayX = clamp(overlayX, 0, 20000);
        overlayY = clamp(overlayY, 0, 20000);
        overlayOpen = overlayOpen == 0 ? 0 : 1;
        overlayTab = overlayTab == 1 ? 1 : 0;

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
        detectMinPct = clamp(detectMinPct, 1, 100);
        ignoreMs = clamp(ignoreMs, 0, 60000);
        detectConfirm = clamp(detectConfirm, 1, 5);

        equipOn = equipOn == 0 ? 0 : 1;
        equipTaps = clamp(equipTaps, 1, 10);   // um gesto do Android aceita até 10 toques
        equipSpacingMs = clamp(equipSpacingMs, 20, 2000);
        equipPauseMs = clamp(equipPauseMs, 0, 10000);
        equipEvery = clamp(equipEvery, 1, 1000);

        if (placeId <= 0) {
            placeId = DEFAULT_PLACE_ID;
            placeName = DEFAULT_PLACE_NAME;
        }
        if (placeName == null || placeName.isEmpty()) {
            placeName = "Jogo " + placeId;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
