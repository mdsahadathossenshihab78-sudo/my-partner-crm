package com.telesales.status;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1001;
    private static final int RC_AUTH = 1002;
    private static final String SHEET_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final String SHEET_ID = "10Lp_BOTSk-HNdNt-lyMuD57V0oW8qP4zhGNDi9KLgDE";

    private GoogleSignInClient signInClient;
    private String token;
    private RowAdapter adapter;
    private View loginView, mainView;
    private TextView loginError, avatarView, statTotal, statConnected, statNotConnected, statValid, statusText;
    private TextInputEditText searchBox;
    private View reportView, reportOutputCard;
    private TextView reportStatus, reportOutput, regList, ftdList, ftdTotal, invList, probList;
    private TextInputEditText regNumber, ftdNumber, ftdAmount, invNumber, probText;
    private Spinner invReason;
    private String reportSheetId;
    private List<String[]> regToday = new ArrayList<>(), ftdToday = new ArrayList<>(), invToday = new ArrayList<>(), probToday = new ArrayList<>();
    private final String[] reasons = {"কল ধরেনি", "Not connected", "বন্ধ", "ভুল নাম্বার"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loginView = findViewById(R.id.loginView);
        mainView = findViewById(R.id.mainView);
        loginError = findViewById(R.id.loginError);
        avatarView = findViewById(R.id.avatarView);
        statTotal = findViewById(R.id.statTotal);
        statConnected = findViewById(R.id.statConnected);
        statNotConnected = findViewById(R.id.statNotConnected);
        statValid = findViewById(R.id.statValid);
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

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SHEET_SCOPE))
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);

        MaterialButton googleBtn = findViewById(R.id.googleBtn);
        googleBtn.setOnClickListener(v -> {
            loginError.setText("");
            startActivityForResult(signInClient.getSignInIntent(), RC_SIGN_IN);
        });
        avatarView.setOnClickListener(v -> {
            signInClient.signOut().addOnCompleteListener(t -> {
                token = null; showLogin();
                Toast.makeText(this, "Logout hoyeche", Toast.LENGTH_SHORT).show();
            });
        });
        findViewById(R.id.refreshBtn).setOnClickListener(v -> loadRows());

        ArrayAdapter<String> ra = new ArrayAdapter<>(this, R.layout.spinner_item, reasons);
        ra.setDropDownViewResource(R.layout.spinner_dropdown_item);
        invReason.setAdapter(ra);

        findViewById(R.id.reportBtn).setOnClickListener(v -> showReport());
        findViewById(R.id.reportBackBtn).setOnClickListener(v -> showMain());
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
                    SheetsClient.updateRow(token, SHEET_ID, row);
                    runOnUiThread(() -> {
                        btn.setEnabled(true); btn.setText("Save korun");
                        Toast.makeText(this, "রো " + row.row + " শিটে আপডেট হয়েছে", Toast.LENGTH_SHORT).show();
                        adapter.filter(searchBox.getText() == null ? "" : searchBox.getText().toString());
                        updateSummary();
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
            public void onTextChanged(CharSequence s, int a, int b, int c) { adapter.filter(s.toString()); updateSummary(); }
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
    }
    private void showMain() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
        reportView.setVisibility(View.GONE);
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
        loadReportData();
    }

    private void fetchToken(GoogleSignInAccount acct) {
        statusText.setText("লগইন হচ্ছে...");
        showMain();
        new Thread(() -> {
            try {
                token = GoogleAuthUtil.getToken(this, acct.getAccount(), "oauth2:" + SHEET_SCOPE);
                runOnUiThread(() -> { setAvatar(acct.getEmail()); loadRows(); });
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
        statusText.setText("শিট থেকে লোড হচ্ছে...");
        new Thread(() -> {
            try {
                List<Row> rows = SheetsClient.getRows(token, SHEET_ID);
                runOnUiThread(() -> {
                    adapter.setRows(rows);
                    adapter.filter(searchBox.getText() == null ? "" : searchBox.getText().toString());
                    statusText.setText("শিটের সাথে সংযুক্ত — " + rows.size() + "টি রো");
                    updateSummary();
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("লোড হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    private void updateSummary() {
        List<Row> rows = adapter.getAll();
        int c = 0, n = 0, vy = 0;
        for (Row r : rows) {
            if ("call connected".equals(r.status)) c++;
            else if ("not connected".equals(r.status)) n++;
            if ("Yes".equals(r.valid)) vy++;
        }
        statTotal.setText(String.valueOf(rows.size()));
        statConnected.setText(String.valueOf(c));
        statNotConnected.setText(String.valueOf(n));
        statValid.setText(String.valueOf(vy));
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
    private static List<String[]> filterToday(List<String[]> rows, String today) {
        List<String[]> out = new ArrayList<>();
        for (String[] r : rows) if (r.length > 0 && today.equals(r[0])) out.add(r);
        return out;
    }
    private static String cell(String[] r, int i) {
        return r.length > i ? r[i] : "";
    }

    private void ensureReportSheet() throws Exception {
        if (reportSheetId != null) return;
        SharedPreferences p = getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE);
        reportSheetId = p.getString("report_sheet_id", null);
        if (reportSheetId == null) {
            reportSheetId = SheetsClient.createReportSpreadsheet(token);
            p.edit().putString("report_sheet_id", reportSheetId).apply();
        }
    }

    private void loadReportData() {
        if (token == null) { showLogin(); return; }
        reportStatus.setText("Report load hocche...");
        new Thread(() -> {
            try {
                ensureReportSheet();
                String today = dhakaDate();
                regToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "Registration"), today);
                ftdToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "FTD"), today);
                invToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "Invalid"), today);
                probToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "Problems"), today);
                runOnUiThread(() -> { refreshReportLists(); reportStatus.setText(""); });
            } catch (Exception e) {
                runOnUiThread(() -> reportStatus.setText("Report load hoyni: " + e.getMessage()));
            }
        }).start();
    }

    private void refreshReportLists() {
        if (regToday.isEmpty()) regList.setText("ekhono kichu add hoyni");
        else {
            StringBuilder sb = new StringBuilder();
            for (String[] r : regToday) sb.append(cell(r, 1)).append("\n");
            regList.setText(sb.toString().trim());
        }
        double sum = 0;
        if (ftdToday.isEmpty()) ftdList.setText("ekhono kichu add hoyni");
        else {
            StringBuilder sb = new StringBuilder();
            for (String[] r : ftdToday) {
                sb.append(cell(r, 1)).append(" - ").append(cell(r, 2)).append("\n");
                try { sum += Double.parseDouble(cell(r, 2)); } catch (Exception ignored) {}
            }
            ftdList.setText(sb.toString().trim());
        }
        ftdTotal.setText("Total Amount: " + fmtAmount(sum));
        if (invToday.isEmpty()) invList.setText("ekhono kichu add hoyni");
        else {
            StringBuilder sb = new StringBuilder();
            for (String[] r : invToday) sb.append(cell(r, 1)).append(" - ").append(cell(r, 2)).append("\n");
            invList.setText(sb.toString().trim());
        }
        if (probToday.isEmpty()) probList.setText("ekhono kichu add hoyni");
        else {
            StringBuilder sb = new StringBuilder();
            for (String[] r : probToday) sb.append(cell(r, 1)).append("\n");
            probList.setText(sb.toString().trim());
        }
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
                List<Row> rows = SheetsClient.getRows(token, SHEET_ID);
                int total = 0, conn = 0;
                for (Row r : rows) {
                    if (r.status != null && !r.status.isEmpty()) {
                        total++;
                        if ("call connected".equals(r.status)) conn++;
                    }
                }
                ensureReportSheet();
                String today = dhakaDate();
                regToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "Registration"), today);
                ftdToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "FTD"), today);
                invToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "Invalid"), today);
                probToday = filterToday(SheetsClient.getReportRows(token, reportSheetId, "Problems"), today);
                double sum = 0;
                for (String[] r : ftdToday) {
                    try { sum += Double.parseDouble(cell(r, 2)); } catch (Exception ignored) {}
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Date: ").append(dhaka("dd-MM-yyyy")).append("\n");
                sb.append("Time: ").append(dhaka("hh:mm a")).append("\n");
                sb.append("Agent ID: bdxsukuna30\n");
                sb.append("Total Calls: ").append(total).append("\n");
                sb.append("Connected: ").append(conn).append("\n");
                sb.append("Registration: ").append(regToday.size()).append("\n");
                if (!regToday.isEmpty()) {
                    sb.append("Registration Numbers: ");
                    for (int i = 0; i < regToday.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(cell(regToday.get(i), 1));
                    }
                    sb.append("\n");
                }
                sb.append("FTD: ").append(ftdToday.size()).append("\n");
                if (!ftdToday.isEmpty()) {
                    sb.append("FTD Details: ");
                    for (int i = 0; i < ftdToday.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(cell(ftdToday.get(i), 1)).append(" - ").append(cell(ftdToday.get(i), 2));
                    }
                    sb.append("\n");
                }
                sb.append("Total Amount Recharged: ").append(fmtAmount(sum)).append("\n");
                sb.append("Invalid Number:\n");
                if (invToday.isEmpty()) sb.append("-\n");
                else for (String[] r : invToday) sb.append(cell(r, 1)).append(" - ").append(cell(r, 2)).append("\n");
                sb.append("Problems:\n");
                if (probToday.isEmpty()) sb.append("-\n");
                else for (String[] r : probToday) sb.append(cell(r, 1)).append("\n");
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
