package com.telesales.status;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Batch feature #10b: protidin rat 9tay (Asia/Dhaka) ajker hisab-er notification.
 *  Sheet theke hisab niye dekhay; auth/net fail hole chupchap bad (bhul hisab noy).
 *  Log porte na parle kono notification noy (0 call bole bhul janano noy); numbers ba
 *  reports porte na parle sudhu oi ongsho bad diye baki hisab dekhabe. */
public class DaySummaryReceiver extends BroadcastReceiver {
    public static final String ACTION_DAY_SUMMARY = "com.telesales.status.DAY_SUMMARY";
    private static final String CHANNEL_ID = "telesales_daily";
    private static final int NOTIF_ID = 0xD41;
    private static final int HOUR = 21;

    /** Porer 21:00-ay alarm bosano (idempotent — ekoi PendingIntent bodle jabe). */
    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        c.set(Calendar.HOUR_OF_DAY, HOUR);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        Intent i = new Intent(ctx, DaySummaryReceiver.class);
        i.setAction(ACTION_DAY_SUMMARY);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, NOTIF_ID, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !ACTION_DAY_SUMMARY.equals(intent.getAction())) return;
        schedule(ctx); // porer din-er alarm age bosano
        new Thread(() -> sendSummary(ctx)).start();
    }

    private static String dhakaToday() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String cell(String[] r, int i) { return r != null && r.length > i && r[i] != null ? r[i].trim() : ""; }

    /** v4.28 writeSheetFor-er niyom (receiver-er jonno): FromDate khali ba ajker soman/
     *  ager hole latest; na hole sobcheye ager entry. */
    private static String sheetFor(Map<String, List<String[]>> chains, String store, String today) {
        List<String[]> chain = chains == null ? null : chains.get(store);
        if (chain == null || chain.isEmpty()) return null;
        String best = null, bestFrom = null;
        for (String[] e : chain) {
            String from = cell(e, 2);
            String id = cell(e, 1);
            if (id.isEmpty()) continue;
            if (from.isEmpty() || from.compareTo(today) <= 0) {
                if (bestFrom == null || from.compareTo(bestFrom) >= 0) { best = id; bestFrom = from; }
            }
        }
        if (best != null) return best;
        String early = null, earlyFrom = null;
        for (String[] e : chain) {
            String from = cell(e, 2);
            String id = cell(e, 1);
            if (id.isEmpty()) continue;
            if (earlyFrom == null || from.compareTo(earlyFrom) < 0) { early = id; earlyFrom = from; }
        }
        return early;
    }

    private static boolean isCountCell(String n) {
        if (n.isEmpty() || n.startsWith("0") || n.startsWith("+")) return false;
        for (int i = 0; i < n.length(); i++) if (!Character.isDigit(n.charAt(i))) return false;
        return true;
    }

    private static void sendSummary(Context ctx) {
        try {
            GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(ctx);
            if (acct == null || acct.getAccount() == null) return;
            String agentId = ctx.getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).getString("agent_id", null);
            if (agentId == null || agentId.isEmpty()) return;
            String token;
            try {
                token = GoogleAuthUtil.getToken(ctx, acct.getAccount(), "oauth2:" + MainActivity.SHEET_SCOPE + " " + MainActivity.DRIVE_SCOPE);
            } catch (GoogleAuthException | IOException e) { return; }
            if (token == null || token.isEmpty()) return;
            String today = dhakaToday();
            Map<String, List<String[]>> chains = new HashMap<>();
            try {
                List<String[]> cfg = SheetsClient.getConfigRows(token, MainActivity.CONFIG_FILE_ID);
                if (cfg != null) for (String[] r : cfg) {
                    String store = cell(r, 0);
                    if (!("NUMBERS".equals(store) || "STATUS_LOG".equals(store) || "REPORTS".equals(store))) continue;
                    List<String[]> l = chains.get(store);
                    if (l == null) { l = new ArrayList<>(); chains.put(store, l); }
                    l.add(r);
                }
            } catch (Exception ignored) { chains.clear(); }
            String logId = sheetFor(chains, "STATUS_LOG", today);
            if (logId == null) logId = MainActivity.SHARED_STATUS_LOG_ID;
            // Log-i mul hisab: porte na parle notification noy.
            int total = 0, conn = 0;
            try {
                List<String[]> logs = SheetsClient.getReportRows(token, logId, agentId);
                if (logs != null) for (String[] r : logs) {
                    if (!today.equals(cell(r, 0))) continue;
                    String st = cell(r, 2);
                    if (st.isEmpty()) continue;
                    total++;
                    if ("call connected".equalsIgnoreCase(st)) conn++;
                }
            } catch (Exception e) { return; }
            // FTD (na pele ongsho bad).
            int ftdN = 0; double ftdSum = 0; boolean ftdOk = false;
            try {
                String repId = sheetFor(chains, "REPORTS", today);
                if (repId == null) repId = MainActivity.SHARED_REPORT_ID;
                List<String[]> reps = SheetsClient.getReportRows(token, repId, agentId);
                if (reps != null) {
                    ftdOk = true;
                    for (String[] r : reps) {
                        if (!today.equals(cell(r, 0)) || !"FTD".equals(cell(r, 1))) continue;
                        String n = cell(r, 2);
                        if (isCountCell(n)) { try { ftdN += Integer.parseInt(n); } catch (Exception ignored) { ftdN++; } }
                        else ftdN++;
                        try { ftdSum += Double.parseDouble(cell(r, 3)); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) { ftdOk = false; }
            // Baki (na pele ongsho bad).
            int remain = -1;
            try {
                String numId = sheetFor(chains, "NUMBERS", today);
                if (numId != null) {
                    List<Row> rows = SheetsClient.getRows(token, numId, agentId);
                    remain = 0;
                    if (rows != null) for (Row r : rows) if (r != null && (r.status == null || r.status.isEmpty())) remain++;
                }
            } catch (Exception ignored) { remain = -1; }
            StringBuilder text = new StringBuilder();
            text.append(total).append("টা কল • ").append(conn).append("টা Connected");
            if (ftdOk) text.append(" • ").append(ftdN).append("টা এফটিডি (৳").append(fmtAmt(ftdSum)).append(")");
            if (remain >= 0) text.append(" • ").append(remain).append("টা বাকি");
            ensureChannel(ctx);
            Intent open = new Intent(ctx, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openPi = PendingIntent.getActivity(ctx, NOTIF_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_logo_t)
                    .setContentTitle("আজকের হিসাব")
                    .setContentText(text.toString())
                    .setContentIntent(openPi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            try {
                NotificationManagerCompat.from(ctx).notify(NOTIF_ID, b.build());
            } catch (SecurityException ignored) { /* notification onumoti na thakle chupchap bad */ }
        } catch (Exception ignored) { /* summary kokhono crash noy */ }
    }

    private static String fmtAmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "দিনের হিসাব", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Protidin rat 9tay ajker call-er hisab");
                nm.createNotificationChannel(ch);
            }
        }
    }
}
