package com.telesales.status;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

    private GoogleSignInClient signInClient;
    private String token;
    private String agentId;
    private String statusLogSheetId;
    private String selectedDate = dhakaDate();
    private Map<String,String> lastStatusMap = new HashMap<>();
    private RowAdapter adapter;
    private View loginView, mainView;
    private View agentView;
    private TextView agentError, agentSubtitle;
    private TextInputEditText agentInput;
    private TextView loginError, avatarView, statusText, weekSummary;
    private TextView statConnDay, statValidDay, statInvDay, statRegDay, statFtdDay, statFtdAmtDay;
    private MaterialButton dateBtn;
    private TextInputEditText searchBox;
    private View reportView, reportOutputCard;
    private TextView reportStatus, reportOutput, ftdTotal;
    private LinearLayout regList, ftdList, invList, probList;
    private TextInputEditText regNumber, ftdNumber, ftdAmount, invNumber, probText;
    private Spinner invReason;
    private String reportSheetId;
    /** Report sheet-er ekta row: cells + sheet-er row number (delete korar jonno). */
    static class REntry { final String[] cells; final int sheetRow; REntry(String[] c, int r) { cells = c; sheetRow = r; } }
    private List<REntry> regToday = new ArrayList<>(), ftdToday = new ArrayList<>(), invToday = new ArrayList<>(), probToday = new ArrayList<>();
    private final String[] reasons = {"কল ধরেনি", "Not connected", "বন্ধ", "ভুল নাম্বার"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loginView = findViewById(R.id.loginView);
        mainView = findViewById(R.id.mainView);
        loginError = findViewById(R.id.loginError);
        avatarView = findViewById(R.id.avatarView);
        statConnDay = findViewById(R.id.statConnDay);
        statValidDay = findViewById(R.id.statValidDay);
        statInvDay = findViewById(R.id.statInvDay);
        statRegDay = findViewById(R.id.statRegDay);
        statFtdDay = findViewById(R.id.statFtdDay);
        statFtdAmtDay = findViewById(R.id.statFtdAmtDay);
        weekSummary = findViewById(R.id.weekSummary);
        dateBtn = findViewById(R.id.btnDate);
        statusText = findViewById(R.id.statusText);
        searchBox = findViewById(R.id.searchBox);
        reportView = findViewById(R.id.reportView);
        reportOutputCard = findViewById(R.id.reportOutputCard);
        reportStatus = findViewById(R.id.reportStatus);
        reportOutput = findViewById(R.id.reportOutput);
        regNumber = findViewById(R.id.regNumber);
        ftdNumber = findViewById(R.id.ftdNumber);
        ftdAmount = findViewById(R.id.ftdAmount);
        invNumber = findViewById(R.id.invNumber);
        invReason = findViewById(R.id.invReason);
        probText = findViewById(R.id.probText);
        regList = findViewById(R.id.regList);
        ftdList = findViewById(R.id.ftdList);
        ftdTotal = findViewById(R.id.ftdTotal);
        invList = findViewById(R.id.invList);
        probList = findViewById(R.id.probList);
        agentView = findViewById(R.id.agentView);
        agentInput = findViewById(R.id.agentInput);
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
                getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().remove("agent_id").apply();
                showLogin();
                Toast.makeText(this, "Logout hoyeche", Toast.LENGTH_SHORT).show();
            });
        });
        findViewById(R.id.refreshBtn).setOnClickListener(v -> loadRows());

        ArrayAdapter<String> ra = new ArrayAdapter<>(this, R.layout.spinner_item, reasons);
        ra.setDropDownViewResource(R.layout.spinner_dropdown_item);
        invReason.setAdapter(ra);

        findViewById(R.id.reportBtn).setOnClickListener(v -> showReport());
        findViewById(R.id.reportBackBtn).setOnClickListener(v -> showMain());
        findViewById(R.id.btnPrevDay).setOnClickListener(v -> shiftDay(-1));
        findViewById(R.id.btnNextDay).setOnClickListener(v -> shiftDay(1));
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
        findViewById(R.id.btnTabPending).setOnClickListener(v -> { adapter.setTab(0); setTabUI(); });
        findViewById(R.id.btnTabDone).setOnClickListener(v -> { adapter.setTab(1); setTabUI(); });
        findViewById(R.id.regAddBtn).setOnClickListener(v -> {
            String n = txt(regNumber);
            if (n.isEmpty()) { Toast.makeText(this, "Number din", Toast.LENGTH_SHORT).show(); return; }
            addReportEntry("Registration", new String[]{dhakaDate(), RowAdapter.dialNumber(n)}, () -> regNumber.setText(""));
        });
        findViewById(R.id.ftdAddBtn).setOnClickListener(v -> {
            String n = txt(ftdNumber);
            String a = txt(ftdAmount);
            double amt;
            try { amt = Double.parseDouble(a); } catch (Exception e) { amt = -1; }
            if (n.isEmpty() || amt <= 0) { Toast.makeText(this, "Number ar thik amount din", Toast.LENGTH_SHORT).show(); return; }
            addReportEntry("FTD", new String[]{dhakaDate(), RowAdapter.dialNumber(n), fmtAmount(amt)}, () -> { ftdNumber.setText(""); ftdAmount.setText(""); });
        });
        findViewById(R.id.invAddBtn).setOnClickListener(v -> {
            String n = txt(invNumber);
            if (n.isEmpty()) { Toast.makeText(this, "Number din", Toast.LENGTH_SHORT).show(); return; }
            String reason = reasons[invReason.getSelectedItemPosition()];
            addReportEntry("Invalid", new String[]{dhakaDate(), RowAdapter.dialNumber(n), reason}, () -> invNumber.setText(""));
        });
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
                            SheetsClient.appendReportRow(token, statusLogSheetId, "Status Log", new String[]{dhakaDate(), RowAdapter.dialNumber(row.number), row.status == null ? "" : row.status, row.valid == null ? "" : row.valid});
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

    private void showLogin() {
        loginView.setVisibility(View.VISIBLE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
    }
    private void showMain() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.GONE);
        setAgentSubtitle();
        loadDayStats();
    }
    private void showAgent() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.GONE);
        reportView.setVisibility(View.GONE);
        agentView.setVisibility(View.VISIBLE);
        agentError.setText("");
    }
    private void setAgentSubtitle() {
        if (agentSubtitle != null && agentId != null) agentSubtitle.setText("Agent: " + agentId);
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
                getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).edit().putString("agent_id", finalMatch).apply();
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
        reportView.setVisibility(View.VISIBLE);
        agentView.setVisibility(View.GONE);
        loadReportData();
    }

    private void fetchToken(GoogleSignInAccount acct) {
        loginError.setText("লগইন হচ্ছে...");
        new Thread(() -> {
            try {
                token = GoogleAuthUtil.getToken(this, acct.getAccount(), "oauth2:" + SHEET_SCOPE + " " + DRIVE_SCOPE);
                runOnUiThread(() -> {
                    setAvatar(acct.getEmail());
                    String saved = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).getString("agent_id", null);
                    if (saved != null && !saved.isEmpty()) {
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
                    adapter.setRows(rows);
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

    private void ensureStatusLogSheet() throws Exception {
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
                String logId = resolveSharedId("status_log_sheet_id_" + agentId, null, "Telesales Status Log - " + agentId);
                String repId = resolveSharedId("report_sheet_id_" + agentId,
                        "bdxsukuna30".equals(agentId) ? "report_sheet_id" : null, "Telesales Report - " + agentId);
                List<REntry> logs = logId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, logId, "Status Log"));
                List<REntry> invs = repId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, repId, "Invalid"));
                List<REntry> regs = repId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, repId, "Registration"));
                List<REntry> ftds = repId == null ? new ArrayList<>() : withRows(SheetsClient.getReportRows(token, repId, "FTD"));
                int conn = 0, valid = 0;
                for (REntry e : logs) if (day.equals(cellE(e, 0))) {
                    if ("call connected".equals(cellE(e, 2))) conn++;
                    if ("Yes".equals(cellE(e, 3))) valid++;
                }
                int inv = filterTodayE(invs, day).size();
                int reg = filterTodayE(regs, day).size();
                List<REntry> ftdToday = filterTodayE(ftds, day);
                int ftdN = ftdToday.size();
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
                List<REntry> ftdWeek = filterRange(ftds, blockStart, blockEnd);
                int ftdNW = ftdWeek.size();
                double ftdSumW = 0;
                for (REntry e : ftdWeek) { try { ftdSumW += Double.parseDouble(cellE(e, 2)); } catch (Exception ignored) {} }
                String finalBlockStart = blockStart, finalBlockEnd = blockEnd;
                int finalConn = conn, finalValid = valid, finalInv = inv, finalReg = reg, finalFtdN = ftdN, finalConnW = connW, finalFtdNW = ftdNW;
                double finalFtdSum = ftdSum, finalFtdSumW = ftdSumW;
                runOnUiThread(() -> {
                    statConnDay.setText(String.valueOf(finalConn));
                    statValidDay.setText(String.valueOf(finalValid));
                    statInvDay.setText(String.valueOf(finalInv));
                    statRegDay.setText(String.valueOf(finalReg));
                    statFtdDay.setText(String.valueOf(finalFtdN));
                    statFtdAmtDay.setText("৳" + fmtAmount(finalFtdSum));
                    weekSummary.setText("Ei 7 din (" + displayDay(finalBlockStart) + " – " + displayDay(finalBlockEnd) + "): Connected " + finalConnW + " • FTD " + finalFtdNW + " (৳" + fmtAmount(finalFtdSumW) + ")");
                });
            } catch (Exception e) {
                runOnUiThread(() -> weekSummary.setText("Hisab load hoyni: " + e.getMessage()));
            }
        }).start();
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

    private void loadReportData() {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Report load hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                String today = dhakaDate();
                regToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Registration")), today);
                ftdToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "FTD")), today);
                invToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Invalid")), today);
                probToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Problems")), today);
                runOnUiThread(() -> { refreshReportLists(); reportStatus.setText(""); });
            } catch (Exception e) {
                runOnUiThread(() -> reportStatus.setText("Report load hoyni: " + e.getMessage()));
            }
        }).start();
    }

    private void refreshReportLists() {
        fillList(regList, regToday, "Registration");
        fillList(ftdList, ftdToday, "FTD");
        fillList(invList, invToday, "Invalid");
        fillList(probList, probToday, "Problems");
        double sum = 0;
        for (REntry r : ftdToday) {
            try { sum += Double.parseDouble(cellE(r, 2)); } catch (Exception ignored) {}
        }
        ftdTotal.setText("Total Amount: " + fmtAmount(sum));
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
            String text = ("FTD".equals(tab) || "Invalid".equals(tab))
                    ? cellE(e, 1) + " - " + cellE(e, 2) : cellE(e, 1);
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
            row.addView(del);
            parent.addView(row);
        }
    }

    /** Sheet thekeo row-ta muche fele, tarpor list abar load kore. */
    private void deleteReportEntry(String tab, REntry e) {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Delete hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                SheetsClient.deleteReportRow(token, reportSheetId, tab, e.sheetRow);
                runOnUiThread(() -> { reportOutputCard.setVisibility(View.GONE); loadReportData(); });
            } catch (Exception ex) {
                runOnUiThread(() -> reportStatus.setText("Delete hoyni: " + ex.getMessage()));
            }
        }).start();
    }

    private void addReportEntry(String tab, String[] vals, Runnable clear) {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Add hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                SheetsClient.appendReportRow(token, reportSheetId, tab, vals);
                runOnUiThread(() -> { if (clear != null) clear.run(); loadReportData(); });
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
                List<Row> rows = SheetsClient.getRows(token, SHEET_ID, agentId);
                int total = 0, conn = 0;
                for (Row r : rows) {
                    if (r.status != null && !r.status.isEmpty()) {
                        total++;
                        if ("call connected".equals(r.status)) conn++;
                    }
                }
                ensureReportSheet();
                String today = dhakaDate();
                regToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Registration")), today);
                ftdToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "FTD")), today);
                invToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Invalid")), today);
                probToday = filterTodayE(withRows(SheetsClient.getReportRows(token, reportSheetId, "Problems")), today);
                double sum = 0;
                for (REntry r : ftdToday) {
                    try { sum += Double.parseDouble(cellE(r, 2)); } catch (Exception ignored) {}
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Date: ").append(dhaka("dd-MM-yyyy")).append("\n");
                sb.append("Time: ").append(dhaka("hh:mm a")).append("\n");
                sb.append("Agent ID: ").append(agentId).append("\n");
                sb.append("Total Calls: ").append(total).append("\n");
                sb.append("Connected: ").append(conn).append("\n");
                sb.append("Registration: ").append(regToday.size()).append("\n");
                if (!regToday.isEmpty()) {
                    sb.append("Registration Numbers: ");
                    for (int i = 0; i < regToday.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(cellE(regToday.get(i), 1));
                    }
                    sb.append("\n");
                }
                sb.append("FTD: ").append(ftdToday.size()).append("\n");
                if (!ftdToday.isEmpty()) {
                    sb.append("FTD Details: ");
                    for (int i = 0; i < ftdToday.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(cellE(ftdToday.get(i), 1)).append(" - ").append(cellE(ftdToday.get(i), 2));
                    }
                    sb.append("\n");
                }
                sb.append("Total Amount Recharged: ").append(fmtAmount(sum)).append("\n");
                sb.append("Invalid Number:\n");
                if (invToday.isEmpty()) sb.append("-\n");
                else for (REntry r : invToday) sb.append(cellE(r, 1)).append(" - ").append(cellE(r, 2)).append("\n");
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
