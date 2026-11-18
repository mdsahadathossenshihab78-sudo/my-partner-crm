package com.telesales.partner;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** v4.31: call reminder — LOCAL ONLY (telesales_prefs, kono sheet/server noy).
 *  Update/reboot-e thakbe, sudhu uninstall/Clear Data-e jabe.
 *  Ek number-e ektai active reminder; notun dile purono bodle jabe. */
public class ReminderStore {
    private static final String PREFS = "telesales_prefs";
    private static final String KEY = "call_reminders_json";

    public static class Item {
        public final String number;
        public final long time;
        public Item(String n, long t) { number = n; time = t; }
    }

    public static List<Item> list(Context ctx) {
        List<Item> out = new ArrayList<>();
        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
            if (raw == null || raw.isEmpty()) return out;
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String n = o.optString("n", "");
                long t = o.optLong("t", 0);
                if (!n.isEmpty() && t > 0) out.add(new Item(n, t));
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Notun reminder save — oi number-er purono thakle seta soriye (replace). */
    public static void save(Context ctx, String number, long time) {
        List<Item> kept = new ArrayList<>();
        for (Item it : list(ctx)) if (!it.number.equals(number)) kept.add(it);
        kept.add(new Item(number, time));
        write(ctx, kept);
    }

    public static void remove(Context ctx, String number) {
        List<Item> kept = new ArrayList<>();
        for (Item it : list(ctx)) if (!it.number.equals(number)) kept.add(it);
        write(ctx, kept);
    }

    private static void write(Context ctx, List<Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("n", it.number);
                o.put("t", it.time);
                a.put(o);
            }
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {}
    }
}
