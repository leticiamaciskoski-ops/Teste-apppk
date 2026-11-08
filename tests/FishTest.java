import com.meujogo.serverhop.*;

public class FishTest {
    static int total = 0, fails = 0;
    static void check(boolean ok, String n) { total++; if (!ok) { fails++; System.out.println("FALHOU: " + n); } }

    public static void main(String[] a) {
        Config c = new Config();
        check(c.equipOn == 1 && c.equipTaps == 3, "pegar a vara ligado por padrao");
        check(!c.fishCalibrated(), "nao calibrado");
        c.holdX = c.holdY = c.tapX = c.tapY = c.detectX = c.detectY = 5;
        check(!c.fishCalibrated(), "falta o slot da vara");
        c.equipX = c.equipY = 7;
        check(c.fishCalibrated(), "calibrado com a vara");
        c.equipX = c.equipY = -1; c.equipOn = 0;
        check(c.fishCalibrated(), "sem pegar a vara nao exige o slot");
        c.equipOn = 1; c.equipTaps = 99; c.equipSpacingMs = 1; c.equipEvery = 0; c.equipPauseMs = -5;
        c.holdMs = 10; c.holdJitterMs = 500;
        c.sanitize();
        check(c.equipTaps == 10 && c.equipSpacingMs == 20 && c.equipEvery == 1 && c.equipPauseMs == 0, "sanitize vara");
        check(c.holdMs == 50 && c.holdJitterMs == 0, "sanitize pesca");
        c.equipX = 11; c.equipY = 12;
        Config d = new Config();
        d.fromMap(c.toMap());
        check(d.equipX == 11 && d.equipY == 12 && d.equipTaps == 10, "ida e volta do mapa");
        check(ColorMatch.maxChannelDiff(0xFF102030, 0xFF102030) == 0, "cor igual");
        check(ColorMatch.maxChannelDiff(0xFF102030, 0xFF1A2030) == 10, "cor diff");
        int[] b = TapPlan.batchSizes(13, 10);
        check(b.length == 2 && b[0] == 10 && b[1] == 3, "13 toques = 10+3");
        RunStats s = new RunStats();
        s.recordWait(1000); s.recordWait(3000);
        check(s.waitAvg() == 2000 && s.signals == 2, "estatisticas");
        check(s.recordTaps(400, 500) && !s.recordTaps(600, 500) && s.tapsOverWindow == 1, "janela dos toques");
        // deteccao do sinal por fracao da area, acima do fundo
        final int[] img = new int[11 * 11];
        java.util.Arrays.fill(img, 0xFF204060);
        ColorMatch.Pixels px = new ColorMatch.Pixels() { public int get(int x, int y) { return img[y * 11 + x]; } };
        int sig = 0xFFFFD040;
        int f0 = ColorMatch.fraction(px, 11, 11, 5, 5, 5, sig, 40);
        check(f0 == 0, "sem sinal: 0%");
        for (int i = 0; i < 20; i++) img[i + 40] = sig; // 20 de 121 pixels
        int f1 = ColorMatch.fraction(px, 11, 11, 5, 5, 5, sig, 40);
        check(f1 == 17, "20 de 121 pixels = 17%");
        check(ColorMatch.signalHit(f1, f0, 15) && !ColorMatch.signalHit(f0, f0, 15), "sinal acima do fundo");
        check(ColorMatch.signalHit(60, 50, 15) == false && ColorMatch.signalHit(70, 50, 15), "fundo ja parecido exige mais");
        check(!ColorMatch.detectable(95, 15) && ColorMatch.detectable(85, 15), "detectavel");
        check(ColorMatch.fraction(px, 11, 11, 0, 0, 2, sig, 40) == 0, "borda da imagem");
        check(c.detectMinPct == 15 && c.ignoreMs == 800 && c.detectConfirm == 1, "padroes da deteccao");
        c.detectMinPct = 500; c.detectConfirm = 99; c.ignoreMs = -1; c.sanitize();
        check(c.detectMinPct == 100 && c.detectConfirm == 5 && c.ignoreMs == 0, "sanitize deteccao");
        boolean threw = false;
        try { c.get("joyX"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "chaves de locomocao removidas");
        System.out.println(total + " verificacoes, " + fails + " falhas");
        if (fails > 0) System.exit(1);
    }
}
