package com.meujogo.autoclick;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/** Guarda e lê a configuração nas preferências do app. */
public final class ConfigStore {

    private static final String FILE = "autoclick_config";

    private ConfigStore() {
    }

    public static Config load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Config c = new Config();
        Map<String, Integer> m = new HashMap<>();
        for (String k : Config.ALL_KEYS) {
            if (sp.contains(k)) {
                m.put(k, sp.getInt(k, c.get(k)));
            }
        }
        c.fromMap(m);
        if (sp.contains("placeId") && sp.getLong("placeId", 0) > 0) {
            c.placeId = sp.getLong("placeId", c.placeId);
            c.placeName = sp.getString("placeName", c.placeName);
        }
        return c;
    }

    public static void save(Context ctx, Config c) {
        c.sanitize();
        SharedPreferences.Editor e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit();
        for (Map.Entry<String, Integer> en : c.toMap().entrySet()) {
            e.putInt(en.getKey(), en.getValue());
        }
        e.putLong("placeId", c.placeId);
        e.putString("placeName", c.placeName);
        e.apply();
    }
}
