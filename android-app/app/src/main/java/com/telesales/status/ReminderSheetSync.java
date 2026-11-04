package com.telesales.status;

import android.content.Context;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Batch feature #9: reminder-er sheet backup — phone bodol/reinstall-eo reminder fire
 *  asbe. Tab: shared Status Log file-er "Reminders" [AgentId | Number | TimeEpoch].
 *  Sob call best-effort (fail hole local reminder-i cholbe; sheet sync kokhono kaj atkabe na).
 *  Ek number-e ektai active reminder (local-er motoi). */
public class ReminderSheetSync {
    public static final String TAB = "Reminders";

    /** Receiver-er jonno token (SaveDrainWorker-er motoi); na pele null. */
    public static String getToken(Context ctx) {
        try {
            GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(ctx);
            if (acct == null || acct.getAccount() == null) return null;
            return GoogleAuthUtil.getToken(ctx, acct.getAccount(), "oauth2:" + MainActivity.SHEET_SCOPE + " " + MainActivity.DRIVE_SCOPE);
        } catch (GoogleAuthException | IOException e) { return null; }
    }

    private static String cell(String[] r, int i) { return r != null && r.length > i && r[i] != null ? r[i].trim() : ""; }

    /** Ek number-er purono row muche notun row lekha. */
    public static void save(String token, String agentId, String number, long time) {
        try {
            if (token == null || token.isEmpty() || agentId == null || agentId.isEmpty() || number == null || number.isEmpty()) return;
            SheetsClient.ensureSheetTab(token, MainActivity.CONFIG_FILE_ID, TAB);
            List<String[]> rows = SheetsClient.getReportRows(token, MainActivity.CONFIG_FILE_ID, TAB);
            if (rows == null || rows.isEmpty()) {
                SheetsClient.appendReportRow(token, MainActivity.CONFIG_FILE_ID, TAB, new String[]{"AgentId", "Number", "Time"});
            } else {
                removeMatching(token, agentId, number, rows);
            }
            SheetsClient.appendReportRow(token, MainActivity.CONFIG_FILE_ID, TAB, new String[]{agentId, number, String.valueOf(time)});
        } catch (Exception ignored) { }
    }

    public static void remove(String token, String agentId, String number) {
        try {
            if (token == null || token.isEmpty() || agentId == null || agentId.isEmpty() || number == null || number.isEmpty()) return;
            List<String[]> rows = SheetsClient.getReportRows(token, MainActivity.CONFIG_FILE_ID, TAB);
            if (rows == null) return;
            removeMatching(token, agentId, number, rows);
        } catch (Exception ignored) { }
    }

    /** Sheet row = list index + 2 (row 1 header); niche theke muche (uporer index bodlay na). */
    private static void removeMatching(String token, String agentId, String number, List<String[]> rows) throws Exception {
        for (int i = rows.size() - 1; i >= 0; i--) {
            String[] r = rows.get(i);
            if (r != null && agentId.equals(cell(r, 0)) && number.equals(cell(r, 1))) {
                SheetsClient.deleteReportRow(token, MainActivity.CONFIG_FILE_ID, TAB, i + 2);
            }
        }
    }

    /** Sheet theke ei agent-er reminder restore: local-e nei emon bhabishyoter number-gulo
     *  schedule kora hoy (local-e thakle local-i jitbe — hoyto offline-e notun kore set kora);
     *  par hoye jawa row muche fela hoy. Returns restored count. */
    public static int restore(String token, String agentId, Context ctx) {
        try {
            if (token == null || token.isEmpty() || agentId == null || agentId.isEmpty()) return 0;
            List<String[]> rows = SheetsClient.getReportRows(token, MainActivity.CONFIG_FILE_ID, TAB);
            if (rows == null) return 0;
            Set<String> localNums = new HashSet<>();
            for (ReminderStore.Item it : ReminderStore.list(ctx)) localNums.add(it.number);
            long now = System.currentTimeMillis();
            int restored = 0;
            for (int i = rows.size() - 1; i >= 0; i--) {
                String[] r = rows.get(i);
                if (r == null || !agentId.equals(cell(r, 0))) continue;
                String num = cell(r, 1);
                if (num.isEmpty()) continue;
                long t;
                try { t = Long.parseLong(cell(r, 2)); } catch (Exception e) { continue; }
                if (t <= now) {
                    try { SheetsClient.deleteReportRow(token, MainActivity.CONFIG_FILE_ID, TAB, i + 2); } catch (Exception ignored) {}
                    continue;
                }
                if (localNums.contains(num)) continue;
                ReminderStore.save(ctx, num, t);
                ReminderReceiver.schedule(ctx, num, t);
                localNums.add(num);
                restored++;
            }
            return restored;
        } catch (Exception ignored) { return 0; }
    }
}
