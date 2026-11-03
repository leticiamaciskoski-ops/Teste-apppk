import com.meujogo.autoclick.RobloxApi;
import com.meujogo.autoclick.ServerHop;

import java.util.List;

/**
 * Testes de RobloxApi. Precisam do org.json no classpath (no Android ele já existe).
 * Uso: java -cp ... RobloxApiTest offline   (sem rede, com respostas de exemplo)
 *      java -cp ... RobloxApiTest live      (consulta a API real do Roblox; informativo)
 */
public class RobloxApiTest {

    static int checks = 0, failures = 0;

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) failures++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "offline";
        if (mode.equals("live")) {
            live();
        } else {
            offline();
        }
        System.out.println("\n" + checks + " verificacoes, " + failures + " falhas");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void offline() throws Exception {
        System.out.println("[parse da lista de servidores]");
        String json = "{\"previousPageCursor\":null,\"nextPageCursor\":\"abc==\",\"data\":["
                + "{\"id\":\"0574e3d9-d528-4616-88de-2e7cb464d617\",\"maxPlayers\":7,\"playing\":1,\"playerTokens\":[\"x\"],\"fps\":59.9,\"ping\":50},"
                + "{\"id\":\"\",\"maxPlayers\":7,\"playing\":2},"
                + "{\"maxPlayers\":7,\"playing\":2},"
                + "{\"id\":null,\"playing\":2},"
                + "{\"playing\":3,\"maxPlayers\":7,\"id\":\"9d5ce4bf-13e1-4420-8c74-06be476ca79f\"}]}";
        RobloxApi.Page p = RobloxApi.parsePage(json);
        check(p.servers.size() == 2, "ignora entradas sem id (achou " + p.servers.size() + ")");
        check(p.servers.get(0).id.equals("0574e3d9-d528-4616-88de-2e7cb464d617") && p.servers.get(0).playing == 1
                && p.servers.get(0).maxPlayers == 7, "campos id/playing/maxPlayers");
        check("abc==".equals(p.nextCursor), "cursor da proxima pagina");

        RobloxApi.Page last = RobloxApi.parsePage("{\"previousPageCursor\":null,\"nextPageCursor\":null,\"data\":[]}");
        check(last.nextCursor == null && last.servers.isEmpty(), "cursor JSON null vira null (nao a palavra \"null\")");
        check(RobloxApi.parsePage("{}").servers.isEmpty(), "objeto sem data = lista vazia");
        boolean threw = false;
        try { RobloxApi.parsePage("<html>nao e json</html>"); } catch (org.json.JSONException e) { threw = true; }
        check(threw, "resposta que nao e JSON da JSONException");

        System.out.println("[parse do jogo]");
        String g = "{\"data\":[{\"id\":10563114921,\"rootPlaceId\":107778070777162,\"name\":\"Steal An Egg\",\"playing\":936061}]}";
        RobloxApi.GameInfo gi = RobloxApi.parseGame(g, 0);
        check(gi != null && gi.rootPlaceId == 107778070777162L && gi.universeId == 10563114921L && "Steal An Egg".equals(gi.name),
                "rootPlaceId, universo e nome");
        check(RobloxApi.parseGame("{\"data\":[]}", 1) == null, "jogo inexistente = null");
    }

    static void live() throws Exception {
        System.out.println("[API real do Roblox]");
        RobloxApi.GameInfo a = RobloxApi.resolveGame(10563114921L);   // ID de UNIVERSO (como aparece em sites de estatisticas)
        check(a != null && a.rootPlaceId == 107778070777162L, "universo 10563114921 -> place 107778070777162 ("
                + (a == null ? "null" : a.rootPlaceId + " / " + a.name) + ")");
        RobloxApi.GameInfo b = RobloxApi.resolveGame(107778070777162L); // ID de LUGAR
        check(b != null && b.rootPlaceId == 107778070777162L, "place 107778070777162 -> mesmo place ("
                + (b == null ? "null" : b.rootPlaceId + " / " + b.name) + ")");
        List<ServerHop.Server> all = RobloxApi.fetchSmallServers(107778070777162L, 1, 2, 1);
        List<ServerHop.Server> cand = ServerHop.filter(all, 1, 2, null);
        check(!all.isEmpty(), "lista de servidores lida: " + all.size());
        check(!cand.isEmpty(), "ha servidores com 1-2 jogadores: " + cand.size());
        boolean ascending = true;
        for (int i = 1; i < all.size(); i++) if (all.get(i).playing < all.get(i - 1).playing) ascending = false;
        check(ascending, "ordem crescente de jogadores (Asc = menores primeiro)");
        if (!cand.isEmpty()) {
            System.out.println("  exemplo de link: " + ServerHop.deepLink(107778070777162L, cand.get(0).id));
        }
    }
}
