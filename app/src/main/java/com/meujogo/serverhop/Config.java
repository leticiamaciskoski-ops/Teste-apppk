package com.meujogo.serverhop;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configuração do app (Java puro, para poder ser testada na JVM). */
public class Config {

    /** Steal An Egg: lugar raiz do universo 10563114921 (confirmado pela API do Roblox). */
    public static final long DEFAULT_PLACE_ID = 107778070777162L;
    public static final String DEFAULT_PLACE_NAME = "Steal An Egg";

    public long placeId = DEFAULT_PLACE_ID;
    public String placeName = DEFAULT_PLACE_NAME;

    /** Índice do HopMode (0 = vazio, 1 = movimentado). */
    public int mode = 0;
    /** Páginas de 100 servidores a consultar por busca (o Roblox limita a ~3 consultas por minuto). */
    public int maxPages = 2;

    /** Posição do painel flutuante (px) e se está aberto (1) ou recolhido em bolha (0). */
    public int overlayX = 24;
    public int overlayY = 240;
    public int overlayOpen = 1;

    public static final String[] INT_KEYS = {"mode", "maxPages", "overlayX", "overlayY", "overlayOpen"};

    public HopMode hopMode() {
        return HopMode.fromIndex(mode);
    }

    public int get(String key) {
        switch (key) {
            case "mode": return mode;
            case "maxPages": return maxPages;
            case "overlayX": return overlayX;
            case "overlayY": return overlayY;
            case "overlayOpen": return overlayOpen;
            default: throw new IllegalArgumentException(key);
        }
    }

    public void set(String key, int v) {
        switch (key) {
            case "mode": mode = v; break;
            case "maxPages": maxPages = v; break;
            case "overlayX": overlayX = v; break;
            case "overlayY": overlayY = v; break;
            case "overlayOpen": overlayOpen = v; break;
            default: throw new IllegalArgumentException(key);
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
