package com.meujogo.serverhop;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas à API pública do Roblox (sem login). Bloqueante: chamar fora da thread principal.
 * Usa só HttpURLConnection e org.json (ambos já vêm no Android).
 */
public final class RobloxApi {

    private RobloxApi() {
    }

    private static final String UA = "Mozilla/5.0 (Linux; Android 13) ServerHop/0.3";

    public static final class ApiException extends Exception {
        public final int httpCode;
        public final int retryAfterSec;
        public final int resetSec;

        public ApiException(int httpCode, int retryAfterSec, int resetSec, String message) {
            super("HTTP " + httpCode + (message == null ? "" : ": " + message));
            this.httpCode = httpCode;
            this.retryAfterSec = retryAfterSec;
            this.resetSec = resetSec;
        }
    }

    public static final class GameInfo {
        public final long rootPlaceId;
        public final long universeId;
        public final String name;

        public GameInfo(long rootPlaceId, long universeId, String name) {
            this.rootPlaceId = rootPlaceId;
            this.universeId = universeId;
            this.name = name;
        }
    }

    // ------------------------------------------------------------------
    // JSON (sem rede, testável)
    // ------------------------------------------------------------------

    /**
     * Interpreta a resposta de /v1/games/{placeId}/servers/Public. Ignora entradas sem id.
     * fps/ping ausentes ou nulos viram -1 (status desconhecido).
     */
    public static ServerHop.Page parsePage(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        List<ServerHop.Server> list = new ArrayList<>();
        JSONArray data = o.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject s = data.optJSONObject(i);
                if (s == null || s.isNull("id")) {
                    continue;
                }
                String id = s.optString("id", "");
                if (id.isEmpty()) {
                    continue;
                }
                double fps = known(s, "fps") ? s.optDouble("fps", -1) : -1;
                int ping = known(s, "ping") ? s.optInt("ping", -1) : -1;
                list.add(new ServerHop.Server(id, s.optInt("playing", 0), s.optInt("maxPlayers", 0), fps, ping));
            }
        }
        // Atenção: no Android, optString devolve a palavra "null" para JSON null; por isso o isNull.
        String next = o.isNull("nextPageCursor") ? null : o.optString("nextPageCursor", null);
        if (next != null && next.isEmpty()) {
            next = null;
        }
        return new ServerHop.Page(list, next);
    }

    private static boolean known(JSONObject s, String key) {
        return s.has(key) && !s.isNull(key);
    }

    /** Interpreta a resposta de /v1/games?universeIds=... (primeiro jogo da lista, ou null). */
    public static GameInfo parseGame(String json, long fallbackUniverse) throws JSONException {
        JSONArray d = new JSONObject(json).optJSONArray("data");
        if (d == null || d.length() == 0) {
            return null;
        }
        JSONObject g = d.getJSONObject(0);
        long root = g.optLong("rootPlaceId", 0);
        if (root <= 0) {
            return null;
        }
        return new GameInfo(root, g.optLong("id", fallbackUniverse), g.optString("name", "?"));
    }

    // ------------------------------------------------------------------
    // Rede
    // ------------------------------------------------------------------

    public static ServerHop.Page fetchServers(long placeId, String sortOrder, String cursor)
            throws IOException, ApiException, JSONException {
        return parsePage(get(ServerHop.serversUrl(placeId, sortOrder, 100, cursor)));
    }

    /** Lê páginas na ordem certa para o modo até achar servidores (veja ServerHop.collect). */
    public static List<ServerHop.Server> fetchCandidates(final long placeId, HopMode mode, int maxPages)
            throws Exception {
        return ServerHop.collect(new ServerHop.PageSource() {
            @Override
            public ServerHop.Page fetch(String sortOrder, String cursor) throws Exception {
                return fetchServers(placeId, sortOrder, cursor);
            }
        }, mode, maxPages, 1500);
    }

    /**
     * Converte um número (ID do lugar OU ID do universo, como aparece em sites de estatísticas)
     * no lugar raiz do jogo. Retorna null se o jogo não existir.
     */
    public static GameInfo resolveGame(long id) throws IOException, ApiException, JSONException {
        long universe = 0;
        try {
            String j = get("https://apis.roblox.com/universes/v1/places/" + id + "/universe");
            JSONObject o = new JSONObject(j);
            if (!o.isNull("universeId")) {
                universe = o.optLong("universeId", 0);
            }
        } catch (ApiException e) {
            if (e.httpCode != 400 && e.httpCode != 404) {
                throw e;
            }
        }
        if (universe <= 0) {
            universe = id; // não é um lugar: talvez seja um ID de universo
        }
        return parseGame(get("https://games.roblox.com/v1/games?universeIds=" + universe), universe);
    }

    static String get(String url) throws IOException, ApiException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            if (code == 200) {
                return readAll(c.getInputStream());
            }
            String body = "";
            try {
                body = readAll(c.getErrorStream());
            } catch (IOException ignored) {
                // sem corpo
            }
            throw new ApiException(code, intHeader(c, "Retry-After", 5), intHeader(c, "x-ratelimit-reset", 0),
                    body.length() > 160 ? body.substring(0, 160) : body);
        } finally {
            c.disconnect();
        }
    }

    private static int intHeader(HttpURLConnection c, String name, int fallback) {
        String v = c.getHeaderField(name);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }
}
