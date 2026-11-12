package com.telesales.status;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.android.gms.tasks.Tasks;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Firestore drain — app na khulleo joma save Firestore-e pathay.
 *  UI chhoy na (no Toast/View/Activity), Activity reference rakhe na.
 *  FirebaseAuth session disk-e persist kore, tai worker-e notun sign-in lage na;
 *  user na thakle Result.failure() (app khule login korte hobe — retry-e lav nei).
 *  Entry sorbe sudhu confirmed safal write-er por; in-app drain-er sathe interleave
 *  holeo nirapad (number merge + calls-doc set idempotent, log dedup doc-ID diye).
 *  Quota hole entry joma thakbe, Result.retry() — Sheets fallback worker-e nei,
 *  in-app save path-e quota hole Sheets-e jay (saveViaSheets). */
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
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return Result.failure();
        FirestoreStore store = new FirestoreStore();

        boolean authFail = false;
        boolean transientFail = false;

        // OUTBOX pass: number merge + calls doc (clear hole calls delete).
        // numbersId stamp ar lage na — doc ID-i number.
        List<String[]> deliveredOutbox = new ArrayList<>();
        try {
            JSONArray ob = new JSONArray(p.getString(OUTBOX_KEY, "[]"));
            for (int i = 0; i < ob.length(); i++) {
                JSONObject o = ob.optJSONObject(i);
                if (o == null || !myAgent.equals(o.optString("agent"))) continue;
                Row r = new Row(o.optInt("row"), o.optString("number"), o.optString("status"),
                        o.optString("valid"), o.optString("r1"), o.optString("r2"), o.optString("others"));
                String entryDate = dateFromTs(o.optLong("ts"));
                String entryTime = timeFromTs(o.optLong("ts"));
                boolean entryClear = (r.status == null || r.status.isEmpty()) && (r.valid == null || r.valid.isEmpty());
                try {
                    if (entryClear) {
                        saveNumberOnly(myAgent, r, entryDate, entryTime);
                        deleteCallsForDateNumber(myAgent, entryDate, r.number);
                    } else {
                        saveNumberStatusSync(store, myAgent, r, entryDate, entryTime);
                    }
                    deliveredOutbox.add(new String[]{o.optString("agent"), o.optString("number"), String.valueOf(o.optLong("ts"))});
                } catch (Exception e) {
                    if (FirestoreStore.isQuotaError(e)) { transientFail = true; break; }
                    if (isTransient(e)) { transientFail = true; break; }
                    authFail = true; break;
                }
            }
        } catch (Exception ignored) {}
        if (!deliveredOutbox.isEmpty()) removeOutboxEntries(ctx, deliveredOutbox);

        // PENDING pass: sudhu "log" type. Clear entry chain-wide delete chay —
        // seta in-app drain korbe, worker noy.
        Set<String> remainingOutboxNums = new HashSet<>();
        try {
            JSONArray ob = new JSONArray(p.getString(OUTBOX_KEY, "[]"));
            for (int i = 0; i < ob.length(); i++) {
                JSONObject o = ob.optJSONObject(i);
                if (o != null && myAgent.equals(o.optString("agent")))
                    remainingOutboxNums.add(FirestoreStore.normLogNumber(o.optString("number")));
            }
        } catch (Exception ignored) {}
        List<String[]> deliveredPending = new ArrayList<>();
        if (!authFail) {
            try {
                JSONArray arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null || !myAgent.equals(o.optString("agent"))) continue;
                    if (!"log".equals(o.optString("type"))) continue; // clear skip
                    String num = FirestoreStore.normLogNumber(o.optString("number"));
                    if (remainingOutboxNums.contains(num)) continue; // main update baki — order thik rakhte pore
                    try {
                        createCallDocIfAbsent(myAgent, o.optString("date"), o.optString("number"),
                                o.optString("status"), o.optString("valid"), timeFromTs(o.optLong("ts", 0)));
                        deliveredPending.add(new String[]{o.optString("agent"), o.optString("number"), String.valueOf(o.optLong("ts"))});
                    } catch (Exception e) {
                        if (FirestoreStore.isQuotaError(e)) { transientFail = true; break; }
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

    /** FirestoreStore callback-ke blocking call-e badla — doWork background thread-ei chole. */
    private static void saveNumberStatusSync(FirestoreStore store, String agent, Row row, String date, String time) throws Exception {
        final Exception[] error = new Exception[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        store.saveNumberStatus(agent, row, date, time, new FirestoreStore.Callback<Void>() {
            public void onSuccess(Void v) { latch.countDown(); }
            public void onError(Exception e) { error[0] = e; latch.countDown(); }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new Exception("Firestore save timed out");
        if (error[0] != null) throw error[0];
    }

    /** Number-doc sudhu merge (calls doc noy) — clear ba unchanged save-er jonno. */
    private static void saveNumberOnly(String agent, Row row, String date, String time) throws Exception {
        String number = FirestoreStore.normLogNumber(row.number);
        if (number.isEmpty()) throw new IllegalArgumentException("row number is empty");
        Map<String, Object> data = new HashMap<>();
        data.put("number", number);
        data.put("status", row.status == null ? "" : row.status);
        data.put("valid", row.valid == null ? "" : row.valid);
        data.put("remark1", row.remark1 == null ? "" : row.remark1);
        data.put("remark2", row.remark2 == null ? "" : row.remark2);
        data.put("others", row.others == null ? "" : row.others);
        data.put("lastCalledDate", date == null ? "" : date);
        data.put("lastCalledTime", time == null ? "" : time);
        data.put("syncedToSheet", false);
        Tasks.await(FirebaseFirestore.getInstance().collection("agents").document(agent)
                .collection("numbers").document(number).set(data, SetOptions.merge()), 30, TimeUnit.SECONDS);
    }

    /** Oi date + number-er SOB calls doc muche fela (date-e query, number client-side). */
    private static void deleteCallsForDateNumber(String agent, String date, String number) throws Exception {
        String norm = FirestoreStore.normLogNumber(number);
        if (norm.isEmpty()) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        QuerySnapshot snap = Tasks.await(db.collection("agents").document(agent).collection("calls")
                .whereEqualTo("date", date == null ? "" : date).get(), 30, TimeUnit.SECONDS);
        WriteBatch batch = db.batch();
        boolean any = false;
        for (DocumentSnapshot doc : snap.getDocuments()) {
            if (norm.equals(FirestoreStore.normLogNumber(doc.getString("number")))) { batch.delete(doc.getReference()); any = true; }
        }
        if (any) Tasks.await(batch.commit(), 30, TimeUnit.SECONDS);
    }

    /** Calls doc ID-i dedup key (date_number_status_valid) — age theke thakle skip. */
    private static void createCallDocIfAbsent(String agent, String date, String number, String status, String valid, String time) throws Exception {
        String norm = FirestoreStore.normLogNumber(number);
        if (norm.isEmpty()) return;
        String safeDate = date == null ? "" : date;
        String safeStatus = status == null ? "" : status;
        String safeValid = valid == null ? "" : valid;
        String callDocId = safeDate + "_" + norm + "_" + safeStatus + "_" + safeValid;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentSnapshot existing = Tasks.await(db.collection("agents").document(agent).collection("calls")
                .document(callDocId).get(), 30, TimeUnit.SECONDS);
        if (existing != null && existing.exists()) return;
        Map<String, Object> callData = new HashMap<>();
        callData.put("date", safeDate);
        callData.put("number", norm);
        callData.put("status", safeStatus);
        callData.put("valid", safeValid);
        callData.put("time", time == null ? "" : time);
        callData.put("createdAt", FieldValue.serverTimestamp());
        callData.put("syncedToSheet", false);
        Tasks.await(db.collection("agents").document(agent).collection("calls")
                .document(callDocId).set(callData), 30, TimeUnit.SECONDS);
    }

    private static String dateFromTs(long ts) {
        if (ts <= 0) return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        return f.format(new Date(ts));
    }

    private static String timeFromTs(long ts) {
        if (ts <= 0) return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        return f.format(new Date(ts));
    }

    /** Samoyik somossa (quota/network/server) — retry korle lav ache. */
    private static boolean isTransient(Exception e) {
        if (FirestoreStore.isQuotaError(e)) return true;
        String m = e == null ? "" : e.getMessage();
        if (m == null) m = "";
        if (m.contains("429") || m.contains("500") || m.contains("502") || m.contains("503") || m.contains("504")) return true;
        String low = m.toLowerCase();
        return low.contains("timeout") || low.contains("timed out") || low.contains("unable to resolve host")
                || low.contains("failed to connect") || low.contains("network") || low.contains("socket") || low.contains("eof")
                || low.contains("unavailable");
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
                            && FirestoreStore.normLogNumber(d[1]).equals(FirestoreStore.normLogNumber(o.optString("number")))
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
                            && FirestoreStore.normLogNumber(d[1]).equals(FirestoreStore.normLogNumber(o.optString("number")))
                            && d[2].equals(String.valueOf(o.optLong("ts")))) { hit = true; break; }
                }
                if (!hit) kept.put(o);
            }
            p.edit().putString(PENDING_LOG_KEY, kept.toString()).commit();
        } catch (Exception ignored) {}
    }
}
