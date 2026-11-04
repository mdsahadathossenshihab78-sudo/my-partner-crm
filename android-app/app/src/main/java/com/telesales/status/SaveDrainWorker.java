package com.telesales.status;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** v4.32: background drain — app na khulleo joma save sheet-e pathay.
 *  UI chhoy na (no Toast/View/Activity), Activity reference rakhe na.
 *  Stored sheet ID chara entry kokhono pathay na (legacy / stamp-hoyni entry
 *  in-app drain-er jonno rekhe deya hoy — andaj kore kono sheet-e lekha hoy na).
 *  Entry sorbe sudhu confirmed safal write-er por; in-app drain-er sathe interleave
 *  holeo nirapad (updateRow idempotent + log dedup guard). */
public class SaveDrainWorker extends Worker {
    public SaveDrainWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static final String PREFS = "telesales_prefs";
    private static final String OUTBOX_KEY = "outbox_v419";
    private static final String PENDING_LOG_KEY = "pending_log_v420";

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String myAgent = p.getString("agent_id", null);
        if (myAgent == null || myAgent.isEmpty()) return Result.failure();
        GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(ctx);
        if (acct == null || acct.getAccount() == null) return Result.failure();
        String token;
        try {
            token = GoogleAuthUtil.getToken(ctx, acct.getAccount(),
                    "oauth2:" + MainActivity.SHEET_SCOPE + " " + MainActivity.DRIVE_SCOPE);
        } catch (UserRecoverableAuthException e) {
            return Result.failure(); // consent lagbe — user chara hobe na, loop noy
        } catch (IOException e) {
            return Result.retry(); // network samoyik
        } catch (GoogleAuthException e) {
            return Result.failure();
        } catch (Exception e) {
            return Result.failure();
        }
        if (token == null || token.isEmpty()) return Result.failure();

        boolean authFail = false;
        boolean transientFail = false;

        // OUTBOX pass: main update sudhu (log side alada pending entry-te hobe).
        List<String[]> deliveredOutbox = new ArrayList<>();
        try {
            JSONArray ob = new JSONArray(p.getString(OUTBOX_KEY, "[]"));
            for (int i = 0; i < ob.length(); i++) {
                JSONObject o = ob.optJSONObject(i);
                if (o == null || !myAgent.equals(o.optString("agent"))) continue;
                String numbersId = o.optString("numbersId");
                if (numbersId == null || numbersId.isEmpty()) continue; // legacy / stamp-hoyni
                Row r = new Row(o.optInt("row"), o.optString("number"), o.optString("status"),
                        o.optString("valid"), o.optString("r1"), o.optString("r2"), o.optString("others"));
                try {
                    SheetsClient.updateRow(token, numbersId, myAgent, r);
                    deliveredOutbox.add(new String[]{o.optString("agent"), o.optString("number"), String.valueOf(o.optLong("ts"))});
                } catch (Exception e) {
                    String m = e.getMessage() == null ? "" : e.getMessage();
                    if (m.contains("bodle geche")) {
                        // Row sore geche — ar apply kora nirapad noy (in-app drain-er motoi entry sorbe).
                        deliveredOutbox.add(new String[]{o.optString("agent"), o.optString("number"), String.valueOf(o.optLong("ts"))});
                        continue;
                    }
                    if (isTransient(e)) { transientFail = true; break; }
                    authFail = true; break;
                }
            }
        } catch (Exception ignored) {}
        if (!deliveredOutbox.isEmpty()) removeOutboxEntries(ctx, deliveredOutbox);

        // PENDING pass: sudhu "log" type + stored logSheetId/logTab thakle.
        // Clear entry chain-wide delete chay — seta in-app drain korbe, worker noy.
        Set<String> remainingOutboxNums = new HashSet<>();
        try {
            JSONArray ob = new JSONArray(p.getString(OUTBOX_KEY, "[]"));
            for (int i = 0; i < ob.length(); i++) {
                JSONObject o = ob.optJSONObject(i);
                if (o != null && myAgent.equals(o.optString("agent")))
                    remainingOutboxNums.add(SheetsClient.normLogNumber(o.optString("number")));
            }
        } catch (Exception ignored) {}
        List<String[]> deliveredPending = new ArrayList<>();
        Map<String, Set<String>> logRowsCache = new HashMap<>();
        if (!authFail) {
            try {
                JSONArray arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null || !myAgent.equals(o.optString("agent"))) continue;
                    if (!"log".equals(o.optString("type"))) continue; // clear skip
                    String logSheetId = o.optString("logSheetId");
                    String logTab = o.optString("logTab");
                    if (logSheetId == null || logSheetId.isEmpty() || logTab == null || logTab.isEmpty()) continue; // legacy / stamp-hoyni
                    String num = SheetsClient.normLogNumber(o.optString("number"));
                    if (remainingOutboxNums.contains(num)) continue; // main update baki — order thik rakhte pore
                    String key = o.optString("date") + "|" + num + "|" + o.optString("status") + "|" + o.optString("valid");
                    try {
                        Set<String> existing = logRowsCache.get(logSheetId + "|" + logTab);
                        if (existing == null) {
                            existing = new HashSet<>();
                            try {
                                for (String[] lr : SheetsClient.getReportRows(token, logSheetId, logTab)) {
                                    if (lr != null && lr.length >= 4)
                                        existing.add(lr[0] + "|" + SheetsClient.normLogNumber(lr[1]) + "|" + lr[2] + "|" + lr[3]);
                                }
                                logRowsCache.put(logSheetId + "|" + logTab, existing);
                            } catch (Exception re) { existing = null; } // read fail — skip noy, append chesta hobe
                        }
                        if (existing != null && existing.contains(key)) {
                            deliveredPending.add(new String[]{o.optString("agent"), o.optString("number"), String.valueOf(o.optLong("ts"))});
                            continue;
                        }
                        SheetsClient.appendReportRow(token, logSheetId, logTab, new String[]{
                                o.optString("date"), RowAdapter.dialNumber(o.optString("number")),
                                o.optString("status"), o.optString("valid")});
                        if (existing != null) existing.add(key);
                        deliveredPending.add(new String[]{o.optString("agent"), o.optString("number"), String.valueOf(o.optLong("ts"))});
                    } catch (Exception e) {
                        if (isTransient(e)) { transientFail = true; break; }
                        authFail = true; break;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (!deliveredPending.isEmpty()) removePendingEntries(ctx, deliveredPending);

        if (authFail) return Result.failure();
        if (transientFail) return Result.retry();
        return Result.success();
    }

    /** Samoyik somossa (quota/network/server) — retry korle lav ache. */
    private static boolean isTransient(Exception e) {
        String m = e == null ? "" : e.getMessage();
        if (m.contains("429") || m.contains("500") || m.contains("502") || m.contains("503") || m.contains("504")) return true;
        String low = m.toLowerCase();
        return low.contains("timeout") || low.contains("timed out") || low.contains("unable to resolve host")
                || low.contains("failed to connect") || low.contains("network") || low.contains("socket") || low.contains("eof");
    }

    /** Fresh-read-merge removal: shesh muhurte notun joma entry thakleo harabe na
     *  (purono snapshot-er write-back-e notun entry muchto). */
    private static void removeOutboxEntries(Context ctx, List<String[]> delivered) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString(OUTBOX_KEY, "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean hit = false;
                for (String[] d : delivered) {
                    if (d[0].equals(o.optString("agent"))
                            && SheetsClient.normLogNumber(d[1]).equals(SheetsClient.normLogNumber(o.optString("number")))
                            && d[2].equals(String.valueOf(o.optLong("ts")))) { hit = true; break; }
                }
                if (!hit) kept.put(o);
            }
            p.edit().putString(OUTBOX_KEY, kept.toString()).commit();
        } catch (Exception ignored) {}
    }

    private static void removePendingEntries(Context ctx, List<String[]> delivered) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean hit = false;
                for (String[] d : delivered) {
                    if (d[0].equals(o.optString("agent"))
                            && SheetsClient.normLogNumber(d[1]).equals(SheetsClient.normLogNumber(o.optString("number")))
                            && d[2].equals(String.valueOf(o.optLong("ts")))) { hit = true; break; }
                }
                if (!hit) kept.put(o);
            }
            p.edit().putString(PENDING_LOG_KEY, kept.toString()).commit();
        } catch (Exception ignored) {}
    }
}
