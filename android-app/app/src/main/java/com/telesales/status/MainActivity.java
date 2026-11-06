package com.telesales.status;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import org.json.JSONObject;
import org.json.JSONArray;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList; import android.graphics.Color; import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent; import android.webkit.CookieManager; import android.webkit.WebView; import android.webkit.WebViewClient; import android.webkit.WebResourceRequest; import android.webkit.WebResourceError;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1001;
    private static final int RC_AUTH = 1002;
    // v4.32: package-visible — SaveDrainWorker eki scope string-e token nebe (ek source of truth).
    static final String SHEET_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String SHEET_ID = "10Lp_BOTSk-HNdNt-lyMuD57V0oW8qP4zhGNDi9KLgDE";
    /** v4.29 admin gate: config file-er malik BA bortoman numbers sheet-er malik BA user-er bola admin Gmail BA Admins tab-er list — je kono ekta holei Admin Panel khulbe. Lookup fail hole fail-closed. */
    private static final String ADMIN_EMAIL = "shihabno.18@gmail.com";

    private GoogleSignInClient signInClient;
    private String token;
    private String agentId;
    private String statusLogSheetId; private String statusLogTab = "Status Log"; static final String SHARED_STATUS_LOG_ID = "1t22IL9miZ_5QYWD3rJVioXZlKsGwlk6X8bW_TQCjbfQ";
    // v4.25: shared Report file "Telesales Agent Reports" — ID code-e (log file-er moto);
    // prottek agent-er tab (agentId) -e Date|Type|Number|Amount|Note row thake.
    static final String SHARED_REPORT_ID = "1jYB5KeGw1LDH4FnjbwBr9qL-IHgmzPIhWU7GNeb5uhk";
    private String selectedDate = dhakaDate(); private String reportDate = dhakaDate();
    private Map<String,String> lastStatusMap = new HashMap<>();
    // Batch fix #20: notification tap-e asa number — row load howar por khulte hobe.
    private boolean rowsLoaded = false;
    // v4.24 optimistic save: Save tap-er sathe sathe UI update, network background-e.
    // savingRows = in-flight double-tap guard (key = sheet row + number); optimisticTileKeys =
    // tile +1 ekbar-i hobe (retry/re-save-e double-count noy).
    private java.util.Set<String> savingRows = new java.util.HashSet<>();
    private java.util.Set<String> optimisticTileKeys = new java.util.HashSet<>();
    private RowAdapter adapter;
    private View loginView, mainView;
    private View agentView;
    private TextView agentError, agentSubtitle;
    private TextInputEditText agentInput;
    private View adminView;
    private TextView adminStatus;
    private TextInputEditText adminAgentInput, adminNumbersInput;
    private TextInputEditText adminInNumbers, adminInLog, adminInReports, adminInDaily;
    private TextView adminCurNumbers, adminCurLog, adminCurReports, adminCurDaily, adminSheetHistory, adminSheetStatus;
    private TextInputEditText adminNewAdminInput, teamNameInput, teamAgentsInput, teamTabInput, teamNumbersInput;
    private TextView adminAdminStatus, teamStatus, teamSelectedLabel, teamNumbersStatus;
    private LinearLayout teamListContainer;
    private LinearLayout adminSectionNumber, adminSectionTeam, adminSectionSheet, adminSectionAdmins, adminSectionDashboard;
    private TextInputEditText testSheetInput, testSheetTabInput, testSheetNumbersInput;
    private TextView testSheetStatus, testSheetNumbersStatus;
    /** v4.31: call reminder — dashboard section views + pending notification-permission retry. */
    private LinearLayout reminderListContainer;
    private View reminderSection;
    private TextView reminderEmptyText;
    private String pendingReminderNumber = null;
    private long pendingReminderTime = 0;
    private static final int REQ_NOTIFY_REMINDER = 9101;
    private volatile java.util.Set<String> adminEmailsCache = null;
    private TeamRow selectedTeam = null;
    private java.util.List<TeamRow> teamRowsCache = new ArrayList<>();
    /** v4.29 team row: Teams tab-er ekta row (sheetRow = tab-er row number, header bad diye). */
    private static class TeamRow { final int sheetRow; final String name, sheetId, created, active; TeamRow(int r, String n, String s, String c, String a) { sheetRow = r; name = n; sheetId = s; created = c; active = a; } }
    private static class VerifiedSheet { final String id; final String title; final int tabs; VerifiedSheet(String i, String t, int n) { id = i; title = t; tabs = n; } }
    private Map<String, VerifiedSheet> verifiedSheets = new HashMap<>();
    private TextView loginError, avatarView, statusText, weekSummary, queuedCountText;
    private TextView statConnDay, statNotConnDay, statValidDay, statInvDay, statRegDay, statFtdDay, statFtdAmtDay;
    /** Batch feature #10: ajker target card-er obostha (duitar source-i load hole tobei dekhabe). */
    private View targetCard; private TextView targetCardText;
    private int lastTotalCallDay = 0, lastRemaining = 0;
    private boolean targetStatsReady = false, targetRemainReady = false;
    private MaterialButton dateBtn; private Button reportPrevBtn, reportDateBtn, reportNextBtn, reportTodayBtn; private TextView reportDateNote;
    private TextInputEditText searchBox;
    private View reportView, reportOutputCard;
    private View guideView;
    private TextView guideText;
    private View dashboardView, listContent; private TextView statTotalCallDay, statTotalNumDay, statTotalRemainDay; private View bottomMenuBar, websiteView; private WebView websiteWeb; private boolean websiteLoaded = false;
    private int currentSection = 0;
    private static final String DAILY_REPORTS_ID = "1BPaQrV6Znqcs45pNEUib8AOHnxzz93Z8Y8vEMAAHOzg";
    // v4.19 cache/guards: shared log ID + tab ekbar confirm hole protibar Drive search noy;
    // load-er sequence diye purono response notun data-r upor boshbe na.
    private String sharedLogCacheAgent = null, sharedLogCacheId = null;
    private int rowsSeq = 0, statsSeq = 0, reportSeq = 0;
    private boolean reportStatsFallbackUsed = false, reportLoadFallbackUsed = false;
    private boolean rowsLoading = false, authRetryUsed = false;
    private android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingRowsLoad, pendingStatsLoad;
    private TextView reportStatus, reportOutput, ftdTotal, ftdStepCount, regStepCount, invAutoText;
    private LinearLayout regList, invList, probList, ftdList, ftdStepAmountRow;
    private TextInputEditText regCount, ftdStepAmount, invNumber, probText;
    
    private long updateDownloadId = -1;
    private BroadcastReceiver updateReceiver;
    private Spinner invReason;
    private String reportSheetId;
    /** Report sheet-er ekta row: cells + sheet-er row number (delete korar jonno) + v4.28 source sheet/order (switch diner row kon sheet-e, seta chenar jonno). */
    static class REntry { final String[] cells; final int sheetRow; final String[] rawCells; final String sourceSheet; final int sourceOrder; REntry(String[] c, int r) { this(c, r, null, null, 0); } REntry(String[] c, int r, String[] raw) { this(c, r, raw, null, 0); } REntry(String[] c, int r, String[] raw, String src, int ord) { cells = c; sheetRow = r; rawCells = raw; sourceSheet = src; sourceOrder = ord; } }
    // v4.28 SHEET SWITCHER: config file kokhono switch hoy na — ORIGINAL shared Status Log
    // file-ei "SheetConfig" tab-e 4 store-er ID chain thake. Chain-er prottek entry:
    // (sheetId, fromDate) — fromDate khali mane "age theke". Hardcoded ID-gulo sudhu
    // fallback/default (config na pele ager motoi cholbe).
    static final String CONFIG_FILE_ID = SHARED_STATUS_LOG_ID;
    private static class ConfigEntry { final String sheetId; final String fromDate; ConfigEntry(String s, String d) { sheetId = s; fromDate = d == null ? "" : d; } }
    private volatile Map<String, List<ConfigEntry>> sheetConfigCache = null;
    private static Map<String, List<ConfigEntry>> defaultConfigMap() { Map<String, List<ConfigEntry>> m = new HashMap<>(); List<ConfigEntry> n = new ArrayList<>(); n.add(new ConfigEntry(SHEET_ID, "")); m.put("NUMBERS", n); List<ConfigEntry> l = new ArrayList<>(); l.add(new ConfigEntry(SHARED_STATUS_LOG_ID, "")); m.put("STATUS_LOG", l); List<ConfigEntry> r = new ArrayList<>(); r.add(new ConfigEntry(SHARED_REPORT_ID, "")); m.put("REPORTS", r); List<ConfigEntry> d = new ArrayList<>(); d.add(new ConfigEntry(DAILY_REPORTS_ID, "")); m.put("DAILY", d); return m; }
    private Map<String, List<ConfigEntry>> currentConfig() { Map<String, List<ConfigEntry>> c = sheetConfigCache; return c != null ? c : defaultConfigMap(); }
    /** v4.28: kono date-er jonno write sheet — chain-er moddhe oi date-er age ba soman sobcheye notun FromDate-oyala entry; na thakle sobcheye ager entry. */
    private static String writeSheetFor(Map<String, List<ConfigEntry>> cfg, String store, String date) { if (cfg == null) cfg = defaultConfigMap(); List<ConfigEntry> chain = cfg.get(store); if (chain == null || chain.isEmpty()) return null; String best = null, bestFrom = null; for (ConfigEntry e : chain) { if (e.fromDate.isEmpty() || e.fromDate.compareTo(date) <= 0) { if (bestFrom == null || e.fromDate.compareTo(bestFrom) >= 0) { best = e.sheetId; bestFrom = e.fromDate; } } } if (best != null) return best; String early = null, earlyFrom = null; for (ConfigEntry e : chain) { if (earlyFrom == null || e.fromDate.compareTo(earlyFrom) < 0) { early = e.sheetId; earlyFrom = e.fromDate; } } return early; }
    /** v4.28: kono date-er jonno read sheet-gulo, chain order-e (purono → notun, tai sourceOrder bare = notun):
     *  write sheet + (switch din hole) oi diner sob switch sheet + oi diner ager sheet (sokaler row-r jonno).
     *  DEVIATION NOTE: spec-er akkhorik formula ({write} + {FromDate==date}) switch diner
     *  sokaler row (ager sheet-e) bad dito — user-er "ager kaj dekhate hobe" shorto vangto;
     *  tai ager sheet-o jog kora hoyeche. */
    private static List<String> readSheetsFor(Map<String, List<ConfigEntry>> cfg, String store, String date) { java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>(); if (cfg != null) { List<ConfigEntry> chain = cfg.get(store); if (chain != null && !chain.isEmpty()) { String w = writeSheetFor(cfg, store, date); boolean isSwitchDay = false; for (ConfigEntry e : chain) if (date.equals(e.fromDate)) { isSwitchDay = true; break; } String prev = null, prevFrom = null; if (isSwitchDay) { for (ConfigEntry e : chain) { if (e.fromDate.compareTo(date) < 0) { if (prevFrom == null || e.fromDate.compareTo(prevFrom) >= 0) { prev = e.sheetId; prevFrom = e.fromDate; } } } } for (ConfigEntry e : chain) { if (e.sheetId.equals(w) || date.equals(e.fromDate) || (isSwitchDay && e.sheetId.equals(prev))) out.add(e.sheetId); } } } if (out.isEmpty()) { String d = writeSheetFor(defaultConfigMap(), store, date); if (d != null) out.add(d); } return new ArrayList<>(out); }
    private String currentNumbersId() { return writeSheetFor(currentConfig(), "NUMBERS", dhakaDate()); }
    private List<String> logReadIds(String date) { return readSheetsFor(currentConfig(), "STATUS_LOG", date); }
    private List<String> reportReadIds(String date) { return readSheetsFor(currentConfig(), "REPORTS", date); }
    /** v4.28: config tab theke chain pora — kokhono throw kore na, na pele ager map/default cholbe. */
    private void tryRefreshConfig() { try { if (token == null) return; List<String[]> rows = SheetsClient.getConfigRows(token, CONFIG_FILE_ID); Map<String, List<ConfigEntry>> m = parseConfigRows(rows); if (m != null) { sheetConfigCache = m; reportSheetId = null; } } catch (Exception ignored) { /* config na pele ager map/default cholbe */ } }
    private static Map<String, List<ConfigEntry>> parseConfigRows(List<String[]> rows) { if (rows == null) return null; Map<String, List<ConfigEntry>> m = defaultConfigMap(); java.util.Set<String> seenStores = new java.util.HashSet<>(); for (String[] r : rows) { if (r == null || r.length < 2) continue; String store = r[0] == null ? "" : r[0].trim(); String id = r[1] == null ? "" : r[1].trim(); if (id.isEmpty()) continue; if (!("NUMBERS".equals(store) || "STATUS_LOG".equals(store) || "REPORTS".equals(store) || "DAILY".equals(store))) continue; if (!seenStores.contains(store)) { m.put(store, new ArrayList<>()); seenStores.add(store); } m.get(store).add(new ConfigEntry(id, r.length > 2 && r[2] != null ? r[2].trim() : "")); } for (List<ConfigEntry> chain : m.values()) java.util.Collections.sort(chain, (a, b) -> a.fromDate.compareTo(b.fromDate)); return m; }
    private boolean lastLogReadUsedLegacy = false;
    // v4.28 parent fix: read-er somoy write sheet-er tab ekbar ensure (notun agent / switch-er por
    // khali report na dekhiye ager motoi tab banano); chain-er kono sheet fail hole partial note.
    private java.util.Set<String> ensuredLogSheets = new java.util.HashSet<>();
    private java.util.Set<String> ensuredReportSheets = new java.util.HashSet<>();
    private boolean lastLogPartialFail = false, lastReportPartialFail = false;
    /** v4.28: kono date-er log — chain-er sob sheet theke (switch diner sokal + bikal mile); sob fail hole legacy fallback, na hole prothom error. */
    private List<REntry> readLogRowsForDate(String date) throws Exception { lastLogReadUsedLegacy = false; lastLogPartialFail = false; List<String> ids = logReadIds(date); if (agentId != null) { String w = writeSheetFor(currentConfig(), "STATUS_LOG", date); if (w != null && !ensuredLogSheets.contains(w)) { try { SheetsClient.ensureSheetTab(token, w, agentId); ensuredLogSheets.add(w); } catch (Exception ignored) {} } } List<REntry> out = new ArrayList<>(); Exception first = null; for (int si = 0; si < ids.size(); si++) { String id = ids.get(si); try { out.addAll(withRows(SheetsClient.getReportRows(token, id, agentId), id, si)); } catch (Exception e) { lastLogPartialFail = true; if (first == null) first = e; } } if (!out.isEmpty() || first == null) return out; StatusLogTarget legacy = resolveLegacyStatusLogTarget(); if (legacy != null) { List<REntry> lr = withRows(SheetsClient.getReportRows(token, legacy.sheetId, legacy.tab), legacy.sheetId, 0); lastLogReadUsedLegacy = true; return lr; } throw first; }
    /** v4.28: kono date-er report row — chain-er sob sheet theke, source sheet/order tag-soho (delete-er jonno). */
    private List<REntry> readReportRowsForDate(String date) throws Exception { lastReportPartialFail = false; List<String> ids = reportReadIds(date); if (agentId != null) { String w = writeSheetFor(currentConfig(), "REPORTS", date); if (w != null && !ensuredReportSheets.contains(w)) { try { SheetsClient.ensureReportTab(token, w, agentId); ensuredReportSheets.add(w); } catch (Exception ignored) {} } } List<REntry> out = new ArrayList<>(); Exception first = null; for (int si = 0; si < ids.size(); si++) { String id = ids.get(si); try { out.addAll(withRows(SheetsClient.getReportRows(token, id, agentId), id, si)); } catch (Exception e) { lastReportPartialFail = true; if (first == null) first = e; } } if (!out.isEmpty() || first == null) return out; throw first; }
    /** v4.28: shared log target hole oi date-er sob chain sheet theke, legacy hole sudhu legacy target theke muchbe. */
    private void deleteLogRowsForTarget(StatusLogTarget t, String date, String number) throws Exception { if (t.shared) deleteLogRowsEverywhere(date, number, t.sheetId); else SheetsClient.deleteLogRowsForDateNumber(token, t.sheetId, t.tab, date, number); }
    private void deleteLogRowsEverywhere(String date, String number, String primarySheetId) throws Exception { List<String> ids = logReadIds(date); if (primarySheetId != null && !ids.contains(primarySheetId)) { ids = new ArrayList<>(ids); ids.add(0, primarySheetId); } Exception primaryError = null; for (String id : ids) { try { SheetsClient.deleteLogRowsForDateNumber(token, id, agentId, date, number); } catch (Exception e) { if (id.equals(primarySheetId)) primaryError = e; } } if (primaryError != null) throw primaryError; }
    private List<REntry> regToday = new ArrayList<>(), ftdToday = new ArrayList<>(), invToday = new ArrayList<>(), probToday = new ArrayList<>();
    private final String[] reasons = {"কল ধরেনি", "নট কানেক্টেড", "বন্ধ", "ভুল নাম্বার"};

    /** Bangla user manual: agent-ra kobe Connected/Not Connected/Valid Yes/No dibe, remark, report. */
    private static final String GUIDE_TEXT =
        "১. লগইন\n" +
        "গুগল দিয়ে চালিয়ে যান চাপুন, আপনার জিমেইল দিয়ে লগইন করুন। প্রথমবার এজেন্ট আইডি চাইবে — আপনার ট্যাবের নাম (যেমন bdxsukuna03) লিখুন। এরপর থেকে ওই জিমেইলে লগইন করলেই সরাসরি আপনার লিস্ট খুলবে। উপরের অক্ষরে চাপ দিলে লগআউট হবে।\n\n" +
        "২. কল লিস্ট\n" +
        "কল লিস্টে নাম্বারগুলো থাকে। কল বাকি ট্যাবে যেসব নাম্বারে এখনো কল করা হয়নি, সেগুলো থাকে। কল করে স্ট্যাটাস দিলে সেটা কল করা হয়েছে ট্যাবে চলে যাবে। ড্যাশবোর্ডে তারিখ বদলে ওই দিনের হিসাব দেখতে পারবেন। সার্চ বক্সে নাম্বার লিখে খুঁজতে পারবেন। রিফ্রেশ চাপলে শিট থেকে নতুন লিস্ট আসবে।\n\n" +
        "৩. কল করা ও স্ট্যাটাস দেওয়া\n" +
        "কল চাপলে কল যাবে। কপি চাপলে নাম্বার কপি হবে। কল করার পর স্ট্যাটাস দিন, তারপর সেভ চাপুন। সেভ না চাপলে শিটে কিছু বদলাবে না।\n\n" +
        "কল Connected কখন দিবেন:\n" +
        "যখন কলটা ধরেছে — অর্থাৎ কেউ ফোন রিসিভ করেছে — তখন Connected দিবেন।\n\n" +
        "কল Not Connected কখন দিবেন:\n" +
        "যখন কেউ কল রিসিভ করেনি, বা ফোন বন্ধ, বা কল কেটে দিয়েছে — তখন Not Connected দিবেন।\n\n" +
        "ভ্যালিড কখন Yes দিবেন:\n" +
        "কল ধরেছে এবং সে ডিপোজিট করবে — এখনই করুক, বা বিকালে করুক, বা সন্ধ্যায় করুক — ডিপোজিট করার কথা যারা বলেছে, তাদের Yes দিবেন।\n\n" +
        "ভ্যালিড কখন না দিবেন:\n" +
        "যারা ডিপোজিট করবে না; যারা গেম খেলে না; ফোন ধরেছে কিন্তু ডিপোজিট করবে না বলেছে; বা যার নাম্বার দিয়ে অ্যাকাউন্ট খোলা নেই — তাদের না দিবেন।\n\n" +
        "১ম কল রিমার্ক:\n" +
        "প্রথম কল করে যা জানতে পারলেন — ফোন ধরেছে না ধরেনি, ডিপোজিট করতে চেয়েছে কি চায়নি, যেকোনো কথা — এখানে লিখে দিতে পারবেন।\n\n" +
        "২য় কল রিমার্ক:\n" +
        "দ্বিতীয়বার কল করে যা জানলেন, সেটা এখানে লিখবেন।\n\n" +
        "অন্যান্য নাম্বার:\n" +
        "কাস্টমারের যদি আরেকটা এক্সট্রা নাম্বার থাকে, সেটা এখানে অ্যাড করতে পারবেন।\n\n" +
        "৪. রিপোর্ট\n" +
        "নিচের মেনু থেকে রিপোর্ট-এ গেলে দিনের হিসাব দেওয়া যায়:\n" +
        "• রেজিস্ট্রেশন: + চাপলে একটা করে বাড়বে, − চাপলে আজকের শেষটা বাদ যাবে; কোনো টাকা চাওয়া হবে না।\n" +
        "• এফটিডি: + চাপলে একটা করে বাড়বে; প্রতিবার নিচে ওই এফটিডি-র টাকাটা লিখতে হবে। শুধু মোট সংখ্যা আর মোট টাকা দেখাবে, আলাদা আলাদা এফটিডি-র টাকা দেখাবে না। ভুল হলে − চাপলে আজকের শেষ এফটিডি-টা বাদ যাবে।\n" +
        "• Invalid নাম্বার: আলাদা করে লিখতে হবে না — নিজে থেকে হিসাব হয়। যেসব নাম্বারে Not Connected আর Valid No দুটোই আছে, সেসব নাম্বার ওই দিনে একবার করে গোনা হয় (একই নাম্বার দুবার থাকলেও ১)। আগের হাতে লেখা Invalid লেখাগুলো শুধু পুরোনো হিসাব হিসেবে থেকে যাবে।\n" +
        "• সমস্যা: কাজের সময় কোনো সমস্যা হলে এখানে লিখুন।\n" +
        "কোনো এন্ট্রি ভুল হলে পাশের ✕ চাপলে সেটা বাদ যাবে। রিপোর্ট বানান চাপলে নির্বাচিত তারিখ, সময়, এজেন্ট আইডি, মোট কল, Connected, রেজিস্ট্রেশন, এফটিডি, মোট টাকা, Invalid আর সমস্যা দিয়ে রিপোর্ট তৈরি হবে। কপি চাপলে পুরো রিপোর্ট কপি হবে — তারপর যেখানে পাঠাতে চান, পেস্ট করে দিতে পারবেন।\n\n" +
        "৫. ওয়েবসাইট বাটন\n" +
        "নিচের মেনু থেকে ওয়েবসাইট চাপলে আপনার সাইটটা অ্যাপের ভিতরেই খুলবে, আর নিচে মেনু দেখা যাবে। প্রথমবার ইউজারনেম-পাসওয়ার্ড দিয়ে লগইন করতে হবে; তারপর থেকে লগইন মনে থাকবে, আপডেট দিলেও ভুলবে না। মেনু থেকে যেকোনো অপশনে যেতে পারবেন।\n\n" +
        "৬. দিনের হিসাব (ড্যাশবোর্ড)\n" +
        "ড্যাশবোর্ডে ওই দিনের Connected, Not Connected, Valid, Invalid, এফটিডি, এফটিডি টাকা আর রেজিস্ট্রেশন দেখায়। নিচে ৭ দিনের মোট হিসাবের লাইনও থাকে।" + "উপরে মোট কল ওই দিনের সব কল, আর মোট নাম্বার লিস্টের সব নাম্বার দেখায়।";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keyboard: app-e dhuklei search field-e keyboard auto-khulbe na — sudhu tap korle khulbe
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_main);
        loginView = findViewById(R.id.loginView);
        mainView = findViewById(R.id.mainView);
        loginError = findViewById(R.id.loginError);
        avatarView = findViewById(R.id.avatarView);
        statConnDay = findViewById(R.id.statConnDay);
        statNotConnDay = findViewById(R.id.statNotConnDay);
        statValidDay = findViewById(R.id.statValidDay);
        statInvDay = findViewById(R.id.statInvDay);
        statRegDay = findViewById(R.id.statRegDay);
        statFtdDay = findViewById(R.id.statFtdDay);
        statFtdAmtDay = findViewById(R.id.statFtdAmtDay); statTotalCallDay = findViewById(R.id.statTotalCallDay); statTotalNumDay = findViewById(R.id.statTotalNumDay); statTotalRemainDay = findViewById(R.id.statTotalRemainDay);
        // Batch feature #1: dashboard tile tap -> oi din-er oi type-er list (FTD/Registration-er
        // entry Report screen-e thake, tai ogulo Report-e khulbe).
        statConnDay.setOnClickListener(v -> openFilteredList("connected"));
        statNotConnDay.setOnClickListener(v -> openFilteredList("notconnected"));
        statValidDay.setOnClickListener(v -> openFilteredList("valid"));
        statInvDay.setOnClickListener(v -> openFilteredList("invalid"));
        statFtdDay.setOnClickListener(v -> showReport());
        statFtdAmtDay.setOnClickListener(v -> showReport());
        statRegDay.setOnClickListener(v -> showReport());
        weekSummary = findViewById(R.id.weekSummary);
        targetCard = findViewById(R.id.targetCard);
        targetCardText = findViewById(R.id.targetCardText);
        queuedCountText = findViewById(R.id.queuedCountText);
        dateBtn = findViewById(R.id.btnDate);
        statusText = findViewById(R.id.statusText);
        searchBox = findViewById(R.id.searchBox);
        reportView = findViewById(R.id.reportView);
        guideView = findViewById(R.id.guideView); bottomMenuBar = findViewById(R.id.bottomMenuBar); websiteView = findViewById(R.id.websiteView); websiteWeb = findViewById(R.id.websiteWeb); websiteWeb.getSettings().setJavaScriptEnabled(true); websiteWeb.getSettings().setDomStorageEnabled(true); CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(websiteWeb, true);
        // Batch fix #16: site/net down hole khali page noy — toast + retry flag reset.
        websiteWeb.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) { websiteLoaded = false; Toast.makeText(MainActivity.this, "সাইট খুলছে না — নেট চেক করে আবার ওয়েবসাইট চাপুন", Toast.LENGTH_LONG).show(); }
            }
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                websiteLoaded = false; Toast.makeText(MainActivity.this, "সাইট খুলছে না — নেট চেক করে আবার ওয়েবসাইট চাপুন", Toast.LENGTH_LONG).show();
            }
        });
        guideText = findViewById(R.id.guideText);
        guideText.setText(GUIDE_TEXT);
        dashboardView = findViewById(R.id.dashboardView);
        reminderListContainer = findViewById(R.id.reminderList);
        reminderSection = findViewById(R.id.reminderSection);
        reminderEmptyText = findViewById(R.id.reminderEmptyText);
        listContent = findViewById(R.id.listContent);
        reportOutputCard = findViewById(R.id.reportOutputCard);
        reportStatus = findViewById(R.id.reportStatus);
        reportOutput = findViewById(R.id.reportOutput);
        regStepCount = findViewById(R.id.regStepCount); invAutoText = findViewById(R.id.invAutoText);


        // v4.18: removed view.
        // v4.18: removed view.
        probText = findViewById(R.id.probText);
        // v4.18: removed view.
        ftdStepCount = findViewById(R.id.ftdStepCount);
        ftdStepAmountRow = findViewById(R.id.ftdStepAmountRow);
        ftdStepAmount = findViewById(R.id.ftdStepAmount);
        ftdTotal = findViewById(R.id.ftdTotal);
        // v4.18: removed view.
        probList = findViewById(R.id.probList);
        ftdList = findViewById(R.id.ftdList);
        agentView = findViewById(R.id.agentView);
        agentInput = findViewById(R.id.agentInput);
        adminView = findViewById(R.id.adminView);
        adminAgentInput = findViewById(R.id.adminAgentInput);
        adminNumbersInput = findViewById(R.id.adminNumbersInput);
        adminStatus = findViewById(R.id.adminStatus);
        adminInNumbers = findViewById(R.id.adminInNumbers);
        adminInLog = findViewById(R.id.adminInLog);
        adminInReports = findViewById(R.id.adminInReports);
        adminInDaily = findViewById(R.id.adminInDaily);
        adminCurNumbers = findViewById(R.id.adminCurNumbers);
        adminCurLog = findViewById(R.id.adminCurLog);
        adminCurReports = findViewById(R.id.adminCurReports);
        adminCurDaily = findViewById(R.id.adminCurDaily);
        adminSheetHistory = findViewById(R.id.adminSheetHistory);
        adminSheetStatus = findViewById(R.id.adminSheetStatus);
        adminNewAdminInput = findViewById(R.id.adminNewAdminInput);
        adminAdminStatus = findViewById(R.id.adminAdminStatus);
        teamNameInput = findViewById(R.id.teamNameInput);
        teamAgentsInput = findViewById(R.id.teamAgentsInput);
        teamStatus = findViewById(R.id.teamStatus);
        teamListContainer = findViewById(R.id.teamListContainer);
        teamSelectedLabel = findViewById(R.id.teamSelectedLabel);
        teamTabInput = findViewById(R.id.teamTabInput);
        teamNumbersInput = findViewById(R.id.teamNumbersInput);
        teamNumbersStatus = findViewById(R.id.teamNumbersStatus);
        adminSectionNumber = findViewById(R.id.adminSectionNumber);
        adminSectionTeam = findViewById(R.id.adminSectionTeam);
        adminSectionSheet = findViewById(R.id.adminSectionSheet);
        adminSectionAdmins = findViewById(R.id.adminSectionAdmins);
        adminSectionDashboard = findViewById(R.id.adminSectionDashboard);
        testSheetInput = findViewById(R.id.testSheetInput);
        testSheetStatus = findViewById(R.id.testSheetStatus);
        testSheetTabInput = findViewById(R.id.testSheetTabInput);
        testSheetNumbersInput = findViewById(R.id.testSheetNumbersInput);
        testSheetNumbersStatus = findViewById(R.id.testSheetNumbersStatus);
        agentError = findViewById(R.id.agentError);
        agentSubtitle = findViewById(R.id.agentSubtitle);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SHEET_SCOPE), new Scope(DRIVE_SCOPE))
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);

        MaterialButton googleBtn = findViewById(R.id.googleBtn);
        googleBtn.setOnClickListener(v -> {
            loginError.setText("");
            startActivityForResult(signInClient.getSignInIntent(), RC_SIGN_IN);
        });
        // Batch fix #2: avatar tap-e soja logout noy — age confirm.
        avatarView.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("লগআউট করবেন?")
                .setMessage("আপনার অ্যাকাউন্ট থেকে বের হয়ে যাবেন।")
                .setPositiveButton("হ্যাঁ, লগআউট", (d, w) -> {
                    signInClient.signOut().addOnCompleteListener(t -> {
                        token = null; agentId = null; reportSheetId = null; statusLogSheetId = null;
                        getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().remove("agent_id").remove("agent_email").apply();
                        showLogin();
                        Toast.makeText(this, "লগআউট হয়েছে", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("না", null)
                .show();
        });
        findViewById(R.id.refreshBtn).setOnClickListener(v -> debouncedLoadRows());

        ArrayAdapter<String> ra = new ArrayAdapter<>(this, R.layout.spinner_item, reasons);
        ra.setDropDownViewResource(R.layout.spinner_dropdown_item);
        // v4.18: manual Invalid form removed; Invalid is now an auto-count from the Status Log.

        findViewById(R.id.menuDashboardBtn).setOnClickListener(v -> { applySection(0); loadDayStats(); });
        findViewById(R.id.menuHomeBtn).setOnClickListener(v -> { applySection(1); maybeAutoRefreshList(); });
        findViewById(R.id.menuReportBtn).setOnClickListener(v -> showReport());
        findViewById(R.id.menuWebsiteBtn).setOnClickListener(v -> showWebsite());
        findViewById(R.id.menuGuideBtn).setOnClickListener(v -> showGuide());
         
         
        findViewById(R.id.btnPrevDay).setOnClickListener(v -> shiftDay(-1));
        findViewById(R.id.btnNextDay).setOnClickListener(v -> shiftDay(1)); reportPrevBtn = findViewById(R.id.btnReportPrevDay); reportDateBtn = findViewById(R.id.btnReportDate); reportNextBtn = findViewById(R.id.btnReportNextDay); reportTodayBtn = findViewById(R.id.btnReportToday); reportDateNote = findViewById(R.id.reportDateNote); reportPrevBtn.setOnClickListener(v -> shiftReportDay(-1)); reportNextBtn.setOnClickListener(v -> shiftReportDay(1)); reportTodayBtn.setOnClickListener(v -> setReportDate(dhakaDate())); reportDateBtn.setOnClickListener(v -> { try { Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.setTime(parseDay(reportDate)); new DatePickerDialog(MainActivity.this, (view, y, m, d) -> { Calendar n = Calendar.getInstance(TimeZone.getTimeZone("UTC")); n.set(y, m, d, 0, 0, 0); n.set(Calendar.MILLISECOND, 0); String nd = fmtDay(n.getTime()); if (nd.compareTo(dhakaDate()) > 0) nd = dhakaDate(); setReportDate(nd); }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show(); } catch (Exception e) { } }); updateReportDateBtn(); updateReportEditState();
        dateBtn.setOnClickListener(v -> {
            try {
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                c.setTime(parseDay(selectedDate));
                new DatePickerDialog(this, (view, y, m, day) -> {
                    Calendar nc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    nc.set(y, m, day, 0, 0, 0);
                    nc.set(Calendar.MILLISECOND, 0);
                    String nd = fmtDay(nc.getTime());
                    if (nd.compareTo(dhakaDate()) > 0) nd = dhakaDate();
                    selectedDate = nd; updateDateBtn(); debouncedLoadDayStats();
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            } catch (Exception ignored) {}
        });
        updateDateBtn();
        // v4.19: rotation/process death-er por ager kaj (search, tarikh, section, report form) fire asbe.
        if (savedInstanceState != null) {
            String sq = savedInstanceState.getString("search", null);
            if (sq != null && searchBox != null) searchBox.setText(sq);
            String sd = savedInstanceState.getString("selectedDate", null);
            if (sd != null) { selectedDate = sd; updateDateBtn(); }
            String rd = savedInstanceState.getString("reportDate", null);
            if (rd != null) { reportDate = rd; updateReportDateBtn(); updateReportEditState(); }
            currentSection = savedInstanceState.getInt("section", currentSection);
            String pt = savedInstanceState.getString("probText", null);
            if (pt != null && probText != null) probText.setText(pt);
            String fa = savedInstanceState.getString("ftdStepAmount", null);
            if (fa != null && ftdStepAmount != null) ftdStepAmount.setText(fa);
        }
        findViewById(R.id.agentContinueBtn).setOnClickListener(v -> submitAgent());
        // Batch fix #18: Agent ID screen-er Back sudhu login screen-e ferot jabe —
        // age eta puro Google sign-out kore dito (abar chooser + sign-in lagto).
        findViewById(R.id.agentBackBtn).setOnClickListener(v -> showLogin());
        findViewById(R.id.adminAddBtn).setOnClickListener(v -> submitAdminNumbers());
        findViewById(R.id.adminVerifyNumbers).setOnClickListener(v -> verifySheetForStore("NUMBERS", adminInNumbers));
        findViewById(R.id.adminVerifyLog).setOnClickListener(v -> verifySheetForStore("STATUS_LOG", adminInLog));
        findViewById(R.id.adminVerifyReports).setOnClickListener(v -> verifySheetForStore("REPORTS", adminInReports));
        findViewById(R.id.adminVerifyDaily).setOnClickListener(v -> verifySheetForStore("DAILY", adminInDaily));
        findViewById(R.id.adminSwitchNumbers).setOnClickListener(v -> switchSheetForStore("NUMBERS", adminInNumbers));
        findViewById(R.id.adminSwitchLog).setOnClickListener(v -> switchSheetForStore("STATUS_LOG", adminInLog));
        findViewById(R.id.adminSwitchReports).setOnClickListener(v -> switchSheetForStore("REPORTS", adminInReports));
        findViewById(R.id.adminSwitchDaily).setOnClickListener(v -> switchSheetForStore("DAILY", adminInDaily));
        findViewById(R.id.adminAddAdminBtn).setOnClickListener(v -> submitAddAdmin());
        findViewById(R.id.teamCreateBtn).setOnClickListener(v -> createTeamSheet());
        findViewById(R.id.teamNumbersAddBtn).setOnClickListener(v -> submitTeamNumbers());
        findViewById(R.id.adminTabNumber).setOnClickListener(v -> showAdminSection("NUMBER"));
        findViewById(R.id.adminTabTeam).setOnClickListener(v -> showAdminSection("TEAM"));
        findViewById(R.id.adminTabSheet).setOnClickListener(v -> showAdminSection("SHEET"));
        findViewById(R.id.adminTabAdmins).setOnClickListener(v -> showAdminSection("ADMINS"));
        findViewById(R.id.adminTabDashboard).setOnClickListener(v -> showAdminSection("DASHBOARD"));
        findViewById(R.id.teamDashLoadBtn).setOnClickListener(v -> loadTeamDashboard());
        findViewById(R.id.testSheetSaveBtn).setOnClickListener(v -> saveTestSheet());
        findViewById(R.id.testSheetAddBtn).setOnClickListener(v -> submitTestSheetNumbers());
        findViewById(R.id.adminLogoutBtn).setOnClickListener(v -> {
            signInClient.signOut().addOnCompleteListener(t -> {
                token = null; agentId = null; reportSheetId = null; statusLogSheetId = null;
                getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().remove("agent_id").remove("agent_email").apply();
                showLogin();
                Toast.makeText(this, "লগআউট হয়েছে", Toast.LENGTH_SHORT).show();
            });
        });
        findViewById(R.id.btnTabPending).setOnClickListener(v -> { adapter.setTab(0); setTabUI(); });
        findViewById(R.id.btnTabDone).setOnClickListener(v -> { adapter.setTab(1); setTabUI(); });
        findViewById(R.id.regPlusBtn).setOnClickListener(v -> { addReportEntry("Registration", new String[]{dhakaDate(), "1"}, () -> loadReportData()); });
            findViewById(R.id.regMinusBtn).setOnClickListener(v -> { REntry last = null; for (REntry e : regToday) if (last == null || e.sourceOrder > last.sourceOrder || (e.sourceOrder == last.sourceOrder && e.sheetRow > last.sheetRow)) last = e; if (last == null) { Toast.makeText(this, "রেজিস্ট্রেশন নেই", Toast.LENGTH_SHORT).show(); return; } REntry delReg = last; new AlertDialog.Builder(MainActivity.this).setTitle("বাদ দেবেন?").setMessage("শেষ রেজিস্ট্রেশন এন্ট্রিটি বাদ দেবেন?").setPositiveButton("হ্যাঁ, বাদ দিন", (d, w) -> deleteReportEntry("Registration", delReg)).setNegativeButton("না", null).show(); });
            
        findViewById(R.id.ftdPlusBtn).setOnClickListener(v -> {
            if (ftdStepAmountRow.getVisibility() == View.VISIBLE) { ftdStepAmount.requestFocus(); return; }
            int cur = ftdToday == null ? 0 : ftdTotalCount(ftdToday);
            ftdStepAmount.setHint((cur + 1) + " নম্বর এফটিডি কত টাকার?");
            ftdStepAmountRow.setVisibility(View.VISIBLE);
            ftdStepAmount.requestFocus();
        });
        // Batch feature #5: FTD quick amount — tap korlei amount bosbe.
        findViewById(R.id.ftdQuick100).setOnClickListener(v -> { if (ftdStepAmount != null) ftdStepAmount.setText("100"); });
        findViewById(R.id.ftdQuick200).setOnClickListener(v -> { if (ftdStepAmount != null) ftdStepAmount.setText("200"); });
        findViewById(R.id.ftdQuick500).setOnClickListener(v -> { if (ftdStepAmount != null) ftdStepAmount.setText("500"); });
        findViewById(R.id.ftdStepAddBtn).setOnClickListener(v -> {
            String a = txt(ftdStepAmount);
            double amt;
            try { amt = Double.parseDouble(a); } catch (Exception e) { amt = -1; }
            if (amt <= 0) { Toast.makeText(this, "ঠিক টাকা দিন", Toast.LENGTH_SHORT).show(); return; }
            addReportEntry("FTD", new String[]{dhakaDate(), "1", fmtAmount(amt)}, () -> {
                ftdStepAmount.setText("");
                ftdStepAmountRow.setVisibility(View.GONE);
                loadDayStats();
            });
        });
        findViewById(R.id.ftdMinusBtn).setOnClickListener(v -> {
            REntry last = null;
            if (ftdToday != null) for (REntry e : ftdToday) if (last == null || e.sourceOrder > last.sourceOrder || (e.sourceOrder == last.sourceOrder && e.sheetRow > last.sheetRow)) last = e;
            if (last == null) { Toast.makeText(this, "আজ কোনো এফটিডি নেই", Toast.LENGTH_SHORT).show(); return; }
            // Batch fix #17: konta bad jacche seta age dekhano — sesh FTD-r taka soho.
            REntry delFtd = last;
            new AlertDialog.Builder(MainActivity.this).setTitle("বাদ দেবেন?").setMessage("শেষ এফটিডি: ৳" + cellE(delFtd, 2) + " — বাদ দেবেন?").setPositiveButton("হ্যাঁ, বাদ দিন", (d, w) -> deleteReportEntry("FTD", delFtd)).setNegativeButton("না", null).show();
        });
        // v4.18: manual Invalid entry removed.
            findViewById(R.id.probAddBtn).setOnClickListener(v -> {
            String t = txt(probText);
            if (t.isEmpty()) { Toast.makeText(this, "সমস্যা লিখুন", Toast.LENGTH_SHORT).show(); return; }
            addReportEntry("Problems", new String[]{dhakaDate(), t}, () -> probText.setText(""));
        });
        findViewById(R.id.generateBtn).setOnClickListener(v -> generateReport());
        // Batch feature #8: report sorasori share — chooser theke WhatsApp/Telegram +
        // group/person user beche nebe (kono nirdisto ID-te sorasori pathano noy).
        findViewById(R.id.shareBtn).setOnClickListener(v -> {
            String text = reportOutput == null || reportOutput.getText() == null ? "" : reportOutput.getText().toString().trim();
            if (text.isEmpty()) { Toast.makeText(this, "আগে রিপোর্ট বানান, তারপর শেয়ার করুন", Toast.LENGTH_SHORT).show(); return; }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Report kothay pathaben?"));
        });
        findViewById(R.id.copyBtn).setOnClickListener(v -> {
            String t = reportOutput.getText() == null ? "" : reportOutput.getText().toString();
            if (t.isEmpty()) { Toast.makeText(this, "আগে রিপোর্ট বানান", Toast.LENGTH_SHORT).show(); return; }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Daily Report", t));
            Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show();
        });

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter(new RowAdapter.OnSave() {
            public void onSave(Row row, MaterialButton btn) {
            // v4.24: button disable/spinner noy — tap-er sathe sathe row + tile UI-te update,
            // network (sheet + log) background-e cholbe; double-tap guard-e ekoi row-e ektai flight.
            String optKey = row.row + "|" + (row.number == null ? "" : row.number.trim());
            if (savingRows.contains(optKey)) return;
            savingRows.add(optKey);
            String optNow = (row.status == null ? "" : row.status) + "|" + (row.valid == null ? "" : row.valid);
            String optOld = lastStatusMap.get(row.number == null ? "" : row.number.trim());
            boolean optChanged = !optNow.equals(optOld);
            boolean optClear = (row.status == null || row.status.isEmpty()) && (row.valid == null || row.valid.isEmpty());
            // v5.6 (35-rows fix): ager diner call — Save-e ajker log-e uthbe, status na bodleo.
            final boolean isPrevDay = row.prevDay;
            optChanged = optChanged || (isPrevDay && !optClear);
            // Batch fix #4: Not Connected + Valid Yes guide onujayi osombhob — save atkabe
            // na, sudhu sotorko korbe (agent jate bhul kore na fele).
            if ("not connected".equals(row.status) && "Yes".equals(row.valid))
                Toast.makeText(MainActivity.this, "সতর্কতা: Not Connected-এর সাথে Valid Yes মেলে না — ঠিক আছে তো?", Toast.LENGTH_LONG).show();
            if (optChanged && !optClear && optimisticTileKeys.add(optKey)) applyLocalSaveToTiles(row.status, row.valid);
            // v5.4 (user correction — v5.3 bhul porechilo): Save-e row sathe sathe called
            // tab-e jabe aar sekhanei thakbe — kono number bad jabe na. savedStatus ekhanei
            // notun value-e bosano hoy, jate row-ta pending theke sore; moveCalledToTop-e
            // called-er upore bosbe. Clear-save hole ulto: row-ta abar pending-e.
            String optQ = searchBox.getText() == null ? "" : searchBox.getText().toString();
            if (optClear) { row.savedStatus = ""; row.prevDay = false; adapter.removeCalledRow(row, optQ); }
            else if (optChanged) { row.savedStatus = row.status == null ? "" : row.status; row.prevDay = false; adapter.moveCalledToTop(row, optQ); }
            else adapter.filter(optQ);
            // v4.33: row-ta screen-e jemon ache, baki hero-o sathe sathe temon (save/clear duitotei).
            refreshRemainingHero();
            // v4.32 + v4.41: exit-proof save — network thread shuru howar AGEI save intent-ti
            // disk-e commit() diye joma (apply async — kill hole harate pare). Thread safal hole
            // ei entry sorbe; fail hole drain/worker pathabe; majhpothe app morleo joma thakbe.
            // v4.41: tap-er sathei load-er somoy resolve kora numbers sheet ID stamp kora hoy —
            // thread na cholleo (tap-er pori app bondho hole) worker ei entry pathate parbe.
            // Thread pore nijer fresh resolve diye abar stamp korbe. Stamp na howa entry
            // (load kokhono hoyni) worker skip korbe, in-app drain pathabe.
            final long saveTs = System.currentTimeMillis();
            final String saveDate = dhakaDate();
            final boolean preQueuedLog = optChanged;
            final String preDialNum = RowAdapter.dialNumber(row.number);
            queueOutboxWithTs(row, saveTs, true);
            if (lastResolvedNumbersId != null && !lastResolvedNumbersId.isEmpty())
                stampOutboxNumbersId(agentId, row.number, saveTs, lastResolvedNumbersId);
            if (optChanged) {
                if (optClear) queuePendingWithTs("clear", saveDate, preDialNum, "", "", saveTs, true);
                else queuePendingWithTs("log", saveDate, preDialNum, row.status == null ? "" : row.status, row.valid == null ? "" : row.valid, saveTs, true);
            }
            enqueueSaveDrainWorker();
            new Thread(() -> {
                String key = row.number == null ? "" : row.number.trim();
                String now = (row.status == null ? "" : row.status) + "|" + (row.valid == null ? "" : row.valid);
                String old = lastStatusMap.get(key);
                boolean isClear = (row.status == null || row.status.isEmpty()) && (row.valid == null || row.valid.isEmpty());
                boolean changed = !now.equals(old) || (isPrevDay && !isClear);
                String dialNum = RowAdapter.dialNumber(row.number);
                try {
                    tryRefreshConfig();
                    String numbersIdForSave = resolveNumbersIdForAgent(agentId);
                    if (numbersIdForSave != null && !numbersIdForSave.isEmpty()) lastResolvedNumbersId = numbersIdForSave;
                    stampOutboxNumbersId(agentId, row.number, saveTs, numbersIdForSave);
                    SheetsClient.updateRow(token, numbersIdForSave, agentId, row);
                    // v4.32: main update land koreche — pre-queue kora outbox entry-ti sorbe.
                    removeOutboxEntry(agentId, row.number, saveTs);
                    String logError = null;
                    boolean logAppended = false;
                    boolean logCleared = false;
                    if (changed) {
                        try {
                            ensureStatusLogSheet();
                            // v4.32: log target stamp — worker sudhu stored ID-tei pathabe, andaj noy.
                            // Clear entry-te stamp noy (chain-wide delete worker-er kaj noy — in-app drain korbe).
                            if (!isClear) stampPendingLogTarget(agentId, row.number, saveTs, statusLogSheetId, statusLogTab);
                            if (isClear) {
                                // v4.20 clear-undo + v4.28: switch diner sob chain sheet theke muchbe.
                                if (agentId.equals(statusLogTab)) deleteLogRowsEverywhere(dhakaDate(), dialNum, statusLogSheetId);
                                else SheetsClient.deleteLogRowsForDateNumber(token, statusLogSheetId, statusLogTab, dhakaDate(), dialNum);
                                purgePendingFor(dhakaDate(), dialNum, "log");
                                // v4.32: ei save-er pre-queue clear entry-ti sudhu ts-match-e sorbe
                                // (purge "clear" ager failed save-er entry-o muchto).
                                removePendingEntry(agentId, row.number, saveTs);
                                lastStatusMap.put(key, now);
                                logCleared = true;
                            } else {
                                SheetsClient.appendReportRow(token, statusLogSheetId, statusLogTab, new String[]{dhakaDate(), dialNum, row.status == null ? "" : row.status, row.valid == null ? "" : row.valid, dhakaTimeFrom(saveTs)});
                                lastStatusMap.put(key, now);
                                logAppended = true;
                                purgePendingFor(dhakaDate(), dialNum, "clear");
                                // v4.32: ei save-er pre-queue log entry-ti sudhu ts-match-e sorbe
                                // (purge "log" ager failed save-er entry-o muchto).
                                removePendingEntry(agentId, row.number, saveTs);
                                SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                                if (p.getString("log_anchor_date_" + agentId, null) == null)
                                    p.edit().putString("log_anchor_date_" + agentId, dhakaDate()).apply();
                            }
                        } catch (Exception le) {
                            logError = le.getMessage(); maybeInvalidateLogCache(le);
                            // v4.32: log entry tap-er somoyei pre-queue kora ache — abar queue
                            // korle duplicate hobe. Drain/worker pre-queue kora entry pathabe.
                            lastStatusMap.put(key, now);
                        }
                    } else if (preQueuedLog) {
                        // v4.32: UI-te changed mone holeo thread-e changed noy (sheet-e age thekei
                        // ei value) — pre-queue kora entry jhule thakle pore bhul row append hoto.
                        // Sudhu ei save-er entry sorbe (ts-match); ager pending entry drain-e jabe.
                        removePendingEntry(agentId, row.number, saveTs);
                    }
                    String finalLogError = logError;
                    boolean finalLogAppended = logAppended;
                    boolean finalLogCleared = logCleared;
                    runOnUiThread(() -> {
                        authRetryUsed = false;
                        savingRows.remove(optKey); optimisticTileKeys.remove(optKey);
                        markRowCleanInSnapshot(row); DraftStore.clear(MainActivity.this, row.number); refreshQueuedCounter();
                        btn.setEnabled(true); btn.setText("সেভ করুন");
                        Toast.makeText(MainActivity.this, "রো " + row.row + " শিটে আপডেট হয়েছে" + (finalLogError != null ? " (লগ পরে সিঙ্ক হবে)" : (finalLogCleared ? " (আজকের লগ রো মুছে গেছে)" : "")), Toast.LENGTH_LONG).show();
                        adapter.filter(searchBox.getText() == null ? "" : searchBox.getText().toString());
                        refreshRemainingHero();
                        if (finalLogAppended) { refreshInvalidTileAfterSave(); }
                        else if (finalLogCleared) loadDayStats();
                        drainOutboxAsync(); drainPendingLogsAsync();
                    });
                } catch (Exception e) {
                    if (isAuthError(e) && !authRetryUsed && refreshTokenSync()) {
                        authRetryUsed = true;
                        runOnUiThread(() -> { savingRows.remove(optKey); btn.setEnabled(true); btn.setText("সেভ করুন"); btn.performClick(); });
                    } else {
                        // v4.32: tap-er somoyei outbox + pending-log commit() diye joma ache —
                        // abar queue korle duplicate hobe. Worker + drain pore pathabe.
                        runOnUiThread(() -> {
                            savingRows.remove(optKey);
                            btn.setEnabled(true); btn.setText("সেভ করুন");
                            Toast.makeText(MainActivity.this, "সেভ হয়নি — পরে আবার চেষ্টা হবে (জমা আছে): " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }
            }).start();
            }
            // Batch fix #3: clear-save (status+valid dutoi khali) asolei ajker log muchbe
            // kina — thread-er changed-gonona hubuhu miliye (lastStatusMap-er sathe tulona).
            public boolean needsClearConfirm(Row row) {
                if (row == null) return false;
                boolean clear = (row.status == null || row.status.isEmpty()) && (row.valid == null || row.valid.isEmpty());
                if (!clear) return false;
                String key = row.number == null ? "" : row.number.trim();
                String old = lastStatusMap.get(key);
                String now = (row.status == null ? "" : row.status) + "|" + (row.valid == null ? "" : row.valid);
                return !now.equals(old);
            }
        });
        recycler.setAdapter(adapter);
        adapter.setOnRemind(row -> showReminderDialog(row));
        setTabUI();

        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { if (adapter != null) adapter.filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        // Batch fix #13: search clear-✕.
        View searchClearBtn = findViewById(R.id.searchClearBtn);
        if (searchClearBtn != null) searchClearBtn.setOnClickListener(v -> { if (searchBox != null) searchBox.setText(""); });

        GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
        if (acct != null) {
            setAvatar(acct.getEmail());
            fetchToken(acct);
        } else {
            showLogin();
        }
    }

    private void setAvatar(String email) {
        if (avatarView == null) return;
        if (email != null && !email.isEmpty()) avatarView.setText(email.substring(0, 1).toUpperCase());
        else avatarView.setText("S");
    }

    @Override public void onBackPressed() { if (websiteView != null && websiteView.getVisibility() == View.VISIBLE && websiteWeb != null && websiteWeb.canGoBack()) { websiteWeb.goBack(); } else { super.onBackPressed(); } } @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (intent != null) pendingReminderNumber = intent.getStringExtra(ReminderReceiver.EXTRA_NUMBER); maybeOpenPendingReminder(); } private void showLogin() {
        rowsLoaded = false;
        loginView.setVisibility(View.VISIBLE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.GONE);
        guideView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.GONE); websiteView.setVisibility(View.GONE);
    }
    // Batch fix #7: login/Agent ID screen-e atke thakleo update check cholbe (age sudhu
    // main view-e cholto); fetch-e token lage na, tai login/agent branch-e token shorto nei.
    @Override protected void onResume() { super.onResume(); if (mainView != null && mainView.getVisibility() == View.VISIBLE && token != null) { checkForUpdate(); refreshReminderList(); refreshQueuedCounter(); if (currentSection == 1) maybeAutoRefreshList(); } else if ((loginView != null && loginView.getVisibility() == View.VISIBLE) || (agentView != null && agentView.getVisibility() == View.VISIBLE)) { checkForUpdate(); } } private void showMain() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
        applySection(currentSection);
        // Batch feature #10b: diner hisab-er alarm bosano (idempotent).
        DaySummaryReceiver.schedule(this);
        // Batch feature #9: sheet theke reminder restore (phone bodol/reinstall-er por).
        new Thread(() -> {
            int n = ReminderSheetSync.restore(token, agentId, MainActivity.this);
            if (n > 0) runOnUiThread(() -> refreshReminderList());
        }).start();
        maybeOpenPendingReminder();
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.GONE);
        guideView.setVisibility(View.GONE); websiteView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.VISIBLE);
        setAgentSubtitle();
        loadDayStats();
        refreshReminderList();
        checkForUpdate();
    }
    /** Niche menu: 0 = Dashboard (date + sob hisab), 1 = Home (number list). */
    private static class StatusLogTarget { String sheetId; String tab; boolean shared; StatusLogTarget(String s, String t, boolean sh) { sheetId = s; tab = t; shared = sh; } } private static String cell(String[] r, int i) { return (r != null && i < r.length && r[i] != null) ? r[i] : ""; } private static String logRowKey(String[] r) { return cell(r, 0) + "|" + cell(r, 1) + "|" + cell(r, 2) + "|" + cell(r, 3); } private StatusLogTarget resolveLegacyStatusLogTarget() {
        // v4.19: registry-te legacy ID thakle setai age (shared log-er vitore; main sheet-e noy).
        String[] reg = registryEntryForAgent();
        if (reg != null && reg[1] != null && !reg[1].isEmpty()) return new StatusLogTarget(reg[1], "Status Log", false);
        String legacyId = resolveSharedId("status_log_sheet_id_" + agentId, null, "Telesales Status Log - " + agentId);
        if (legacyId == null) return null;
        backfillRegistry(null, legacyId);
        return new StatusLogTarget(legacyId, "Status Log", false);
    }

    /** v4.19 continuous migration: protibar login-e (prothom resolve-e) legacy file-er
     *  je row shared tab-e nei, seta dedup kore copy hobe — purono version legacy-te
     *  likhleo porer login-e chole asbe. Legacy khali ba na thakle kichu hobe na.
     *  Migration fail hole shared tab-er kaj atkabe na (caller catch kore). */
    private void migrateLegacyStatusLogToShared(String sharedId) throws Exception {
        StatusLogTarget legacy = resolveLegacyStatusLogTarget();
        if (legacy == null || sharedId.equals(legacy.sheetId)) return;
        List<String[]> legacyRows = SheetsClient.getReportRows(token, legacy.sheetId, "Status Log");
        List<String[]> sharedRows = SheetsClient.getReportRows(token, sharedId, agentId);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] r : sharedRows) seen.add(logRowKey(r));
        List<String[]> missing = new ArrayList<>();
        for (String[] r : legacyRows) {
            if (r == null || r.length == 0) continue;
            String[] four = new String[]{cell(r, 0), cell(r, 1), cell(r, 2), cell(r, 3)};
            if (!seen.contains(logRowKey(four))) missing.add(four);
        }
        SheetsClient.appendReportRows(token, sharedId, agentId, missing);
    }

    private StatusLogTarget resolveStatusLogTargetForDate(String date) throws Exception {
        // v4.28 cache: (agent + resolved write sheet) ekbar confirm hole protibar tab check noy.
        String writeId = writeSheetFor(currentConfig(), "STATUS_LOG", date);
        if (writeId == null) writeId = SHARED_STATUS_LOG_ID;
        if (agentId != null && agentId.equals(sharedLogCacheAgent) && writeId.equals(sharedLogCacheId)) {
            return new StatusLogTarget(writeId, agentId, true);
        }
        try { SheetsClient.ensureSheetTab(token, writeId, agentId); }
        catch (Exception e) {
            StatusLogTarget legacy = resolveLegacyStatusLogTarget();
            if (legacy != null) return legacy;
            throw e;
        }
        try { migrateLegacyStatusLogToShared(writeId); } catch (Exception ignored) { }
        sharedLogCacheAgent = agentId; sharedLogCacheId = writeId;
        return new StatusLogTarget(writeId, agentId, true);
    }

    /** v4.19: shared log cache batil — porer resolve abar tab ache kina jachai korbe
     *  (append "tab missing" hole ekhan theke daka hoy). */
    private void invalidateSharedLogCache() { sharedLogCacheAgent = null; sharedLogCacheId = null; }

    private void updateMenuState(int active) { MaterialButton[] btns = new MaterialButton[]{(MaterialButton) findViewById(R.id.menuDashboardBtn), (MaterialButton) findViewById(R.id.menuHomeBtn), (MaterialButton) findViewById(R.id.menuReportBtn), (MaterialButton) findViewById(R.id.menuWebsiteBtn), (MaterialButton) findViewById(R.id.menuGuideBtn)}; int dim = Color.parseColor("#9AA3C7"); for (int i = 0; i < btns.length; i++) { MaterialButton b = btns[i]; if (b == null) continue; boolean on = (i == active); b.setBackgroundResource(on ? R.drawable.btn_gradient : R.drawable.glass_field); b.setTextColor(on ? Color.WHITE : dim); b.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL); b.setIconTint(ColorStateList.valueOf(on ? Color.WHITE : dim)); } } private void applySection(int sec) {
        currentSection = sec;
        dashboardView.setVisibility(sec == 0 ? View.VISIBLE : View.GONE);
        listContent.setVisibility(sec == 1 ? View.VISIBLE : View.GONE); reportView.setVisibility(View.GONE); guideView.setVisibility(View.GONE); websiteView.setVisibility(View.GONE); mainView.setVisibility(View.VISIBLE); bottomMenuBar.setVisibility(View.VISIBLE);
        updateMenuState(sec);
        findViewById(R.id.menuHomeBtn).setBackgroundResource(sec == 1 ? R.drawable.btn_gradient : R.drawable.glass_field);
        // v4.20: list-e dhukle called tab-er date purono hole taja hisab ana.
        if (sec == 1 && adapter != null && selectedDate != null && !selectedDate.equals(adapter.getCalledDate())) loadDayStats();
    }
    private void showAgent() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.VISIBLE);
        adminView.setVisibility(View.GONE);
        guideView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.GONE); websiteView.setVisibility(View.GONE);
        agentError.setText("");
    }
    /** Admin Panel: sudhu admin Gmail-e login korle khulbe, Agent ID lagbe na. */
    private void showAdmin() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.VISIBLE);
        guideView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.GONE); websiteView.setVisibility(View.GONE);
        adminStatus.setText("");
        showAdminSection("NUMBER");
        refreshTestSheetStatus();
        refreshAdminSheetSection();
        refreshTeamsSection();
    }
    private void setAgentSubtitle() {
        if (agentSubtitle != null && agentId != null) agentSubtitle.setText("এজেন্ট: " + agentId);
    }
    /** Admin: paste kora number-gulo target Agent-er tab-er seshe, paste kora order-e jog kore.
     *  Agertheke thaka number ba paste-er vitore duplicate thakle skip hobe (abar boshbe na). */
    private void submitAdminNumbers() {
        String target = txt(adminAgentInput);
        String pasted = adminNumbersInput.getText() == null ? "" : adminNumbersInput.getText().toString();
        if (target.isEmpty()) { adminStatus.setText("এজেন্ট আইডি দিন"); return; }
        if (pasted.trim().isEmpty()) { adminStatus.setText("নাম্বার পেস্ট করুন"); return; }
        if (token == null) { showLogin(); return; }
        adminStatus.setText("যোগ করা হচ্ছে...");
        new Thread(() -> {
            try {
                tryRefreshConfig();
                List<String> tabs;
                try { tabs = SheetsClient.getTabs(token, currentNumbersId()); }
                catch (Exception ex) { if (isAuthError(ex) && refreshTokenSync()) tabs = SheetsClient.getTabs(token, currentNumbersId()); else throw ex; }
                String match = null;
                for (String t : tabs) if (t.equals(target)) { match = t; break; }
                if (match == null) for (String t : tabs) if (t.equalsIgnoreCase(target)) { match = t; break; }
                if (match == null) throw new Exception("Ei Agent ID-te kono list pai ni — ID ta abar miliye dekhun");
                String finalMatch = match;
                java.util.Set<String> existing = new java.util.HashSet<>();
                for (Row r : SheetsClient.getRows(token, currentNumbersId(), finalMatch)) {
                    String n = normalizeAdminNumber(r.number);
                    if (validAdminNumber(n)) existing.add(n);
                }
                java.util.List<String> toAdd = new java.util.ArrayList<>();
                java.util.Set<String> seen = new java.util.HashSet<>();
                int dup = 0, bad = 0;
                for (String part : pasted.split("[\\n,;\\s]+")) {
                    if (part.isEmpty()) continue;
                    String n = normalizeAdminNumber(part);
                    if (!validAdminNumber(n)) { bad++; continue; }
                    if (existing.contains(n) || !seen.add(n)) { dup++; continue; }
                    toAdd.add(n);
                }
                if (toAdd.isEmpty()) throw new Exception("নতুন কোনো নাম্বার পাইনি" + (dup > 0 ? " (" + dup + "টা ডুপ্লিকেট বাদ)" : "") + (bad > 0 ? " (" + bad + "টা ঠিক নাম্বার না)" : ""));
                java.util.List<String> finalToAdd = toAdd; int dupFinal = dup, badFinal = bad;
                runOnUiThread(() -> {
                    // Batch fix #15: live list-e jawar age confirm — bhul paste atkano jabe.
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("লাইভ লিস্টে যাবে")
                        .setMessage("লাইভ লিস্টে " + finalToAdd.size() + "টা নাম্বার যাবে (" + finalMatch + ")" + (dupFinal > 0 ? " — " + dupFinal + "টা ডুপ্লিকেট বাদ যাবে" : "") + (badFinal > 0 ? " — " + badFinal + "টা ঠিক নাম্বার না, বাদ যাবে" : "") + "। ঠিক আছে?")
                        .setPositiveButton("হ্যাঁ, যোগ করুন", (d, w) -> doAppendAdminNumbers(finalMatch, finalToAdd, dupFinal, badFinal))
                        .setNegativeButton("না", null)
                        .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> adminStatus.setText("হয়নি: " + e.getMessage()));
            }
        }).start();
    }
    /** Batch fix #15: confirm-er por asol append — alada method, jate dialog-er positive
     *  theke daka jay (thread-er bhetor dialog atkano jay na). */
    private void doAppendAdminNumbers(String match, java.util.List<String> toAdd, int dup, int bad) {
        if (token == null) { showLogin(); return; }
        adminStatus.setText("যোগ করা হচ্ছে...");
        new Thread(() -> {
            try {
                SheetsClient.appendNumbers(token, currentNumbersId(), match, toAdd);
                int added = toAdd.size();
                runOnUiThread(() -> {
                    adminStatus.setText(added + "টা নাম্বার " + match + "-এর লিস্টে যোগ হয়েছে" + (dup > 0 ? " (" + dup + "টা ডুপ্লিকেট বাদ)" : "") + (bad > 0 ? " (" + bad + "টা ঠিক নাম্বার না)" : ""));
                    adminNumbersInput.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> adminStatus.setText("হয়নি: " + e.getMessage()));
            }
        }).start();
    }
    /** v4.30: Admin Panel tab — sudhu ekta section dekhabe, bakigulo lukabe. Default: Number. */
    private void showAdminSection(String section) {
        if (adminSectionNumber != null) adminSectionNumber.setVisibility("NUMBER".equals(section) ? View.VISIBLE : View.GONE);
        if (adminSectionTeam != null) adminSectionTeam.setVisibility("TEAM".equals(section) ? View.VISIBLE : View.GONE);
        if (adminSectionSheet != null) adminSectionSheet.setVisibility("SHEET".equals(section) ? View.VISIBLE : View.GONE);
        if (adminSectionAdmins != null) adminSectionAdmins.setVisibility("ADMINS".equals(section) ? View.VISIBLE : View.GONE);
        if (adminSectionDashboard != null) adminSectionDashboard.setVisibility("DASHBOARD".equals(section) ? View.VISIBLE : View.GONE);
        MaterialButton[] tabs = new MaterialButton[]{(MaterialButton) findViewById(R.id.adminTabNumber), (MaterialButton) findViewById(R.id.adminTabTeam), (MaterialButton) findViewById(R.id.adminTabSheet), (MaterialButton) findViewById(R.id.adminTabAdmins), (MaterialButton) findViewById(R.id.adminTabDashboard)};
        String[] keys = new String[]{"NUMBER", "TEAM", "SHEET", "ADMINS", "DASHBOARD"};
        int dim = Color.parseColor("#9AA3C7");
        for (int i = 0; i < tabs.length; i++) {
            MaterialButton b = tabs[i];
            if (b == null) continue;
            boolean on = keys[i].equals(section);
            b.setBackgroundResource(on ? R.drawable.btn_gradient : R.drawable.glass_field);
            b.setTextColor(on ? Color.WHITE : dim);
            b.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }
    /** Batch feature #4: admin team dashboard — live sheet-er sob agent-er ajker hisab.
     *  Daily Reports file theke Generate kora hisab (1 read) + numbers sheet theke baki
     *  (batchGet, 1 call). Generate na kora agent-er Total/Connected/FTD "—" thakbe;
     *  junk tab (Sheet... / Copy of... diye shuru tab) bad deoa hoy. */
    private void loadTeamDashboard() {
        if (token == null) { showLogin(); return; }
        TextView st = findViewById(R.id.teamDashStatus);
        LinearLayout list = findViewById(R.id.teamDashList);
        if (st != null) st.setText("লোড হচ্ছে...");
        if (list != null) list.removeAllViews();
        new Thread(() -> {
            try {
                String today = dhakaDate();
                String dailyId = writeSheetFor(currentConfig(), "DAILY", today);
                if (dailyId == null) dailyId = DAILY_REPORTS_ID;
                java.util.Map<String, String[]> byAgent = new java.util.HashMap<>();
                try {
                    List<String[]> drows = SheetsClient.getReportRows(token, dailyId, "Reports");
                    String want = displayDay(today);
                    if (drows != null) for (String[] r : drows) {
                        if (r != null && r.length >= 7 && want.equals(cell(r, 0))) byAgent.put(cell(r, 1), r);
                    }
                } catch (Exception ignored) { /* Daily na pele sudhu baki dekhabe */ }
                String numId = currentNumbersId();
                List<String> tabs = SheetsClient.getTabs(token, numId);
                List<String> agents = new ArrayList<>();
                for (String t : tabs) {
                    if (t == null) continue;
                    if (t.startsWith("Sheet") || t.startsWith("Copy of ")) continue;
                    agents.add(t);
                }
                java.util.Map<String, Integer> remainByAgent = new java.util.HashMap<>();
                try {
                    List<String> ranges = new ArrayList<>();
                    for (String a : agents) ranges.add("'" + a.replace("'", "''") + "'!A6:F");
                    java.util.Map<String, List<String[]>> vals = SheetsClient.getValuesBatch(token, numId, ranges);
                    for (String a : agents) {
                        List<String[]> rows = vals == null ? null : vals.get(a);
                        int rem = 0;
                        if (rows != null) for (String[] r : rows) {
                            String s = r != null && r.length > 1 && r[1] != null ? r[1].trim() : "";
                            if (s.isEmpty()) rem++;
                        }
                        remainByAgent.put(a, rem);
                    }
                } catch (Exception ignored) { /* baki na pele sudhu Daily hisab */ }
                List<String> lines = new ArrayList<>();
                int totCall = 0, totConn = 0, totFtd = 0, totRem = 0; double totAmt = 0;
                for (String a : agents) {
                    String[] d = byAgent.get(a);
                    Integer rem = remainByAgent.get(a);
                    String remS = rem == null ? "?" : String.valueOf(rem);
                    if (rem != null) totRem += rem;
                    if (d == null) {
                        lines.add(a + " — Generate হয়নি • বাকি " + remS);
                    } else {
                        int tc = 0, cc = 0, fc = 0; double fa = 0;
                        try { tc = Integer.parseInt(cell(d, 2)); } catch (Exception ignored) {}
                        try { cc = Integer.parseInt(cell(d, 3)); } catch (Exception ignored) {}
                        try { fc = Integer.parseInt(cell(d, 5)); } catch (Exception ignored) {}
                        try { fa = Double.parseDouble(cell(d, 6)); } catch (Exception ignored) {}
                        totCall += tc; totConn += cc; totFtd += fc; totAmt += fa;
                        lines.add(a + " — মোট " + tc + " • Connected " + cc + " • এফটিডি " + fc + " (৳" + fmtAmount(fa) + ") • বাকি " + remS);
                    }
                }
                final String head = "আজ (" + displayDay(today) + ") — " + agents.size() + " জন এজেন্ট • মোট কল " + totCall + " • Connected " + totConn + " • এফটিডি " + totFtd + " (৳" + fmtAmount(totAmt) + ") • বাকি " + totRem;
                runOnUiThread(() -> {
                    if (st != null) st.setText(head);
                    if (list != null) {
                        for (String ln : lines) {
                            TextView t = new TextView(MainActivity.this);
                            t.setText(ln);
                            t.setTextSize(13);
                            t.setTextColor(Color.WHITE);
                            t.setPadding(0, 8, 0, 8);
                            list.addView(t);
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (st != null) st.setText("লোড হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.30: save kora test sheet-er ID (telesales_prefs — Agent ID-er motoi pattern). */
    private String savedTestSheetId() {
        return getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).getString("test_sheet_id", null);
    }
    /** v4.30: test sheet status line — title thakle title, na thakle ID, kichu na thakle khali-barta. */
    private void refreshTestSheetStatus() {
        if (testSheetStatus == null) return;
        SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
        String id = p.getString("test_sheet_id", null);
        if (id == null || id.isEmpty()) { testSheetStatus.setText("কোনো টেস্ট শিট সেভ করা হয়নি — আগে লিংক/আইডি দিয়ে সেভ করুন"); return; }
        String title = p.getString("test_sheet_title", null);
        testSheetStatus.setText("টেস্ট শিট: " + (title != null && !title.isEmpty() ? title : id));
    }
    /** v4.30: test sheet link/ID jachai kore save — Drive files.get + spreadsheet mimeType (Sheet Settings-er motoi). Fail hole ager save bodlabe na. */
    private void saveTestSheet() {
        String id = SheetsClient.extractSpreadsheetId(txt(testSheetInput));
        if (id.isEmpty()) { if (testSheetStatus != null) testSheetStatus.setText("লিংক বা আইডি দিন"); return; }
        if (token == null) { showLogin(); return; }
        if (testSheetStatus != null) testSheetStatus.setText("যাচাই হচ্ছে...");
        new Thread(() -> {
            try {
                String[] info = SheetsClient.getSpreadsheetName(token, id);
                if (!"application/vnd.google-apps.spreadsheet".equals(info[1])) throw new Exception("Eta spreadsheet noy — sheet-er link/ID din");
                getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().putString("test_sheet_id", id).putString("test_sheet_title", info[0]).apply();
                runOnUiThread(() -> { if (testSheetStatus != null) testSheetStatus.setText("টেস্ট শিট সেভ হয়েছে: " + info[0]); if (testSheetInput != null) testSheetInput.setText(""); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (testSheetStatus != null) testSheetStatus.setText("যাচাই হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.30: test sheet-e number bosan — legacy admin logic-i (normalize/dedupe/append A6:A RAW), sudhu target save kora test sheet, live sheet-e noy. */
    private void submitTestSheetNumbers() {
        String sheetId = savedTestSheetId();
        if (sheetId == null || sheetId.isEmpty()) { if (testSheetNumbersStatus != null) testSheetNumbersStatus.setText("আগে টেস্ট শিটের লিংক/আইডি বসান"); return; }
        String target = txt(testSheetTabInput);
        String pasted = testSheetNumbersInput.getText() == null ? "" : testSheetNumbersInput.getText().toString();
        if (target.isEmpty()) { if (testSheetNumbersStatus != null) testSheetNumbersStatus.setText("ট্যাব / এজেন্ট আইডি দিন"); return; }
        if (pasted.trim().isEmpty()) { if (testSheetNumbersStatus != null) testSheetNumbersStatus.setText("নাম্বার পেস্ট করুন"); return; }
        if (token == null) { showLogin(); return; }
        if (testSheetNumbersStatus != null) testSheetNumbersStatus.setText("যোগ করা হচ্ছে...");
        new Thread(() -> {
            try {
                List<String> tabs = SheetsClient.getTabs(token, sheetId);
                String match = null;
                for (String t : tabs) if (t.equals(target)) { match = t; break; }
                if (match == null) for (String t : tabs) if (t.equalsIgnoreCase(target)) { match = t; break; }
                if (match == null) throw new Exception("Ei test sheet-e '" + target + "' tab pai ni");
                String finalMatch = match;
                java.util.Set<String> existing = new java.util.HashSet<>();
                for (Row r : SheetsClient.getRows(token, sheetId, finalMatch)) {
                    String n = normalizeAdminNumber(r.number);
                    if (validAdminNumber(n)) existing.add(n);
                }
                java.util.List<String> toAdd = new java.util.ArrayList<>();
                java.util.Set<String> seen = new java.util.HashSet<>();
                int dup = 0, bad = 0;
                for (String part : pasted.split("[\\n,;\\s]+")) {
                    if (part.isEmpty()) continue;
                    String n = normalizeAdminNumber(part);
                    if (!validAdminNumber(n)) { bad++; continue; }
                    if (existing.contains(n) || !seen.add(n)) { dup++; continue; }
                    toAdd.add(n);
                }
                if (toAdd.isEmpty()) throw new Exception("নতুন কোনো নাম্বার পাইনি" + (dup > 0 ? " (" + dup + "টা ডুপ্লিকেট বাদ)" : "") + (bad > 0 ? " (" + bad + "টা ঠিক নাম্বার না)" : ""));
                SheetsClient.appendNumbers(token, sheetId, finalMatch, toAdd);
                int added = toAdd.size();
                int dupFinal = dup, badFinal = bad;
                runOnUiThread(() -> {
                    if (testSheetNumbersStatus != null) testSheetNumbersStatus.setText(added + "টা নাম্বার " + finalMatch + "-এর লিস্টে যোগ হয়েছে (টেস্ট শিট)" + (dupFinal > 0 ? " (" + dupFinal + "টা ডুপ্লিকেট বাদ)" : "") + (badFinal > 0 ? " (" + badFinal + "টা ঠিক নাম্বার না)" : ""));
                    if (testSheetNumbersInput != null) testSheetNumbersInput.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (testSheetNumbersStatus != null) testSheetNumbersStatus.setText("হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.28 admin gate: config file-er malik (kokhono bodlay na) BA bortoman numbers
     *  sheet-er malik. Lookup fail hole fail-closed. (v4.29: + admin Gmail + Admins tab — checkIsAdminWithEmail dekho.) */
    private boolean checkIsAdmin() {
        try { if (SheetsClient.isFileOwnedByMe(token, CONFIG_FILE_ID)) return true; } catch (Exception ignored) {}
        try { return SheetsClient.isFileOwnedByMe(token, currentNumbersId()); } catch (Exception ignored) { return false; }
    }
    /** v4.29 admin gate (purno): malik-check (ager moto) BA user-er bola admin Gmail BA Admins tab-er list. Fail-closed. */
    private boolean checkIsAdminWithEmail(String email) {
        try { if (checkIsAdmin()) return true; } catch (Exception ignored) {}
        if (email != null && ADMIN_EMAIL.equalsIgnoreCase(email.trim())) return true;
        return isListedAdmin(email);
    }
    private boolean isListedAdmin(String email) {
        if (email == null) return false;
        java.util.Set<String> c = adminEmailsCache;
        if (c == null) return false;
        return c.contains(email.trim().toLowerCase(Locale.US));
    }
    /** v4.29: Admins tab pora — SUDHU background thread theke dakbe. Sofol hole cache bosbe, fail hole ager cache thakbe (kokhono crash noy). */
    private void refreshAdminsCacheSync() {
        if (token == null) return;
        try {
            List<String[]> rows = SheetsClient.getReportRows(token, CONFIG_FILE_ID, "Admins");
            java.util.Set<String> s = new java.util.HashSet<>();
            for (String[] r : rows) {
                if (r == null || r.length < 1 || r[0] == null) continue;
                String e = r[0].trim().toLowerCase(Locale.US);
                if (!e.isEmpty()) s.add(e);
            }
            adminEmailsCache = s;
        } catch (Exception ignored) { /* tab na thakle / fail hole ager cache (ba khali) cholbe */ }
    }
    /** v4.29: Teams tab pora — fail hole khali list (Team-03 agent-der kichu bodlabe na). */
    private List<TeamRow> getTeamRowsQuiet() {
        List<TeamRow> out = new ArrayList<>();
        if (token == null) return out;
        try {
            List<String[]> rows = SheetsClient.getReportRows(token, CONFIG_FILE_ID, "Teams");
            for (int i = 0; i < rows.size(); i++) {
                String[] r = rows.get(i);
                if (r == null || r.length < 2) continue;
                String n = r[0] == null ? "" : r[0].trim();
                String s = r[1] == null ? "" : r[1].trim();
                if (n.isEmpty() || s.isEmpty()) continue;
                out.add(new TeamRow(i + 2, n, s, r.length > 2 && r[2] != null ? r[2] : "", r.length > 3 && r[3] != null ? r[3].trim() : ""));
            }
        } catch (Exception ignored) {}
        return out;
    }
    /** v4.29: agent-er numbers source — age Active team sheet-e tab khunja (Teams tab order-e, error hole skip), na pele ager config NUMBERS resolution. */
    private String resolveNumbersIdForAgent(String agent) {
        if (agent != null && token != null) {
            for (TeamRow t : getTeamRowsQuiet()) {
                if (!"Yes".equalsIgnoreCase(t.active)) continue;
                try {
                    List<String> tabs = SheetsClient.getTabs(token, t.sheetId);
                    for (String tb : tabs) if (agent.equals(tb)) return t.sheetId;
                } catch (Exception ignored) { /* ei sheet-e error — porer team dekha hobe */ }
            }
        }
        return currentNumbersId();
    }
    /** v4.29: submitAgent-er jonno — Active team sheet-e tab khunja (exact, na pele case-insensitive). Na pele null. */
    private String findAgentTabInActiveTeams(String agent) {
        if (agent == null || token == null) return null;
        for (TeamRow t : getTeamRowsQuiet()) {
            if (!"Yes".equalsIgnoreCase(t.active)) continue;
            try {
                List<String> tabs = SheetsClient.getTabs(token, t.sheetId);
                for (String tb : tabs) if (agent.equals(tb)) return tb;
                for (String tb : tabs) if (agent.equalsIgnoreCase(tb)) return tb;
            } catch (Exception ignored) {}
        }
        return null;
    }
    /** v4.29: Admin Panel theke arekjon admin add — Admins tab-e [Email, AddedBy, AddedDate]. Age theke thakle skip. */
    private void submitAddAdmin() {
        String email = txt(adminNewAdminInput).trim().toLowerCase(Locale.US);
        if (email.isEmpty() || !email.contains("@")) { if (adminAdminStatus != null) adminAdminStatus.setText("ঠিক জিমেইল দিন (@ সহ)"); return; }
        if (token == null) { showLogin(); return; }
        if (adminAdminStatus != null) adminAdminStatus.setText("যোগ করা হচ্ছে...");
        new Thread(() -> {
            try {
                SheetsClient.ensureAdminsTab(token, CONFIG_FILE_ID);
                List<String[]> rows = SheetsClient.getReportRows(token, CONFIG_FILE_ID, "Admins");
                for (String[] r : rows) {
                    if (r != null && r.length > 0 && email.equals(r[0] == null ? "" : r[0].trim().toLowerCase(Locale.US))) {
                        runOnUiThread(() -> { if (adminAdminStatus != null) adminAdminStatus.setText("এই জিমেইল আগে থেকেই অ্যাডমিন আছে"); });
                        return;
                    }
                }
                SharedPreferences prefs = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                String by = prefs.getString("current_email", "");
                SheetsClient.appendReportRow(token, CONFIG_FILE_ID, "Admins", new String[]{email, by == null ? "" : by, dhakaDate()});
                refreshAdminsCacheSync();
                runOnUiThread(() -> { if (adminAdminStatus != null) adminAdminStatus.setText(email + " অ্যাডমিন হিসেবে যোগ হয়েছে"); if (adminNewAdminInput != null) adminNewAdminInput.setText(""); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (adminAdminStatus != null) adminAdminStatus.setText("হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.29: team name theke sheet title — "Team-04" likhle "Telesales (Team-04)", "04" likhle eki title. */
    private static String teamTitleFor(String name) {
        String n = name.trim();
        if (n.toLowerCase(Locale.US).startsWith("team-")) return "Telesales (" + n + ")";
        return "Telesales (Team-" + n + ")";
    }
    private static boolean badTeamOrAgentName(String s) {
        if (s == null || s.isEmpty() || s.length() > 50) return true;
        for (char c : s.toCharArray()) if (c == '[' || c == ']' || c == ':' || c == '*' || c == '?' || c == '/' || c == '\\' || c < 0x20) return true;
        return false;
    }
    /** v4.29: notun team-er sheet banano — LIVE BODLABE NA: sheet alada thakbe, Teams tab-e Active='No' hoye thakbe, pore admin chaile sobai-er jonno add korte parbe. */
    private void createTeamSheet() {
        String name = txt(teamNameInput);
        String agentsRaw = teamAgentsInput.getText() == null ? "" : teamAgentsInput.getText().toString();
        if (name.isEmpty()) { if (teamStatus != null) teamStatus.setText("টিমের নাম দিন"); return; }
        if (badTeamOrAgentName(name)) { if (teamStatus != null) teamStatus.setText("টিমের নামে এই চিহ্ন চলবে না: [ ] : * ? / \\"); return; }
        java.util.LinkedHashSet<String> agents = new java.util.LinkedHashSet<>();
        java.util.Set<String> lowerSeen = new java.util.HashSet<>();
        int badAgents = 0;
        for (String part : agentsRaw.split("[\\n,;]+")) {
            String a = part.trim();
            if (a.isEmpty()) continue;
            if (badTeamOrAgentName(a) || !lowerSeen.add(a.toLowerCase(Locale.US))) { badAgents++; continue; }
            agents.add(a);
        }
        if (agents.isEmpty()) { if (teamStatus != null) teamStatus.setText("অন্তত একটা এজেন্ট আইডি দিন (প্রতি লাইনে একটা)"); return; }
        if (token == null) { showLogin(); return; }
        if (teamStatus != null) teamStatus.setText("শিট বানানো হচ্ছে...");
        final int badAgentsFinal = badAgents;
        new Thread(() -> {
            try {
                for (TeamRow t : getTeamRowsQuiet()) {
                    if (name.equalsIgnoreCase(t.name)) throw new Exception("Ei team name-e age thekei ekta sheet ache");
                }
                // Header row 5: bortoman numbers sheet-er prothom tab theke, na pele ager fallback.
                String[] fallback = {"MOBILE NUMBER", "CALLING STATUS", "Valid Conversation", "1ST CALL REMARK", "2ND CALL REMAR", "Others number"};
                String[] header = fallback;
                try {
                    String cur = currentNumbersId();
                    List<String> curTabs = SheetsClient.getTabs(token, cur);
                    for (String tb : curTabs) {
                        if (tb == null || tb.isEmpty()) continue;
                        String[] h = SheetsClient.getHeaderRow5(token, cur, tb);
                        boolean any = false;
                        if (h != null) for (String c : h) if (c != null && !c.trim().isEmpty()) { any = true; break; }
                        if (any) { header = h; break; }
                    }
                } catch (Exception ignored) {}
                String title = teamTitleFor(name);
                String newId = SheetsClient.createTeamSpreadsheet(token, title, new ArrayList<>(agents), header);
                SheetsClient.ensureTeamsTab(token, CONFIG_FILE_ID);
                SheetsClient.appendReportRow(token, CONFIG_FILE_ID, "Teams", new String[]{name, newId, dhakaDate(), "No"});
                runOnUiThread(() -> { if (teamStatus != null) teamStatus.setText("'" + title + "' শিট তৈরি — " + agents.size() + "টা ট্যাব, এখন অ্যাক্টিভ নয় (সবার জন্য যোগ করলে চলবে)" + (badAgentsFinal > 0 ? " (" + badAgentsFinal + "টা এজেন্ট আইডি বাদ দেওয়া হয়েছে)" : "")); if (teamAgentsInput != null) teamAgentsInput.setText(""); refreshTeamsSection(); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (teamStatus != null) teamStatus.setText("শিট বানানো হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.29: banano team-gulo dekhano — prottek row-te nam, Active, toiri tarikh, tab-gulo + add/bondho + number bosan button. */
    private void refreshTeamsSection() {
        if (token == null || teamListContainer == null) return;
        new Thread(() -> {
            List<TeamRow> rows = getTeamRowsQuiet();
            // prottek team-er tab list (network — admin screen-ei, agent load-e noy)
            final java.util.Map<String, List<String>> tabsBySheet = new java.util.HashMap<>();
            for (TeamRow t : rows) {
                try { tabsBySheet.put(t.sheetId, SheetsClient.getTabs(token, t.sheetId)); } catch (Exception ignored) { tabsBySheet.put(t.sheetId, new ArrayList<>()); }
            }
            teamRowsCache = rows;
            runOnUiThread(() -> {
                teamListContainer.removeAllViews();
                if (rows.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("এখনো কোনো টিম শিট বানানো হয়নি");
                    empty.setTextColor(Color.parseColor("#9AA3C7"));
                    empty.setTextSize(13);
                    teamListContainer.addView(empty);
                    return;
                }
                for (TeamRow t : rows) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(0, 16, 0, 16);
                    TextView title = new TextView(this);
                    title.setText(t.name + " — " + ("Yes".equalsIgnoreCase(t.active) ? "সবার জন্য চালু" : "বন্ধ") + " (" + t.created + ")");
                    title.setTextColor(Color.WHITE);
                    title.setTextSize(15);
                    title.setTypeface(null, Typeface.BOLD);
                    row.addView(title);
                    TextView tabs = new TextView(this);
                    List<String> tb = tabsBySheet.get(t.sheetId);
                    StringBuilder tbText = new StringBuilder("Tabs: ");
                    if (tb == null || tb.isEmpty()) tbText.append("kono tab pai ni");
                    else for (int k = 0; k < tb.size(); k++) { if (k > 0) tbText.append(", "); tbText.append(tb.get(k)); }
                    tabs.setText(tbText.toString());
                    tabs.setTextColor(Color.parseColor("#9AA3C7"));
                    tabs.setTextSize(12);
                    row.addView(tabs);
                    LinearLayout btnRow = new LinearLayout(this);
                    btnRow.setOrientation(LinearLayout.HORIZONTAL);
                    MaterialButton toggle = new MaterialButton(this);
                    toggle.setText("Yes".equalsIgnoreCase(t.active) ? "বন্ধ করুন" : "সবার জন্য যোগ করুন");
                    toggle.setTextColor(Color.WHITE);
                    toggle.setBackgroundResource("Yes".equalsIgnoreCase(t.active) ? R.drawable.glass_field : R.drawable.btn_gradient);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    toggle.setLayoutParams(lp);
                    toggle.setOnClickListener(v -> setTeamActive(t, !"Yes".equalsIgnoreCase(t.active)));
                    btnRow.addView(toggle);
                    MaterialButton nums = new MaterialButton(this);
                    nums.setText("নাম্বার বসান");
                    nums.setTextColor(Color.WHITE);
                    nums.setBackgroundResource(R.drawable.glass_field);
                    LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    lp2.setMargins(10, 0, 0, 0);
                    nums.setLayoutParams(lp2);
                    nums.setOnClickListener(v -> selectTeam(t));
                    btnRow.addView(nums);
                    row.addView(btnRow);
                    teamListContainer.addView(row);
                }
            });
        }).start();
    }
    /** v4.29: team chalu/bondho — Teams tab-er Active ghor bodlano. SheetConfig NUMBERS switch-e kono hat noy. */
    private void setTeamActive(TeamRow t, boolean active) {
        if (token == null) { showLogin(); return; }
        if (teamStatus != null) teamStatus.setText("আপডেট হচ্ছে...");
        new Thread(() -> {
            try {
                SheetsClient.updateCells(token, CONFIG_FILE_ID, "Teams!D" + t.sheetRow, new String[][]{{active ? "Yes" : "No"}});
                runOnUiThread(() -> { if (teamStatus != null) teamStatus.setText("'" + t.name + "' " + (active ? "সবার জন্য চালু হয়েছে" : "বন্ধ হয়েছে")); refreshTeamsSection(); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (teamStatus != null) teamStatus.setText("হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.29: team select — tarpor tab nam + number paste kore oi team-er sheet-e jog hobe (live sheet-e noy). */
    private void selectTeam(TeamRow t) {
        selectedTeam = t;
        if (teamSelectedLabel != null) teamSelectedLabel.setText("নির্বাচিত টিম: " + t.name + " — ট্যাব (এজেন্ট আইডি) লিখুন, নাম্বার পেস্ট করুন");
        if (teamTabInput != null) teamTabInput.requestFocus();
    }
    /** v4.29: select kora team-er sheet-er ekta tab-e number jog — ager admin number-logic-i (normalize/dedupe/append A6:A RAW). Live sheet-e kichu jabe na. */
    private void submitTeamNumbers() {
        TeamRow team = selectedTeam;
        if (team == null) { if (teamNumbersStatus != null) teamNumbersStatus.setText("আগে টিম লিস্ট থেকে একটা টিমের 'নাম্বার বসান' চাপুন"); return; }
        String target = txt(teamTabInput);
        String pasted = teamNumbersInput.getText() == null ? "" : teamNumbersInput.getText().toString();
        if (target.isEmpty()) { if (teamNumbersStatus != null) teamNumbersStatus.setText("ট্যাব / এজেন্ট আইডি দিন"); return; }
        if (pasted.trim().isEmpty()) { if (teamNumbersStatus != null) teamNumbersStatus.setText("নাম্বার পেস্ট করুন"); return; }
        if (token == null) { showLogin(); return; }
        if (teamNumbersStatus != null) teamNumbersStatus.setText("যোগ করা হচ্ছে...");
        new Thread(() -> {
            try {
                List<String> tabs = SheetsClient.getTabs(token, team.sheetId);
                String match = null;
                for (String tb : tabs) if (target.equals(tb)) { match = tb; break; }
                if (match == null) for (String tb : tabs) if (target.equalsIgnoreCase(tb)) { match = tb; break; }
                if (match == null) throw new Exception("Ei team-e '" + target + "' tab pai ni");
                String finalMatch = match;
                java.util.Set<String> existing = new java.util.HashSet<>();
                for (Row r : SheetsClient.getRows(token, team.sheetId, finalMatch)) {
                    String n = normalizeAdminNumber(r.number);
                    if (validAdminNumber(n)) existing.add(n);
                }
                java.util.List<String> toAdd = new java.util.ArrayList<>();
                java.util.Set<String> seen = new java.util.HashSet<>();
                int dup = 0, bad = 0;
                for (String part : pasted.split("[\\n,;\\s]+")) {
                    if (part.isEmpty()) continue;
                    String n = normalizeAdminNumber(part);
                    if (!validAdminNumber(n)) { bad++; continue; }
                    if (existing.contains(n) || !seen.add(n)) { dup++; continue; }
                    toAdd.add(n);
                }
                if (toAdd.isEmpty()) throw new Exception("নতুন কোনো নাম্বার পাইনি" + (dup > 0 ? " (" + dup + "টা ডুপ্লিকেট বাদ)" : "") + (bad > 0 ? " (" + bad + "টা ঠিক নাম্বার না)" : ""));
                SheetsClient.appendNumbers(token, team.sheetId, finalMatch, toAdd);
                int added = toAdd.size(), dupFinal = dup, badFinal = bad;
                runOnUiThread(() -> {
                    if (teamNumbersStatus != null) teamNumbersStatus.setText(added + "টা নাম্বার " + team.name + " / " + finalMatch + "-এ যোগ হয়েছে" + (dupFinal > 0 ? " (" + dupFinal + "টা ডুপ্লিকেট বাদ)" : "") + (badFinal > 0 ? " (" + badFinal + "টা ঠিক নাম্বার না)" : ""));
                    if (teamNumbersInput != null) teamNumbersInput.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (teamNumbersStatus != null) teamNumbersStatus.setText("হয়নি: " + e.getMessage()); });
            }
        }).start();
    }

    /** v4.31: call reminder — somoy dekhano (Dhaka time). */
    private static String fmtReminderTime(long epoch) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
            return f.format(new Date(epoch));
        } catch (Exception e) { return ""; }
    }

    private static long atDhaka(int dayOffset, int hour, int minute) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        c.add(Calendar.DAY_OF_YEAR, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Dashboard-er "Mone koriye deoa" section — bhabishyoter reminder + prottek-tar cancel (X).
     *  Somoy par hoye gele alarm cancel + storage theke bad (fired/stale kichui jhulbe na). */
    private void refreshReminderList() {
        if (reminderListContainer == null) return;
        long now = System.currentTimeMillis();
        List<ReminderStore.Item> items = ReminderStore.list(this);
        List<ReminderStore.Item> future = new ArrayList<>();
        for (ReminderStore.Item it : items) {
            if (it.time > now) future.add(it);
            else { ReminderReceiver.cancel(this, it.number); ReminderStore.remove(this, it.number); final String staleNum = it.number; new Thread(() -> ReminderSheetSync.remove(token, agentId, staleNum)).start(); }
        }
        future.sort((a, b) -> Long.compare(a.time, b.time));
        reminderListContainer.removeAllViews();
        if (reminderEmptyText != null) reminderEmptyText.setVisibility(future.isEmpty() ? View.VISIBLE : View.GONE);
        if (reminderSection != null) reminderSection.setVisibility(future.isEmpty() ? View.GONE : View.VISIBLE);
        for (ReminderStore.Item it : future) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            float dens = getResources().getDisplayMetrics().density;
            row.setPadding(0, (int) (8 * dens), 0, (int) (8 * dens));
            ImageView bell = new ImageView(this);
            LinearLayout.LayoutParams bellLp = new LinearLayout.LayoutParams((int) (18 * dens), (int) (18 * dens));
            bellLp.setMarginEnd((int) (8 * dens));
            bell.setLayoutParams(bellLp);
            bell.setImageResource(R.drawable.ic_bell);
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(lp);
            tv.setText(fmtReminderTime(it.time) + " — " + it.number);
            tv.setTextSize(13);
            tv.setTextColor(Color.WHITE);
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextSize(18);
            x.setTextColor(Color.WHITE);
            x.setPadding(24, 12, 24, 12);
            final String num = it.number;
            x.setOnClickListener(v -> {
                ReminderReceiver.cancel(MainActivity.this, num);
                ReminderStore.remove(MainActivity.this, num);
                // Batch feature #9: sheet backup thekeo bad.
                new Thread(() -> ReminderSheetSync.remove(token, agentId, num)).start();
                refreshReminderList();
            });
            TypedValue sel = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, sel, true);
            row.setBackgroundResource(sel.resourceId);
            row.setOnClickListener(v -> openReminderNumber(num));
            row.addView(bell);
            row.addView(tv);
            row.addView(x);
            reminderListContainer.addView(row);
        }
    }

    /** v4.34: reminder-e tap -> call list-e oi number-er row khule dekhano (Call + Copy okhan theke).
     *  Pending hole কল বাকি tab, call hoye thakle কল করা হয়েছে tab; search-e number bosano hoy
     *  jate row-ti ekai chokhe pore. List-e na pele toast diye janano hoy. */
    /** Batch feature #1: tile tap -> call list, oi type-e filter kora (called tab). */
    private void openFilteredList(String type) {
        applySection(1);
        if (adapter == null) return;
        adapter.setTab(1);
        setTabUI();
        adapter.setTypeFilter(type);
        if (searchBox != null) searchBox.setText("");
    }
    private void openReminderNumber(String number) {
        if (adapter == null) return;
        String dial = RowAdapter.dialNumber(number);
        int tab = 0;
        List<Row> all = adapter.getAll();
        if (all != null) {
            for (Row r : all) {
                if (r != null && RowAdapter.dialNumber(r.number).equals(dial)) {
                    tab = (r.status == null || r.status.isEmpty()) ? 0 : 1;
                    break;
                }
            }
        }
        applySection(1);
        adapter.setTab(tab); setTabUI();
        if (searchBox != null) searchBox.setText(dial);
        boolean loadingCalled = (tab == 1 && selectedDate != null && !selectedDate.equals(adapter.getCalledDate()));
        if (adapter.getItemCount() == 0 && !loadingCalled) {
            Toast.makeText(this, "এই নাম্বার আজকের লিস্টে পেলাম না — হয়তো অন্য দিন কল হয়েছে বা লিস্ট থেকে সরানো হয়েছে", Toast.LENGTH_LONG).show();
        }
    }

    /** Row-er "Mone koriye dao" — somoy bachun: aj bikel 4ta / agamikal sokal 10ta / nijer somoy. */
    /** Batch fix #20: notification tap-e asa number-er row khola — sudhu tokhon jokhon
     *  main view khola ar row load hoyeche; na hole pending rekhe pore khulbe (bhul toast noy). */
    private void maybeOpenPendingReminder() {
        String n = pendingReminderNumber;
        if (n == null && getIntent() != null) n = getIntent().getStringExtra(ReminderReceiver.EXTRA_NUMBER);
        if (n == null || n.isEmpty()) return;
        if (adapter == null || !rowsLoaded || mainView == null || mainView.getVisibility() != View.VISIBLE) { pendingReminderNumber = n; return; }
        pendingReminderNumber = null;
        if (getIntent() != null) getIntent().removeExtra(ReminderReceiver.EXTRA_NUMBER);
        openReminderNumber(n);
    }
    private void showReminderDialog(Row row) {
        if (row == null) return;
        final String number = RowAdapter.dialNumber(row.number);
        if (number.isEmpty()) { Toast.makeText(this, "নাম্বার পাইনি", Toast.LENGTH_SHORT).show(); return; }
        final String[] opts = {"আজ বিকেল ৪টা", "আগামীকাল সকাল ১০টা", "নিজের সময়"};
        new AlertDialog.Builder(this)
                .setTitle("কখন মনে করিয়ে দেবো?")
                .setItems(opts, (d, which) -> {
                    if (which == 0) scheduleReminder(number, atDhaka(0, 16, 0));
                    else if (which == 1) scheduleReminder(number, atDhaka(1, 10, 0));
                    else pickCustomReminderTime(number);
                })
                .show();
    }

    private void pickCustomReminderTime(final String number) {
        // v4.36: যেকোনো তারিখ বাছা যাবে (আগে শুধু আজ/আগামীকাল ছিল)।
        // অতীতের তারিখ পিকারেই বন্ধ; অতীতের সময় হলে scheduleReminder আগের মতোই আটকাবে।
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        DatePickerDialog dp = new DatePickerDialog(MainActivity.this, (view, year, month, day) -> {
            Calendar t = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
            new TimePickerDialog(MainActivity.this, (v2, hour, minute) -> {
                Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
                picked.set(year, month, day, hour, minute, 0);
                picked.set(Calendar.MILLISECOND, 0);
                scheduleReminder(number, picked.getTimeInMillis());
            }, t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE), false).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        Calendar todayStart = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        dp.getDatePicker().setMinDate(todayStart.getTimeInMillis());
        dp.show();
    }

    private void scheduleReminder(String number, long epoch) {
        if (epoch <= System.currentTimeMillis()) {
            Toast.makeText(this, "এই সময় পার হয়ে গেছে — ভবিষ্যতের সময় দিন", Toast.LENGTH_SHORT).show();
            return;
        }
        // Android 13+: notification onumoti age (prothom reminder-e chawa hobe)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminderNumber = number;
            pendingReminderTime = epoch;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY_REMINDER);
            return;
        }
        scheduleReminderNow(number, epoch);
    }

    private void scheduleReminderNow(String number, long epoch) {
        // Android 12+: thik somoyer alarm onumoti na thakle settings khulbe — chupchap inexact noy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Toast.makeText(this, "ঠিক সময়ে রিমাইন্ডারের জন্য অ্যালার্ম অনুমতি দিন", Toast.LENGTH_LONG).show();
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
                return;
            }
        }
        ReminderReceiver.cancel(this, number); // purono thakle bodle jabe (ek number-e ektai)
        ReminderStore.save(this, number, epoch);
        ReminderReceiver.schedule(this, number, epoch);
        // Batch feature #9: sheet-eo backup (phone bodol/reinstall-eo thakbe).
        new Thread(() -> ReminderSheetSync.save(token, agentId, number, epoch)).start();
        refreshReminderList();
        Toast.makeText(this, "মনে করিয়ে দেওয়া হলো: " + fmtReminderTime(epoch), Toast.LENGTH_SHORT).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFY_REMINDER) {
            String num = pendingReminderNumber;
            long t = pendingReminderTime;
            pendingReminderNumber = null;
            pendingReminderTime = 0;
            if (num != null && t > 0 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scheduleReminderNow(num, t);
            } else {
                Toast.makeText(this, "নোটিফিকেশন অনুমতি না দিলে রিমাইন্ডার আসবে না", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String storeLabel(String store) {
        if ("NUMBERS".equals(store)) return "নাম্বার শিট";
        if ("STATUS_LOG".equals(store)) return "স্ট্যাটাস লগ শিট";
        if ("REPORTS".equals(store)) return "রিপোর্ট শিট";
        return "ডেইলি রিপোর্ট শিট";
    }
    /** v4.28: Admin Panel khulle config seed (admin-only) + bortoman sheet title + history dekhano. */
    private void refreshAdminSheetSection() {
        if (token == null) return;
        new Thread(() -> {
            try {
                try {
                    try { SheetsClient.ensureAdminsTab(token, CONFIG_FILE_ID); } catch (Exception ignored) {}
                    try { SheetsClient.ensureTeamsTab(token, CONFIG_FILE_ID); } catch (Exception ignored) {}
                    SheetsClient.ensureConfigTab(token, CONFIG_FILE_ID);
                    List<String[]> rows = SheetsClient.getConfigRows(token, CONFIG_FILE_ID);
                    if (rows == null || rows.isEmpty()) {
                        SheetsClient.appendConfigRow(token, CONFIG_FILE_ID, new String[]{"NUMBERS", SHEET_ID, "", "seed"});
                        SheetsClient.appendConfigRow(token, CONFIG_FILE_ID, new String[]{"STATUS_LOG", SHARED_STATUS_LOG_ID, "", "seed"});
                        SheetsClient.appendConfigRow(token, CONFIG_FILE_ID, new String[]{"REPORTS", SHARED_REPORT_ID, "", "seed"});
                        SheetsClient.appendConfigRow(token, CONFIG_FILE_ID, new String[]{"DAILY", DAILY_REPORTS_ID, "", "seed"});
                    }
                } catch (Exception ignored) { /* seed na holeo bortoman title dekhano jabe */ }
                tryRefreshConfig();
                Map<String, List<ConfigEntry>> cfg = currentConfig();
                String[] stores = {"NUMBERS", "STATUS_LOG", "REPORTS", "DAILY"};
                final String[] titles = new String[4];
                final StringBuilder hist = new StringBuilder();
                for (int si = 0; si < stores.length; si++) {
                    String store = stores[si];
                    String cur = writeSheetFor(cfg, store, dhakaDate());
                    String curTitle = "জানা যায়নি"; boolean known = false;
                    try { String[] info = SheetsClient.getSpreadsheetName(token, cur); if (info[0] != null && !info[0].isEmpty()) { curTitle = info[0]; known = true; } } catch (Exception ignored) {}
                    titles[si] = curTitle;
                    List<ConfigEntry> chain = cfg.get(store);
                    if (chain != null) for (ConfigEntry e : chain) {
                        hist.append(storeLabel(store)).append(": ").append(e.fromDate.isEmpty() ? "আগে থেকে" : e.fromDate + " থেকে").append(" — ").append(e.sheetId.equals(cur) && known ? curTitle : e.sheetId).append("\n");
                    }
                }
                runOnUiThread(() -> {
                    if (adminCurNumbers != null) adminCurNumbers.setText("নাম্বার শিট: " + titles[0]);
                    if (adminCurLog != null) adminCurLog.setText("স্ট্যাটাস লগ শিট: " + titles[1]);
                    if (adminCurReports != null) adminCurReports.setText("রিপোর্ট শিট: " + titles[2]);
                    if (adminCurDaily != null) adminCurDaily.setText("ডেইলি রিপোর্ট শিট: " + titles[3]);
                    if (adminSheetHistory != null) adminSheetHistory.setText(hist.length() == 0 ? "" : hist.toString().trim());
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (adminSheetStatus != null) adminSheetStatus.setText("শিট সেটিংস লোড হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.28: paste kora link/ID jachai — sheet ache kina, spreadsheet kina, koyta tab. */
    private void verifySheetForStore(String store, TextInputEditText input) {
        String id = SheetsClient.extractSpreadsheetId(txt(input));
        if (id.isEmpty()) { if (adminSheetStatus != null) adminSheetStatus.setText("আইডি বা লিংক দিন"); return; }
        if (adminSheetStatus != null) adminSheetStatus.setText("যাচাই হচ্ছে...");
        new Thread(() -> {
            try {
                String[] info = SheetsClient.getSpreadsheetName(token, id);
                if (!"application/vnd.google-apps.spreadsheet".equals(info[1])) throw new Exception("এটা স্প্রেডশিট নয় — শিটের লিংক/আইডি দিন");
                List<String> tabs = SheetsClient.getTabs(token, id);
                VerifiedSheet v = new VerifiedSheet(id, info[0], tabs.size());
                verifiedSheets.put(store, v);
                runOnUiThread(() -> { if (adminSheetStatus != null) adminSheetStatus.setText("ঠিক আছে: " + info[0] + " (" + tabs.size() + "টা ট্যাব) — এবার বদলাও চাপুন"); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (adminSheetStatus != null) adminSheetStatus.setText("যাচাই হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.28: jachai kora sheet-e switch — confirm-er por config-e notun row, sathe sathe karjokor. */
    private void switchSheetForStore(String store, TextInputEditText input) {
        String id = SheetsClient.extractSpreadsheetId(txt(input));
        VerifiedSheet v = verifiedSheets.get(store);
        if (v == null || !v.id.equals(id)) { if (adminSheetStatus != null) adminSheetStatus.setText("আগে যাচাই চাপুন, তারপর বদলাও"); return; }
        // Batch fix #5: NUMBERS switch-e sudhu khali tab banano hoy (number row kopi hoy
        // na — copyNumbersTabs dekhun), tai "ager data dekha jabe" bola bhul chilo. Onno
        // store-e chain-read-e ager data sotti-i dekha jay, tai okhane ager lekha thakbe.
        String switchMsg = storeLabel(store) + " এখন থেকে '" + v.title + "' শিটে চলবে। "
                + ("NUMBERS".equals(store)
                        ? "নতুন শিটে শুধু ট্যাব বানানো হবে — আগের নাম্বার কপি হবে না, তাই লিস্ট প্রথমে খালি দেখাবে; পুরোনো শিটের ডাটা মুছবে না।"
                        : "আগের ডাটা দেখা যাবে, কিছু মুছবে না।");
        new AlertDialog.Builder(this)
                .setTitle("শিট বদলাবেন?")
                .setMessage(switchMsg)
                .setPositiveButton("বদলাও", (d, w) -> doSheetSwitch(store, v))
                .setNegativeButton("না", null)
                .show();
    }
    private void doSheetSwitch(String store, VerifiedSheet v) {
        if (adminSheetStatus != null) adminSheetStatus.setText("বদলানো হচ্ছে...");
        new Thread(() -> {
            try {
                String prevId = writeSheetFor(currentConfig(), store, dhakaDate());
                SheetsClient.appendConfigRow(token, CONFIG_FILE_ID, new String[]{store, v.id, dhakaDate(), "admin switch"});
                tryRefreshConfig();
                verifiedSheets.remove(store);
                String extra = "";
                if (prevId != null && !prevId.equals(v.id)) {
                    if ("NUMBERS".equals(store)) extra = copyNumbersTabs(prevId, v.id);
                    else if ("STATUS_LOG".equals(store)) extra = copyAgentTabs(prevId, v.id, true);
                    else if ("REPORTS".equals(store)) extra = copyAgentTabs(prevId, v.id, false);
                    else if ("DAILY".equals(store)) { try { SheetsClient.ensureDailyTab(token, v.id); extra = "Reports tab toiri ache"; } catch (Exception e) { extra = "Reports tab banano hoyni: " + e.getMessage(); } }
                }
                final String extraFinal = extra;
                runOnUiThread(() -> { if (adminSheetStatus != null) adminSheetStatus.setText("'" + v.title + "' চালু হয়েছে" + (extraFinal.isEmpty() ? "" : " — " + extraFinal)); refreshAdminSheetSection(); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (adminSheetStatus != null) adminSheetStatus.setText("বদলানো হয়নি: " + e.getMessage()); });
            }
        }).start();
    }
    /** v4.28: NUMBERS switch-er por ager numbers sheet-er sob tab notun sheet-e kopir chesta (header row 5-soho). */
    private String copyNumbersTabs(String prevId, String newId) {
        try {
            List<String> prevTabs = SheetsClient.getTabs(token, prevId);
            List<String> newTabs = SheetsClient.getTabs(token, newId);
            java.util.Set<String> have = new java.util.HashSet<>(newTabs);
            String[] fallback = {"MOBILE NUMBER", "CALLING STATUS", "Valid Conversation", "1ST CALL REMARK", "2ND CALL REMAR", "Others number"};
            int created = 0, existed = 0, failed = 0;
            for (String t : prevTabs) {
                if (t == null || t.isEmpty()) continue;
                if (have.contains(t)) { existed++; continue; }
                try {
                    String[] h = null;
                    try { h = SheetsClient.getHeaderRow5(token, prevId, t); } catch (Exception ignored) {}
                    if (h == null || h.length == 0) h = fallback;
                    SheetsClient.ensureNumbersTab(token, newId, t, h);
                    created++;
                } catch (Exception ignored) { failed++; }
            }
            return created + "টা ট্যাব বানানো হয়েছে, " + existed + "টা আগে থেকেই ছিল" + (failed > 0 ? ", " + failed + "টা হয়নি" : "");
        } catch (Exception e) {
            return "ট্যাব কপি হয়নি: " + e.getMessage();
        }
    }
    /** v4.28 parent fix: LOG/REPORTS switch-er por ager sheet-er agent tab-gulo notun sheet-e
     *  banano — Guide/Registry/SheetConfig bad diye (Registry kopi korle purono pin fire asto). */
    private String copyAgentTabs(String prevId, String newId, boolean isLog) {
        try {
            List<String> prevTabs = SheetsClient.getTabs(token, prevId);
            List<String> newTabs = SheetsClient.getTabs(token, newId);
            java.util.Set<String> have = new java.util.HashSet<>(newTabs);
            java.util.Set<String> skip = new java.util.HashSet<>(java.util.Arrays.asList("Guide", "Registry", "SheetConfig", "Reports"));
            int created = 0, existed = 0, failed = 0;
            for (String t : prevTabs) {
                if (t == null || t.isEmpty() || skip.contains(t)) continue;
                if (have.contains(t)) { existed++; continue; }
                try { if (isLog) SheetsClient.ensureSheetTab(token, newId, t); else SheetsClient.ensureReportTab(token, newId, t); created++; }
                catch (Exception ignored) { failed++; }
            }
            return created + "ta agent tab banano hoyeche, " + existed + "ta age thekei chilo" + (failed > 0 ? ", " + failed + "ta hoyni" : "");
        } catch (Exception e) {
            return "agent tab kopi hoyni: " + e.getMessage();
        }
    }
    /** Paste/sheet-er number-ke sheet-er niyome anbe: 0 na thakle 0 jog, +880/880 hole 0-rup. */
    private static String normalizeAdminNumber(String raw) {
        String s = raw.trim().replaceAll("[\\s\\-]", "");
        if (s.startsWith("+880")) s = "0" + s.substring(4);
        else if (s.startsWith("880") && s.length() == 13) s = "0" + s.substring(3);
        else if (!s.startsWith("0")) s = "0" + s;
        return s;
    }
    private static boolean validAdminNumber(String s) {
        return s.matches("0\\d{10}");
    }
    /** Agent ID jachai: sheet-e oi nam-e tab ache kina dekhbe, thakle save kore list khulbe. */
    private void submitAgent() {
        String id = txt(agentInput);
        if (id.isEmpty()) { agentError.setText("এজেন্ট আইডি লিখুন"); return; }
        if (token == null) { showLogin(); return; }
        agentError.setText("লিস্ট চেক করা হচ্ছে...");
        new Thread(() -> {
            try {
                tryRefreshConfig();
                List<String> tabs;
                try { tabs = SheetsClient.getTabs(token, currentNumbersId()); }
                catch (Exception ex) { if (isAuthError(ex) && refreshTokenSync()) tabs = SheetsClient.getTabs(token, currentNumbersId()); else throw ex; }
                String match = null;
                for (String t : tabs) if (t.equals(id)) { match = t; break; }
                if (match == null) for (String t : tabs) if (t.equalsIgnoreCase(id)) { match = t; break; }
                if (match == null) match = findAgentTabInActiveTeams(id);
                if (match == null) throw new Exception("Ei Agent ID-te kono list pai ni — ID ta abar miliye dekhun");
                String finalMatch = match;
                SharedPreferences prefs1 = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                prefs1.edit().putString("agent_id", finalMatch).putString("agent_email", prefs1.getString("current_email", null)).apply();
                runOnUiThread(() -> { agentId = finalMatch; reportSheetId = null; statusLogSheetId = null; setAgentSubtitle(); showMain(); loadRows(); });
            } catch (Exception e) {
                runOnUiThread(() -> agentError.setText("হয়নি: " + e.getMessage()));
            }
        }).start();
    }
    private void setTabUI() {
        boolean pending = adapter == null || adapter.getTab() == 0;
        findViewById(R.id.btnTabPending).setBackgroundResource(pending ? R.drawable.btn_gradient : R.drawable.glass_field);
        findViewById(R.id.btnTabDone).setBackgroundResource(pending ? R.drawable.glass_field : R.drawable.btn_gradient);
    }
    private void showReport() {
        if (token == null) { showLogin(); return; }
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.GONE);
        updateMenuState(2); reportView.setVisibility(View.VISIBLE); updateReportDateBtn(); updateReportEditState();
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.GONE);
        guideView.setVisibility(View.GONE); websiteView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.VISIBLE); findViewById(R.id.menuDashboardBtn).setBackgroundResource(R.drawable.glass_field); findViewById(R.id.menuHomeBtn).setBackgroundResource(R.drawable.glass_field);
        loadReportData();
    }

    /** User Guide khulbe: Bangla manual, niche menu theke. */
    private void showGuide() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.GONE);
        updateMenuState(4); guideView.setVisibility(View.VISIBLE); websiteView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.VISIBLE); findViewById(R.id.menuDashboardBtn).setBackgroundResource(R.drawable.glass_field); findViewById(R.id.menuHomeBtn).setBackgroundResource(R.drawable.glass_field);
    }

    private void fetchToken(GoogleSignInAccount acct) {
        loginError.setText("লগইন হচ্ছে...");
        new Thread(() -> {
            try {
                token = GoogleAuthUtil.getToken(this, acct.getAccount(), "oauth2:" + SHEET_SCOPE + " " + DRIVE_SCOPE);
                runOnUiThread(() -> {
                    setAvatar(acct.getEmail());
                    SharedPreferences prefs0 = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                    prefs0.edit().putString("current_email", acct.getEmail()).apply();
                    // Mul sheet-er malik login korle Agent ID chhara-i Admin Panel khulbe.
                    // Malik check Drive files.get ownedByMe diye hoy — kono Gmail code-e lekha nei.
                    // Lookup fail hole chupchap agent flow-e jabe (fail-closed, admin khulbe na).
                    new Thread(() -> {
                        tryRefreshConfig();
                        refreshAdminsCacheSync();
                        boolean owner = checkIsAdminWithEmail(acct.getEmail());
                        boolean isOwner = owner;
                        runOnUiThread(() -> {
                            if (isOwner) { showAdmin(); return; }
                            String saved = prefs0.getString("agent_id", null);
                            String savedEmail = prefs0.getString("agent_email", null);
                            // Agent ID sudhu tokhon-i reuse hobe jokhon seta EI Gmail-er — onno Gmail-e
                            // dhukle ager joner agent list ar khulbe na, agent screen asbe (mix-up fix).
                            if (saved != null && !saved.isEmpty() && savedEmail != null && acct.getEmail() != null && savedEmail.equalsIgnoreCase(acct.getEmail())) {
                                agentId = saved; reportSheetId = null; setAgentSubtitle(); showMain(); loadRows();
                            } else {
                                showAgent();
                            }
                        });
                    }).start();
                });
            } catch (UserRecoverableAuthException e) {
                runOnUiThread(() -> startActivityForResult(e.getIntent(), RC_AUTH));
            } catch (Exception e) {
                runOnUiThread(() -> { loginError.setText(loginFailText(e)); showLogin(); });
            }
        }).start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount acct = task.getResult(ApiException.class);
                fetchToken(acct);
            } catch (Exception e) {
                loginError.setText(loginFailText(e));
            }
        } else if (requestCode == RC_AUTH) {
            GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
            if (acct != null) fetchToken(acct); else showLogin();
        }
    }

    /** v4.42: login fail text — ApiException-er message "12500: "-er moto khali thakle pichoner ": " dekhabe na. */
    private static String loginFailText(Exception e) { String m = e == null ? null : e.getMessage(); if (m == null) m = ""; m = m.trim(); if (m.endsWith(":")) m = m.substring(0, m.length() - 1).trim(); return m.isEmpty() ? "লগইন হয়নি" : "লগইন হয়নি: " + m; }

    /** v4.41: loadRows / save thread j numbers sheet ID resolve koreche — tap-er
     *  somoy outbox entry-te stamp deoar jonno (thread na cholleo worker pathabe). */
    private String lastResolvedNumbersId = null;

    private void loadRows() {
        if (token == null) { showLogin(); return; }
        if (agentId == null) { showAgent(); return; }
        if (rowsLoading) return; // v4.19: choloman load thakle double load noy
        rowsLoading = true;
        final int seq = ++rowsSeq;
        statusText.setText("শিট থেকে লোড হচ্ছে...");
        new Thread(() -> {
            try {
                tryRefreshConfig();
                String resolvedNumbersId = resolveNumbersIdForAgent(agentId);
                if (resolvedNumbersId != null && !resolvedNumbersId.isEmpty()) lastResolvedNumbersId = resolvedNumbersId;
                List<Row> rows = SheetsClient.getRows(token, resolvedNumbersId, agentId);
                rowsLoading = false;
                runOnUiThread(() -> {
                    rowsLoading = false;
                    if (seq != rowsSeq) return; // purono response notun data-r upor boshbe na
                    authRetryUsed = false;
                    takeLoadedSnapshot(rows);
                    // v5.3: tab-er hishab + change-check — sheet-er value diye (draft diye noy).
                    // Draft apply howar AGEI savedStatus + lastStatusMap sheet value diye bosate hobe;
                    // na hole draft-kei 'age thekei save kora' mone kore Save-e log lekha hoto na,
                    // aar draft thaka row filter-e pending theke gayeb hoye jeto.
                    lastStatusMap.clear();
                    for (Row r : rows) { r.savedStatus = r.status == null ? "" : r.status; lastStatusMap.put(r.number == null ? "" : r.number.trim(), (r.status == null ? "" : r.status) + "|" + (r.valid == null ? "" : r.valid)); }
                    applyDrafts(rows, false); adapter.setRows(rows); statTotalNumDay.setText(String.valueOf(rows.size())); refreshRemainingHero();
                    refreshQueuedCounter();
                    adapter.filter(searchBox.getText() == null ? "" : searchBox.getText().toString());
                    rowsLoaded = true; maybeOpenPendingReminder();
                    statusText.setText("শিটের সাথে সংযুক্ত — " + rows.size() + "টি রো");
                    if (rows.isEmpty()) {
                        statusText.setGravity(Gravity.CENTER);
                        statusText.setTextSize(14);
                        android.view.ViewGroup.MarginLayoutParams slp = (android.view.ViewGroup.MarginLayoutParams) statusText.getLayoutParams();
                        slp.topMargin = (int) (48 * getResources().getDisplayMetrics().density);
                        statusText.setLayoutParams(slp);
                    } else {
                        statusText.setGravity(Gravity.START);
                        statusText.setTextSize(13);
                        android.view.ViewGroup.MarginLayoutParams slp = (android.view.ViewGroup.MarginLayoutParams) statusText.getLayoutParams();
                        slp.topMargin = 0;
                        statusText.setLayoutParams(slp);
                    }
                    loadDayStats();
                    drainOutboxAsync();
                });
            } catch (Exception e) {
                rowsLoading = false;
                if (isAuthError(e) && !authRetryUsed && refreshTokenSync()) { authRetryUsed = true; loadRows(); return; }
                runOnUiThread(() -> statusText.setText("লোড হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    /** Shared Report/Log file-er ID: age local, na thakle Drive-e nam diye khunja (peye gele local-e save).
     *  App ar website — duijon-e ektai file use korbe, tai app-er update website-e dekhabe. */
    private String resolveSharedId(String prefsKey, String legacyKey, String title) {
        SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
        String id = p.getString(prefsKey, null);
        if (id == null && legacyKey != null) id = p.getString(legacyKey, null);
        if (id == null) {
            id = SheetsClient.findSpreadsheetByName(token, title);
            if (id != null) p.edit().putString(prefsKey, id).apply();
        }
        return id;
    }

    private void ensureStatusLogSheet() throws Exception { ensureStatusLogSheetForDate(dhakaDate()); }
    private void ensureStatusLogSheetForDate(String date) throws Exception { StatusLogTarget t = resolveStatusLogTargetForDate(date); if (t != null) { statusLogSheetId = t.sheetId; statusLogTab = t.tab; return; } statusLogSheetId = SheetsClient.createStatusLogSpreadsheet(token, agentId); statusLogTab = "Status Log"; SharedPreferences p = getSharedPreferences("telesales_prefs", MODE_PRIVATE); p.edit().putString("status_log_sheet_id_" + agentId, statusLogSheetId).apply(); } private void ensureStatusLogSheetLegacyUnused() throws Exception {
        if (statusLogSheetId != null) return;
        SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
        statusLogSheetId = resolveSharedId("status_log_sheet_id_" + agentId, null, "Telesales Status Log - " + agentId);
        if (statusLogSheetId == null) {
            statusLogSheetId = SheetsClient.createStatusLogSpreadsheet(token, agentId);
            p.edit().putString("status_log_sheet_id_" + agentId, statusLogSheetId).apply();
        }
    }

    private static java.util.Date parseDay(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.parse(s);
    }
    private static String fmtDay(java.util.Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }
    private static String displayDay(String s) {
        try {
            SimpleDateFormat out = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            out.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
            return out.format(parseDay(s));
        } catch (Exception e) { return s; }
    }
    private static String addDays(String day, int n) {
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTime(parseDay(day));
            c.add(Calendar.DAY_OF_YEAR, n);
            return fmtDay(c.getTime());
        } catch (Exception e) { return day; }
    }
    private static long daysBetween(String from, String to) {
        try {
            return (parseDay(to).getTime() - parseDay(from).getTime()) / 86400000L;
        } catch (Exception e) { return 0; }
    }
    private static List<REntry> filterRange(List<REntry> rows, String from, String to) {
        List<REntry> out = new ArrayList<>();
        for (REntry r : rows) if (r.cells.length > 0 && r.cells[0].compareTo(from) >= 0 && r.cells[0].compareTo(to) <= 0) out.add(r);
        return out;
    }
    private void updateDateBtn() {
        dateBtn.setText(displayDay(selectedDate) + (selectedDate.equals(dhakaDate()) ? " • আজ" : ""));
    }
    private void shiftDay(int delta) {
        String nd = addDays(selectedDate, delta);
        if (nd.compareTo(dhakaDate()) > 0) return;
        selectedDate = nd; updateDateBtn(); debouncedLoadDayStats();
    }

    /** Nirbachito diner + oi diner 7-diner block-er hisab (log + report file theke). */
    private void loadDayStats() {
        if (token == null || agentId == null) return;
        String day = selectedDate;
        final int seq = ++statsSeq;
        new Thread(() -> {
            try {
                SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                tryRefreshConfig();
                List<REntry> logs;
                try { logs = readLogRowsForDate(day); }
                catch (Exception le) {
                    runOnUiThread(() -> {
                        if (weekSummary != null) weekSummary.setText("হিসাব লোড হয়নি: লগ ফাইল পাইনি — রিফ্রেশ করুন");
                        if (adapter != null) adapter.setCalledRows(null, day);
                    });
                    return;
                }
                // v4.28: report ekhon date-chain theke (switch diner dui sheet mile);
                // report file na pele agekar motoi khali list (dashboard-er log tile atkabe na).
                List<REntry> regs = new ArrayList<>(), ftds = new ArrayList<>();
                try {
                    ReportSplit rs = splitReportRows(readReportRowsForDate(day));
                    regs = rs.reg; ftds = rs.ftd;
                } catch (Exception ignored) { }
                int conn = 0, valid = 0, notConn = 0, totalCall = 0;
                for (REntry e : logs) if (day.equals(cellE(e, 0))) { totalCall++; if ("call connected".equals(cellE(e, 2))) conn++;
                    if ("Yes".equals(cellE(e, 3))) valid++;
                    if ("not connected".equals(cellE(e, 2))) notConn++;
                }
                int inv = countAutoInvalid(logs, day);
                int reg = regTotalCount(filterTodayE(regs, day));
                List<REntry> ftdToday = filterTodayE(ftds, day);
                int ftdN = ftdTotalCount(ftdToday);
                double ftdSum = 0;
                for (REntry e : ftdToday) { try { ftdSum += Double.parseDouble(cellE(e, 2)); } catch (Exception ignored) {} }
                String anchor = p.getString("log_anchor_date_" + agentId, null);
                String min = null;
                for (REntry e : logs) if (e.cells.length > 0 && (min == null || e.cells[0].compareTo(min) < 0)) min = e.cells[0];
                if (min != null) anchor = min;
                else if (anchor == null) anchor = day;
                long diff = daysBetween(anchor, day);
                long idx = diff >= 0 ? diff / 7 : 0;
                String blockStart = addDays(anchor, (int) (idx * 7));
                String blockEnd = addDays(blockStart, 6);
                int connW = 0;
                for (REntry e : filterRange(logs, blockStart, blockEnd)) if ("call connected".equals(cellE(e, 2))) connW++;
                int notConnW = 0;
                for (REntry e : filterRange(logs, blockStart, blockEnd)) if ("not connected".equals(cellE(e, 2))) notConnW++;
                List<REntry> ftdWeek = filterRange(ftds, blockStart, blockEnd);
                int ftdNW = ftdTotalCount(ftdWeek);
                double ftdSumW = 0;
                for (REntry e : ftdWeek) { try { ftdSumW += Double.parseDouble(cellE(e, 2)); } catch (Exception ignored) {} }
                // Batch fix #12: 7-din line-e Valid/Invalid/Registration-o thakbe.
                int validW = 0;
                for (REntry e : filterRange(logs, blockStart, blockEnd)) if ("Yes".equals(cellE(e, 3))) validW++;
                int invW = countAutoInvalidRange(logs, blockStart, blockEnd);
                int regW = regTotalCount(filterRange(regs, blockStart, blockEnd));
                String finalBlockStart = blockStart, finalBlockEnd = blockEnd;
                final boolean finalUsedLegacy = lastLogReadUsedLegacy;
                final boolean finalPartialFail = lastLogPartialFail || lastReportPartialFail;
                int finalConn = conn, finalValid = valid, finalInv = inv, finalReg = reg, finalFtdN = ftdN, finalConnW = connW, finalFtdNW = ftdNW, finalNotConn = notConn, finalNotConnW = notConnW, finalTotalCall = totalCall, finalValidW = validW, finalInvW = invW, finalRegW = regW;
                double finalFtdSum = ftdSum, finalFtdSumW = ftdSumW;
                runOnUiThread(() -> {
                    if (seq != statsSeq) return; // purono hisab notun diner upor boshbe na
                    statConnDay.setText(String.valueOf(finalConn)); statTotalCallDay.setText(String.valueOf(finalTotalCall));
                    lastTotalCallDay = finalTotalCall; targetStatsReady = true; refreshTargetCard();
                    statNotConnDay.setText(String.valueOf(finalNotConn));
                    statValidDay.setText(String.valueOf(finalValid));
                    statInvDay.setText(String.valueOf(finalInv));
                    statRegDay.setText(String.valueOf(finalReg));
                    statFtdDay.setText(String.valueOf(finalFtdN));
                    statFtdAmtDay.setText("৳" + fmtAmount(finalFtdSum));
                    weekSummary.setText("এই ৭ দিন (" + displayDay(finalBlockStart) + " – " + displayDay(finalBlockEnd) + "): Connected " + finalConnW + " • Not Connected " + finalNotConnW + " • এফটিডি " + finalFtdNW + " (৳" + fmtAmount(finalFtdSumW) + ")" + " • Valid " + finalValidW + " • Invalid " + finalInvW + " • রেজিস্ট্রেশন " + finalRegW + (finalUsedLegacy ? " • পুরোনো লগ ফাইল ব্যবহার হচ্ছে (শেয়ার করা ফাইল পাইনি)" : "") + (finalPartialFail ? " • একটা শিট পড়তে পারিনি (হিসাব অসম্পূর্ণ হতে পারে)" : ""));
                    buildCalledRows(logs, day);
                    reportStatsFallbackUsed = false;
                });
            } catch (Exception e) {
                maybeInvalidateLogCache(e);
                if (isNotFoundError(e) && !reportStatsFallbackUsed) { reportStatsFallbackUsed = true; clearReportCache(); loadDayStats(); return; }
                if (isAuthError(e) && !authRetryUsed && refreshTokenSync()) { authRetryUsed = true; loadDayStats(); return; }
                runOnUiThread(() -> weekSummary.setText("হিসাব লোড হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    private void showWebsite() { loginView.setVisibility(View.GONE); agentView.setVisibility(View.GONE); mainView.setVisibility(View.GONE); reportView.setVisibility(View.GONE); guideView.setVisibility(View.GONE); adminView.setVisibility(View.GONE); updateMenuState(3); websiteView.setVisibility(View.VISIBLE); bottomMenuBar.setVisibility(View.VISIBLE); findViewById(R.id.menuDashboardBtn).setBackgroundResource(R.drawable.glass_field); findViewById(R.id.menuHomeBtn).setBackgroundResource(R.drawable.glass_field); if (!websiteLoaded) { websiteWeb.loadUrl("https://www.12bajeevip.com/m/member/home"); websiteLoaded = true; } }

    /** Notun version ele purono app-e update popup: Update chaple APK download + install prompt asbe. */
    private JSONObject fetchVersionJson() throws Exception { String cb = "?t=" + (System.currentTimeMillis() / 60000); String[] urls = { "https://raw.githubusercontent.com/mdsahadathossenshihab1-lgtm/telesales-status-app/main/version.json" + cb, "https://telesales-status-app.vercel.app/version.json" + cb }; Exception lastError = null; for (String urlString : urls) { try { java.net.URL url = new java.net.URL(urlString); java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(15000); java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream())); StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close(); return new JSONObject(sb.toString()); } catch (Exception e) { lastError = e; } } if (lastError != null) throw lastError; throw new Exception("version fetch failed"); } private void checkForUpdate() {
        
        
        new Thread(() -> {
            try {
                JSONObject j = fetchVersionJson();
                
                
                
                
                
                
                
                int remoteCode = j.getInt("versionCode");
                String remoteName = j.optString("versionName", "");
                String apkUrl = j.optString("apkUrl", "");
                int minCode = j.optInt("minVersionCode", 0);
                int myCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (minCode > 0 && myCode < minCode && !apkUrl.isEmpty()) {
                    // Batch fix #6: v4.19+ notun key-te in-place update bose; sudhu v4.19-er
                    // ager purono-key build-e signature na mille Uninstall lage — dialog-e
                    // dutoi bola ache (age in-place chesta, na bosle Uninstall).
                    runOnUiThread(() -> { if (isFinishing()) return; new AlertDialog.Builder(MainActivity.this)
                            .setTitle("অ্যাপ আপডেট করা জরুরি")
                            .setMessage("এই ভার্সন আর চলবে না। নতুন ভার্সন " + remoteName + " পেতে আপডেট চাপুন — ফাইল ডাউনলোড হবে, তারপর ইনস্টল চাপলেই বসে যাবে। (যদি ইনস্টল না বসে, তখন পুরোনো অ্যাপটা আনইনস্টল করে ডাউনলোডস থেকে ফাইলটা ইনস্টল করুন। আগে রিফ্রেশ চেপে জমা সেভ সিঙ্ক করে নিন।)")
                            .setCancelable(false)
                            .setPositiveButton("আপডেট", (d, w) -> startUpdateDownload(apkUrl))
                            .show(); });
                } else if (remoteCode > myCode && !apkUrl.isEmpty()) {
                    // Batch fix #8: non-blocking dialog dine ekbar-er beshi noy (call theke
                    // ferar somoy barbar pop hoto); blocking dialog-e throttle noy.
                    SharedPreferences up = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                    int lastShownCode = up.getInt("update_dialog_last_code", -1);
                    if (remoteCode == lastShownCode && System.currentTimeMillis() - up.getLong("update_dialog_last_ms", 0) < 24L * 60 * 60 * 1000) return;
                    up.edit().putLong("update_dialog_last_ms", System.currentTimeMillis()).putInt("update_dialog_last_code", remoteCode).apply();
                    runOnUiThread(() -> { if (isFinishing()) return; new AlertDialog.Builder(MainActivity.this)
                            .setTitle("অ্যাপ আপডেট করুন")
                            .setMessage("নতুন ভার্সন " + remoteName + " এসেছে। আপডেট করলে নতুন সুবিধা পাবেন।")
                            .setPositiveButton("আপডেট", (d, w) -> startUpdateDownload(apkUrl))
                            .setNegativeButton("পরে", null)
                            .show(); });
                }
            } catch (Exception ignored) { /* net/file na pele purono version choltei thakbe, kono error noy */ }
        }).start();
    }

    private void startUpdateDownload(String apkUrl) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("টেলিসেলস অ্যাপ আপডেট");
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "telesales-status.apk");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setMimeType("application/vnd.android.package-archive");
            updateDownloadId = dm.enqueue(req);
            if (updateReceiver == null) {
                updateReceiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context ctx, Intent intent) {
                        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id != updateDownloadId) return;
                        try {
                            DownloadManager dm2 = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                            Uri uri = dm2.getUriForDownloadedFile(id);
                            if (uri == null) { Toast.makeText(MainActivity.this, "ডাউনলোড হয়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show(); return; }
                            Intent install = new Intent(Intent.ACTION_VIEW);
                            install.setDataAndType(uri, "application/vnd.android.package-archive");
                            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(install);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "ইনস্টল খোলা যায়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show();
                        }
                    }
                };
                registerReceiver(updateReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            Toast.makeText(this, "আপডেট ডাউনলোড হচ্ছে...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "ডাউনলোড শুরু হয়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Report ----------
    private static String txt(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
    private static String dhaka(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        return f.format(new Date());
    }
    private static String dhakaDate() { return dhaka("yyyy-MM-dd"); }
    /** v5.5: kono millis timestamp-ke Dhaka "HH:mm" banano (save-er tap-time theke). ts<=0 hole ekhoner somoy. */
    private static String dhakaTimeFrom(long ts) {
        if (ts <= 0) return dhaka("HH:mm");
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        return f.format(new Date(ts));
    }
    /** v5.5: log-er "HH:mm" somoy-ke Bangla 12-ghonta rup-e dekhanor jonno (16:32 -> ৪:৩২ বিকেল). RowAdapter-o eita byabohar kore. */
    static String formatTimeBn(String hhmm) {
        if (hhmm == null || hhmm.isEmpty()) return "";
        String[] pp = hhmm.split(":");
        int h;
        try { h = Integer.parseInt(pp[0]); } catch (Exception e) { return ""; }
        String m = pp.length > 1 ? pp[1] : "00";
        String ap;
        if (h >= 5 && h < 12) ap = "সকাল";
        else if (h >= 12 && h < 16) ap = "দুপুর";
        else if (h >= 16 && h < 20) ap = "বিকেল";
        else ap = "রাত";
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        return toBnDigits(String.valueOf(h12)) + ":" + toBnDigits(m) + " " + ap;
    }
    private static String toBnDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') b.append("০১২৩৪৫৬৭৮৯".charAt(c - '0'));
            else b.append(c);
        }
        return b.toString();
    }
    private static String fmtAmount(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
    /** v4.25: shared Report file-er agent tab-er row (Date|Type|Number|Amount|Note) theke
     *  purono shape-e project — counting/display/Generate code bodlay ni. sheetRow + rawCells thake
     *  (delete-er age puro row miliye dekhar jonno rawCells lage). */
    private static REntry projectReportRow(REntry u, String type) {
        String d = cellE(u, 0), n = cellE(u, 2);
        String[] old;
        if ("FTD".equals(type)) old = new String[]{d, n, cellE(u, 3)};
        else if ("Invalid".equals(type)) old = new String[]{d, n, cellE(u, 4)};
        else if ("Problems".equals(type)) old = new String[]{d, cellE(u, 4)};
        else old = new String[]{d, n}; // Registration
        return new REntry(old, u.sheetRow, u.cells, u.sourceSheet, u.sourceOrder);
    }
    /** v4.25: agent tab-er sob row ekbar pore Type-onujayi 4 list-e vag kora. */
    private static class ReportSplit { List<REntry> reg = new ArrayList<>(); List<REntry> ftd = new ArrayList<>(); List<REntry> inv = new ArrayList<>(); List<REntry> prob = new ArrayList<>(); }
    private static ReportSplit splitReportRows(List<REntry> unified) {
        ReportSplit s = new ReportSplit();
        for (REntry u : unified) {
            String t = cellE(u, 1).trim();
            if ("Registration".equals(t)) s.reg.add(projectReportRow(u, t));
            else if ("FTD".equals(t)) s.ftd.add(projectReportRow(u, t));
            else if ("Invalid".equals(t)) s.inv.add(projectReportRow(u, t));
            else if ("Problems".equals(t)) s.prob.add(projectReportRow(u, t));
        }
        return s;
    }
    private static List<REntry> withRows(List<String[]> rows) { return withRows(rows, null, 0); }
    private static List<REntry> withRows(List<String[]> rows, String source, int order) {
        List<REntry> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) out.add(new REntry(rows.get(i), i + 2, null, source, order)); // A2:Z pora hoy, tai i-tomo row = sheet row i+2
        return out;
    }
    private static List<REntry> filterTodayE(List<REntry> rows, String today) {
        List<REntry> out = new ArrayList<>();
        for (REntry r : rows) if (r.cells.length > 0 && today.equals(r.cells[0])) out.add(r);
        return out;
    }
    private static String cellE(REntry r, int i) {
        return r.cells.length > i ? r.cells[i] : "";
    }

    /** FTD row-r Number cell-e phone number na count: 0/+ diye suru hole phone, na hole count. */
    private static boolean isFtdCountRow(REntry e) {
        String n = cellE(e, 1);
        if (n.isEmpty() || n.startsWith("0") || n.startsWith("+")) return false;
        for (int i = 0; i < n.length(); i++) if (!Character.isDigit(n.charAt(i))) return false;
        return true;
    }
    /** Ekta FTD row kotota FTD bojhay: count row hole tar count, phone row hole 1. */
    private static int ftdCountOf(REntry e) {
        if (!isFtdCountRow(e)) return 1;
        try { return Integer.parseInt(cellE(e, 1)); } catch (Exception ex) { return 1; }
    }
    private static int ftdTotalCount(List<REntry> list) {
        int t = 0; for (REntry e : list) t += ftdCountOf(e); return t;
    }

    /** Registration row-r Number cell-e phone number na count: FTD-r motoi niyom. */
    private static boolean isRegCountRow(REntry e) {
        String n = cellE(e, 1);
        if (n.isEmpty() || n.startsWith("0") || n.startsWith("+")) return false;
        for (int i = 0; i < n.length(); i++) if (!Character.isDigit(n.charAt(i))) return false;
        return true;
    }
    private static int regCountOf(REntry e) {
        if (!isRegCountRow(e)) return 1;
        try { return Integer.parseInt(cellE(e, 1)); } catch (Exception ex) { return 1; }
    }
    private static int regTotalCount(List<REntry> list) {
        int t = 0; for (REntry e : list) t += regCountOf(e); return t;
    }

    private String ensureReportSheetForWrite() throws Exception {
        if (reportSheetId != null) return reportSheetId;
        // v4.28: report write sheet = date-chain-er bortoman sheet (switch-er pore notun);
        // tab na thakle header-soho khola hoy. Pora (read) ei field bebohar kore na.
        String wid = writeSheetFor(currentConfig(), "REPORTS", dhakaDate());
        if (wid == null) wid = SHARED_REPORT_ID;
        SheetsClient.ensureReportTab(token, wid, agentId);
        reportSheetId = wid;
        return wid;
    }

    private boolean isReportToday() { return reportDate.equals(dhakaDate()); } private void updateReportDateBtn() { if (reportDateBtn != null) reportDateBtn.setText(displayDay(reportDate)); } private void updateReportEditState() { boolean isToday = isReportToday(); int[] ids = {R.id.regPlusBtn, R.id.regMinusBtn, R.id.ftdPlusBtn, R.id.ftdMinusBtn, R.id.ftdStepAmount, R.id.ftdStepAddBtn, R.id.probText, R.id.probAddBtn}; for (int id : ids) { View v = findViewById(id); if (v != null) v.setEnabled(isToday); } View stepRow = findViewById(R.id.ftdStepAmountRow); if (stepRow != null && !isToday) stepRow.setVisibility(View.GONE); if (reportDateNote != null) reportDateNote.setVisibility(isToday ? View.GONE : View.VISIBLE); } private void setReportDate(String nd) { if (nd == null) return; if (nd.compareTo(dhakaDate()) > 0) nd = dhakaDate(); reportDate = nd; updateReportDateBtn(); updateReportEditState(); if (reportOutputCard != null) reportOutputCard.setVisibility(View.GONE); loadReportData(); } private void shiftReportDay(int delta) { try { Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.setTime(parseDay(reportDate)); c.add(Calendar.DAY_OF_MONTH, delta); String nd = fmtDay(c.getTime()); if (nd.compareTo(dhakaDate()) > 0) nd = dhakaDate(); setReportDate(nd); } catch (Exception e) { } } private void loadReportData() {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("রিপোর্ট লোড হচ্ছে...");
        new Thread(() -> {
            try {
                tryRefreshConfig();
                final int seq = ++reportSeq;
                String today = reportDate;
                ReportSplit s = splitReportRows(readReportRowsForDate(today));
                regToday = filterTodayE(s.reg, today);
                ftdToday = filterTodayE(s.ftd, today);
                invToday = filterTodayE(s.inv, today);
                probToday = filterTodayE(s.prob, today);
                final boolean partialReport = lastReportPartialFail;
                runOnUiThread(() -> { if (seq != reportSeq || !reportDate.equals(today)) return; reportLoadFallbackUsed = false; refreshReportLists(); reportStatus.setText(partialReport ? "রিপোর্ট লোড হয়েছে, কিন্তু একটা শিট পড়তে পারিনি — হিসাব অসম্পূর্ণ হতে পারে" : ""); loadAutoInvalidForReport(); });
            } catch (Exception e) {
                if (isNotFoundError(e) && !reportLoadFallbackUsed) {
                    reportLoadFallbackUsed = true;
                    clearReportCache();
                    loadReportData(); return;
                }
                if (isAuthError(e) && !authRetryUsed && refreshTokenSync()) { authRetryUsed = true; loadReportData(); return; }
                runOnUiThread(() -> reportStatus.setText("রিপোর্ট লোড হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    private int countAutoInvalid(List<REntry> logs, String date) { return countAutoInvalidRange(logs, date, date); }
    /** Batch fix #12: range-er Invalid (distinct number, not connected + Valid No) — week hisab-er jonno. */
    private int countAutoInvalidRange(List<REntry> logs, String start, String end) { java.util.Set<String> seen = new java.util.HashSet<>(); if (logs == null) return 0; for (REntry e : logs) { if (e == null) continue; String d = cellE(e, 0); if (d.compareTo(start) < 0 || d.compareTo(end) > 0) continue; if (!"not connected".equalsIgnoreCase(cellE(e, 2).trim())) continue; if (!"No".equalsIgnoreCase(cellE(e, 3).trim())) continue; String n = cellE(e, 1).replace(" ", "").replace("-", ""); if (n.startsWith("+880")) n = "0" + n.substring(4); else if (n.startsWith("880")) n = "0" + n.substring(3); else if (!n.startsWith("0")) n = "0" + n; if (!n.isEmpty()) seen.add(n); } return seen.size(); } private void loadAutoInvalidForReport() { final String d = reportDate; new Thread(() -> { try { if (token == null) return; List<REntry> logs = readLogRowsForDate(d); final int c = countAutoInvalid(logs, d); runOnUiThread(() -> { if (invAutoText != null) invAutoText.setText("Invalid: " + c); }); } catch (Exception ignored) { } }).start(); } private void refreshReportLists() {
        if (regStepCount != null) regStepCount.setText(String.valueOf(regTotalCount(regToday)));
        // v4.18: Invalid per-entry list removed; auto-count is shown in invAutoText.
        fillList(probList, probToday, "Problems");
        double sum = 0;
        for (REntry r : ftdToday) {
            try { sum += Double.parseDouble(cellE(r, 2)); } catch (Exception ignored) {}
        }
        ftdStepCount.setText(String.valueOf(ftdTotalCount(ftdToday)));
        ftdTotal.setText((reportDate.equals(dhakaDate()) ? "আজ মোট: " : displayDay(reportDate) + " মোট: ") + ftdTotalCount(ftdToday) + "টা • ৳" + fmtAmount(sum));
        // Batch fix #19: ajker FTD entry-gulo alada kore dekha + prottekta ✕ diye muchha
        // jabe (report TEXT-e sudhu count + total thakbe, ager motoi).
        if (ftdList != null) fillList(ftdList, ftdToday, "FTD");
    }

    /** Ekta list-er prottek entry-r pase ✕ delete button bosay. */
    private void fillList(LinearLayout parent, List<REntry> list, String tab) {
        parent.removeAllViews();
        if (list.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("এখনো কিছু যোগ হয়নি");
            t.setTextSize(14);
            t.setTextColor(Color.WHITE);
            parent.addView(t);
            return;
        }
        for (REntry e : list) {
            String text;
            if ("FTD".equals(tab) && isFtdCountRow(e)) text = cellE(e, 1) + "টা - " + cellE(e, 2);
            else if ("FTD".equals(tab) && cellE(e, 1).isEmpty()) text = cellE(e, 2);
            else if ("FTD".equals(tab) || "Invalid".equals(tab)) text = cellE(e, 1) + " - " + cellE(e, 2);
            else if ("Registration".equals(tab) && isRegCountRow(e)) text = cellE(e, 1) + "টা";
            else text = cellE(e, 1);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            float dens = getResources().getDisplayMetrics().density;
            row.setPadding(0, (int) (6 * dens), 0, (int) (6 * dens));
            TextView t = new TextView(this);
            t.setText(text);
            t.setTextSize(14);
            t.setTextColor(Color.WHITE);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button del = new Button(this);
            del.setText("✕");
            del.setTextSize(14);
            del.setTextColor(Color.WHITE);
            del.setBackgroundResource(R.drawable.glass_field);
            del.setMinWidth(0);
            del.setMinimumHeight(0);
            del.setMinimumWidth(0);
            del.setPadding(20, 8, 20, 8);
            del.setOnClickListener(v -> deleteReportEntry(tab, e));
            row.addView(t);
            if (isReportToday()) row.addView(del);
            parent.addView(row);
        }
    }

    /** Sheet thekeo row-ta muche fele, tarpor list abar load kore.
     *  v4.25: tab ekhon agentId (shared file-er agent tab); tab param ekhon Type
     *  (delete-er API-te lage na, shudhu caller-er chenar jonno). Guard-e rawCells
     *  (asol unified row) miliye dekha hoy. */
    private void deleteReportEntry(String tab, REntry e) { if (!isReportToday()) { Toast.makeText(this, "অতীতের রিপোর্ট শুধু দেখা ও কপি করা যাবে", Toast.LENGTH_SHORT).show(); return; }
        if (token == null) { showLogin(); return; }
        reportStatus.setText("মুছে ফেলা হচ্ছে...");
        new Thread(() -> {
            try {
                // v4.28: row-ta je sheet-e ache sekhan thekei muchbe (switch diner row purono sheet-eo thakte pare).
                String delSheet = e.sourceSheet != null ? e.sourceSheet : ensureReportSheetForWrite();
                // v4.20: delete-er age PURO row miliye dekha — sudhu date millei hobe na
                // (eki diner onek row thake, row sore gele vul row muchto).
                String[] current = SheetsClient.readRowCells(token, delSheet, agentId, e.sheetRow);
                String[] expect = e.rawCells != null ? e.rawCells : e.cells;
                boolean same = true;
                for (int ci = 0; ci < expect.length; ci++) {
                    String cur = ci < current.length ? current[ci] : "";
                    String exp = expect[ci] == null ? "" : expect[ci];
                    if (!cur.trim().equals(exp.trim())) { same = false; break; }
                }
                if (!same) throw new Exception("Sheet-e row bodle geche — Refresh kore abar delete korun");
                SheetsClient.deleteReportRow(token, delSheet, agentId, e.sheetRow);
                runOnUiThread(() -> { reportOutputCard.setVisibility(View.GONE); loadReportData(); loadDayStats(); });
            } catch (Exception ex) {
                if (isAuthError(ex) && !authRetryUsed && refreshTokenSync()) { authRetryUsed = true; deleteReportEntry(tab, e); return; }
                runOnUiThread(() -> reportStatus.setText("মুছে ফেলা হয়নি: " + ex.getMessage()));
            }
        }).start();
    }

    /** v4.25: tab param ekhon Type (Registration/FTD/Invalid/Problems); caller-ra ager
     *  motoi purono shape-e vals pathay, ekhane unified row-te map kore agent tab-e lekha hoy:
     *  [Date, Type, Number, Amount, Note]. */
    private void addReportEntry(String tab, String[] vals, Runnable clear) { if (!isReportToday()) { Toast.makeText(this, "অতীতের রিপোর্ট শুধু দেখা ও কপি করা যাবে", Toast.LENGTH_SHORT).show(); return; }
        if (token == null) { showLogin(); return; }
        reportStatus.setText("যোগ করা হচ্ছে...");
        new Thread(() -> {
            try {
                String wid = ensureReportSheetForWrite();
                String v0 = vals.length > 0 ? vals[0] : "";
                String v1 = vals.length > 1 ? vals[1] : "";
                String v2 = vals.length > 2 ? vals[2] : "";
                String[] u;
                if ("FTD".equals(tab)) u = new String[]{v0, "FTD", v1, v2, ""};
                else if ("Invalid".equals(tab)) u = new String[]{v0, "Invalid", v1, "", v2};
                else if ("Problems".equals(tab)) u = new String[]{v0, "Problems", "", "", v1};
                else u = new String[]{v0, "Registration", v1, "", ""};
                SheetsClient.appendReportRow(token, wid, agentId, u);
                runOnUiThread(() -> { if (clear != null) clear.run(); loadReportData(); loadDayStats(); });
            } catch (Exception e) {
                if (isAuthError(e) && !authRetryUsed && refreshTokenSync()) { authRetryUsed = true; addReportEntry(tab, vals, clear); return; }
                runOnUiThread(() -> reportStatus.setText("যোগ হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    /** Batch fix #11: ei din-er report age joma ache kina (Daily Reports file-e oi
     *  date + agent-er row ache kina). Porte na parle false (fail-open — ager motoi joma
     *  hobe; duplicate holeo ager behavior-er baire noy). */
    private boolean alreadySubmittedDaily(String day) {
        try {
            if (token == null || agentId == null) return false;
            String dailyId = writeSheetFor(currentConfig(), "DAILY", day);
            if (dailyId == null) dailyId = DAILY_REPORTS_ID;
            if (dailyId == null) return false;
            List<String[]> rows = SheetsClient.getReportRows(token, dailyId, "Reports");
            String want = displayDay(day);
            for (String[] r : rows) {
                if (r != null && r.length >= 2 && want.equals(r[0]) && agentId.equals(r[1])) return true;
            }
            return false;
        } catch (Exception ignored) { return false; }
    }
    private void generateReport() {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("রিপোর্ট বানানো হচ্ছে...");
        new Thread(() -> {
            try {
                tryRefreshConfig();
                List<REntry> logs = readLogRowsForDate(reportDate);
                int total = 0, conn = 0;
                for (REntry e : logs) {
                    if (reportDate.equals(cellE(e, 0))) {
                        total++;
                        if ("call connected".equals(cellE(e, 2))) conn++;
                    }
                }
                String today = reportDate;
                ReportSplit s = splitReportRows(readReportRowsForDate(today));
                regToday = filterTodayE(s.reg, today);
                ftdToday = filterTodayE(s.ftd, today);
                invToday = filterTodayE(s.inv, today);
                probToday = filterTodayE(s.prob, today);
                double sum = 0;
                for (REntry r : ftdToday) {
                    try { sum += Double.parseDouble(cellE(r, 2)); } catch (Exception ignored) {}
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Date: ").append(displayDay(today)).append("\n");
                // Batch fix #10: past-date report-e bortoman somoy dekhale mone hoy oi
                // somoye call hoyeche — tai past date-e "pore banano", ajker date-e somoy.
                if (today.equals(dhakaDate())) sb.append("Time: ").append(dhaka("hh:mm a")).append("\n");
                else sb.append("Time: Generated later\n");
                sb.append("Agent ID: ").append(agentId).append("\n");
                sb.append("Total Calls: ").append(total).append("\n");
                sb.append("Connected: ").append(conn).append("\n");
                sb.append("Registration: ").append(regTotalCount(regToday)).append("\n");
                // v4.27: Registration details ar dekhabo na — sudhu count (user order).
                sb.append("FTD: ").append(ftdTotalCount(ftdToday)).append("\n");
                sb.append("Total Amount Recharged: ").append(fmtAmount(sum)).append("\n");
                sb.append("Invalid: ").append(countAutoInvalid(logs, today)).append("\n");
                // v4.18: manual Invalid rows are not listed. sb.append("-\n");
                // v4.18: manual Invalid rows are not listed. sb.append(cellE(r, 1)).append(" - ").append(cellE(r, 2)).append("\n");
                sb.append("Problems:\n");
                if (probToday.isEmpty()) sb.append("-\n");
                else for (REntry r : probToday) sb.append(cellE(r, 1)).append("\n");
                String text = sb.toString().trim();
                final int totalF = total, connF = conn; final double sumF = sum;
                if (alreadySubmittedDaily(today)) {
                    // Batch fix #11: age joma thakle na janiye abar joma noy — jigges korbe.
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("আগেই জমা আছে")
                            .setMessage("এই দিনের রিপোর্ট আগেই জমা আছে। আবার জমা করবেন?")
                            .setPositiveButton("হ্যাঁ, আবার জমা করুন", (d, w) -> {
                                new Thread(() -> {
                                    final String note = appendDailyReport(totalF, connF, regToday, ftdToday, sumF, logs, today, probToday);
                                    runOnUiThread(() -> { refreshReportLists(); reportOutput.setText(text); reportOutputCard.setVisibility(View.VISIBLE); reportStatus.setText(note); });
                                }).start();
                            })
                            .setNegativeButton("না", (d, w) -> {
                                refreshReportLists(); reportOutput.setText(text); reportOutputCard.setVisibility(View.VISIBLE);
                                reportStatus.setText("আগেই জমা আছে — আবার জমা করা হয়নি");
                            })
                            .show();
                    });
                } else {
                    final String dailyNote = appendDailyReport(total, conn, regToday, ftdToday, sum, logs, today, probToday);
                    runOnUiThread(() -> {
                        refreshReportLists();
                        reportOutput.setText(text);
                        reportOutputCard.setVisibility(View.VISIBLE);
                        reportStatus.setText(dailyNote);
                    });
                }
            } catch (Exception e) {
                if (isAuthError(e) && !authRetryUsed && refreshTokenSync()) { authRetryUsed = true; generateReport(); return; }
                runOnUiThread(() -> reportStatus.setText("রিপোর্ট বানানো হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    // ---------- v4.19 helpers ----------
    private static boolean isAuthError(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m != null && m.contains("401");
    }

    /** 401 hole token batil kore abar token newa (ekbar) — ghonta-khanek por token expire
     *  hole relaunch charai cholbe. Background thread theke daka hoy. */
    private boolean refreshTokenSync() {
        try {
            GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
            if (acct == null || acct.getAccount() == null) return false;
            if (token != null) { try { GoogleAuthUtil.invalidateToken(this, token); } catch (Exception ignored) {} }
            token = GoogleAuthUtil.getToken(this, acct.getAccount(), "oauth2:" + SHEET_SCOPE + " " + DRIVE_SCOPE);
            return token != null && !token.isEmpty();
        } catch (Exception e) { return false; }
    }

    /** Log tab-er read/append "tab missing"-er moto fail hole cache batil, jate porer
     *  resolve abar tab ache kina jachai kore. */
    private void maybeInvalidateLogCache(Exception e) {
        String m = e == null ? "" : String.valueOf(e.getMessage()).toLowerCase();
        if (m.contains("tab") || m.contains("404") || m.contains("not found")) invalidateSharedLogCache();
    }

    /** v4.19 debounce: Refresh / tarikh bodol druto chaple shudhu sesh chap-ta 400ms pore cholbe. */
    private void debouncedLoadRows() {
        if (pendingRowsLoad != null) uiHandler.removeCallbacks(pendingRowsLoad);
        pendingRowsLoad = this::loadRows;
        uiHandler.postDelayed(pendingRowsLoad, 400);
    }
    private void debouncedLoadDayStats() {
        if (pendingStatsLoad != null) uiHandler.removeCallbacks(pendingStatsLoad);
        pendingStatsLoad = this::loadDayStats;
        uiHandler.postDelayed(pendingStatsLoad, 400);
    }

    /** Batch fix #9: load-er somoy sheet-er obosthar snapshot — auto-refresh sudhu
     *  tokhon jokhon kono row-te unsaved lekha/status nei (na hole lekha muchhe jeto). */
    private List<Row> loadedSnapshot = null;
    private void takeLoadedSnapshot(List<Row> rows) {
        loadedSnapshot = new ArrayList<>();
        if (rows == null) return;
        for (Row r : rows) {
            Row c = new Row(r.row, r.number, r.status, r.valid, r.remark1, r.remark2, r.others);
            c.viewOnly = r.viewOnly;
            loadedSnapshot.add(c);
        }
    }
    private static boolean strEq(String a, String b) { return a == null ? b == null : a.equals(b); }
    private boolean hasDirtyRows() {
        if (loadedSnapshot == null || adapter == null || adapter.getAll() == null) return false;
        java.util.Map<Integer, Row> snap = new java.util.HashMap<>();
        for (Row s : loadedSnapshot) snap.put(s.row, s);
        for (Row r : adapter.getAll()) {
            Row s = snap.get(r.row);
            if (s == null) continue;
            if (!strEq(s.status, r.status) || !strEq(s.valid, r.valid) || !strEq(s.remark1, r.remark1) || !strEq(s.remark2, r.remark2) || !strEq(s.others, r.others)) return true;
        }
        return false;
    }
    /** Batch feature #7: phone-e joma thaka unsent save-er sonkha (1 outbox entry =
     *  1 save; log-only pending ekhane gonay ase na — ogulor main save sheet-e geche). */
    private void refreshQueuedCounter() {
        if (queuedCountText == null) return;
        int n = 0;
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            n = new JSONArray(p.getString(OUTBOX_KEY, "[]")).length();
        } catch (Exception ignored) {}
        if (n <= 0) { queuedCountText.setVisibility(View.GONE); return; }
        queuedCountText.setText("জমা বাকি: " + n + "টা সেভ এখনো শিটে যায়নি — নেট আসলেই যাবে");
        queuedCountText.setVisibility(View.VISIBLE);
    }
    /** Batch fix #9: list-e ferar somoy admin-er notun number dekhano — sudhu clean
     *  obosthay (dirty ba in-flight save thakle refresh noy, lekha bachbe). */
    private void maybeAutoRefreshList() {
        if (token == null || adapter == null) return;
        if (!savingRows.isEmpty()) return;
        if (hasDirtyRows()) return;
        debouncedLoadRows();
    }
    private void markRowCleanInSnapshot(Row row) {
        if (loadedSnapshot == null || row == null) return;
        for (Row s : loadedSnapshot) {
            if (s.row == row.row) {
                s.status = row.status; s.valid = row.valid;
                s.remark1 = row.remark1; s.remark2 = row.remark2; s.others = row.others;
                break;
            }
        }
    }

    /** v4.41: unsaved edit-er draft row-te bosano. Sheet theke asa row-er
     *  value-r sathe draft mille draft-ti already-saved dhore muchhe fela hoy
     *  (offline save pore deliver holeo draft jhule thakbe na). */
    private void applyDrafts(java.util.List<Row> rows, boolean onlyLogOnly) {
        try {
            if (rows == null) return;
            org.json.JSONObject all = DraftStore.loadAll(this);
            if (all.length() == 0) return;
            for (Row r : rows) {
                if (r == null) continue;
                if (onlyLogOnly && r.row != -1) continue;
                String key = RowAdapter.dialNumber(r.number);
                if (key == null || all.isNull(key)) continue;
                org.json.JSONObject d = all.getJSONObject(key);
                String ds = d.optString("status", ""), dv = d.optString("valid", "");
                String d1 = d.optString("remark1", ""), d2 = d.optString("remark2", ""), do_ = d.optString("others", "");
                String cs = r.status == null ? "" : r.status, cv = r.valid == null ? "" : r.valid;
                String c1 = r.remark1 == null ? "" : r.remark1, c2 = r.remark2 == null ? "" : r.remark2, co = r.others == null ? "" : r.others;
                if (ds.equals(cs) && dv.equals(cv) && d1.equals(c1) && d2.equals(c2) && do_.equals(co)) {
                    DraftStore.clear(this, r.number);
                } else {
                    r.status = ds; r.valid = dv; r.remark1 = d1; r.remark2 = d2; r.others = do_;
                }
            }
        } catch (Exception ignored) {}
    }

    /** v4.33: মোট বাকি hero — live list-e status khali row-gulor sonkha. Kol baki
     *  tab-er niyom-ei gona hoy (status null ba khali), tai hero aar tab sob somoy
     *  milbe. List-based: dashboard-er tarikh bodol eke bodlay na; loadDayStats
     *  eke chhoye na. Log-only row (main sheet-e nei) live list-e nei, tai gonay ase na. */
    /** Batch feature #10: ajker target card — sudhu ajker tarikh-e dekhabe (goto din-e
     *  ajker baki dekhano bhul hoto). Clear-save-er por total tile-er motoi card-o porer
     *  loadDayStats porjonto ager sonkha dekhate pare (ager thekei tile-er ekoi simaboddota). */
    private void refreshTargetCard() {
        if (targetCard == null || targetCardText == null) return;
        if (!targetStatsReady || !targetRemainReady) return;
        if (selectedDate == null || !selectedDate.equals(dhakaDate())) { targetCard.setVisibility(View.GONE); return; }
        if (lastRemaining <= 0) targetCardText.setText("আজকের সব কল শেষ ✓ — মোট " + lastTotalCallDay + "টা কল হয়েছে");
        else targetCardText.setText("আজ: " + lastTotalCallDay + "টা কল হয়েছে • " + lastRemaining + "টা বাকি");
        targetCard.setVisibility(View.VISIBLE);
    }
    private void refreshRemainingHero() {
        if (adapter == null || statTotalRemainDay == null) return;
        try {
            int remain = 0;
            for (Row r : adapter.getAll()) {
                if (r != null && (r.status == null || r.status.isEmpty())) remain++;
            }
            statTotalRemainDay.setText(String.valueOf(remain));
            lastRemaining = remain; targetRemainReady = true; refreshTargetCard();
        } catch (Exception ignored) { /* hero ager value-i thakbe — bhul sonkha dekhano noy */ }
    }

    /** v4.19: Save-er por puro loadDayStats (4 read + Drive search) na chaliye tile-gulo
     *  local-e update: log-e row sudhu ajker tarikhe jog hoy, tai selected din aj holei
     *  total/status/valid barbe; FTD/Registration/Invalid/weekSummary bodlay na. */
    private void applyLocalSaveToTiles(String newStatus, String newValid) {
        if (selectedDate == null || !selectedDate.equals(dhakaDate())) return;
        try {
            statTotalCallDay.setText(String.valueOf(Integer.parseInt(statTotalCallDay.getText().toString()) + 1));
            lastTotalCallDay++; refreshTargetCard();
            if ("call connected".equals(newStatus)) statConnDay.setText(String.valueOf(Integer.parseInt(statConnDay.getText().toString()) + 1));
            if ("not connected".equals(newStatus)) statNotConnDay.setText(String.valueOf(Integer.parseInt(statNotConnDay.getText().toString()) + 1));
            if ("Yes".equals(newValid)) statValidDay.setText(String.valueOf(Integer.parseInt(statValidDay.getText().toString()) + 1));
        } catch (Exception ignored) { /* tile-e number na thakle porer loadDayStats thik kore debe */ }
    }

    /** v4.19 OUTBOX: save fail hole (net/quota/timeout) row-ta phone-e joma thakbe, harabe na.
     *  Porer login/refresh/safal save-er por background-e abar pathano hobe. updateRow PUT
     *  idempotent bole retry nirapad (eki row abar lekha = eki fol). */
    private static final String OUTBOX_KEY = "outbox_v419";
    private void queueOutbox(Row row) { queueOutboxWithTs(row, System.currentTimeMillis(), false); }
    /** v4.32: outbox entry — sync=true hole commit() (tap-time pre-queue; apply async harate pare). */
    private void queueOutboxWithTs(Row row, long ts, boolean sync) {
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(OUTBOX_KEY, "[]")); } catch (Exception e) { arr = new JSONArray(); }
            JSONObject o = new JSONObject();
            o.put("agent", agentId == null ? "" : agentId);
            o.put("row", row.row);
            o.put("number", row.number == null ? "" : row.number);
            o.put("status", row.status == null ? "" : row.status);
            o.put("valid", row.valid == null ? "" : row.valid);
            o.put("r1", row.remark1 == null ? "" : row.remark1);
            o.put("r2", row.remark2 == null ? "" : row.remark2);
            o.put("others", row.others == null ? "" : row.others);
            o.put("ts", ts);
            arr.put(o);
            SharedPreferences.Editor ed = p.edit().putString(OUTBOX_KEY, arr.toString());
            if (sync) ed.commit(); else ed.apply();
        } catch (Exception ignored) {}
    }
    /** v4.32: pre-queue kora outbox entry sorano — sudhu ei agent + number + ts mile gele.
     *  Onno save-er entry (eki number holeo) chhowa hobe na. */
    private void removeOutboxEntry(String agent, String number, long ts) {
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(OUTBOX_KEY, "[]")); } catch (Exception e) { return; }
            JSONArray kept = new JSONArray();
            String norm = SheetsClient.normLogNumber(number);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean same = agent != null && agent.equals(o.optString("agent"))
                        && norm.equals(SheetsClient.normLogNumber(o.optString("number")))
                        && ts == o.optLong("ts");
                if (same) continue;
                kept.put(o);
            }
            p.edit().putString(OUTBOX_KEY, kept.toString()).commit();
        } catch (Exception ignored) {}
    }
    /** v4.32: pre-queue kora outbox entry-te numbers sheet ID stamp (thread-e resolve howar por).
     *  Worker sudhu ei stored ID-tei pathabe — na thakle skip (in-app drain korbe). */
    private void stampOutboxNumbersId(String agent, String number, long ts, String numbersId) {
        try {
            if (numbersId == null || numbersId.isEmpty()) return;
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(OUTBOX_KEY, "[]")); } catch (Exception e) { return; }
            String norm = SheetsClient.normLogNumber(number);
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean same = agent != null && agent.equals(o.optString("agent"))
                        && norm.equals(SheetsClient.normLogNumber(o.optString("number")))
                        && ts == o.optLong("ts");
                if (same) { o.put("numbersId", numbersId); changed = true; }
            }
            if (changed) p.edit().putString(OUTBOX_KEY, arr.toString()).commit();
        } catch (Exception ignored) {}
    }
    private void drainOutboxAsync() {
        if (token == null || agentId == null) return;
        final String myAgent = agentId;
        new Thread(() -> {
            try {
                SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                JSONArray arr;
                try { arr = new JSONArray(p.getString(OUTBOX_KEY, "[]")); } catch (Exception e) { return; }
                boolean changed = false;
                boolean authTried = false;
                String numbersId = resolveNumbersIdForAgent(myAgent);
                for (int i = 0; i < arr.length(); ) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null || !myAgent.equals(o.optString("agent"))) { i++; continue; }
                    Row r = new Row(o.optInt("row"), o.optString("number"), o.optString("status"), o.optString("valid"), o.optString("r1"), o.optString("r2"), o.optString("others"));
                    try {
                        // v4.41: row sore gele number diye bortoman row khunje abar save —
                        // team roj number soray, tai purono row number-e likhle "bodle geche"
                        // hoye entry muchhe jeto ar user-ke abar save korte hoto.
                        try {
                            SheetsClient.updateRow(token, numbersId, myAgent, r);
                        } catch (Exception ue) {
                            String um = ue.getMessage() == null ? "" : ue.getMessage();
                            if (!um.contains("bodle geche")) throw ue;
                            Row fresh = SheetsClient.findRowByNumber(token, numbersId, myAgent, SheetsClient.normLogNumber(r.number));
                            if (fresh == null) { arr.remove(i); changed = true; continue; } // number sheet-e ar nei — drop
                            r.row = fresh.row;
                            SheetsClient.updateRow(token, numbersId, myAgent, r); // abar bodle gele outer catch-e jabe
                        }
                        // v4.20: drain-e main update land korlei log side-o thik korte hobe.
                        String entryDate = dateFromTs(o.optLong("ts"));
                        String dNum = RowAdapter.dialNumber(r.number);
                        boolean entryClear = (r.status == null || r.status.isEmpty()) && (r.valid == null || r.valid.isEmpty());
                        if (entryClear) {
                            try {
                                StatusLogTarget et = resolveStatusLogTargetForDate(entryDate);
                                deleteLogRowsForTarget(et, entryDate, dNum);
                                purgePendingFor(entryDate, dNum, "log");
                            } catch (Exception de) {
                                queuePendingClear(entryDate, dNum);
                                enqueueSaveDrainWorker();
                                arr.remove(i); changed = true;
                                break; // delete fail — porer entry pore, order thik thakbe
                            }
                        } else {
                            purgePendingFor(entryDate, dNum, "clear");
                        }
                        arr.remove(i); changed = true; // eki index-e porer entry asbe, i barbe na
                    } catch (Exception e) {
                        String m = e.getMessage() == null ? "" : e.getMessage();
                        if (m.contains("bodle geche")) { arr.remove(i); changed = true; continue; } // row sore geche — ar apply kora nirapad noy, user abar save korbe
                        if (isAuthError(e) && !authTried && refreshTokenSync()) { authTried = true; continue; }
                        break; // prothom fail-e thambe — baki gulo pore abar chesta hobe, order thik thakbe
                    }
                }
                if (changed) p.edit().putString(OUTBOX_KEY, arr.toString()).apply();
                drainPendingLogsAsync();
            } catch (Exception ignored) {}
        }).start();
    }

    /** v4.20 PENDING-LOG: log append fail hole (ba main save fail hole) log row-ta phone-e
     *  joma thakbe — pore drain-e append/clear hobe, harabe na. Type: "log" = append,
     *  "clear" = oi date+number-er log row muche fela. */
    private static final String PENDING_LOG_KEY = "pending_log_v420";
    private static String dateFromTs(long ts) {
        if (ts <= 0) return dhakaDate();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        return f.format(new Date(ts));
    }
    private void queuePending(String type, String date, String number, String status, String valid) { queuePendingWithTs(type, date, number, status, valid, System.currentTimeMillis(), false); }
    /** v4.32: pending entry — sync=true hole commit() (tap-time pre-queue). */
    private void queuePendingWithTs(String type, String date, String number, String status, String valid, long ts, boolean sync) {
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]")); } catch (Exception e) { arr = new JSONArray(); }
            JSONObject o = new JSONObject();
            o.put("agent", agentId == null ? "" : agentId);
            o.put("type", type); o.put("date", date == null ? "" : date);
            o.put("number", number == null ? "" : number);
            o.put("status", status == null ? "" : status);
            o.put("valid", valid == null ? "" : valid);
            o.put("ts", ts);
            arr.put(o);
            SharedPreferences.Editor ed = p.edit().putString(PENDING_LOG_KEY, arr.toString());
            if (sync) ed.commit(); else ed.apply();
        } catch (Exception ignored) {}
    }
    /** v4.32: background drain worker enqueue — APPEND chain (choloman run-e badha noy,
     *  notun entry-r run kokhono skip hobe na). Network thaklei cholbe, backoff 1 min. */
    private void enqueueSaveDrainWorker() {
        try {
            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SaveDrainWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build();
            WorkManager.getInstance(this).enqueueUniqueWork("telesales-save-drain", ExistingWorkPolicy.APPEND, req);
        } catch (Exception ignored) {}
    }
    /** v4.32: pre-queue kora log entry-te log sheet ID + tab stamp (ensureStatusLogSheet-er por).
     *  Worker sudhu ei stored target-ei append korbe. Clear entry-te stamp hoy na. */
    private void stampPendingLogTarget(String agent, String number, long ts, String logSheetId, String logTab) {
        try {
            if (logSheetId == null || logSheetId.isEmpty() || logTab == null || logTab.isEmpty()) return;
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]")); } catch (Exception e) { return; }
            String norm = SheetsClient.normLogNumber(number);
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean same = agent != null && agent.equals(o.optString("agent"))
                        && "log".equals(o.optString("type"))
                        && norm.equals(SheetsClient.normLogNumber(o.optString("number")))
                        && ts == o.optLong("ts");
                if (same) { o.put("logSheetId", logSheetId); o.put("logTab", logTab); changed = true; }
            }
            if (changed) p.edit().putString(PENDING_LOG_KEY, arr.toString()).commit();
        } catch (Exception ignored) {}
    }
    private void queuePendingLog(String date, String number, String status, String valid) { queuePending("log", date, number, status, valid); }
    private void queuePendingClear(String date, String number) { queuePending("clear", date, number, "", ""); }
    /** Pending theke (date, number)-er entry muche fela — type null hole sob type.
     *  Sudhu ei agent-er entry muchbe (onno agent-er ta noy). */
    private void purgePendingFor(String date, String number, String type) {
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]")); } catch (Exception e) { return; }
            JSONArray kept = new JSONArray();
            String norm = SheetsClient.normLogNumber(number);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String ea = o.optString("agent");
                if (!ea.isEmpty() && agentId != null && !ea.equals(agentId)) { kept.put(o); continue; }
                boolean same = date.equals(o.optString("date")) && norm.equals(SheetsClient.normLogNumber(o.optString("number")));
                if (same && (type == null || type.equals(o.optString("type")))) continue;
                kept.put(o);
            }
            p.edit().putString(PENDING_LOG_KEY, kept.toString()).apply();
        } catch (Exception ignored) {}
    }
    /** v4.32: pre-queue kora pending entry sorano — sudhu ei agent + number + ts mile gele.
     *  purgePendingFor(date, number, type) sob purono entry muchto (eki number-er ager
     *  failed save-er log row harato); ts-match-e sudhu ei save-er entry sorbe. */
    private void removePendingEntry(String agent, String number, long ts) {
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]")); } catch (Exception e) { return; }
            JSONArray kept = new JSONArray();
            String norm = SheetsClient.normLogNumber(number);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean same = agent != null && agent.equals(o.optString("agent"))
                        && norm.equals(SheetsClient.normLogNumber(o.optString("number")))
                        && ts == o.optLong("ts");
                if (same) continue;
                kept.put(o);
            }
            p.edit().putString(PENDING_LOG_KEY, kept.toString()).commit();
        } catch (Exception ignored) {}
    }
    /** Pending log/clear drain: FIFO; outbox-e oi number-er main update baki thakle
     *  tar log entry skip (pore abar, order thik rakhte eki number-er porer gulo-o skip),
     *  clear entry skip noy. Fail hole thambe (order thik). */
    private void drainPendingLogsAsync() {
        if (token == null || agentId == null) return;
        final String myAgent = agentId;
        new Thread(() -> {
            try {
                SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                JSONArray arr;
                try { arr = new JSONArray(p.getString(PENDING_LOG_KEY, "[]")); } catch (Exception e) { return; }
                if (arr.length() == 0) return;
                java.util.Set<String> outboxNums = new java.util.HashSet<>();
                try {
                    JSONArray ob = new JSONArray(p.getString(OUTBOX_KEY, "[]"));
                    for (int i = 0; i < ob.length(); i++) {
                        JSONObject o = ob.optJSONObject(i);
                        if (o != null && myAgent.equals(o.optString("agent"))) outboxNums.add(SheetsClient.normLogNumber(o.optString("number")));
                    }
                } catch (Exception ignored) {}
                // v4.28: prottek entry tar nijer date-er write target-e jabe (chain).
                boolean changed = false;
                boolean authTried = false;
                java.util.Map<String, StatusLogTarget> targetForDateCache = new java.util.HashMap<>();
                // v4.32: target tab-er row cache (dedup check-er jonno, proti tab ekbar pora hobe).
                java.util.Map<String, java.util.Set<String>> logRowsCache = new java.util.HashMap<>();
                java.util.Set<String> skippedNums = new java.util.HashSet<>();
                for (int i = 0; i < arr.length(); ) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) { i++; continue; }
                    String ea = o.optString("agent");
                    if (!ea.isEmpty() && !myAgent.equals(ea)) { i++; continue; }
                    String type = o.optString("type");
                    String num = SheetsClient.normLogNumber(o.optString("number"));
                    if ("log".equals(type) && (outboxNums.contains(num) || skippedNums.contains(num))) { skippedNums.add(num); i++; continue; }
                    try {
                        StatusLogTarget et = targetForDateCache.get(o.optString("date"));
                        if (et == null) { et = resolveStatusLogTargetForDate(o.optString("date")); targetForDateCache.put(o.optString("date"), et); }
                        if ("clear".equals(type)) {
                            deleteLogRowsForTarget(et, o.optString("date"), o.optString("number"));
                        } else {
                            // v4.32: timeout-after-success / double-queue hole eki row duibar
                            // append hoto — target tab-e age thekei thakle skip (entry sorbe).
                            java.util.Set<String> existing = logRowsCache.get(et.sheetId + "|" + et.tab);
                            if (existing == null) {
                                existing = new java.util.HashSet<>();
                                try {
                                    for (String[] lr : SheetsClient.getReportRows(token, et.sheetId, et.tab)) {
                                        if (lr != null && lr.length >= 4) existing.add(lr[0] + "|" + SheetsClient.normLogNumber(lr[1]) + "|" + lr[2] + "|" + lr[3]);
                                    }
                                    logRowsCache.put(et.sheetId + "|" + et.tab, existing);
                                } catch (Exception re) { existing = null; } // read fail — skip noy, append chesta hobe
                            }
                            if (existing != null) {
                                String dupKey = o.optString("date") + "|" + num + "|" + o.optString("status") + "|" + o.optString("valid");
                                if (existing.contains(dupKey)) { arr.remove(i); changed = true; continue; }
                            }
                            SheetsClient.appendReportRow(token, et.sheetId, et.tab, new String[]{o.optString("date"), RowAdapter.dialNumber(o.optString("number")), o.optString("status"), o.optString("valid"), dhakaTimeFrom(o.optLong("ts", 0))});
                            // v4.32: eki drain-e porer duplicate entry-o jate skip hoy.
                            java.util.Set<String> ex = logRowsCache.get(et.sheetId + "|" + et.tab);
                            if (ex != null) ex.add(o.optString("date") + "|" + num + "|" + o.optString("status") + "|" + o.optString("valid"));
                        }
                        arr.remove(i); changed = true;
                    } catch (Exception e) {
                        if (isAuthError(e) && !authTried && refreshTokenSync()) { authTried = true; continue; }
                        break;
                    }
                }
                if (changed) p.edit().putString(PENDING_LOG_KEY, arr.toString()).apply();
            } catch (Exception ignored) {}
        }).start();
    }
    private static boolean isNotFoundError(Exception e) {
        String m = e == null ? "" : String.valueOf(e.getMessage()).toLowerCase();
        return m.contains("404") || m.contains("not found");
    }
    /** Stale Report ID cache khali kora (prefs + field) — porer resolve abar Drive search korbe. */
    private void clearReportCache() {
        try {
            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = p.edit().remove("report_sheet_id_" + agentId);
            if ("bdxsukuna30".equals(agentId)) ed.remove("report_sheet_id");
            ed.apply();
        } catch (Exception ignored) {}
        reportSheetId = null;
    }
    /** Save-er por Invalid tile taja kora (ekta log read — cache thakle Drive search noy). */
    private void refreshInvalidTileAfterSave() {
        if (token == null || agentId == null) return;
        if (selectedDate == null || !selectedDate.equals(dhakaDate())) return;
        new Thread(() -> {
            try {
                List<REntry> logs = readLogRowsForDate(dhakaDate());
                final int c = countAutoInvalid(logs, dhakaDate());
                runOnUiThread(() -> { if (statInvDay != null && selectedDate != null && selectedDate.equals(dhakaDate())) statInvDay.setText(String.valueOf(c)); });
            } catch (Exception ignored) {}
        }).start();
    }
    /** v4.20: Dashboard date-onujayi called tab-er row toiri — log theke oi diner call kora
     *  number; main sheet-e thakle editable row, na thakle view-only log row. */
    private void buildCalledRows(List<REntry> logs, String day) {
        try {
            if (adapter == null) return;
            java.util.Map<String, Row> mainByNorm = new java.util.HashMap<>();
            for (Row r : adapter.getAll()) mainByNorm.put(SheetsClient.normLogNumber(r.number), r);
            java.util.Map<String, String[]> latest = new java.util.HashMap<>();
            java.util.Map<String, String> displayNum = new java.util.HashMap<>();
            java.util.Map<String, Integer> lastPos = new java.util.HashMap<>();
            java.util.Map<String, Integer> lastOrder = new java.util.HashMap<>();
            if (logs != null) for (REntry e : logs) {
                if (e == null || !day.equals(cellE(e, 0))) continue;
                String st = cellE(e, 2);
                if (st == null || st.isEmpty()) continue;
                String norm = SheetsClient.normLogNumber(cellE(e, 1));
                if (norm.isEmpty()) continue;
                latest.put(norm, new String[]{st, cellE(e, 3), cellE(e, 4)});
                displayNum.put(norm, cellE(e, 1));
                lastPos.put(norm, e.sheetRow);
                lastOrder.put(norm, e.sourceOrder);
            }
            // v5.4: joma thaka (ekhono log-e na jawa) save-o called tab-e dekhabe.
            // Log append-e ektu deri holeo number-ta called theke gayeb hobe na;
            // log-e entry ele pending entry-ti sorbe, tokhon log-er entry-i thakbe.
            try {
                SharedPreferences pp = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                JSONArray parr = new JSONArray(pp.getString(PENDING_LOG_KEY, "[]"));
                for (int i = 0; i < parr.length(); i++) {
                    JSONObject o = parr.optJSONObject(i);
                    if (o == null) continue;
                    if (!"log".equals(o.optString("type"))) continue;
                    if (agentId == null || !agentId.equals(o.optString("agent"))) continue;
                    if (!day.equals(o.optString("date"))) continue;
                    String pst = o.optString("status", "");
                    if (pst.isEmpty()) continue;
                    String pnorm = SheetsClient.normLogNumber(o.optString("number", ""));
                    if (pnorm.isEmpty() || latest.containsKey(pnorm)) continue;
                    latest.put(pnorm, new String[]{pst, o.optString("valid", ""), dhakaTimeFrom(o.optLong("ts", 0))});
                    displayNum.put(pnorm, o.optString("number", ""));
                    lastOrder.put(pnorm, Integer.MAX_VALUE);
                    lastPos.put(pnorm, (int) (o.optLong("ts", 0) / 1000));
                }
            } catch (Exception ignored) {}
            java.util.List<Row> called = new java.util.ArrayList<>();
            // v5.5: ager din-er somoy jate notun din-e na dekha jay — age sobar somoy muchhe, tarpor ajker called-der-tai bosbe.
            for (Row rr : adapter.getAll()) rr.statusTime = "";
            for (java.util.Map.Entry<String, String[]> en : latest.entrySet()) {
                String tm = en.getValue().length > 2 && en.getValue()[2] != null ? en.getValue()[2] : "";
                Row m = mainByNorm.get(en.getKey());
                if (m != null) { m.statusTime = tm; called.add(m); }
                else {
                    Row s = new Row(-1, displayNum.get(en.getKey()), en.getValue()[0], en.getValue()[1], "", "", "");
                    s.viewOnly = true;
                    s.statusTime = tm;
                    called.add(s);
                }
            }
            // v4.26: notun call upore, purono niche — log-er sesh row (boro sheetRow)
            // age; HashMap order-e ar ultapalta dekhabe na.
            java.util.Collections.sort(called, (a, b) -> {
                String na = SheetsClient.normLogNumber(a.number), nb = SheetsClient.normLogNumber(b.number);
                int oa = lastOrder.getOrDefault(na, -1), ob = lastOrder.getOrDefault(nb, -1);
                if (oa != ob) return Integer.compare(ob, oa);
                int pa = lastPos.getOrDefault(na, -1), pb = lastPos.getOrDefault(nb, -1);
                return Integer.compare(pb, pa);
            });
            // v5.6 (35-rows fix): sheet-e status ache kintu ajker log-e entry nei (ager diner call)
            // — kono tab-ei asto na; called tab-er seshe "আগের দিনের কল" badge soho dekhabe.
            for (Row rr : adapter.getAll()) rr.prevDay = false;
            java.util.Set<String> inCalled = new java.util.HashSet<>(latest.keySet());
            for (Row r : adapter.getAll()) {
                if (r.savedStatus == null || r.savedStatus.isEmpty()) continue;
                String n = SheetsClient.normLogNumber(r.number);
                if (n.isEmpty() || inCalled.contains(n)) continue;
                r.prevDay = true; r.statusTime = "";
                called.add(r);
            }
            applyDrafts(called, true); adapter.setCalledRows(called, day);
            // Batch feature #2: prottek number-er ager call-er itihas (log theke, proti
            // date-e sesh row-tai — eki din-e ekadhik save hole ekbar dekhabe).
            java.util.Map<String, java.util.TreeMap<String, String>> byNum = new java.util.HashMap<>();
            if (logs != null) for (REntry e : logs) {
                if (e == null) continue;
                String d0 = cellE(e, 0);
                if (d0 == null || d0.isEmpty()) continue;
                String st = cellE(e, 2);
                if (st == null || st.isEmpty()) continue;
                String num = RowAdapter.dialNumber(cellE(e, 1));
                if (num.isEmpty()) continue;
                String tm = cellE(e, 4);
                String label = displayDay(d0) + " " + ("call connected".equalsIgnoreCase(st.trim()) ? "Connected" : "Not Connected") + (tm != null && !tm.isEmpty() ? " " + formatTimeBn(tm) : "") + ("Yes".equals(cellE(e, 3)) ? " ✓" : "");
                java.util.TreeMap<String, String> m = byNum.get(num);
                if (m == null) { m = new java.util.TreeMap<>(); byNum.put(num, m); }
                m.put(d0, label);
            }
            java.util.Map<String, java.util.List<RowAdapter.Hist>> hist = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, java.util.TreeMap<String, String>> en : byNum.entrySet()) {
                java.util.List<RowAdapter.Hist> l = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, String> d : en.getValue().entrySet()) l.add(new RowAdapter.Hist(d.getKey(), d.getValue()));
                hist.put(en.getKey(), l);
            }
            adapter.setHistory(hist);
        } catch (Exception ignored) {}
    }

    /** v4.19 REGISTRY read: shared log-er Registry tab theke ei agent-er {reportId, legacyId}.
     *  Registry kokhono main sheet-e khonja hoy na — shudhu shared log file-e. */
    private String[] registryEntryForAgent() {
        try {
            if (token == null || agentId == null) return null;
            String sharedId = CONFIG_FILE_ID;
            if (sharedId == null) return null;
            return SheetsClient.getRegistryEntry(token, sharedId, agentId);
        } catch (Exception e) { return null; }
    }

    /** v4.19 REGISTRY backfill: Drive search-e pawa ID registry-te likhe rakha (best-effort,
     *  kokhono main kaj atkabe na). Porer version-e scope soranor age coverage ei poth-e gore uthbe. */
    private void backfillRegistry(String reportId, String legacyId) {
        try {
            if (token == null || agentId == null) return;
            if (reportId == null && legacyId == null) return;
            String sharedId = CONFIG_FILE_ID;
            if (sharedId == null) return;
            SheetsClient.upsertRegistryEntry(token, sharedId, agentId, reportId, legacyId);
        } catch (Exception ignored) { /* backfill kokhono main kaj atkabe na */ }
    }

    /** v4.19: Generate-er somoy "Telesales Daily Reports" file-er Reports tab-e ek row.
     *  File na thakle / access na thakle Generate atkabe na — soft note debe, report text thik thakbe. */
    private String appendDailyReport(int total, int conn, List<REntry> regs, List<REntry> ftds, double sum, List<REntry> logs, String day, List<REntry> probs) {
        try {
            if (token == null || agentId == null) return "";
            String dailyId = writeSheetFor(currentConfig(), "DAILY", day);
            if (dailyId == null) dailyId = DAILY_REPORTS_ID;
            if (dailyId == null) return "ডেইলি রিপোর্টস ফাইলে জমা হয়নি (ফাইল পাইনি) — রিপোর্ট ঠিক আছে, কপি করতে পারবেন";
            SheetsClient.ensureDailyTab(token, dailyId);
            StringBuilder probsText = new StringBuilder();
            for (REntry r : probs) { if (probsText.length() > 0) probsText.append("; "); probsText.append(cellE(r, 1)); }
            SheetsClient.appendReportRow(token, dailyId, "Reports", new String[]{displayDay(day), agentId, String.valueOf(total), String.valueOf(conn), String.valueOf(regTotalCount(regs)), String.valueOf(ftdTotalCount(ftds)), fmtAmount(sum), String.valueOf(countAutoInvalid(logs, day)), probsText.length() == 0 ? "-" : probsText.toString(), dhaka("dd-MM-yyyy hh:mm a")});
            return "";
        } catch (Exception e) {
            return "ডেইলি রিপোর্টস ফাইলে জমা হয়নি (" + e.getMessage() + ") — রিপোর্ট ঠিক আছে, কপি করতে পারবেন";
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            outState.putString("search", searchBox == null || searchBox.getText() == null ? "" : searchBox.getText().toString());
            outState.putString("selectedDate", selectedDate);
            outState.putString("reportDate", reportDate);
            outState.putInt("section", currentSection);
            outState.putString("probText", probText == null || probText.getText() == null ? "" : probText.getText().toString());
            outState.putString("ftdStepAmount", ftdStepAmount == null || ftdStepAmount.getText() == null ? "" : ftdStepAmount.getText().toString());
        } catch (Exception ignored) {}
        // NOTE: row-er vitore lekha kintu Save na-chapa remark (RecyclerView holder-e thake)
        // ekhane dhora hoyni — holder recycle hoy bole seta dhorte adapter-e alada refactor lagbe.
    }

    @Override protected void onDestroy() {
        try { if (updateReceiver != null) unregisterReceiver(updateReceiver); } catch (Exception ignored) {}
        updateReceiver = null;
        super.onDestroy();
    }
}
