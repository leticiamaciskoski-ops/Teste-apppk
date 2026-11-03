package com.meujogo.autoclick;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Guarda a última lista de servidores consultada por um curto período (TTL).
 * O Roblox limita essa consulta a ~3 por minuto, então vários hops seguidos
 * reaproveitam a mesma lista em vez de consultar de novo.
 */
public final class ServerPool {

    private final List<ServerHop.Server> servers = new ArrayList<>();
    private long fetchedAt = -1;
    private long placeId = -1;

    public synchronized void replace(long place, List<ServerHop.Server> list, long nowMs) {
        servers.clear();
        servers.addAll(list);
        placeId = place;
        fetchedAt = nowMs;
    }

    public synchronized boolean fresh(long place, long nowMs, long ttlMs) {
        return fetchedAt >= 0 && place == placeId && nowMs - fetchedAt <= ttlMs;
    }

    /** Sorteia um candidato da lista guardada, ou null se ela estiver velha, de outro jogo ou sem candidatos. */
    public synchronized ServerHop.Server take(long place, long nowMs, long ttlMs, int min, int max,
                                              Set<String> exclude, Random rng) {
        if (!fresh(place, nowMs, ttlMs)) {
            return null;
        }
        return ServerHop.pick(ServerHop.filter(servers, min, max, exclude), rng);
    }

    public synchronized int size() {
        return servers.size();
    }

    public synchronized void clear() {
        servers.clear();
        fetchedAt = -1;
        placeId = -1;
    }
}
