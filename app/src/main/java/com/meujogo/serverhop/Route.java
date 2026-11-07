package com.meujogo.serverhop;

import java.util.Random;

/**
 * Lógica pura da rota de pesca: andar até a poça, pescar por um tempo, andar de novo e voltar
 * a pescar. Sem Android, para poder ser testada na JVM.
 */
public final class Route {

    private Route() {
    }

    public enum Phase {
        /** Andando até a poça de pesca. */
        WALK_TO("Andando até a poça"),
        /** Pescando (por fishSec segundos). */
        FISH("Pescando"),
        /** Andando para sair da poça (depois disso, anda de novo até ela). */
        WALK_BACK("Saindo da poça");

        public final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    /** Fase em que começa: na poça (pesca) ou no ponto de partida (anda primeiro). */
    public static Phase first(boolean routeOn, boolean startAtPond) {
        return (!routeOn || startAtPond) ? Phase.FISH : Phase.WALK_TO;
    }

    /** Próxima fase. Sem rota, só pesca. */
    public static Phase next(Phase p, boolean routeOn) {
        if (!routeOn) {
            return Phase.FISH;
        }
        switch (p) {
            case WALK_TO:
                return Phase.FISH;
            case FISH:
                return Phase.WALK_BACK;
            default:
                return Phase.WALK_TO;
        }
    }

    /** Ângulo entre 0 e 359 (aceita negativos e maiores que 360). */
    public static int normAngle(int a) {
        int r = a % 360;
        return r < 0 ? r + 360 : r;
    }

    public static int opposite(int angle) {
        return normAngle(angle + 180);
    }

    /**
     * Ponto onde o dedo deve ficar para o joystick andar naquela direção.
     * 0 = cima, 90 = direita, 180 = baixo, 270 = esquerda (a tela tem y para baixo).
     */
    public static int[] joystickTarget(int cx, int cy, int radiusPx, int angleDeg) {
        double a = Math.toRadians(normAngle(angleDeg));
        int x = cx + (int) Math.round(radiusPx * Math.sin(a));
        int y = cy - (int) Math.round(radiusPx * Math.cos(a));
        return new int[]{x, y};
    }

    /** Sorteia baseMs +/- pct%. Com pct = 0 devolve baseMs. */
    public static long jitter(long baseMs, int pct, Random rng) {
        if (pct <= 0 || baseMs <= 0) {
            return baseMs;
        }
        double f = 1.0 + ((rng.nextDouble() * 2.0) - 1.0) * pct / 100.0;
        return Math.max(1, Math.round(baseMs * f));
    }

    /** 330000 -> "5:30"; 3725000 -> "1:02:05". */
    public static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return h + ":" + two(m) + ":" + two(sec);
        }
        return m + ":" + two(sec);
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    public static String compass(int angle) {
        String[] n = {"↑ cima", "↗ cima-direita", "→ direita", "↘ baixo-direita", "↓ baixo", "↙ baixo-esquerda",
                "← esquerda", "↖ cima-esquerda"};
        return n[(normAngle(angle) + 22) % 360 / 45];
    }
}
