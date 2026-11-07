import com.meujogo.serverhop.*;
import java.util.List;

public class RobloxApiTest {
    static int fails = 0, total = 0;
    static void check(String n, boolean ok) { total++; if (!ok) { fails++; System.out.println("FALHOU: " + n); } }

    public static void main(String[] a) throws Exception {
        boolean live = a.length > 0 && a[0].equals("live");
        String json = "{\"previousPageCursor\":null,\"nextPageCursor\":\"abc\",\"data\":["
            + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000001\",\"maxPlayers\":7,\"playing\":1,\"fps\":59.9,\"ping\":80},"
            + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000002\",\"maxPlayers\":7,\"playing\":1},"
            + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000003\",\"maxPlayers\":7,\"playing\":2,\"fps\":null,\"ping\":null},"
            + "{\"maxPlayers\":7,\"playing\":2}]}";
        ServerHop.Page p = RobloxApi.parsePage(json);
        check("3 entradas com id", p.servers.size() == 3);
        check("cursor", "abc".equals(p.nextCursor));
        check("primeiro conhecido", p.servers.get(0).statusKnown() && p.servers.get(0).healthy());
        check("sem fps/ping desconhecido", !p.servers.get(1).statusKnown());
        check("null desconhecido", !p.servers.get(2).statusKnown());
        check("cursor null", RobloxApi.parsePage("{\"nextPageCursor\":null,\"data\":[]}").nextCursor == null);
        RobloxApi.GameInfo g = RobloxApi.parseGame("{\"data\":[{\"id\":10563114921,\"rootPlaceId\":107778070777162,\"name\":\"Steal An Egg\"}]}", 1);
        check("game", g != null && g.rootPlaceId == 107778070777162L && g.name.equals("Steal An Egg"));
        check("game vazio", RobloxApi.parseGame("{\"data\":[]}", 1) == null);

        if (live) {
            RobloxApi.GameInfo u = RobloxApi.resolveGame(10563114921L);
            check("universo -> lugar", u != null && u.rootPlaceId == 107778070777162L);
            RobloxApi.GameInfo pl = RobloxApi.resolveGame(107778070777162L);
            check("lugar -> lugar", pl != null && pl.rootPlaceId == 107778070777162L);
            for (HopMode m : HopMode.values()) {
                Thread.sleep(21000); // limite de 3 consultas por minuto
                List<ServerHop.Server> l = RobloxApi.fetchCandidates(107778070777162L, m, 1);
                System.out.println(m + ": " + l.size() + " candidatos");
                for (ServerHop.Server s : l) check("saudavel " + s.id, s.statusKnown() && s.healthy());
            }
        }
        System.out.println(total + " verificacoes, " + fails + " falhas");
        if (fails > 0) System.exit(1);
    }
}
