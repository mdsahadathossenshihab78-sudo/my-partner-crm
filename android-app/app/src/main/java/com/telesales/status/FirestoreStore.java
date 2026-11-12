package com.telesales.status;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Firestore data layer for the Telesales Status app (project fir-a-topup-bazar).
 *
 * Data model: ~/workspace/fix-db-migration/DESIGN.md section 4. Field names are
 * used exactly as written there. Access rules: firestore.rules in the same
 * folder - an agent may read and write only their own agents/{agentId} data
 * plus the shared read-only commissionStats and ftdUsers collections; this
 * class only performs operations those rules allow for an agent.
 *
 * Every method is asynchronous: Firebase Tasks run off the UI thread and the
 * Callback is invoked on the main thread. No method blocks the caller.
 * No password, token or API key is ever logged or stored by this class.
 */
public class FirestoreStore {

    /** Callback style used by every async method in this class. */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    /**
     * Distinct error type for the Firestore write-quota case. The caller
     * catches this (or checks isQuotaError) and falls back to the Sheet path.
     */
    public static class QuotaExceededException extends Exception {
        public QuotaExceededException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One row of an agent's call history (agents/{agentId}/calls). */
    public static class CallEntry {
        public final String date;
        public final String number;
        public final String status;
        public final String valid;
        public final String time;

        public CallEntry(String date, String number, String status, String valid, String time) {
            this.date = date == null ? "" : date;
            this.number = number == null ? "" : number;
            this.status = status == null ? "" : status;
            this.valid = valid == null ? "" : valid;
            this.time = time == null ? "" : time;
        }
    }

    /** One row of the Reports collection (top-level reports docs). */
    public static class ReportEntry {
        public final String date;
        public final String type;
        public final String number;
        public final double amount;
        public final String note;

        public ReportEntry(String date, String type, String number, double amount, String note) {
            this.date = date == null ? "" : date;
            this.type = type == null ? "" : type;
            this.number = number == null ? "" : number;
            this.amount = amount;
            this.note = note == null ? "" : note;
        }
    }

    /** One row of the ftdUsers collection (one doc per username). */
    public static class FtdUser {
        public final String username;
        public final double firstDepositAmount;
        public final String accountCreated;
        public final String lastLogin;
        public final String depositDate;
        public final String syncedAt;

        public FtdUser(String username, double firstDepositAmount, String accountCreated,
                       String lastLogin, String depositDate, String syncedAt) {
            this.username = username == null ? "" : username;
            this.firstDepositAmount = firstDepositAmount;
            this.accountCreated = accountCreated == null ? "" : accountCreated;
            this.lastLogin = lastLogin == null ? "" : lastLogin;
            this.depositDate = depositDate == null ? "" : depositDate;
            this.syncedAt = syncedAt == null ? "" : syncedAt;
        }
    }

    public static final String STATUS_CONNECTED = "call connected";
    public static final String STATUS_NOT_CONNECTED = "not connected";
    public static final String VALID_YES = "Yes";
    public static final String VALID_NO = "No";

    public static final String LAYOUT_STANDARD = "standard";
    public static final String LAYOUT_SHIFTED = "shifted";

    private static final String COL_AGENTS = "agents";
    private static final String COL_NUMBERS = "numbers";
    private static final String COL_CALLS = "calls";
    private static final String COL_REPORTS = "reports";
    private static final String COL_DAILY_REPORTS = "dailyReports";
    private static final String COL_COMMISSION_STATS = "commissionStats";
    private static final String COL_FTD_USERS = "ftdUsers";
    private static final String COL_ROLES = "roles";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    /**
     * Sheet layout (standard or shifted) per normalised number, from the most
     * recent loadAgentNumbers call. Row has no sheetLayout field, so the
     * caller reads it here. Numbers absent from the map are standard.
     */
    private Map<String, String> lastSheetLayouts = new HashMap<>();

    public FirestoreStore() {
        this(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance());
    }

    public FirestoreStore(FirebaseFirestore db, FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    /**
     * Number normalisation, matching SheetsClient.normLogNumber: strip spaces
     * and dashes, +880 or 880 becomes a leading 0, any other non-empty value
     * not starting with 0 gets a 0 prepended (so a 10-digit bare number
     * becomes 0-prefixed). Two additions from DESIGN.md section 7b, which the
     * migration already applied to every stored number: commas are stripped,
     * and an 0880 double prefix loses its extra 0 first, so the result
     * addresses the same Firestore doc the migration wrote.
     */
    public static String normLogNumber(String n) {
        if (n == null) return "";
        String d = n.replace(" ", "").replace("-", "").replace(",", "");
        if (d.startsWith("0880")) d = d.substring(1);
        if (d.startsWith("+880")) d = "0" + d.substring(4);
        else if (d.startsWith("880")) d = "0" + d.substring(3);
        else if (!d.isEmpty() && !d.startsWith("0")) d = "0" + d;
        return d;
    }

    /** True when the number matches the DESIGN section 5 valid form: 0 plus 10 digits. */
    public static boolean isValidNormalised(String n) {
        return n != null && n.matches("0\\d{10}");
    }

    /**
     * True when the failure is a Firestore quota / resource-exhausted error.
     * Walks the cause chain and checks the Firestore error code first, then
     * the message text as a fallback.
     */
    public static boolean isQuotaError(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException ffe = (FirebaseFirestoreException) t;
                if (ffe.getCode() == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                    return true;
                }
            }
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("resource-exhausted")
                        || lower.contains("resource_exhausted")
                        || lower.contains("resource exhausted")
                        || lower.contains("quota")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /** Sign in with a Google ID token (admin path). The token is never logged or stored. */
    public void signInWithGoogleCredential(String idToken, Callback<Void> callback) {
        if (idToken == null || idToken.isEmpty()) {
            callback.onError(new IllegalArgumentException("idToken is empty"));
            return;
        }
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e));
    }

    /** Sign in with email and password (agent path). The password is never logged or stored. */
    public void signInWithEmail(String email, String password, Callback<Void> callback) {
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            callback.onError(new IllegalArgumentException("email or password is empty"));
            return;
        }
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e));
    }

    /** Email of the currently signed-in user, or null when nobody is signed in. */
    public String getCurrentEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    /**
     * Reads roles/{gmail} and returns its agentId, or null when there is no
     * such doc or it has no agentId. The gmail is lowercased for the doc ID,
     * matching how the migration stored roles docs.
     */
    public void lookupAgentIdForGmail(String gmail, Callback<String> callback) {
        if (gmail == null || gmail.trim().isEmpty()) {
            callback.onSuccess(null);
            return;
        }
        String docId = gmail.trim().toLowerCase(Locale.ROOT);
        db.collection(COL_ROLES).document(docId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onSuccess(null);
                        return;
                    }
                    String agentId = doc.getString("agentId");
                    callback.onSuccess((agentId == null || agentId.isEmpty()) ? null : agentId);
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    /**
     * Reads agents/{agentId}/numbers and maps each doc to a Row. Row.row is
     * the sheetRow, Row.statusTime is lastCalledTime. The list is sorted by
     * sheetRow ascending (docs without a sheetRow sort last) so the order
     * matches the Sheet. Sheet layouts are kept in lastSheetLayouts; read
     * them with getSheetLayoutForNumber or getLastLoadSheetLayouts.
     */
    public void loadAgentNumbers(String agentId, Callback<List<Row>> callback) {
        if (agentId == null || agentId.isEmpty()) {
            callback.onError(new IllegalArgumentException("agentId is empty"));
            return;
        }
        db.collection(COL_AGENTS).document(agentId).collection(COL_NUMBERS).get()
                .addOnSuccessListener(snapshot -> {
                    List<Row> rows = new ArrayList<>();
                    Map<String, String> layouts = new HashMap<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String number = normLogNumber(firstNonEmpty(doc.getString("number"), doc.getId()));
                        if (number.isEmpty()) {
                            continue;
                        }
                        String status = str(doc, "status");
                        String valid = str(doc, "valid");
                        String remark1 = str(doc, "remark1");
                        String remark2 = str(doc, "remark2");
                        String others = str(doc, "others");
                        long sheetRow = num(doc, "sheetRow");
                        Row row = new Row((int) sheetRow, number, status, valid, remark1, remark2, others);
                        row.statusTime = str(doc, "lastCalledTime");
                        rows.add(row);
                        String layout = str(doc, "sheetLayout");
                        layouts.put(number, layout.isEmpty() ? LAYOUT_STANDARD : layout);
                    }
                    Collections.sort(rows, (a, b) -> {
                        int ra = a.row == 0 ? Integer.MAX_VALUE : a.row;
                        int rb = b.row == 0 ? Integer.MAX_VALUE : b.row;
                        return Integer.compare(ra, rb);
                    });
                    lastSheetLayouts = layouts;
                    callback.onSuccess(rows);
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    /**
     * Sheet layout for one number from the most recent loadAgentNumbers call:
     * standard or shifted. Returns standard when the number was not in that load.
     */
    public String getSheetLayoutForNumber(String number) {
        String layout = lastSheetLayouts.get(normLogNumber(number));
        return layout == null ? LAYOUT_STANDARD : layout;
    }

    /** Copy of the number-to-layout map from the most recent loadAgentNumbers call. */
    public Map<String, String> getLastLoadSheetLayouts() {
        return new HashMap<>(lastSheetLayouts);
    }

    /**
     * Saves one call result as ONE Firestore WriteBatch (one network request):
     * (a) set/merge the agents/{agentId}/numbers/{number} doc with status,
     * valid, remark1, remark2, others, lastCalledDate, lastCalledTime and
     * syncedToSheet false; (b) set the agents/{agentId}/calls doc whose ID is
     * date_number_status_valid with date, number, status, valid, time, a
     * server-timestamp createdAt and syncedToSheet false. A quota failure is
     * reported as a QuotaExceededException so the caller can fall back to
     * the Sheet path; no failure is ever swallowed.
     */
    public void saveNumberStatus(String agentId, Row row, String date, String time,
                                 Callback<Void> callback) {
        if (agentId == null || agentId.isEmpty()) {
            callback.onError(new IllegalArgumentException("agentId is empty"));
            return;
        }
        if (row == null || row.number == null || row.number.isEmpty()) {
            callback.onError(new IllegalArgumentException("row number is empty"));
            return;
        }
        String number = normLogNumber(row.number);
        String safeDate = date == null ? "" : date;
        String safeTime = time == null ? "" : time;
        String safeStatus = row.status == null ? "" : row.status;
        String safeValid = row.valid == null ? "" : row.valid;
        String safeRemark1 = row.remark1 == null ? "" : row.remark1;
        String safeRemark2 = row.remark2 == null ? "" : row.remark2;
        String safeOthers = row.others == null ? "" : row.others;
        Map<String, Object> numberData = new HashMap<>();
        numberData.put("number", number);
        numberData.put("status", safeStatus);
        numberData.put("valid", safeValid);
        numberData.put("remark1", safeRemark1);
        numberData.put("remark2", safeRemark2);
        numberData.put("others", safeOthers);
        numberData.put("lastCalledDate", safeDate);
        numberData.put("lastCalledTime", safeTime);
        numberData.put("syncedToSheet", false);
        Map<String, Object> callData = new HashMap<>();
        callData.put("date", safeDate);
        callData.put("number", number);
        callData.put("status", safeStatus);
        callData.put("valid", safeValid);
        callData.put("time", safeTime);
        callData.put("createdAt", FieldValue.serverTimestamp());
        callData.put("syncedToSheet", false);
        String callDocId = safeDate + "_" + number + "_" + safeStatus + "_" + safeValid;
        WriteBatch batch = db.batch();
        batch.set(db.collection(COL_AGENTS).document(agentId).collection(COL_NUMBERS)
                .document(number), numberData, SetOptions.merge());
        batch.set(db.collection(COL_AGENTS).document(agentId).collection(COL_CALLS)
                .document(callDocId), callData);
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    if (isQuotaError(e)) {
                        callback.onError(new QuotaExceededException(
                                "Firestore write quota exhausted", e));
                    } else {
                        callback.onError(e);
                    }
                });
    }

    /**
     * Reads agents/{agentId}/calls where date equals the given date
     * (yyyy-MM-dd), sorted by time with entries lacking a time last.
     */
    public void loadCalls(String agentId, String date, Callback<List<CallEntry>> callback) {
        if (agentId == null || agentId.isEmpty()) {
            callback.onError(new IllegalArgumentException("agentId is empty"));
            return;
        }
        db.collection(COL_AGENTS).document(agentId).collection(COL_CALLS)
                .whereEqualTo("date", date == null ? "" : date).get()
                .addOnSuccessListener(snapshot -> {
                    List<CallEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String entryDate = str(doc, "date");
                        String number = normLogNumber(str(doc, "number"));
                        String status = str(doc, "status");
                        String valid = str(doc, "valid");
                        String time = str(doc, "time");
                        entries.add(new CallEntry(entryDate, number, status, valid, time));
                    }
                    Collections.sort(entries, (a, b) -> {
                        boolean aEmpty = a.time.isEmpty();
                        boolean bEmpty = b.time.isEmpty();
                        if (aEmpty && bEmpty) return a.number.compareTo(b.number);
                        if (aEmpty) return 1;
                        if (bEmpty) return -1;
                        int byTime = a.time.compareTo(b.time);
                        return byTime != 0 ? byTime : a.number.compareTo(b.number);
                    });
                    callback.onSuccess(entries);
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    /** Reads the reports docs whose agentId equals the given agent. */
    public void loadReportEntries(String agentId, Callback<List<ReportEntry>> callback) {
        if (agentId == null || agentId.isEmpty()) {
            callback.onError(new IllegalArgumentException("agentId is empty"));
            return;
        }
        db.collection(COL_REPORTS).whereEqualTo("agentId", agentId).get()
                .addOnSuccessListener(snapshot -> {
                    List<ReportEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String entryDate = str(doc, "date");
                        String type = str(doc, "type");
                        String number = reportNumberAsString(doc);
                        double amount = numDouble(doc, "amount");
                        String note = str(doc, "note");
                        entries.add(new ReportEntry(entryDate, type, number, amount, note));
                    }
                    callback.onSuccess(entries);
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    /**
     * Adds one reports doc with an auto-generated ID and syncedToSheet false.
     * An auto-ID is used deliberately: DESIGN section 4 keys migrated docs by
     * sheetRow, which the app does not know, and a deterministic ID built
     * from agentId, date, type and number would collapse legitimately
     * identical rows (the Sheet has several identical FTD rows on one day,
     * per sync_design.md), so each entry must be its own doc.
     */
    public void addReportEntry(String agentId, ReportEntry entry, Callback<Void> callback) {
        if (agentId == null || agentId.isEmpty()) {
            callback.onError(new IllegalArgumentException("agentId is empty"));
            return;
        }
        if (entry == null) {
            callback.onError(new IllegalArgumentException("entry is null"));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("agentId", agentId);
        data.put("date", entry.date);
        data.put("type", entry.type);
        data.put("number", entry.number);
        data.put("amount", entry.amount);
        data.put("note", entry.note);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("syncedToSheet", false);
        db.collection(COL_REPORTS).document().set(data)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    if (isQuotaError(e)) {
                        callback.onError(new QuotaExceededException(
                                "Firestore write quota exhausted", e));
                    } else {
                        callback.onError(e);
                    }
                });
    }

    /**
     * Reads commissionStats/{date} (date yyyy-MM-dd). Returns the doc fields
     * as a map, or null when there is no doc for that date.
     */
    public void loadCommissionStats(String date, Callback<Map<String, Object>> callback) {
        if (date == null || date.isEmpty()) {
            callback.onError(new IllegalArgumentException("date is empty"));
            return;
        }
        db.collection(COL_COMMISSION_STATS).document(date).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getData() == null) {
                        callback.onSuccess(null);
                        return;
                    }
                    callback.onSuccess(new HashMap<>(doc.getData()));
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    /** Reads all ftdUsers docs ordered by username. */
    public void loadFtdUsers(Callback<List<FtdUser>> callback) {
        db.collection(COL_FTD_USERS).orderBy("username").get()
                .addOnSuccessListener(snapshot -> {
                    List<FtdUser> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String username = firstNonEmpty(doc.getString("username"), doc.getId());
                        double firstDepositAmount = numDouble(doc, "firstDepositAmount");
                        String accountCreated = str(doc, "accountCreated");
                        String lastLogin = str(doc, "lastLogin");
                        String depositDate = str(doc, "depositDate");
                        String syncedAt = str(doc, "syncedAt");
                        users.add(new FtdUser(username, firstDepositAmount, accountCreated,
                                lastLogin, depositDate, syncedAt));
                    }
                    callback.onSuccess(users);
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    /**
     * Reads the dailyReports snapshot for one agent and one date
     * (yyyy-MM-dd) and returns its fields as a map, or null when there is
     * none. The query filters on agentId only and the date is matched
     * client-side, so no composite index is needed; when several snapshots
     * exist for the day the latest wins, by createdAt first, then by the
     * generatedAt text.
     */
    public void loadDailyReport(String agentId, String date,
                                Callback<Map<String, Object>> callback) {
        if (agentId == null || agentId.isEmpty()) {
            callback.onError(new IllegalArgumentException("agentId is empty"));
            return;
        }
        if (date == null || date.isEmpty()) {
            callback.onError(new IllegalArgumentException("date is empty"));
            return;
        }
        db.collection(COL_DAILY_REPORTS).whereEqualTo("agentId", agentId).get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> best = null;
                    Timestamp bestCreatedAt = null;
                    String bestGeneratedAt = "";
                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (!date.equals(str(doc, "date"))) {
                            continue;
                        }
                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        String generatedAt = str(doc, "generatedAt");
                        boolean newer;
                        if (best == null) {
                            newer = true;
                        } else if (createdAt != null && bestCreatedAt != null) {
                            newer = createdAt.compareTo(bestCreatedAt) > 0;
                        } else if (createdAt != null) {
                            newer = true;
                        } else if (bestCreatedAt != null) {
                            newer = false;
                        } else {
                            newer = generatedAt.compareTo(bestGeneratedAt) > 0;
                        }
                        if (newer) {
                            best = new HashMap<>(doc.getData());
                            bestCreatedAt = createdAt;
                            bestGeneratedAt = generatedAt;
                        }
                    }
                    callback.onSuccess(best);
                })
                .addOnFailureListener(e -> callback.onError(e));
    }

    private static String str(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        return value == null ? "" : value;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b == null ? "" : b;
    }

    private static long num(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static double numDouble(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * The reports number field is a string per DESIGN section 4 (a count for
     * Registration and FTD, a phone number for Invalid), but a numeric value
     * is read back as its plain text so old docs still load.
     */
    private static String reportNumberAsString(DocumentSnapshot doc) {
        Object value = doc.get("number");
        if (value == null) return "";
        if (value instanceof Number) {
            double numberValue = ((Number) value).doubleValue();
            if (numberValue == Math.floor(numberValue)) {
                return String.valueOf((long) numberValue);
            }
            return String.valueOf(numberValue);
        }
        return String.valueOf(value);
    }
}
