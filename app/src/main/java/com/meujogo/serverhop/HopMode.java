package com.meujogo.serverhop;

/**
 * Tipos de servidor que o hop sabe procurar.
 *
 * A lista pública do Roblox só informa quantos jogadores há em cada servidor (e fps/ping do
 * servidor); não informa nível, tempo de conta nem nada sobre os jogadores. Por isso NÃO existe
 * um modo "iniciantes": só dá para escolher pela lotação.
 */
public enum HopMode {
    /** Quase vazios: 1 a 2 jogadores. Vem na ordem crescente da lista (Asc). */
    EMPTY("Vazio", "servidores quase vazios", "Asc"),
    /** Quase cheios: até 2 vagas livres. Vem na ordem decrescente da lista (Desc). */
    BUSY("Movimentado", "servidores quase cheios", "Desc");

    public final String label;
    public final String title;
    /** Ordem da lista do Roblox que chega mais rápido a esses servidores. */
    public final String sort;

    HopMode(String label, String title, String sort) {
        this.label = label;
        this.title = title;
        this.sort = sort;
    }

    /** Menor número de jogadores aceito, para um servidor de capacidade 'cap'. */
    public int lower(int cap) {
        return this == EMPTY ? 1 : Math.max(2, cap - 2);
    }

    /** Maior número de jogadores aceito (nunca um servidor cheio). */
    public int upper(int cap) {
        return this == EMPTY ? Math.min(2, Math.max(1, cap - 1)) : Math.max(2, cap - 1);
    }

    public boolean accepts(int playing, int maxPlayers) {
        int cap = maxPlayers > 0 ? maxPlayers : ServerHop.DEFAULT_CAP;
        if (playing >= cap) {
            return false; // cheio
        }
        return playing >= lower(cap) && playing <= upper(cap);
    }

    public String describe(int cap) {
        return lower(cap) + " a " + upper(cap) + " jogadores";
    }

    public static HopMode fromIndex(int i) {
        HopMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : EMPTY;
    }
}
