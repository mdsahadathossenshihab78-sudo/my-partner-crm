package com.telesales.status;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** v4.41: unsaved row edit-er draft — phone-e joma thake, app bondho/
 *  reload holeo fire ase; Save sofol hole muchhe jay. Sheet-e kichhu
 *  lekhe na, sudhu local prefs-e thake. */
public class DraftStore {
    private static final String PREFS = "telesales_prefs";
    private static final String KEY = "row_drafts_json";

    public static void save(Context ctx, String number, String status, String valid, String r1, String r2, String others) {
        try {
            if (ctx == null || number == null) return;
            String key = RowAdapter.dialNumber(number);
            if (key == null || key.isEmpty()) return;
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject all = new JSONObject(p.getString(KEY, "{}"));
            String s = status == null ? "" : status, v = valid == null ? "" : valid;
            String a = r1 == null ? "" : r1, b = r2 == null ? "" : r2, o = others == null ? "" : others;
            if (s.isEmpty() && v.isEmpty() && a.isEmpty() && b.isEmpty() && o.isEmpty()) {
                all.remove(key);
            } else {
                JSONObject d = new JSONObject();
                d.put("status", s); d.put("valid", v);
                d.put("remark1", a); d.put("remark2", b); d.put("others", o);
                d.put("ts", System.currentTimeMillis());
                all.put(key, d);
            }
            p.edit().putString(KEY, all.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONObject loadAll(Context ctx) {
        try {
            if (ctx == null) return new JSONObject();
            return new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}"));
        } catch (Exception e) { return new JSONObject(); }
    }

    public static void clear(Context ctx, String number) {
        try {
            if (ctx == null || number == null) return;
            String key = RowAdapter.dialNumber(number);
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject all = new JSONObject(p.getString(KEY, "{}"));
            if (all.has(key)) { all.remove(key); p.edit().putString(KEY, all.toString()).apply(); }
        } catch (Exception ignored) {}
    }
}
