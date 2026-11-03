package com.meujogo.autoclick;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lógica pura do server hop do Roblox (sem Android e sem rede), testável na JVM. */
public final class ServerHop {

    private ServerHop() {
    }

    /** Um servidor público da lista do Roblox. 'id' é o JobId (gameInstanceId). */
    public static final class Server {
        public final String id;
        public final int playing;
        public final int maxPlayers;

        public Server(String id, int playing, int maxPlayers) {
            this.id = id;
            this.playing = playing;
            this.maxPlayers = maxPlayers;
        }

        @Override
        public String toString() {
            return id + " (" + playing + "/" + maxPlayers + ")";
        }
    }

    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d{1,19}");
    private static final Pattern PLACE_Q = Pattern.compile("[?&]placeId=(\\d{1,19})", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_PATH = Pattern.compile("/(?:games|experiences)/(\\d{1,19})");
    private static final Pattern JOB_ID = Pattern.compile("[0-9a-fA-F][0-9a-fA-F-]{7,63}");

    /**
     * Extrai um número de jogo do texto colado: só o número, ou um link como
     * roblox.com/games/123/Nome ou ...start?placeId=123. Retorna -1 se não achar.
     * Atenção: o número pode ser um ID de universo; RobloxApi.resolveGame converte.
     */
    public static long parseId(String input) {
        if (input == null) {
            return -1;
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return -1;
        }
        if (DIGITS_ONLY.matcher(s).matches()) {
            return toLong(s);
        }
        Matcher m = PLACE_Q.matcher(s);
        if (m.find()) {
            return toLong(m.group(1));
        }
        m = IN_PATH.matcher(s);
        if (m.find()) {
            return toLong(m.group(1));
        }
        return -1;
    }

    private static long toLong(String digits) {
        try {
            long v = Long.parseLong(digits);
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Servidores com jogadores entre min e max (inclusive), não cheios e fora da lista 'exclude'. */
    public static List<Server> filter(List<Server> all, int min, int max, Set<String> exclude) {
        List<Server> out = new ArrayList<>();
        for (Server s : all) {
            if (s.playing < min || s.playing > max) {
                continue;
            }
            if (s.maxPlayers > 0 && s.playing >= s.maxPlayers) {
                continue;
            }
            if (exclude != null && exclude.contains(s.id)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    /** Menor número de jogadores da lista (Integer.MAX_VALUE se vazia). */
    public static int minPlaying(List<Server> list) {
        int m = Integer.MAX_VALUE;
        for (Server s : list) {
            if (s.playing < m) {
                m = s.playing;
            }
        }
        return m;
    }

    public static Server pick(List<Server> candidates, Random rng) {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    /** O JobId tem de parecer um GUID; evita montar link com lixo vindo da rede. */
    public static boolean validJobId(String id) {
        return id != null && JOB_ID.matcher(id).matches();
    }

    /** Link que abre o app do Roblox direto num servidor específico. Null se algo for inválido. */
    public static String deepLink(long placeId, String jobId) {
        if (placeId <= 0 || !validJobId(jobId)) {
            return null;
        }
        return "roblox://experiences/start?placeId=" + placeId + "&gameInstanceId=" + jobId;
    }

    /** Alternativa pela web (o Roblox redireciona para o app). */
    public static String webLink(long placeId, String jobId) {
        if (placeId <= 0 || !validJobId(jobId)) {
            return null;
        }
        return "https://www.roblox.com/games/start?placeId=" + placeId + "&gameInstanceId=" + jobId;
    }

    /** URL da lista pública de servidores. sortOrder=Asc lista os com MENOS jogadores primeiro. */
    public static String serversUrl(long placeId, String sortOrder, int limit, String cursor) {
        StringBuilder sb = new StringBuilder("https://games.roblox.com/v1/games/")
                .append(placeId).append("/servers/Public?sortOrder=").append(sortOrder)
                .append("&limit=").append(limit).append("&excludeFullGames=true");
        if (cursor != null && !cursor.isEmpty()) {
            sb.append("&cursor=").append(enc(cursor));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String describe(int min, int max) {
        if (min == max) {
            return min == 1 ? "1 jogador" : min + " jogadores";
        }
        return min + "-" + max + " jogadores";
    }
}
