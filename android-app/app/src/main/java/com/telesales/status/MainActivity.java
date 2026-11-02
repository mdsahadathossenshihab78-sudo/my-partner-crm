package com.telesales.status;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import org.json.JSONObject;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList; import android.graphics.Color; import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent; import android.webkit.CookieManager; import android.webkit.WebView; import android.webkit.WebViewClient;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private static final String SHEET_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String SHEET_ID = "10Lp_BOTSk-HNdNt-lyMuD57V0oW8qP4zhGNDi9KLgDE";
    /** Admin Gmail: ei Gmail-e login korlei Admin Panel khulbe, Agent ID lagbe na. */
    private static final String ADMIN_EMAIL = "shihabno.18@gmail.com";

    private GoogleSignInClient signInClient;
    private String token;
    private String agentId;
    private String statusLogSheetId; private String statusLogTab = "Status Log"; private static final String SHARED_STATUS_LOG_TITLE = "Telesales Status Log";
    private String selectedDate = dhakaDate(); private String reportDate = dhakaDate();
    private Map<String,String> lastStatusMap = new HashMap<>();
    private RowAdapter adapter;
    private View loginView, mainView;
    private View agentView;
    private TextView agentError, agentSubtitle;
    private TextInputEditText agentInput;
    private View adminView;
    private TextView adminStatus;
    private TextInputEditText adminAgentInput, adminNumbersInput;
    private TextView loginError, avatarView, statusText, weekSummary;
    private TextView statConnDay, statNotConnDay, statValidDay, statInvDay, statRegDay, statFtdDay, statFtdAmtDay;
    private MaterialButton dateBtn; private Button reportPrevBtn, reportDateBtn, reportNextBtn, reportTodayBtn; private TextView reportDateNote;
    private TextInputEditText searchBox;
    private View reportView, reportOutputCard;
    private View guideView;
    private TextView guideText;
    private View dashboardView, listContent; private TextView statTotalCallDay, statTotalNumDay; private View bottomMenuBar, websiteView; private WebView websiteWeb; private boolean websiteLoaded = false;
    private int currentSection = 0;
    private TextView reportStatus, reportOutput, ftdTotal, ftdStepCount, regStepCount, invAutoText;
    private LinearLayout regList, invList, probList, ftdStepAmountRow;
    private TextInputEditText regCount, ftdStepAmount, invNumber, probText;
    
    private long updateDownloadId = -1;
    private BroadcastReceiver updateReceiver;
    private Spinner invReason;
    private String reportSheetId;
    /** Report sheet-er ekta row: cells + sheet-er row number (delete korar jonno). */
    static class REntry { final String[] cells; final int sheetRow; REntry(String[] c, int r) { cells = c; sheetRow = r; } }
    private List<REntry> regToday = new ArrayList<>(), ftdToday = new ArrayList<>(), invToday = new ArrayList<>(), probToday = new ArrayList<>();
    private final String[] reasons = {"কল ধরেনি", "Not connected", "বন্ধ", "ভুল নাম্বার"};

    /** Bangla user manual: agent-ra kobe Connected/Not Connected/Valid Yes/No dibe, remark, report. */
    private static final String GUIDE_TEXT =
        "১. লগইন\n" +
        "Continue with Google চাপুন, আপনার Gmail দিয়ে লগইন করুন। প্রথমবার Agent ID চাইবে — আপনার ট্যাবের নাম (যেমন bdxsukuna30) লিখুন। এরপর থেকে ওই Gmail-এ লগইন করলেই সরাসরি আপনার লিস্ট খুলবে। উপরের অক্ষরে চাপ দিলে Logout হবে।\n\n" +
        "২. কল লিস্ট\n" +
        "কল লিস্টে নাম্বারগুলো থাকে। কল বাকি ট্যাবে যেসব নাম্বারে এখনো কল করা হয়নি, সেগুলো থাকে। কল করে স্ট্যাটাস দিলে সেটা কল করা হয়েছে ট্যাবে চলে যাবে। ড্যাশবোর্ডে তারিখ বদলে ওই দিনের হিসাব দেখতে পারবেন। Search বক্সে নাম্বার লিখে খুঁজতে পারবেন। Refresh চাপলে শিট থেকে নতুন লিস্ট আসবে।\n\n" +
        "৩. কল করা ও স্ট্যাটাস দেওয়া\n" +
        "Call চাপলে কল যাবে। Copy চাপলে নাম্বার কপি হবে। কল করার পর স্ট্যাটাস দিন, তারপর Save চাপুন। Save না চাপলে শিটে কিছু বদলাবে না।\n\n" +
        "কল Connected কখন দিবেন:\n" +
        "যখন কলটা ধরেছে — অর্থাৎ কেউ ফোন রিসিভ করেছে — তখন Connected দিবেন।\n\n" +
        "কল Not Connected কখন দিবেন:\n" +
        "যখন কেউ কল রিসিভ করেনি, বা ফোন বন্ধ, বা কল কেটে দিয়েছে — তখন Not Connected দিবেন।\n\n" +
        "Valid কখন Yes দিবেন:\n" +
        "কল ধরেছে এবং সে ডিপোজিট করবে — এখনই করুক, বা বিকালে করুক, বা সন্ধ্যায় করুক — ডিপোজিট করার কথা যারা বলেছে, তাদের Yes দিবেন।\n\n" +
        "Valid কখন No দিবেন:\n" +
        "যারা ডিপোজিট করবে না; যারা গেম খেলে না; ফোন ধরেছে কিন্তু ডিপোজিট করবে না বলেছে; বা যার নাম্বার দিয়ে অ্যাকাউন্ট খোলা নেই — তাদের No দিবেন।\n\n" +
        "১ম কল রিমার্ক:\n" +
        "প্রথম কল করে যা জানতে পারলেন — ফোন ধরেছে না ধরেনি, ডিপোজিট করতে চেয়েছে কি চায়নি, যেকোনো কথা — এখানে লিখে দিতে পারবেন।\n\n" +
        "২য় কল রিমার্ক:\n" +
        "দ্বিতীয়বার কল করে যা জানলেন, সেটা এখানে লিখবেন।\n\n" +
        "অন্য নাম্বার (Others Number):\n" +
        "কাস্টমারের যদি আরেকটা এক্সট্রা নাম্বার থাকে, সেটা এখানে অ্যাড করতে পারবেন।\n\n" +
        "৪. Report\n" +
        "নিচের মেনু থেকে Report-এ গেলে দিনের হিসাব দেওয়া যায়:\n" +
        "• Registration: আজ যতটা রেজিস্ট্রেশন হয়েছে, শুধু সংখ্যাটা লিখুন।\n" +
        "• FTD: + চাপলে একটা করে বাড়বে; প্রতিবার নিচে ওই FTD-র টাকাটা লিখতে হবে। শুধু মোট সংখ্যা আর মোট টাকা দেখাবে, আলাদা আলাদা FTD-র টাকা দেখাবে না। ভুল হলে − চাপলে আজকের শেষ FTD-টা বাদ যাবে।\n" +
        "• Invalid: ").append(countAutoInvalid(logs, today)).append(" যে নাম্বারে কল ঢোকেনি বা নাম্বার ভুল, সেই নাম্বার আর কারণ (কল ধরেনি / Not connected / বন্ধ / ভুল নাম্বার) দিন।\n" +
        "• Problems: কাজের সময় কোনো সমস্যা হলে এখানে লিখুন।\n" +
        "কোনো এন্ট্রি ভুল হলে পাশের ✕ চাপলে সেটা বাদ যাবে। Generate Report চাপলে আজকের তারিখ, সময়, Agent ID, মোট কল, Connected, Registration, FTD, মোট টাকা, Invalid আর Problems দিয়ে রিপোর্ট তৈরি হবে। Copy চাপলে পুরো রিপোর্ট কপি হবে — তারপর যেখানে পাঠাতে চান, পেস্ট করে দিতে পারবেন।\n\n" +
        "৫. Website বাটন\n" +
        "নিচের মেনু থেকে Website চাপলে আপনার সাইটটা অ্যাপের ভিতরেই খুলবে, আর নিচে মেনু দেখা যাবে। প্রথমবার ইউজারনেম-পাসওয়ার্ড দিয়ে লগইন করতে হবে; তারপর থেকে লগইন মনে থাকবে, আপডেট দিলেও ভুলবে না। মেনু থেকে যেকোনো অপশনে যেতে পারবেন।\n\n" +
        "৬. দিনের হিসাব (ড্যাশবোর্ড)\n" +
        "ড্যাশবোর্ডে ওই দিনের Connected, Not Connected, Valid, Invalid, FTD, FTD টাকা আর Registration দেখায়। নিচে ৭ দিনের মোট হিসাবের লাইনও থাকে।" + "উপরে মোট কল ওই দিনের সব কল, আর মোট নাম্বার লিস্টের সব নাম্বার দেখায়।";

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
        statFtdAmtDay = findViewById(R.id.statFtdAmtDay); statTotalCallDay = findViewById(R.id.statTotalCallDay); statTotalNumDay = findViewById(R.id.statTotalNumDay);
        weekSummary = findViewById(R.id.weekSummary);
        dateBtn = findViewById(R.id.btnDate);
        statusText = findViewById(R.id.statusText);
        searchBox = findViewById(R.id.searchBox);
        reportView = findViewById(R.id.reportView);
        guideView = findViewById(R.id.guideView); bottomMenuBar = findViewById(R.id.bottomMenuBar); websiteView = findViewById(R.id.websiteView); websiteWeb = findViewById(R.id.websiteWeb); websiteWeb.getSettings().setJavaScriptEnabled(true); websiteWeb.getSettings().setDomStorageEnabled(true); CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(websiteWeb, true); websiteWeb.setWebViewClient(new WebViewClient());
        guideText = findViewById(R.id.guideText);
        guideText.setText(GUIDE_TEXT);
        dashboardView = findViewById(R.id.dashboardView);
        listContent = findViewById(R.id.listContent);
        reportOutputCard = findViewById(R.id.reportOutputCard);
        reportStatus = findViewById(R.id.reportStatus);
        reportOutput = findViewById(R.id.reportOutput);
        regStepCount = findViewById(R.id.regStepCount); invAutoText = findViewById(R.id.invAutoText);


        invNumber = findViewById(R.id.invNumber);
        invReason = findViewById(R.id.invReason);
        probText = findViewById(R.id.probText);
        regList = findViewById(R.id.regList);
        ftdStepCount = findViewById(R.id.ftdStepCount);
        ftdStepAmountRow = findViewById(R.id.ftdStepAmountRow);
        ftdStepAmount = findViewById(R.id.ftdStepAmount);
        ftdTotal = findViewById(R.id.ftdTotal);
        invList = findViewById(R.id.invList);
        probList = findViewById(R.id.probList);
        agentView = findViewById(R.id.agentView);
        agentInput = findViewById(R.id.agentInput);
        adminView = findViewById(R.id.adminView);
        adminAgentInput = findViewById(R.id.adminAgentInput);
        adminNumbersInput = findViewById(R.id.adminNumbersInput);
        adminStatus = findViewById(R.id.adminStatus);
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
        avatarView.setOnClickListener(v -> {
            signInClient.signOut().addOnCompleteListener(t -> {
                token = null; agentId = null; reportSheetId = null; statusLogSheetId = null;
                getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().remove("agent_id").remove("agent_email").apply();
                showLogin();
                Toast.makeText(this, "Logout hoyeche", Toast.LENGTH_SHORT).show();
            });
        });
        findViewById(R.id.refreshBtn).setOnClickListener(v -> loadRows());

        ArrayAdapter<String> ra = new ArrayAdapter<>(this, R.layout.spinner_item, reasons);
        ra.setDropDownViewResource(R.layout.spinner_dropdown_item);
        // v4.18: manual Invalid form removed; Invalid is now an auto-count from the Status Log.

        findViewById(R.id.menuDashboardBtn).setOnClickListener(v -> { applySection(0); loadDayStats(); });
        findViewById(R.id.menuHomeBtn).setOnClickListener(v -> applySection(1));
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
                    selectedDate = nd; updateDateBtn(); loadDayStats();
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            } catch (Exception ignored) {}
        });
        updateDateBtn();
        findViewById(R.id.agentContinueBtn).setOnClickListener(v -> submitAgent());
        findViewById(R.id.agentBackBtn).setOnClickListener(v -> {
            signInClient.signOut().addOnCompleteListener(t -> showLogin());
        });
        findViewById(R.id.adminAddBtn).setOnClickListener(v -> submitAdminNumbers());
        findViewById(R.id.adminLogoutBtn).setOnClickListener(v -> {
            signInClient.signOut().addOnCompleteListener(t -> {
                token = null; agentId = null; reportSheetId = null; statusLogSheetId = null;
                getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().remove("agent_id").remove("agent_email").apply();
                showLogin();
                Toast.makeText(this, "Logout hoyeche", Toast.LENGTH_SHORT).show();
            });
        });
        findViewById(R.id.btnTabPending).setOnClickListener(v -> { adapter.setTab(0); setTabUI(); });
        findViewById(R.id.btnTabDone).setOnClickListener(v -> { adapter.setTab(1); setTabUI(); });
        findViewById(R.id.regPlusBtn).setOnClickListener(v -> { addReportEntry("Registration", new String[]{dhakaDate(), "1"}, () -> loadReportData()); });
            findViewById(R.id.regMinusBtn).setOnClickListener(v -> { REntry last = null; for (REntry e : regToday) if (last == null || e.sheetRow > last.sheetRow) last = e; if (last == null) { Toast.makeText(this, "Registration nei", Toast.LENGTH_SHORT).show(); return; } deleteReportEntry("Registration", last); });
            
        findViewById(R.id.ftdPlusBtn).setOnClickListener(v -> {
            if (ftdStepAmountRow.getVisibility() == View.VISIBLE) { ftdStepAmount.requestFocus(); return; }
            int cur = ftdToday == null ? 0 : ftdTotalCount(ftdToday);
            ftdStepAmount.setHint((cur + 1) + " নম্বর FTD কত টাকার?");
            ftdStepAmountRow.setVisibility(View.VISIBLE);
            ftdStepAmount.requestFocus();
        });
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
            if (ftdToday != null) for (REntry e : ftdToday) if (last == null || e.sheetRow > last.sheetRow) last = e;
            if (last == null) { Toast.makeText(this, "আজ কোনো FTD নেই", Toast.LENGTH_SHORT).show(); return; }
            deleteReportEntry("FTD", last);
        });
        // v4.18: manual Invalid entry removed.
            findViewById(R.id.probAddBtn).setOnClickListener(v -> {
            String t = txt(probText);
            if (t.isEmpty()) { Toast.makeText(this, "Problem likhun", Toast.LENGTH_SHORT).show(); return; }
            addReportEntry("Problems", new String[]{dhakaDate(), t}, () -> probText.setText(""));
        });
        findViewById(R.id.generateBtn).setOnClickListener(v -> generateReport());
        findViewById(R.id.copyBtn).setOnClickListener(v -> {
            String t = reportOutput.getText() == null ? "" : reportOutput.getText().toString();
            if (t.isEmpty()) { Toast.makeText(this, "Age Generate korun", Toast.LENGTH_SHORT).show(); return; }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Daily Report", t));
            Toast.makeText(this, "Copy hoyeche", Toast.LENGTH_SHORT).show();
        });

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter((row, btn) -> {
            btn.setEnabled(false); btn.setText("সেভ হচ্ছে...");
            new Thread(() -> {
                try {
                    SheetsClient.updateRow(token, SHEET_ID, agentId, row);
                    String key = row.number == null ? "" : row.number.trim();
                    String now = (row.status == null ? "" : row.status) + "|" + (row.valid == null ? "" : row.valid);
                    String old = lastStatusMap.get(key);
                    String logError = null;
                    if (!now.equals(old)) {
                        try {
                            ensureStatusLogSheet();
                            SheetsClient.appendReportRow(token, statusLogSheetId, statusLogTab, new String[]{dhakaDate(), RowAdapter.dialNumber(row.number), row.status == null ? "" : row.status, row.valid == null ? "" : row.valid});
                            lastStatusMap.put(key, now);
                            SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                            if (p.getString("log_anchor_date_" + agentId, null) == null)
                                p.edit().putString("log_anchor_date_" + agentId, dhakaDate()).apply();
                        } catch (Exception le) { logError = le.getMessage(); }
                    }
                    String finalLogError = logError;
                    runOnUiThread(() -> {
                        btn.setEnabled(true); btn.setText("Save korun");
                        Toast.makeText(this, "রো " + row.row + " শিটে আপডেট হয়েছে" + (finalLogError != null ? " (log-e joma hoyni: " + finalLogError + ")" : ""), Toast.LENGTH_LONG).show();
                        adapter.filter(searchBox.getText() == null ? "" : searchBox.getText().toString());
                        loadDayStats();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btn.setEnabled(true); btn.setText("Save korun");
                        Toast.makeText(this, "সেভ হয়নি: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
        recycler.setAdapter(adapter);
        setTabUI();

        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { adapter.filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

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

    @Override public void onBackPressed() { if (websiteView != null && websiteView.getVisibility() == View.VISIBLE && websiteWeb != null && websiteWeb.canGoBack()) { websiteWeb.goBack(); } else { super.onBackPressed(); } } private void showLogin() {
        loginView.setVisibility(View.VISIBLE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.GONE);
        guideView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.GONE); websiteView.setVisibility(View.GONE);
    }
    @Override protected void onResume() { super.onResume(); if (mainView != null && mainView.getVisibility() == View.VISIBLE && token != null) { checkForUpdate(); } } private void showMain() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
        applySection(currentSection);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        adminView.setVisibility(View.GONE);
        guideView.setVisibility(View.GONE); websiteView.setVisibility(View.GONE); bottomMenuBar.setVisibility(View.VISIBLE);
        setAgentSubtitle();
        loadDayStats();
        checkForUpdate();
    }
    /** Niche menu: 0 = Dashboard (date + sob hisab), 1 = Home (number list). */
    private static class StatusLogTarget { String sheetId; String tab; boolean shared; StatusLogTarget(String s, String t, boolean sh) { sheetId = s; tab = t; shared = sh; } } private static String cell(String[] r, int i) { return (r != null && i < r.length && r[i] != null) ? r[i] : ""; } private static String logRowKey(String[] r) { return cell(r, 0) + "|" + cell(r, 1) + "|" + cell(r, 2) + "|" + cell(r, 3); } private StatusLogTarget resolveLegacyStatusLogTarget() { String legacyId = resolveSharedId("status_log_sheet_id_" + agentId, null, "Telesales Status Log - " + agentId); if (legacyId == null) return null; return new StatusLogTarget(legacyId, "Status Log", false); } private void migrateLegacyStatusLogToShared(String sharedId) throws Exception { SharedPreferences p = getSharedPreferences("telesales_prefs", MODE_PRIVATE); String flagKey = "status_log_migrated_to_shared_" + agentId; if (p.getBoolean(flagKey, false)) return; StatusLogTarget legacy = resolveLegacyStatusLogTarget(); if (legacy == null || sharedId.equals(legacy.sheetId)) { p.edit().putBoolean(flagKey, true).apply(); return; } List<String[]> legacyRows = SheetsClient.getReportRows(token, legacy.sheetId, "Status Log"); List<String[]> sharedRows = SheetsClient.getReportRows(token, sharedId, agentId); java.util.Set<String> seen = new java.util.HashSet<>(); for (String[] r : sharedRows) seen.add(logRowKey(r)); List<String[]> missing = new ArrayList<>(); for (String[] r : legacyRows) { if (r == null || r.length == 0) continue; String[] four = new String[]{cell(r, 0), cell(r, 1), cell(r, 2), cell(r, 3)}; if (!seen.contains(logRowKey(four))) missing.add(four); } SheetsClient.appendReportRows(token, sharedId, agentId, missing); p.edit().putBoolean(flagKey, true).apply(); } private StatusLogTarget resolveStatusLogTarget() throws Exception { String sharedId = SheetsClient.findSpreadsheetByName(token, SHARED_STATUS_LOG_TITLE); if (sharedId != null) { try { SheetsClient.ensureSheetTab(token, sharedId, agentId); } catch (Exception e) { StatusLogTarget legacy = resolveLegacyStatusLogTarget(); if (legacy != null) return legacy; throw e; } try { migrateLegacyStatusLogToShared(sharedId); } catch (Exception ignored) { } return new StatusLogTarget(sharedId, agentId, true); } return resolveLegacyStatusLogTarget(); } private void updateMenuState(int active) { MaterialButton[] btns = new MaterialButton[]{(MaterialButton) findViewById(R.id.menuDashboardBtn), (MaterialButton) findViewById(R.id.menuHomeBtn), (MaterialButton) findViewById(R.id.menuReportBtn), (MaterialButton) findViewById(R.id.menuWebsiteBtn), (MaterialButton) findViewById(R.id.menuGuideBtn)}; int dim = Color.parseColor("#9AA3C7"); for (int i = 0; i < btns.length; i++) { MaterialButton b = btns[i]; if (b == null) continue; boolean on = (i == active); b.setBackgroundResource(on ? R.drawable.btn_gradient : R.drawable.glass_field); b.setTextColor(on ? Color.WHITE : dim); b.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL); b.setIconTint(ColorStateList.valueOf(on ? Color.WHITE : dim)); } } private void applySection(int sec) {
        currentSection = sec;
        dashboardView.setVisibility(sec == 0 ? View.VISIBLE : View.GONE);
        listContent.setVisibility(sec == 1 ? View.VISIBLE : View.GONE); reportView.setVisibility(View.GONE); guideView.setVisibility(View.GONE); websiteView.setVisibility(View.GONE); mainView.setVisibility(View.VISIBLE); bottomMenuBar.setVisibility(View.VISIBLE);
        updateMenuState(sec);
        findViewById(R.id.menuHomeBtn).setBackgroundResource(sec == 1 ? R.drawable.btn_gradient : R.drawable.glass_field);
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
    }
    private void setAgentSubtitle() {
        if (agentSubtitle != null && agentId != null) agentSubtitle.setText("Agent: " + agentId);
    }
    /** Admin: paste kora number-gulo target Agent-er tab-er seshe, paste kora order-e jog kore.
     *  Agertheke thaka number ba paste-er vitore duplicate thakle skip hobe (abar boshbe na). */
    private void submitAdminNumbers() {
        String target = txt(adminAgentInput);
        String pasted = adminNumbersInput.getText() == null ? "" : adminNumbersInput.getText().toString();
        if (target.isEmpty()) { adminStatus.setText("Agent ID din"); return; }
        if (pasted.trim().isEmpty()) { adminStatus.setText("Number paste korun"); return; }
        if (token == null) { showLogin(); return; }
        adminStatus.setText("Jog kora hocche...");
        new Thread(() -> {
            try {
                List<String> tabs = SheetsClient.getTabs(token, SHEET_ID);
                String match = null;
                for (String t : tabs) if (t.equals(target)) { match = t; break; }
                if (match == null) for (String t : tabs) if (t.equalsIgnoreCase(target)) { match = t; break; }
                if (match == null) throw new Exception("Ei Agent ID-te kono list pai ni — ID ta abar miliye dekhun");
                String finalMatch = match;
                java.util.Set<String> existing = new java.util.HashSet<>();
                for (Row r : SheetsClient.getRows(token, SHEET_ID, finalMatch)) {
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
                if (toAdd.isEmpty()) throw new Exception("Notun kono number pai ni" + (dup > 0 ? " (" + dup + "ta duplicate skip)" : "") + (bad > 0 ? " (" + bad + "ta thik number na)" : ""));
                SheetsClient.appendNumbers(token, SHEET_ID, finalMatch, toAdd);
                int added = toAdd.size();
                int dupFinal = dup, badFinal = bad;
                runOnUiThread(() -> {
                    adminStatus.setText(added + "ta number " + finalMatch + "-er list-e jog hoyeche" + (dupFinal > 0 ? " (" + dupFinal + "ta duplicate skip)" : "") + (badFinal > 0 ? " (" + badFinal + "ta thik number na)" : ""));
                    adminNumbersInput.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> adminStatus.setText("Hoyni: " + e.getMessage()));
            }
        }).start();
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
        if (id.isEmpty()) { agentError.setText("Agent ID likhun"); return; }
        if (token == null) { showLogin(); return; }
        agentError.setText("List check kora hocche...");
        new Thread(() -> {
            try {
                List<String> tabs = SheetsClient.getTabs(token, SHEET_ID);
                String match = null;
                for (String t : tabs) if (t.equals(id)) { match = t; break; }
                if (match == null) for (String t : tabs) if (t.equalsIgnoreCase(id)) { match = t; break; }
                if (match == null) throw new Exception("Ei Agent ID-te kono list pai ni — ID ta abar miliye dekhun");
                String finalMatch = match;
                SharedPreferences prefs1 = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                prefs1.edit().putString("agent_id", finalMatch).putString("agent_email", prefs1.getString("current_email", null)).apply();
                runOnUiThread(() -> { agentId = finalMatch; reportSheetId = null; statusLogSheetId = null; setAgentSubtitle(); showMain(); loadRows(); });
            } catch (Exception e) {
                runOnUiThread(() -> agentError.setText("Hoyni: " + e.getMessage()));
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
                    // Admin Gmail hole Agent ID chhara-i Admin Panel khulbe.
                    if (acct.getEmail() != null && ADMIN_EMAIL.equalsIgnoreCase(acct.getEmail())) {
                        showAdmin();
                        return;
                    }
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
            } catch (UserRecoverableAuthException e) {
                runOnUiThread(() -> startActivityForResult(e.getIntent(), RC_AUTH));
            } catch (Exception e) {
                runOnUiThread(() -> { loginError.setText("লগইন হয়নি: " + e.getMessage()); showLogin(); });
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
                loginError.setText("লগইন হয়নি: " + e.getMessage());
            }
        } else if (requestCode == RC_AUTH) {
            GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
            if (acct != null) fetchToken(acct); else showLogin();
        }
    }

    private void loadRows() {
        if (token == null) { showLogin(); return; }
        if (agentId == null) { showAgent(); return; }
        statusText.setText("শিট থেকে লোড হচ্ছে...");
        new Thread(() -> {
            try {
                List<Row> rows = SheetsClient.getRows(token, SHEET_ID, agentId);
                runOnUiThread(() -> {
                    adapter.setRows(rows); statTotalNumDay.setText(String.valueOf(rows.size()));
                    adapter.filter(searchBox.getText() == null ? "" : searchBox.getText().toString());
                    statusText.setText("শিটের সাথে সংযুক্ত — " + rows.size() + "টি রো");
                    lastStatusMap.clear();
                    for (Row r : rows) lastStatusMap.put(r.number == null ? "" : r.number.trim(), (r.status == null ? "" : r.status) + "|" + (r.valid == null ? "" : r.valid));
                    loadDayStats();
                });
            } catch (Exception e) {
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

    private void ensureStatusLogSheet() throws Exception { StatusLogTarget t = resolveStatusLogTarget(); if (t != null) { statusLogSheetId = t.sheetId; statusLogTab = t.tab; return; } statusLogSheetId = SheetsClient.createStatusLogSpreadsheet(token, agentId); statusLogTab = "Status Log"; SharedPreferences p = getSharedPreferences("telesales_prefs", MODE_PRIVATE); p.edit().putString("status_log_sheet_id_" + agentId, statusLogSheetId).apply(); } private void ensureStatusLogSheetLegacyUnused() throws Exception {
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
        selectedDate = nd; updateDateBtn(); loadDayStats();
    }

    /** Nirbachito diner + oi diner 7-diner block-er hisab (log + report file theke). */
    private void loadDayStats() {
        if (token == null || agentId == null) return;
        String day = selectedDate;
        new Thread(() -> {
            try {
                SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
                StatusLogTarget logTarget = resolveStatusLogTarget(); if (logTarget == null) return; String logId = logTarget.sheetId;
                String repId = resolveSharedId("report_sheet_id_" + agentId,
                        "bdxsukuna30".equals(agentId) ? "report_sheet_id" : null, "Telesales Report - " + agentId);
                List<REntry> logs = logId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, logId, logTarget.tab));
                List<REntry> invs = repId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, repId, "Invalid"));
                List<REntry> regs = repId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, repId, "Registration"));
                List<REntry> ftds = repId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, repId, "FTD"));
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
                String finalBlockStart = blockStart, finalBlockEnd = blockEnd;
                int finalConn = conn, finalValid = valid, finalInv = inv, finalReg = reg, finalFtdN = ftdN, finalConnW = connW, finalFtdNW = ftdNW, finalNotConn = notConn, finalNotConnW = notConnW, finalTotalCall = totalCall;
                double finalFtdSum = ftdSum, finalFtdSumW = ftdSumW;
                runOnUiThread(() -> {
                    statConnDay.setText(String.valueOf(finalConn)); statTotalCallDay.setText(String.valueOf(finalTotalCall));
                    statNotConnDay.setText(String.valueOf(finalNotConn));
                    statValidDay.setText(String.valueOf(finalValid));
                    statInvDay.setText(String.valueOf(finalInv));
                    statRegDay.setText(String.valueOf(finalReg));
                    statFtdDay.setText(String.valueOf(finalFtdN));
                    statFtdAmtDay.setText("৳" + fmtAmount(finalFtdSum));
                    weekSummary.setText("Ei 7 din (" + displayDay(finalBlockStart) + " – " + displayDay(finalBlockEnd) + "): Connected " + finalConnW + " • Not Connected " + finalNotConnW + " • FTD " + finalFtdNW + " (৳" + fmtAmount(finalFtdSumW) + ")");
                });
            } catch (Exception e) {
                runOnUiThread(() -> weekSummary.setText("Hisab load hoyni: " + e.getMessage()));
            }
        }).start();
    }

    private void showWebsite() { loginView.setVisibility(View.GONE); agentView.setVisibility(View.GONE); mainView.setVisibility(View.GONE); reportView.setVisibility(View.GONE); guideView.setVisibility(View.GONE); adminView.setVisibility(View.GONE); updateMenuState(3); websiteView.setVisibility(View.VISIBLE); bottomMenuBar.setVisibility(View.VISIBLE); findViewById(R.id.menuDashboardBtn).setBackgroundResource(R.drawable.glass_field); findViewById(R.id.menuHomeBtn).setBackgroundResource(R.drawable.glass_field); if (!websiteLoaded) { websiteWeb.loadUrl("https://www.12bajeevip.com/m/member/home"); websiteLoaded = true; } }

    /** Notun version ele purono app-e update popup: Update chaple APK download + install prompt asbe. */
    private JSONObject fetchVersionJson() throws Exception { String[] urls = { "https://raw.githubusercontent.com/mdsahadathossenshihab1-lgtm/telesales-status-app/main/version.json", "https://telesales-status-app.vercel.app/version.json" }; Exception lastError = null; for (String urlString : urls) { try { java.net.URL url = new java.net.URL(urlString); java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(15000); java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream())); StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close(); return new JSONObject(sb.toString()); } catch (Exception e) { lastError = e; } } if (lastError != null) throw lastError; throw new Exception("version fetch failed"); } private void checkForUpdate() {
        
        
        new Thread(() -> {
            try {
                JSONObject j = fetchVersionJson();
                
                
                
                
                
                
                
                int remoteCode = j.getInt("versionCode");
                String remoteName = j.optString("versionName", "");
                String apkUrl = j.optString("apkUrl", "");
                int myCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (remoteCode > myCode && !apkUrl.isEmpty()) {
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
            req.setTitle("Telesales app update");
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
    private static String fmtAmount(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
    private static List<REntry> withRows(List<String[]> rows) {
        List<REntry> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) out.add(new REntry(rows.get(i), i + 2)); // A2:Z pora hoy, tai i-tomo row = sheet row i+2
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

    private void ensureReportSheet() throws Exception {
        if (reportSheetId != null) return;
        SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
        reportSheetId = resolveSharedId("report_sheet_id_" + agentId,
                "bdxsukuna30".equals(agentId) ? "report_sheet_id" : null, "Telesales Report - " + agentId);
        if (reportSheetId == null) {
            reportSheetId = SheetsClient.createReportSpreadsheet(token, agentId);
            p.edit().putString("report_sheet_id_" + agentId, reportSheetId).apply();
        }
    }

    private boolean isReportToday() { return reportDate.equals(dhakaDate()); } private void updateReportDateBtn() { if (reportDateBtn != null) reportDateBtn.setText(displayDay(reportDate)); } private void updateReportEditState() { boolean isToday = isReportToday(); int[] ids = {R.id.regPlusBtn, R.id.regMinusBtn, R.id.ftdPlusBtn, R.id.ftdMinusBtn, R.id.ftdStepAmount, R.id.ftdStepAddBtn, R.id.probText, R.id.probAddBtn}; for (int id : ids) { View v = findViewById(id); if (v != null) v.setEnabled(isToday); } View stepRow = findViewById(R.id.ftdStepAmountRow); if (stepRow != null && !isToday) stepRow.setVisibility(View.GONE); if (reportDateNote != null) reportDateNote.setVisibility(isToday ? View.GONE : View.VISIBLE); } private void setReportDate(String nd) { if (nd == null) return; if (nd.compareTo(dhakaDate()) > 0) nd = dhakaDate(); reportDate = nd; updateReportDateBtn(); updateReportEditState(); if (reportOutputCard != null) reportOutputCard.setVisibility(View.GONE); loadReportData(); } private void shiftReportDay(int delta) { try { Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.setTime(parseDay(reportDate)); c.add(Calendar.DAY_OF_MONTH, delta); String nd = fmtDay(c.getTime()); if (nd.compareTo(dhakaDate()) > 0) nd = dhakaDate(); setReportDate(nd); } catch (Exception e) { } } private void loadReportData() {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Report load hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                String today = reportDate;
                regToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Registration")), today);
                ftdToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "FTD")), today);
                invToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Invalid")), today);
                probToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Problems")), today);
                runOnUiThread(() -> { refreshReportLists(); reportStatus.setText(""); loadAutoInvalidForReport(); });
            } catch (Exception e) {
                runOnUiThread(() -> reportStatus.setText("Report load hoyni: " + e.getMessage()));
            }
        }).start();
    }

    private int countAutoInvalid(List<REntry> logs, String date) { java.util.Set<String> seen = new java.util.HashSet<>(); if (logs == null) return 0; for (REntry e : logs) { if (e == null) continue; if (!date.equals(cellE(e, 0))) continue; if (!"not connected".equalsIgnoreCase(cellE(e, 2).trim())) continue; if (!"No".equalsIgnoreCase(cellE(e, 3).trim())) continue; String n = cellE(e, 1).replace(" ", "").replace("-", ""); if (n.startsWith("+880")) n = "0" + n.substring(4); else if (n.startsWith("880")) n = "0" + n.substring(3); else if (!n.startsWith("0")) n = "0" + n; if (!n.isEmpty()) seen.add(n); } return seen.size(); } private void loadAutoInvalidForReport() { final String d = reportDate; new Thread(() -> { try { if (token == null) return; StatusLogTarget t = resolveStatusLogTarget(); if (t == null) return; List<REntry> logs = withRows(SheetsClient.getReportRows(token, t.sheetId, t.tab)); final int c = countAutoInvalid(logs, d); runOnUiThread(() -> { if (invAutoText != null) invAutoText.setText("Invalid: " + c); }); } catch (Exception ignored) { } }).start(); } private void refreshReportLists() {
        if (regStepCount != null) regStepCount.setText(String.valueOf(regTotalCount(regToday)));
        // v4.18: Invalid per-entry list removed; auto-count is shown in invAutoText.
        fillList(probList, probToday, "Problems");
        double sum = 0;
        for (REntry r : ftdToday) {
            try { sum += Double.parseDouble(cellE(r, 2)); } catch (Exception ignored) {}
        }
        ftdStepCount.setText(String.valueOf(ftdTotalCount(ftdToday)));
        ftdTotal.setText((reportDate.equals(dhakaDate()) ? "আজ মোট: " : displayDay(reportDate) + " মোট: ") + ftdTotalCount(ftdToday) + "টা • ৳" + fmtAmount(sum));
    }

    /** Ekta list-er prottek entry-r pase ✕ delete button bosay. */
    private void fillList(LinearLayout parent, List<REntry> list, String tab) {
        parent.removeAllViews();
        if (list.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("ekhono kichu add hoyni");
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
            del.setMinimumWidth(0);
            del.setPadding(20, 8, 20, 8);
            del.setOnClickListener(v -> deleteReportEntry(tab, e));
            row.addView(t);
            if (isReportToday()) row.addView(del);
            parent.addView(row);
        }
    }

    /** Sheet thekeo row-ta muche fele, tarpor list abar load kore. */
    private void deleteReportEntry(String tab, REntry e) { if (!isReportToday()) { Toast.makeText(this, "অতীতের রিপোর্ট শুধু দেখা ও কপি করা যাবে", Toast.LENGTH_SHORT).show(); return; }
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Delete hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                SheetsClient.deleteReportRow(token, reportSheetId, tab, e.sheetRow);
                runOnUiThread(() -> { reportOutputCard.setVisibility(View.GONE); loadReportData(); loadDayStats(); });
            } catch (Exception ex) {
                runOnUiThread(() -> reportStatus.setText("Delete hoyni: " + ex.getMessage()));
            }
        }).start();
    }

    private void addReportEntry(String tab, String[] vals, Runnable clear) { if (!isReportToday()) { Toast.makeText(this, "অতীতের রিপোর্ট শুধু দেখা ও কপি করা যাবে", Toast.LENGTH_SHORT).show(); return; }
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Add hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                SheetsClient.appendReportRow(token, reportSheetId, tab, vals);
                runOnUiThread(() -> { if (clear != null) clear.run(); loadReportData(); loadDayStats(); });
            } catch (Exception e) {
                runOnUiThread(() -> reportStatus.setText("Add hoyni: " + e.getMessage()));
            }
        }).start();
    }

    private void generateReport() {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Report banano hocche...");
        new Thread(() -> {
            try {
                StatusLogTarget logTarget = resolveStatusLogTarget(); List<REntry> logs = logTarget == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, logTarget.sheetId, logTarget.tab));
                int total = 0, conn = 0;
                for (REntry e : logs) {
                    if (reportDate.equals(cellE(e, 0))) {
                        total++;
                        if ("call connected".equals(cellE(e, 2))) conn++;
                    }
                }
                ensureReportSheet();
                String today = reportDate;
                regToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Registration")), today);
                ftdToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "FTD")), today);
                invToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Invalid")), today);
                probToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Problems")), today);
                double sum = 0;
                for (REntry r : ftdToday) {
                    try { sum += Double.parseDouble(cellE(r, 2)); } catch (Exception ignored) {}
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Date: ").append(displayDay(today)).append("\n");
                sb.append("Time: ").append(dhaka("hh:mm a")).append("\n");
                sb.append("Agent ID: ").append(agentId).append("\n");
                sb.append("Total Calls: ").append(total).append("\n");
                sb.append("Connected: ").append(conn).append("\n");
                sb.append("Registration: ").append(regTotalCount(regToday)).append("\n");
                if (!regToday.isEmpty()) {
                    sb.append("Registration Details: ");
                    for (int i = 0; i < regToday.size(); i++) {
                        if (i > 0) sb.append(", ");
                        REntry re = regToday.get(i);
                        sb.append(isRegCountRow(re) ? cellE(re, 1) + "টা" : cellE(re, 1));
                    }
                    sb.append("\n");
                }
                sb.append("FTD: ").append(ftdTotalCount(ftdToday)).append("\n");
                sb.append("Total Amount Recharged: ").append(fmtAmount(sum)).append("\n");
                sb.append("Invalid: ").append(countAutoInvalid(logs, today)).append("\n");
                // v4.18: manual Invalid rows are not listed. sb.append("-\n");
                // v4.18: manual Invalid rows are not listed. sb.append(cellE(r, 1)).append(" - ").append(cellE(r, 2)).append("\n");
                sb.append("Problems:\n");
                if (probToday.isEmpty()) sb.append("-\n");
                else for (REntry r : probToday) sb.append(cellE(r, 1)).append("\n");
                String text = sb.toString().trim();
                runOnUiThread(() -> {
                    refreshReportLists();
                    reportOutput.setText(text);
                    reportOutputCard.setVisibility(View.VISIBLE);
                    reportStatus.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> reportStatus.setText("Generate hoyni: " + e.getMessage()));
            }
        }).start();
    }
}
