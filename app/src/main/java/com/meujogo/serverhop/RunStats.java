package com.meujogo.serverhop;

/** Estatísticas da sessão de teste (Java puro). */
public class RunStats {

    public int cycles;
    public int signals;
    public int timeouts;
    public int cancelled;
    public int tapsOverWindow;
    public long waitMin = Long.MAX_VALUE;
    public long waitMax;
    public long waitSum;
    public long tapSpanLast;
    public long tapSpanMax;

    public void reset() {
        cycles = 0;
        signals = 0;
        timeouts = 0;
        cancelled = 0;
        tapsOverWindow = 0;
        waitMin = Long.MAX_VALUE;
        waitMax = 0;
        waitSum = 0;
        tapSpanLast = 0;
        tapSpanMax = 0;
    }

    /** Tempo de espera medido entre o fim do "segurar" e a detecção do sinal. */
    public void recordWait(long ms) {
        signals++;
        waitSum += ms;
        if (ms < waitMin) {
            waitMin = ms;
        }
        if (ms > waitMax) {
            waitMax = ms;
        }
    }

    /** Tempo total medido para despachar todos os toques. Retorna true se coube na janela. */
    public boolean recordTaps(long spanMs, long windowMs) {
        tapSpanLast = spanMs;
        if (spanMs > tapSpanMax) {
            tapSpanMax = spanMs;
        }
        boolean ok = spanMs <= windowMs;
        if (!ok) {
            tapsOverWindow++;
        }
        return ok;
    }

    public long waitAvg() {
        return signals == 0 ? 0 : waitSum / signals;
    }

    public String summary() {
        String wait = signals == 0 ? "-" : waitMin + "/" + waitAvg() + "/" + waitMax;
        return "Ciclos " + cycles + " | Sinais " + signals + " | Sem sinal " + timeouts
                + "\nEspera ms (mín/méd/máx): " + wait
                + "\nÚltimos toques: " + tapSpanLast + " ms (máx " + tapSpanMax + "), acima da janela: " + tapsOverWindow;
    }
}
