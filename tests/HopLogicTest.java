import com.meujogo.autoclick.Config;
import com.meujogo.autoclick.ServerHop;
import com.meujogo.autoclick.ServerPool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Testes da lógica pura do server hop. Rodar com tests/run-tests.sh (só precisa de JDK). */
public class HopLogicTest {

    static int checks = 0, failures = 0;

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) failures++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static ServerHop.Server sv(String id, int playing, int max) {
        return new ServerHop.Server(id, playing, max);
    }

    public static void main(String[] args) {
        ids();
        filtering();
        links();
        pool();
        config();
        System.out.println("\n" + checks + " verificacoes, " + failures + " falhas");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void ids() {
        System.out.println("[numero/link do jogo]");
        check(ServerHop.parseId("107778070777162") == 107778070777162L, "so o numero (maior que 32 bits)");
        check(ServerHop.parseId("  10563114921 ") == 10563114921L, "numero com espacos");
        check(ServerHop.parseId("https://www.roblox.com/games/107778070777162/Steal-An-Egg") == 107778070777162L, "link /games/ID/nome");
        check(ServerHop.parseId("roblox.com/games/123456789") == 123456789L, "link sem https");
        check(ServerHop.parseId("https://www.roblox.com/games/start?placeId=555&launchData=x") == 555L, "link start?placeId=");
        check(ServerHop.parseId("roblox://experiences/start?placeId=777&gameInstanceId=abc") == 777L, "deep link roblox://");
        check(ServerHop.parseId("") == -1 && ServerHop.parseId(null) == -1, "vazio/nulo = -1");
        check(ServerHop.parseId("abc") == -1, "texto sem numero = -1");
        check(ServerHop.parseId("99999999999999999999999") == -1, "numero grande demais = -1");
        check(ServerHop.parseId("0") == -1, "zero = -1");
    }

    static void filtering() {
        System.out.println("[filtro de servidores]");
        List<ServerHop.Server> all = new ArrayList<>();
        all.add(sv("a0000000-0000", 0, 7));
        all.add(sv("a1111111-1111", 1, 7));
        all.add(sv("a2222222-2222", 2, 7));
        all.add(sv("a3333333-3333", 3, 7));
        all.add(sv("a4444444-4444", 1, 1)); // cheio (1 de 1)
        Set<String> none = new HashSet<String>();
        List<ServerHop.Server> r = ServerHop.filter(all, 1, 2, none);
        check(r.size() == 2 && r.get(0).playing == 1 && r.get(1).playing == 2, "1-2 jogadores: so 1 e 2, sem cheios nem vazios");
        check(ServerHop.filter(all, 2, 2, none).size() == 1, "exatamente 2");
        Set<String> ex = new HashSet<String>();
        ex.add("a1111111-1111");
        r = ServerHop.filter(all, 1, 2, ex);
        check(r.size() == 1 && r.get(0).playing == 2, "servidores ja visitados sao excluidos");
        check(ServerHop.filter(all, 5, 9, none).isEmpty(), "sem candidatos = lista vazia");
        check(ServerHop.minPlaying(all) == 0 && ServerHop.minPlaying(new ArrayList<ServerHop.Server>()) == Integer.MAX_VALUE, "menor numero de jogadores");
        check(ServerHop.pick(new ArrayList<ServerHop.Server>(), new Random(1)) == null, "pick em lista vazia = null");
        Random rng = new Random(3);
        Set<String> seen = new HashSet<String>();
        List<ServerHop.Server> c = ServerHop.filter(all, 1, 3, none);
        for (int i = 0; i < 200; i++) seen.add(ServerHop.pick(c, rng).id);
        check(seen.size() == c.size(), "pick sorteia entre todos os candidatos");
    }

    static void links() {
        System.out.println("[links]");
        String job = "0574e3d9-d528-4616-88de-2e7cb464d617";
        check(("roblox://experiences/start?placeId=107778070777162&gameInstanceId=" + job)
                .equals(ServerHop.deepLink(107778070777162L, job)), "deep link do app");
        check(("https://www.roblox.com/games/start?placeId=1&gameInstanceId=" + job).equals(ServerHop.webLink(1, job)), "link web");
        check(ServerHop.deepLink(1, "x&evil=1") == null, "job id com lixo e recusado");
        check(ServerHop.deepLink(0, job) == null && ServerHop.deepLink(1, null) == null, "place 0 ou job nulo recusados");
        check(ServerHop.validJobId(job) && !ServerHop.validJobId("") && !ServerHop.validJobId("curto"), "validacao de job id");
        String u = ServerHop.serversUrl(107778070777162L, "Asc", 100, null);
        check(u.equals("https://games.roblox.com/v1/games/107778070777162/servers/Public?sortOrder=Asc&limit=100&excludeFullGames=true"), "url da lista de servidores");
        String u2 = ServerHop.serversUrl(1, "Asc", 100, "ab+/=c");
        check(u2.endsWith("&cursor=ab%2B%2F%3Dc"), "cursor vai codificado na url");
        check("1-2 jogadores".equals(ServerHop.describe(1, 2)) && "1 jogador".equals(ServerHop.describe(1, 1))
                && "2 jogadores".equals(ServerHop.describe(2, 2)), "texto do alcance de jogadores");
    }

    static void pool() {
        System.out.println("[lista guardada (cache)]");
        ServerPool p = new ServerPool();
        Set<String> none = new HashSet<String>();
        Random rng = new Random(5);
        check(p.take(1, 0, 45000, 1, 2, none, rng) == null, "pool vazio = null");
        List<ServerHop.Server> l = new ArrayList<>();
        l.add(sv("a1111111-1111", 1, 7));
        l.add(sv("a2222222-2222", 2, 7));
        p.replace(1, l, 1000);
        check(p.fresh(1, 1000 + 45000, 45000) && !p.fresh(1, 1000 + 45001, 45000), "validade de 45 s");
        check(p.take(1, 2000, 45000, 1, 2, none, rng) != null, "devolve candidato enquanto fresco");
        check(p.take(2, 2000, 45000, 1, 2, none, rng) == null, "outro jogo = nao reaproveita");
        check(p.take(1, 60000, 45000, 1, 2, none, rng) == null, "lista velha = nao reaproveita");
        Set<String> ex = new HashSet<String>();
        ex.add("a1111111-1111");
        ServerHop.Server s = p.take(1, 2000, 45000, 1, 2, ex, rng);
        check(s != null && s.id.equals("a2222222-2222"), "respeita a lista de excluidos");
        ex.add("a2222222-2222");
        check(p.take(1, 2000, 45000, 1, 2, ex, rng) == null, "tudo excluido = null (pede nova consulta)");
        p.clear();
        check(p.size() == 0 && !p.fresh(1, 2000, 45000), "clear limpa");
    }

    static void config() {
        System.out.println("[config do hop]");
        Config c = new Config();
        check(c.placeId == 107778070777162L && "Steal An Egg".equals(c.placeName), "padrao = Steal An Egg (place raiz)");
        check(c.hopMin == 1 && c.hopMax == 2 && c.hopMaxPages == 2, "padrao 1-2 jogadores, 2 paginas");
        c.hopMin = 0; c.hopMax = 0; c.hopMaxPages = 99;
        c.sanitize();
        check(c.hopMin == 1 && c.hopMax == 1 && c.hopMaxPages == 5, "limites: min>=1, max>=min, paginas<=5");
        c.hopMin = 3; c.hopMax = 2;
        c.sanitize();
        check(c.hopMax == 3, "max nunca fica abaixo do min");
        Config d = new Config();
        d.set("hopMin", 2); d.set("hopMax", 4);
        check(d.get("hopMin") == 2 && d.get("hopMax") == 4, "get/set do hop");
        check(Config.HOP_EDITABLE.length == Config.HOP_LABELS.length, "todo campo do hop tem rotulo");
        Config e = new Config();
        e.fromMap(d.toMap());
        check(e.hopMin == 2 && e.hopMax == 4, "ida e volta pelo mapa");
    }
}
