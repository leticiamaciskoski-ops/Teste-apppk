package com.meujogo.serverhop;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Faz o server hop: escolhe um servidor de status conhecido e saudável, na lotação do modo
 * escolhido, e abre o Roblox direto nele. Usado pelo app e pelo painel flutuante.
 */
public final class HopController {

    private HopController() {
    }

    /** Recebe mensagens de andamento, sempre na thread principal. */
    public interface Listener {
        void onStatus(String msg);

        void onPicked(ServerHop.Server s);
    }

    /** Listener que só se interessa pelas mensagens. */
    public abstract static class StatusListener implements Listener {
        @Override
        public void onPicked(ServerHop.Server s) {
            // ignora
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ServerPool POOL = new ServerPool();
    private static final LinkedHashSet<String> RECENT = new LinkedHashSet<>();
    private static final LinkedList<String> HISTORY = new LinkedList<>();
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final Random RNG = new Random();
    private static final long TTL_MS = 45000;
    private static final int RECENT_MAX = 60;
    private static final int HISTORY_MAX = 6;

    /** adjacent = abrir o Roblox ao lado, quando o app está em tela dividida. */
    public static void hop(final Context ctx, final long place, final HopMode mode, final int maxPages,
                           final boolean adjacent, final Listener l) {
        if (!BUSY.compareAndSet(false, true)) {
            say(l, "Já estou buscando um servidor, aguarde.");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work(ctx, l, place, mode, maxPages, adjacent);
                } catch (Exception e) {
                    say(l, explain(e));
                } finally {
                    BUSY.set(false);
                }
            }
        }, "server-hop").start();
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    /** Últimos servidores escolhidos (o mais recente primeiro). */
    public static List<String> history() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    private static void work(Context ctx, Listener l, long place, HopMode mode, int maxPages, boolean adjacent)
            throws Exception {
        if (place <= 0) {
            say(l, "Defina o jogo (Place ID) na tela do app.");
            return;
        }
        ServerHop.Server s = take(place, mode);
        if (s == null) {
            say(l, "Consultando " + mode.title + "...");
            List<ServerHop.Server> list = fetch(l, place, mode, maxPages);
            POOL.replace(place, mode.sort, list, SystemClock.uptimeMillis());
            s = take(place, mode);
        }
        if (s == null) {
            say(l, "Nenhum servidor com status conhecido (" + mode.describe(ServerHop.DEFAULT_CAP) + ") achado agora."
                    + " Tente de novo em instantes.");
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
        remember(s);
        picked(l, s);
        say(l, "Entrando no servidor...");
        launch(ctx, l, link, adjacent);
    }

    private static ServerHop.Server take(long place, HopMode mode) {
        synchronized (RECENT) {
            return POOL.take(place, SystemClock.uptimeMillis(), TTL_MS, mode, RECENT, RNG);
        }
    }

    private static List<ServerHop.Server> fetch(Listener l, long place, HopMode mode, int pages) throws Exception {
        try {
            return RobloxApi.fetchCandidates(place, mode, pages);
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
            return RobloxApi.fetchCandidates(place, mode, pages);
        }
    }

    private static void remember(ServerHop.Server s) {
        String when = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        synchronized (HISTORY) {
            HISTORY.addFirst(when + " · " + ServerHop.describe(s));
            while (HISTORY.size() > HISTORY_MAX) {
                HISTORY.removeLast();
            }
        }
    }

    private static void launch(final Context ctx, final Listener l, final String link, final boolean adjacent) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                    int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
                    if (adjacent) {
                        flags |= Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
                    }
                    i.addFlags(flags);
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
                        l.onStatus("O Android bloqueou a abertura do Roblox. Ative \"exibir sobre outros apps\" para este app.");
                    }
                }
            }
        });
    }

    private static void picked(final Listener l, final ServerHop.Server s) {
        if (l == null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                l.onPicked(s);
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
                return "O Roblox limitou as consultas. Aguarde cerca de "
                        + Math.max(Math.max(a.retryAfterSec, a.resetSec), 5) + " s (às vezes 1 min) e tente de novo.";
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
