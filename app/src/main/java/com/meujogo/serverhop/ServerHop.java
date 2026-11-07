package com.meujogo.serverhop;

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

    /** Capacidade assumida quando a lista não informa (o Steal An Egg usa 7). */
    public static final int DEFAULT_CAP = 7;
    /** Abaixo disso o servidor está engasgado. */
    public static final double MIN_FPS = 30.0;
    /** Acima disso o servidor (ou o jogador dele) está com a conexão ruim. */
    public static final int MAX_PING_MS = 500;

    /** Um servidor público. 'id' é o JobId (gameInstanceId). fps < 0 e ping < 0 = status desconhecido. */
    public static final class Server {
        public final String id;
        public final int playing;
        public final int maxPlayers;
        public final double fps;
        public final int ping;

        public Server(String id, int playing, int maxPlayers, double fps, int ping) {
            this.id = id;
            this.playing = playing;
            this.maxPlayers = maxPlayers;
            this.fps = fps;
            this.ping = ping;
        }

        /** O Roblox informou fps e ping deste servidor? Sem isso ele está em "status desconhecido". */
        public boolean statusKnown() {
            return fps > 0 && ping >= 0;
        }

        /** Status conhecido e dentro dos limites de fps/ping. */
        public boolean healthy() {
            return statusKnown() && fps >= MIN_FPS && ping <= MAX_PING_MS;
        }

        @Override
        public String toString() {
            return id + " (" + playing + "/" + maxPlayers + ", " + Math.round(fps) + " fps, " + ping + " ms)";
        }
    }

    /** Uma página (até 100 servidores) da lista pública. */
    public static final class Page {
        public final List<Server> servers;
        public final String nextCursor;

        public Page(List<Server> servers, String nextCursor) {
            this.servers = servers;
            this.nextCursor = nextCursor;
        }
    }

    /** Fonte de páginas (no app, a API do Roblox; nos testes, páginas de mentira). */
    public interface PageSource {
        Page fetch(String sortOrder, String cursor) throws Exception;
    }

    // ------------------------------------------------------------------
    // Escolha de servidores
    // ------------------------------------------------------------------

    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d{1,19}");
    private static final Pattern PLACE_Q = Pattern.compile("[?&]placeId=(\\d{1,19})", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_PATH = Pattern.compile("/(?:games|experiences)/(\\d{1,19})");
    private static final Pattern JOB_ID = Pattern.compile("[0-9a-fA-F][0-9a-fA-F-]{7,63}");

    /**
     * Servidores que o modo aceita: lotação certa, status conhecido e saudável, não cheios,
     * com id válido e fora da lista 'exclude'. Servidores em status desconhecido NUNCA entram.
     */
    public static List<Server> candidates(List<Server> all, HopMode mode, Set<String> exclude) {
        List<Server> out = new ArrayList<>();
        for (Server s : all) {
            if (!s.healthy()) {
                continue;
            }
            if (!mode.accepts(s.playing, s.maxPlayers)) {
                continue;
            }
            if (!validJobId(s.id)) {
                continue;
            }
            if (exclude != null && exclude.contains(s.id)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    public static Server pick(List<Server> candidates, Random rng) {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * Lê páginas até aparecer algum candidato (ou acabar o limite de páginas). Devolve TODOS os
     * servidores lidos, para a lista poder ser reaproveitada. Para cedo quando a página inteira já
     * passou do que o modo aceita (a lista vem ordenada).
     */
    public static List<Server> collect(PageSource src, HopMode mode, int maxPages, long pauseMs) throws Exception {
        List<Server> all = new ArrayList<>();
        String cursor = null;
        for (int p = 0; p < maxPages; p++) {
            Page page = src.fetch(mode.sort, cursor);
            all.addAll(page.servers);
            if (!candidates(all, mode, null).isEmpty()) {
                break;
            }
            if (page.nextCursor == null || page.servers.isEmpty() || pastRange(page.servers, mode)) {
                break;
            }
            cursor = page.nextCursor;
            if (pauseMs > 0 && p + 1 < maxPages) {
                Thread.sleep(pauseMs); // respeita o limite de consultas do Roblox
            }
        }
        return all;
    }

    /** A página inteira já ficou além do que o modo procura (e as próximas ficarão ainda mais). */
    static boolean pastRange(List<Server> page, HopMode mode) {
        int cap = capOf(page);
        if ("Asc".equals(mode.sort)) {
            return minPlaying(page) > mode.upper(cap);
        }
        return maxPlaying(page) < mode.lower(cap);
    }

    static int capOf(List<Server> list) {
        int cap = 0;
        for (Server s : list) {
            if (s.maxPlayers > cap) {
                cap = s.maxPlayers;
            }
        }
        return cap > 0 ? cap : DEFAULT_CAP;
    }

    public static int minPlaying(List<Server> list) {
        int m = Integer.MAX_VALUE;
        for (Server s : list) {
            if (s.playing < m) {
                m = s.playing;
            }
        }
        return m;
    }

    public static int maxPlaying(List<Server> list) {
        int m = Integer.MIN_VALUE;
        for (Server s : list) {
            if (s.playing > m) {
                m = s.playing;
            }
        }
        return m;
    }

    // ------------------------------------------------------------------
    // Número/link do jogo e links de entrada
    // ------------------------------------------------------------------

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

    /** URL da lista pública. Asc = menos jogadores primeiro; Desc = mais jogadores primeiro. */
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

    /** Texto curto para mostrar o servidor escolhido. */
    public static String describe(Server s) {
        return s.playing + (s.playing == 1 ? " jogador" : " jogadores") + " · " + Math.round(s.fps) + " fps · "
                + s.ping + " ms";
    }
}
