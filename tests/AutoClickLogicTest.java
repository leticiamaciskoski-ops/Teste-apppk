import com.meujogo.autoclick.ColorMatch;
import com.meujogo.autoclick.Config;
import com.meujogo.autoclick.RunStats;
import com.meujogo.autoclick.TapPlan;

import java.util.Arrays;
import java.util.Map;

/** Testes da lógica pura do autoclick. Rodar com tests/run-tests.sh (só precisa de JDK). */
public class AutoClickLogicTest {

    static int checks = 0, failures = 0;

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) failures++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    public static void main(String[] args) {
        colors();
        taps();
        config();
        stats();
        System.out.println("\n" + checks + " verificacoes, " + failures + " falhas");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void colors() {
        System.out.println("[cores]");
        check(ColorMatch.maxChannelDiff(0xFFFF0000, 0xFFFF0000) == 0, "cores iguais = diferenca 0");
        check(ColorMatch.maxChannelDiff(0xFFFF0000, 0xFFE00010) == 31, "maior diferenca entre canais (R=31, B=16 -> 31)");
        check(ColorMatch.maxChannelDiff(0x00FF0000, 0xFFFF0000) == 0, "canal alfa e ignorado");
        check("#FF4D4D".equals(ColorMatch.hex(0xFFFF4D4D)), "hex formata #RRGGBB");

        final int w = 100, h = 80, ref = 0xFFFF4D4D;
        ColorMatch.Pixels img = (x, y) -> (x == 50 && y == 40) ? ref : 0xFF16304F;
        check(ColorMatch.bestDiff(img, w, h, 50, 40, 0, ref) == 0, "pixel exato acha a cor");
        check(ColorMatch.bestDiff(img, w, h, 52, 41, 3, ref) == 0, "acha a cor dentro do raio 3");
        check(ColorMatch.bestDiff(img, w, h, 56, 40, 3, ref) > 100, "fora do raio nao acha");
        check(ColorMatch.bestDiff(img, w, h, 0, 0, 3, ref) > 100, "canto da imagem nao estoura limites");
        check(ColorMatch.bestDiff(img, w, h, 500, 500, 3, ref) == 255, "ponto fora da imagem = 255");
        ColorMatch.Pixels near = (x, y) -> 0xFFF05A55; // diferenca max 0x0F..: R 15, G 13, B 8
        check(ColorMatch.bestDiff(near, w, h, 10, 10, 1, ref) <= 40, "cor parecida fica dentro da tolerancia 40");
    }

    static void taps() {
        System.out.println("[toques em lotes]");
        check(Arrays.equals(TapPlan.batchSizes(13, 10), new int[]{10, 3}), "13 toques, max 10 -> [10, 3]");
        check(Arrays.equals(TapPlan.batchSizes(10, 10), new int[]{10}), "10 toques -> [10]");
        check(Arrays.equals(TapPlan.batchSizes(25, 10), new int[]{10, 10, 5}), "25 toques -> [10, 10, 5]");
        check(TapPlan.batchSizes(0, 10).length == 0, "0 toques -> nenhum lote");
        long est = TapPlan.estimateWindowMs(13, 25, 12, 10, 60);
        check(est == 347, "estimativa 13 toques (25 ms, dur 12, latencia 60) = 347 ms (deu " + est + ")");
        check(TapPlan.estimateWindowMs(13, 25, 12, 10, 60) < 500, "padrao cabe em 500 ms");
        check(TapPlan.estimateWindowMs(13, 45, 12, 10, 60) > 500, "intervalo de 45 ms estoura 500 ms");
        check(TapPlan.estimateWindowMs(10, 25, 12, 10, 60) == 225, "um unico lote nao soma latencia");
    }

    static void config() {
        System.out.println("[configuracao]");
        Config c = new Config();
        check(!c.calibrated(), "novo config nao esta calibrado");
        c.holdX = 100; c.holdY = 200; c.tapX = 300; c.tapY = 400; c.detectX = 500; c.detectY = 600;
        check(c.calibrated(), "calibrado com os 3 pontos");
        Map<String, Integer> m = c.toMap();
        Config d = new Config();
        d.fromMap(m);
        check(d.holdX == 100 && d.detectY == 600 && d.taps == 13 && d.holdMs == 1300, "ida e volta pelo mapa preserva valores");
        c.holdMs = 5; c.taps = 9999; c.pollMs = 10; c.tolerance = 999; c.holdJitterMs = 100000; c.spacingMs = 0;
        c.sanitize();
        check(c.holdMs == 50 && c.taps == 200 && c.pollMs == 340 && c.tolerance == 255 && c.spacingMs == 1,
                "valores absurdos sao limitados");
        check(c.holdJitterMs <= c.holdMs - 50, "variacao do segurar nunca zera o tempo");
        c.set("taps", 7);
        check(c.get("taps") == 7, "get/set por nome");
        boolean threw = false;
        try { c.get("nao_existe"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "chave desconhecida da erro");
        check(Config.EDITABLE.length == Config.LABELS.length, "todo campo editavel tem rotulo");
    }

    static void stats() {
        System.out.println("[estatisticas]");
        RunStats s = new RunStats();
        s.recordWait(3000); s.recordWait(5000); s.recordWait(7000);
        check(s.waitMin == 3000 && s.waitMax == 7000 && s.waitAvg() == 5000 && s.signals == 3, "espera min/media/max");
        check(s.recordTaps(380, 500) && s.tapsOverWindow == 0, "toques dentro da janela");
        check(!s.recordTaps(620, 500) && s.tapsOverWindow == 1 && s.tapSpanMax == 620, "toques fora da janela contam");
        check(s.summary().contains("3000/5000/7000"), "resumo mostra espera");
        s.reset();
        check(s.signals == 0 && s.summary().contains("-"), "reset limpa tudo");
    }
}
