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
        boolean threw = false;
        try { c.get("joyX"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "chaves de locomocao removidas");
        System.out.println(total + " verificacoes, " + fails + " falhas");
        if (fails > 0) System.exit(1);
    }
}
