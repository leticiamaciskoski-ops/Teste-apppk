import com.meujogo.serverhop.*;
import java.util.Random;

public class RouteTest {
    static int total = 0, fails = 0;
    static void check(boolean ok, String n) { total++; if (!ok) { fails++; System.out.println("FALHOU: " + n); } }

    public static void main(String[] a) {
        // fases
        check(Route.first(true, false) == Route.Phase.WALK_TO, "comeca andando");
        check(Route.first(true, true) == Route.Phase.FISH, "ja na poca");
        check(Route.first(false, false) == Route.Phase.FISH, "sem rota pesca");
        check(Route.next(Route.Phase.WALK_TO, true) == Route.Phase.FISH, "ida -> pesca");
        check(Route.next(Route.Phase.FISH, true) == Route.Phase.WALK_BACK, "pesca -> sai");
        check(Route.next(Route.Phase.WALK_BACK, true) == Route.Phase.WALK_TO, "sai -> volta a poca");
        check(Route.next(Route.Phase.WALK_BACK, false) == Route.Phase.FISH, "sem rota sempre pesca");
        // angulos
        check(Route.normAngle(-90) == 270 && Route.normAngle(720) == 0 && Route.normAngle(361) == 1, "normAngle");
        check(Route.opposite(0) == 180 && Route.opposite(270) == 90, "oposto");
        int[] t = Route.joystickTarget(200, 800, 100, 0);
        check(t[0] == 200 && t[1] == 700, "cima");
        t = Route.joystickTarget(200, 800, 100, 90);
        check(t[0] == 300 && t[1] == 800, "direita");
        t = Route.joystickTarget(200, 800, 100, 180);
        check(t[0] == 200 && t[1] == 900, "baixo");
        t = Route.joystickTarget(200, 800, 100, 270);
        check(t[0] == 100 && t[1] == 800, "esquerda");
        t = Route.joystickTarget(0, 0, 100, 45);
        check(t[0] == 71 && t[1] == -71, "diagonal");
        // jitter
        Random r = new Random(1);
        check(Route.jitter(5000, 0, r) == 5000, "sem jitter");
        boolean ok = true;
        for (int i = 0; i < 500; i++) { long v = Route.jitter(5000, 20, r); if (v < 4000 || v > 6000) ok = false; }
        check(ok, "jitter dentro de 20%");
        // relogio
        check(Route.clock(330000).equals("5:30"), "5:30");
        check(Route.clock(0).equals("0:00"), "zero");
        check(Route.clock(3725000).equals("1:02:05"), "hora");
        check(Route.clock(1).equals("0:01"), "arredonda para cima");
        check(Route.compass(0).startsWith("↑") && Route.compass(90).startsWith("→") && Route.compass(180).startsWith("↓")
                && Route.compass(270).startsWith("←") && Route.compass(350).startsWith("↑"), "bussola");
        // config
        Config c = new Config();
        check(c.fishSec == 330, "5 min 30 por padrao");
        check(!c.fishCalibrated() && !c.joystickCalibrated(), "nao calibrado");
        c.walkSec = 999; c.backAngle = -90; c.fishSec = 1; c.walkJitterPct = 99; c.holdMs = 10; c.holdJitterMs = 500;
        c.sanitize();
        check(c.walkSec == 50 && c.backAngle == 270 && c.fishSec == 10 && c.walkJitterPct == 50, "sanitize rota");
        check(c.holdMs == 50 && c.holdJitterMs == 0, "sanitize pesca");
        c.holdX = c.holdY = c.tapX = c.tapY = c.detectX = c.detectY = 5; c.joyX = c.joyY = 9;
        check(c.fishCalibrated() && c.joystickCalibrated(), "calibrado");
        Config d = new Config();
        d.fromMap(c.toMap());
        check(d.joyX == 9 && d.walkSec == 50 && d.detectColor == c.detectColor, "ida e volta do mapa");
        // cores, toques, estatisticas
        check(ColorMatch.maxChannelDiff(0xFF102030, 0xFF102030) == 0, "cor igual");
        check(ColorMatch.maxChannelDiff(0xFF102030, 0xFF1A2030) == 10, "cor diff");
        int[] b = TapPlan.batchSizes(13, 10);
        check(b.length == 2 && b[0] == 10 && b[1] == 3, "13 toques = 10+3");
        RunStats s = new RunStats();
        s.recordWait(1000); s.recordWait(3000);
        check(s.waitAvg() == 2000 && s.signals == 2, "estatisticas");
        check(s.recordTaps(400, 500) && !s.recordTaps(600, 500) && s.tapsOverWindow == 1, "janela dos toques");
        System.out.println(total + " verificacoes, " + fails + " falhas");
        if (fails > 0) System.exit(1);
    }
}
