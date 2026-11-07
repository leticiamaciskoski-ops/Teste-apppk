import com.meujogo.serverhop.Config;
import com.meujogo.serverhop.HopMode;
import com.meujogo.serverhop.ServerHop;
import com.meujogo.serverhop.ServerPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Testes da lógica pura do server hop. Rodar com tests/run-tests.sh (só precisa de JDK). */
public class HopLogicTest {

    static int checks = 0, failures = 0, seq = 0;

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) failures++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    /** Servidor de teste com id de GUID válido. fps/ping < 0 = status desconhecido. */
    static ServerHop.Server sv(int playing, int cap, double fps, int ping) {
        String id = String.format("aaaaaaaa-0000-0000-0000-%012x", ++seq);
        return new ServerHop.Server(id, playing, cap, fps, ping);
    }

    static ServerHop.Server ok(int playing) {
        return sv(playing, 7, 59.9, 80);
    }

    static ServerHop.Server unknown(int playing) {
        return sv(playing, 7, -1, -1);
    }

    /** Páginas de mentira para testar a coleta sem rede. */
    static class Fake implements ServerHop.PageSource {
        final List<ServerHop.Page> pages;
        final List<String> sorts = new ArrayList<String>();
        final List<String> cursors = new ArrayList<String>();
        int calls = 0;

        Fake(ServerHop.Page... p) {
            pages = Arrays.asList(p);
        }

        public ServerHop.Page fetch(String sort, String cursor) {
            sorts.add(sort);
            cursors.add(cursor);
            return pages.get(calls++);
        }
    }

    static ServerHop.Page page(String next, ServerHop.Server... s) {
        return new ServerHop.Page(new ArrayList<ServerHop.Server>(Arrays.asList(s)), next);
    }

    static ServerHop.Page many(int n, boolean healthy, int playing, String next) {
        List<ServerHop.Server> l = new ArrayList<ServerHop.Server>();
        for (int i = 0; i < n; i++) l.add(healthy ? ok(playing) : unknown(playing));
        return new ServerHop.Page(l, next);
    }

    public static void main(String[] args) throws Exception {
        ids();
        status();
        modes();
        candidates();
        collect();
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

    static void status() {
        System.out.println("[status do servidor (fps/ping)]");
        check(ok(1).healthy() && ok(1).statusKnown(), "fps 59,9 e ping 80 = saudavel");
        check(!unknown(1).statusKnown() && !unknown(1).healthy(), "sem fps e sem ping = status DESCONHECIDO");
        check(!sv(1, 7, 59, -1).statusKnown(), "so fps, sem ping = desconhecido");
        check(!sv(1, 7, -1, 80).statusKnown(), "so ping, sem fps = desconhecido");
        check(!sv(1, 7, 0, 80).statusKnown(), "fps 0 = desconhecido");
        check(sv(1, 7, 25, 80).statusKnown() && !sv(1, 7, 25, 80).healthy(), "fps 25 = conhecido mas engasgado");
        check(sv(1, 7, 30, 80).healthy(), "fps 30 = limite aceito");
        check(!sv(1, 7, 59, 501).healthy() && sv(1, 7, 59, 500).healthy(), "ping 500 aceito, 501 recusado");
        check(sv(1, 7, 59, 0).healthy(), "ping 0 e valido");
    }

    static void modes() {
        System.out.println("[modos]");
        check(HopMode.EMPTY.accepts(1, 7) && HopMode.EMPTY.accepts(2, 7), "Vazio aceita 1 e 2");
        check(!HopMode.EMPTY.accepts(0, 7) && !HopMode.EMPTY.accepts(3, 7), "Vazio recusa 0 e 3");
        check(HopMode.BUSY.accepts(5, 7) && HopMode.BUSY.accepts(6, 7), "Movimentado (cap 7) aceita 5 e 6");
        check(!HopMode.BUSY.accepts(4, 7) && !HopMode.BUSY.accepts(7, 7), "Movimentado recusa 4 e cheio (7)");
        check(HopMode.BUSY.accepts(10, 12) && HopMode.BUSY.accepts(11, 12) && !HopMode.BUSY.accepts(9, 12),
                "Movimentado acompanha a capacidade (cap 12 = 10 e 11)");
        check(HopMode.EMPTY.accepts(1, 0), "capacidade desconhecida usa 7");
        check("Asc".equals(HopMode.EMPTY.sort) && "Desc".equals(HopMode.BUSY.sort), "ordem da lista por modo");
        check("1 a 2 jogadores".equals(HopMode.EMPTY.describe(7)) && "5 a 6 jogadores".equals(HopMode.BUSY.describe(7)), "texto do alcance");
        check(HopMode.fromIndex(1) == HopMode.BUSY && HopMode.fromIndex(9) == HopMode.EMPTY && HopMode.fromIndex(-1) == HopMode.EMPTY, "fromIndex");
    }

    static void candidates() {
        System.out.println("[candidatos]");
        List<ServerHop.Server> all = new ArrayList<ServerHop.Server>();
        ServerHop.Server a = ok(1), b = ok(2), c = ok(3), d = unknown(1), e = sv(1, 7, 20, 90), f = sv(1, 1, 59, 50);
        all.addAll(Arrays.asList(a, b, c, d, e, f));
        Set<String> none = new HashSet<String>();
        List<ServerHop.Server> r = ServerHop.candidates(all, HopMode.EMPTY, none);
        check(r.size() == 2 && r.contains(a) && r.contains(b), "Vazio: so 1 e 2 jogadores saudaveis");
        check(!r.contains(d), "servidor em status desconhecido nunca e escolhido");
        check(!r.contains(e), "servidor com fps baixo nunca e escolhido");
        check(!r.contains(f), "servidor cheio (1 de 1) nunca e escolhido");
        Set<String> ex = new HashSet<String>();
        ex.add(a.id);
        check(ServerHop.candidates(all, HopMode.EMPTY, ex).size() == 1, "servidores ja visitados sao excluidos");
        check(ServerHop.candidates(all, HopMode.BUSY, none).isEmpty(), "Movimentado sem 5-6 jogadores = vazio");
        List<ServerHop.Server> bad = new ArrayList<ServerHop.Server>();
        bad.add(new ServerHop.Server("x&evil=1", 1, 7, 60, 50));
        check(ServerHop.candidates(bad, HopMode.EMPTY, none).isEmpty(), "id de servidor invalido e descartado");
        check(ServerHop.pick(new ArrayList<ServerHop.Server>(), new Random(1)) == null, "pick em lista vazia = null");
        Random rng = new Random(3);
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 200; i++) seen.add(ServerHop.pick(r, rng).id);
        check(seen.size() == 2, "pick sorteia entre todos os candidatos");
        check(ServerHop.minPlaying(all) == 1 && ServerHop.maxPlaying(all) == 3
                && ServerHop.minPlaying(new ArrayList<ServerHop.Server>()) == Integer.MAX_VALUE, "min/max de jogadores");
    }

    static void collect() throws Exception {
        System.out.println("[coleta de paginas]");
        // A: 1a pagina so com status desconhecido, 2a tem um saudavel
        Fake fa = new Fake(many(100, false, 1, "c1"), page("c2", ok(1)));
        List<ServerHop.Server> r = ServerHop.collect(fa, HopMode.EMPTY, 3, 0);
        check(fa.calls == 2 && r.size() == 101 && ServerHop.candidates(r, HopMode.EMPTY, null).size() == 1,
                "pula pagina de status desconhecido e acha na seguinte (consultas: " + fa.calls + ")");
        check(fa.cursors.get(0) == null && "c1".equals(fa.cursors.get(1)) && "Asc".equals(fa.sorts.get(0)), "usa o cursor e a ordem Asc");
        // B: respeita o limite de paginas
        Fake fb = new Fake(many(100, false, 1, "c1"), many(100, false, 1, "c2"));
        ServerHop.collect(fb, HopMode.EMPTY, 1, 0);
        check(fb.calls == 1, "respeita o limite de 1 pagina");
        // C: Asc para cedo quando a pagina ja passou do alcance
        Fake fc = new Fake(page("c", ok(4), ok(5), ok(6)));
        ServerHop.collect(fc, HopMode.EMPTY, 5, 0);
        check(fc.calls == 1, "Asc: pagina com minimo 4 ja passou de 2, para");
        // D: Desc acha na primeira
        Fake fd = new Fake(page("c", ok(6), ok(6), ok(5)));
        List<ServerHop.Server> rd = ServerHop.collect(fd, HopMode.BUSY, 3, 0);
        check(fd.calls == 1 && "Desc".equals(fd.sorts.get(0)) && ServerHop.candidates(rd, HopMode.BUSY, null).size() == 3,
                "Movimentado usa Desc e acha na primeira");
        // E: Desc para cedo
        Fake fe = new Fake(page("c", ok(3), ok(2)));
        ServerHop.collect(fe, HopMode.BUSY, 5, 0);
        check(fe.calls == 1, "Desc: pagina com maximo 3 ja passou de 5, para");
        // F: sem proxima pagina
        Fake ff = new Fake(many(100, false, 1, null));
        ServerHop.collect(ff, HopMode.EMPTY, 5, 0);
        check(ff.calls == 1, "sem proxima pagina, para");
        // G: pagina vazia
        Fake fg = new Fake(page("c"));
        check(ServerHop.collect(fg, HopMode.EMPTY, 5, 0).isEmpty() && fg.calls == 1, "pagina vazia, para");
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
        check(ServerHop.serversUrl(107778070777162L, "Desc", 100, null)
                .equals("https://games.roblox.com/v1/games/107778070777162/servers/Public?sortOrder=Desc&limit=100&excludeFullGames=true"),
                "url da lista (Desc)");
        check(ServerHop.serversUrl(1, "Asc", 100, "ab+/=c").endsWith("&cursor=ab%2B%2F%3Dc"), "cursor vai codificado na url");
        check("1 jogador · 60 fps · 83 ms".equals(ServerHop.describe(new ServerHop.Server(job, 1, 7, 59.97, 83)))
                && "5 jogadores · 45 fps · 120 ms".equals(ServerHop.describe(new ServerHop.Server(job, 5, 7, 45.2, 120))),
                "texto do servidor escolhido");
    }

    static void pool() {
        System.out.println("[lista guardada (cache)]");
        ServerPool p = new ServerPool();
        Set<String> none = new HashSet<String>();
        Random rng = new Random(5);
        check(p.take(1, 0, 45000, HopMode.EMPTY, none, rng) == null, "pool vazio = null");
        List<ServerHop.Server> l = new ArrayList<ServerHop.Server>();
        ServerHop.Server a = ok(1), b = ok(2), u = unknown(1);
        l.addAll(Arrays.asList(a, b, u));
        p.replace(1, "Asc", l, 1000);
        check(p.fresh(1, "Asc", 1000 + 45000, 45000) && !p.fresh(1, "Asc", 1000 + 45001, 45000), "validade de 45 s");
        check(p.take(1, 2000, 45000, HopMode.EMPTY, none, rng) != null, "devolve candidato enquanto fresco");
        check(p.take(2, 2000, 45000, HopMode.EMPTY, none, rng) == null, "outro jogo = nao reaproveita");
        check(p.take(1, 2000, 45000, HopMode.BUSY, none, rng) == null, "outra ordem (Desc) = nao reaproveita");
        check(p.take(1, 60000, 45000, HopMode.EMPTY, none, rng) == null, "lista velha = nao reaproveita");
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 100; i++) seen.add(p.take(1, 2000, 45000, HopMode.EMPTY, none, rng).id);
        check(seen.size() == 2 && !seen.contains(u.id), "nunca devolve o de status desconhecido");
        Set<String> ex = new HashSet<String>();
        ex.add(a.id);
        check(p.take(1, 2000, 45000, HopMode.EMPTY, ex, rng).id.equals(b.id), "respeita a lista de excluidos");
        ex.add(b.id);
        check(p.take(1, 2000, 45000, HopMode.EMPTY, ex, rng) == null, "tudo excluido = null (pede nova consulta)");
        p.clear();
        check(p.size() == 0 && !p.fresh(1, "Asc", 2000, 45000), "clear limpa");
    }

    static void config() {
        System.out.println("[config]");
        Config c = new Config();
        check(c.placeId == 107778070777162L && "Steal An Egg".equals(c.placeName), "padrao = Steal An Egg (place raiz)");
        check(c.hopMode() == HopMode.EMPTY && c.maxPages == 2, "padrao: modo Vazio, 2 paginas");
        c.mode = 9; c.maxPages = 99; c.overlayX = -5; c.overlayOpen = 7; c.placeId = 0; c.placeName = "";
        c.sanitize();
        check(c.mode == 1 && c.maxPages == 5 && c.overlayX == 0 && c.overlayOpen == 1, "limites aplicados");
        check(c.placeId == Config.DEFAULT_PLACE_ID && "Steal An Egg".equals(c.placeName), "place invalido volta ao padrao");
        Config d = new Config();
        d.set("mode", 1); d.set("overlayX", 120);
        Config e = new Config();
        e.fromMap(d.toMap());
        check(e.hopMode() == HopMode.BUSY && e.overlayX == 120, "ida e volta pelo mapa");
        boolean threw = false;
        try { c.get("nao_existe"); } catch (IllegalArgumentException ex) { threw = true; }
        check(threw, "chave desconhecida da erro");
    }
}
