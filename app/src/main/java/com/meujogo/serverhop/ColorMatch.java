package com.meujogo.serverhop;

/** Comparação de cores e busca numa pequena área ao redor de um ponto (Java puro). */
public final class ColorMatch {

    private ColorMatch() {
    }

    /** Fonte de pixels (no Android, é o Bitmap da captura de tela). */
    public interface Pixels {
        int get(int x, int y);
    }

    /** Maior diferença entre os canais R, G e B (0..255). O canal alfa é ignorado. */
    public static int maxChannelDiff(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    /**
     * Menor diferença de cor encontrada na área (2r+1) x (2r+1) ao redor de (cx, cy),
     * limitada às bordas da imagem. Retorna 255 se a área ficar vazia.
     */
    public static int bestDiff(Pixels p, int width, int height, int cx, int cy, int r, int ref) {
        int best = 255;
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    continue;
                }
                int d = maxChannelDiff(p.get(x, y), ref);
                if (d < best) {
                    best = d;
                }
            }
        }
        return best;
    }

    public static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}
