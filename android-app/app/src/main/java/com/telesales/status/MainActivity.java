package com.telesales.status;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1001;
    private static final int RC_AUTH = 1002;
    private static final String SHEET_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final String SHEET_ID = "10Lp_BOTSk-HNdNt-lyMuD57V0oW8qP4zhGNDi9KLgDE";

    private GoogleSignInClient signInClient;
    private String token;
    private RowAdapter adapter;
    private View loginView, mainView;
    private TextView loginError, userEmail, summary, statusText;
    private TextInputEditText searchBox;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loginView = findViewById(R.id.loginView);
        mainView = findViewById(R.id.mainView);
        loginError = findViewById(R.id.loginError);
        userEmail = findViewById(R.id.userEmail);
        summary = findViewById(R.id.summary);
        statusText = findViewById(R.id.statusText);
        searchBox = findViewById(R.id.searchBox);

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
        findViewById(R.id.logoutBtn).setOnClickListener(v -> {
            signInClient.signOut().addOnCompleteListener(t -> {
                token = null; showLogin();
            });
        });
        findViewById(R.id.refreshBtn).setOnClickListener(v -> loadRows());

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

        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { adapter.filter(s.toString()); updateSummary(); }
            public void afterTextChanged(Editable s) {}
        });

        GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
        if (acct != null) {
            userEmail.setText(acct.getEmail());
            fetchToken(acct);
        } else {
            showLogin();
        }
    }

    private void showLogin() {
        loginView.setVisibility(View.VISIBLE);
        mainView.setVisibility(View.GONE);
    }
    private void showMain() {
        loginView.setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
    }

    private void fetchToken(GoogleSignInAccount acct) {
        statusText.setText("লগইন হচ্ছে...");
        showMain();
        new Thread(() -> {
            try {
                token = GoogleAuthUtil.getToken(this, acct.getAccount(), "oauth2:" + SHEET_SCOPE);
                runOnUiThread(() -> { userEmail.setText(acct.getEmail()); loadRows(); });
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
        int c = 0, n = 0, b = 0, vy = 0;
        for (Row r : rows) {
            if ("call connected".equals(r.status)) c++;
            else if ("not connected".equals(r.status)) n++;
            else b++;
            if ("Yes".equals(r.valid)) vy++;
        }
        summary.setText("মোট: " + rows.size() + "টি • call connected: " + c + " • not connected: " + n + " • খালি: " + b + " • Valid Yes: " + vy);
    }
}
