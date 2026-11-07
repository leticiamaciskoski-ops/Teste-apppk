package com.meujogo.serverhop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Guarda a última lista de servidores consultada por um curto período (TTL).
 * O Roblox limita essa consulta a ~3 por minuto, então vários hops seguidos
 * reaproveitam a mesma lista em vez de consultar de novo. A lista vale para
 * um jogo e uma ordem (Asc/Desc) específicos.
 */
public final class ServerPool {

    private final List<ServerHop.Server> servers = new ArrayList<>();
    private long fetchedAt = -1;
    private long placeId = -1;
    private String sort = "";

    public synchronized void replace(long place, String sortOrder, List<ServerHop.Server> list, long nowMs) {
        servers.clear();
        servers.addAll(list);
        placeId = place;
        sort = sortOrder;
        fetchedAt = nowMs;
    }

    public synchronized boolean fresh(long place, String sortOrder, long nowMs, long ttlMs) {
        return fetchedAt >= 0 && place == placeId && sortOrder.equals(sort) && nowMs - fetchedAt <= ttlMs;
    }

    /** Sorteia um candidato da lista guardada, ou null se ela estiver velha, de outro jogo/ordem ou sem candidatos. */
    public synchronized ServerHop.Server take(long place, long nowMs, long ttlMs, HopMode mode,
                                              Set<String> exclude, Random rng) {
        if (!fresh(place, mode.sort, nowMs, ttlMs)) {
            return null;
        }
        return ServerHop.pick(ServerHop.candidates(servers, mode, exclude), rng);
    }

    public synchronized int size() {
        return servers.size();
    }

    public synchronized void clear() {
        servers.clear();
        fetchedAt = -1;
        placeId = -1;
        sort = "";
    }
}
