package com.meujogo.autoclick;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Faz o server hop: escolhe um servidor com poucos jogadores e abre o Roblox direto nele.
 * Usado tanto pelo painel flutuante quanto pela tela do app.
 */
public final class HopController {

    private HopController() {
    }

    /** Recebe mensagens de andamento, sempre na thread principal. */
    public interface Listener {
        void onStatus(String msg);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ServerPool POOL = new ServerPool();
    private static final LinkedHashSet<String> RECENT = new LinkedHashSet<>();
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final Random RNG = new Random();
    private static final long TTL_MS = 45000;
    private static final int RECENT_MAX = 60;

    public static void hop(final Context ctx, final Config cfg, final Listener l) {
        if (!BUSY.compareAndSet(false, true)) {
            say(l, "Já estou buscando um servidor, aguarde.");
            return;
        }
        final long place = cfg.placeId;
        final int min = cfg.hopMin;
        final int max = cfg.hopMax;
        final int pages = cfg.hopMaxPages;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work(ctx, l, place, min, max, pages);
                } catch (Exception e) {
                    say(l, explain(e));
                } finally {
                    BUSY.set(false);
                }
            }
        }, "server-hop").start();
    }

    private static void work(Context ctx, Listener l, long place, int min, int max, int pages) throws Exception {
        if (place <= 0) {
            say(l, "Defina o jogo (Place ID) na tela do app.");
            return;
        }
        ServerHop.Server s = take(place, min, max);
        if (s == null) {
            say(l, "Consultando servidores com " + ServerHop.describe(min, max) + "...");
            List<ServerHop.Server> list = fetch(l, place, min, max, pages);
            POOL.replace(place, list, SystemClock.uptimeMillis());
            s = take(place, min, max);
        }
        if (s == null) {
            say(l, "Nenhum servidor com " + ServerHop.describe(min, max) + " nas " + pages
                    + " primeira(s) página(s) agora. Tente de novo em instantes.");
            return;
        }
        String link = ServerHop.deepLink(place, s.id);
        if (link == null) {
            say(l, "O Roblox devolveu um ID de servidor inválido.");
            return;
        }
        synchronized (RECENT) {
            RECENT.add(s.id);
            while (RECENT.size() > RECENT_MAX) {
                RECENT.remove(RECENT.iterator().next());
            }
        }
        say(l, "Entrando num servidor com " + s.playing + " jogador(es)...");
        launch(ctx, l, link);
    }

    private static ServerHop.Server take(long place, int min, int max) {
        synchronized (RECENT) {
            return POOL.take(place, SystemClock.uptimeMillis(), TTL_MS, min, max, RECENT, RNG);
        }
    }

    private static List<ServerHop.Server> fetch(Listener l, long place, int min, int max, int pages) throws Exception {
        try {
            return RobloxApi.fetchSmallServers(place, min, max, pages);
        } catch (RobloxApi.ApiException e) {
            if (e.httpCode != 429) {
                throw e;
            }
            int wait = Math.max(e.retryAfterSec, e.resetSec);
            if (wait > 30) {
                throw e;
            }
            say(l, "Limite do Roblox: aguardando " + wait + " s...");
            Thread.sleep((wait + 1) * 1000L);
            return RobloxApi.fetchSmallServers(place, min, max, pages);
        }
    }

    private static void launch(final Context ctx, final Listener l, final String link) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    if (l != null) {
                        l.onStatus("Abrindo o Roblox no servidor escolhido.");
                    }
                } catch (ActivityNotFoundException e) {
                    if (l != null) {
                        l.onStatus("Não encontrei o app do Roblox neste celular.");
                    }
                } catch (RuntimeException e) {
                    if (l != null) {
                        l.onStatus("O Android bloqueou a abertura do Roblox. Ative \"Exibir sobre outros apps\" para este app.");
                    }
                }
            }
        });
    }

    private static void say(final Listener l, final String msg) {
        if (l == null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                l.onStatus(msg);
            }
        });
    }

    /** Mensagem amigável para um erro de rede/API. */
    public static String explain(Exception e) {
        if (e instanceof RobloxApi.ApiException) {
            RobloxApi.ApiException a = (RobloxApi.ApiException) e;
            if (a.httpCode == 429) {
                return "O Roblox limitou as consultas. Aguarde cerca de " + Math.max(Math.max(a.retryAfterSec, a.resetSec), 5)
                        + " s (às vezes 1 min) e tente de novo.";
            }
            if (a.httpCode == 400 || a.httpCode == 404) {
                return "O Roblox não reconhece esse jogo/Place ID.";
            }
            return "O Roblox respondeu com erro " + a.httpCode + ".";
        }
        if (e instanceof IOException) {
            return "Sem conexão com a internet (ou o Roblox está inacessível).";
        }
        if (e instanceof JSONException) {
            return "Resposta inesperada do Roblox.";
        }
        return "Erro: " + e.getClass().getSimpleName();
    }
}
