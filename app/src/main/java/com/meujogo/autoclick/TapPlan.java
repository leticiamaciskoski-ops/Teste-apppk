package com.meujogo.autoclick;

/**
 * Planejamento dos toques rápidos (Java puro).
 *
 * O Android limita cada gesto de acessibilidade a GestureDescription.getMaxStrokeCount()
 * traços (10 nas versões atuais). Então 13 toques viram 2 gestos (10 + 3), e o intervalo
 * entre o último toque do 1o gesto e o primeiro do 2o depende da latência do sistema.
 */
public final class TapPlan {

    private TapPlan() {
    }

    /** Divide 'taps' em lotes de no máximo 'maxPerGesture'. Ex.: (13, 10) -> [10, 3]. */
    public static int[] batchSizes(int taps, int maxPerGesture) {
        if (taps <= 0 || maxPerGesture <= 0) {
            return new int[0];
        }
        int batches = (taps + maxPerGesture - 1) / maxPerGesture;
        int[] sizes = new int[batches];
        int left = taps;
        for (int i = 0; i < batches; i++) {
            sizes[i] = Math.min(left, maxPerGesture);
            left -= sizes[i];
        }
        return sizes;
    }

    /**
     * Estimativa do tempo entre o início do 1o toque e o início do último toque.
     * latencyMs = atraso do sistema entre um gesto terminar e o próximo começar (estimativa).
     */
    public static long estimateWindowMs(int taps, int spacingMs, int tapDurMs, int maxPerGesture, int latencyMs) {
        int[] b = batchSizes(taps, maxPerGesture);
        if (b.length == 0) {
            return 0;
        }
        long total = 0;
        for (int i = 0; i < b.length; i++) {
            total += (long) (b[i] - 1) * spacingMs;
            if (i > 0) {
                total += tapDurMs + latencyMs;
            }
        }
        return total;
    }
}
