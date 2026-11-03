package com.telesales.status;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
    public interface OnSave { void onSave(Row row, MaterialButton btn); }
    /** v4.31: row-er "Mone koriye dao" — MainActivity somoy-bachai dialog khulbe. */
    public interface OnRemind { void onRemind(Row row); }
    private OnRemind onRemind;
    public void setOnRemind(OnRemind r) { onRemind = r; }
    private List<Row> all = new ArrayList<>();
    private List<Row> shown = new ArrayList<>();
    private final OnSave onSave;
    private final String[] statusOpts = {"", "call connected", "not connected"};
    private final String[] statusLabels = {"— খালি —", "call connected", "not connected"};
    private final String[] validOpts = {"", "Yes", "No"};
    private final String[] validLabels = {"— খালি —", "Yes", "No"};

    public RowAdapter(OnSave onSave) { this.onSave = onSave; }
    /** Date-filtered called tab: log theke asa row-gulo (null = ekhono load hoyni, tokhon purono niyom). */
    private List<Row> calledRows = null;
    private String calledDate = null;
    public void setCalledRows(List<Row> rows, String date) { calledRows = rows == null ? null : new ArrayList<>(rows); calledDate = date; filter(lastQ); }
    public String getCalledDate() { return calledDate; }
    /** v4.26: notun call sathe sathe called tab-er upore (Save-er por Refresh charai).
     *  Ekoi Row object all + calledRows — duitotei thake, tai reference-e sorano nirapad. */
    public void moveCalledToTop(Row r, String q) { if (calledRows == null || r == null) { filter(q); return; } calledRows.remove(r); calledRows.add(0, r); filter(q); }
    /** v4.26: clear-save (status+valid khali) hole row-ta called tab theke sorbe. */
    public void removeCalledRow(Row r, String q) { if (calledRows == null || r == null) { filter(q); return; } calledRows.remove(r); filter(q); }

    public void setRows(List<Row> rows) {
        all = new ArrayList<>(rows); filter(lastQ);
    }
    /** Tab: 0 = কল বাকি (status khali), 1 = কল করা হয়েছে (status deya ache). */
    private int tab = 0;
    private String lastQ = "";
    public void setTab(int t) { tab = t; filter(lastQ); }
    public int getTab() { return tab; }
    /** Sheet-e number jemon ache temon thakbe; app-e dekhano + call korar somoy
     *  samne 0 na thakle 0 jog hobe. 0 ba + diye suru hole kichu bodlabe na. */
    public static String dialNumber(String raw) {
        if (raw == null) return "";
        String n = raw.trim().replaceAll("[\\s\\-]", "");
        if (n.isEmpty() || n.startsWith("0") || n.startsWith("+")) return n;
        return "0" + n;
    }
    public void filter(String q) {
        lastQ = q == null ? "" : q;
        shown.clear();
        String qn = lastQ.trim().replaceAll("[\\s\\-]", "");
        List<Row> src = (tab == 1 && calledRows != null) ? calledRows : all;
        for (Row r : src) {
            boolean inTab;
            if (tab == 0) inTab = (r.status == null || r.status.isEmpty());
            else if (calledRows != null) inTab = true; // calledRows-e thaka manei oi date-e call hoyeche
            else inTab = (r.status != null && !r.status.isEmpty());
            if (!inTab) continue;
            if (qn.isEmpty() || r.number.contains(qn) || dialNumber(r.number).contains(qn)) shown.add(r);
        }
        notifyDataSetChanged();
    }
    public List<Row> getAll() { return all; }

    private int indexOf(String[] opts, String v) {
        for (int i = 0; i < opts.length; i++) if (opts[i].equals(v)) return i;
        return 0;
    }

    /** Sheet-e hate-lekha ochena value (jemon "Connected") thakle seta spinner-e dekhabe ar
     *  Save-e ager motoi thakbe — ager bug-e ochena value khali hoye jeto (silent data loss). */
    private static String[] withUnknown(String[] opts, String v) {
        if (v == null || v.isEmpty()) return opts;
        for (String o : opts) if (o.equals(v)) return opts;
        String[] out = new String[opts.length + 1];
        System.arraycopy(opts, 0, out, 0, opts.length);
        out[opts.length] = v;
        return out;
    }

    private void refreshStatusChip(Holder h, String status) {
        if ("call connected".equals(status)) {
            h.tvChipStatus.setText("● call connected");
            h.tvChipStatus.setBackgroundResource(R.drawable.chip_green);
        } else if ("not connected".equals(status)) {
            h.tvChipStatus.setText("● not connected");
            h.tvChipStatus.setBackgroundResource(R.drawable.chip_rose);
        } else {
            h.tvChipStatus.setText("● খালি");
            h.tvChipStatus.setBackgroundResource(R.drawable.chip_grey);
        }
    }
    private void refreshValidChip(Holder h, String valid) {
        if (valid == null || valid.isEmpty()) {
            h.tvChipValid.setVisibility(View.GONE);
        } else {
            h.tvChipValid.setVisibility(View.VISIBLE);
            h.tvChipValid.setText("Valid: " + valid);
            h.tvChipValid.setBackgroundResource("Yes".equals(valid) ? R.drawable.chip_blue : R.drawable.chip_grey);
        }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false);
        return new Holder(v);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
        Row r = shown.get(pos);
        h.tvNumber.setText(dialNumber(r.number));
        h.tvRow.setText(r.viewOnly ? "লগ থেকে" : "রো " + r.row);
        h.spStatus.setEnabled(!r.viewOnly); h.spValid.setEnabled(!r.viewOnly);
        h.etRemark1.setEnabled(!r.viewOnly); h.etRemark2.setEnabled(!r.viewOnly); h.etOthers.setEnabled(!r.viewOnly);
        h.btnSave.setEnabled(!r.viewOnly);
        h.btnSave.setText(r.viewOnly ? "শুধু দেখা যাবে" : "Save korun");
        final String[] sOpts = withUnknown(statusOpts, r.status);
        final String[] sLabels = withUnknown(statusLabels, r.status);
        final String[] vOpts = withUnknown(validOpts, r.valid);
        final String[] vLabels = withUnknown(validLabels, r.valid);
        ArrayAdapter<String> sa = new ArrayAdapter<>(h.itemView.getContext(), R.layout.spinner_item, sLabels);
        sa.setDropDownViewResource(R.layout.spinner_dropdown_item);
        h.spStatus.setAdapter(sa); h.spStatus.setSelection(indexOf(sOpts, r.status));
        ArrayAdapter<String> va = new ArrayAdapter<>(h.itemView.getContext(), R.layout.spinner_item, vLabels);
        va.setDropDownViewResource(R.layout.spinner_dropdown_item);
        h.spValid.setAdapter(va); h.spValid.setSelection(indexOf(vOpts, r.valid));
        refreshStatusChip(h, r.status);
        refreshValidChip(h, r.valid);
        h.spStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int p2, long id) { refreshStatusChip(h, sOpts[p2]); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        h.spValid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int p2, long id) { refreshValidChip(h, vOpts[p2]); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        h.etRemark1.setText(r.remark1); h.etRemark2.setText(r.remark2); h.etOthers.setText(r.others);
        h.btnCall.setOnClickListener(v -> {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialNumber(r.number)));
            Intent chooser = Intent.createChooser(dial, "কল করার অ্যাপ বেছে নিন");
            h.itemView.getContext().startActivity(chooser);
        });
        h.btnCopy.setOnClickListener(v -> {
            Context ctx = h.itemView.getContext();
            String num = dialNumber(r.number);
            try {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Number", num));
                Toast.makeText(ctx, "Copy hoyeche: " + num, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(ctx, "Copy hoyni, abar chesta korun", Toast.LENGTH_SHORT).show();
            }
        });
        h.btnSave.setOnClickListener(v -> {
            r.status = sOpts[h.spStatus.getSelectedItemPosition()];
            r.valid = vOpts[h.spValid.getSelectedItemPosition()];
            r.remark1 = h.etRemark1.getText() == null ? "" : h.etRemark1.getText().toString().trim();
            r.remark2 = h.etRemark2.getText() == null ? "" : h.etRemark2.getText().toString().trim();
            r.others = h.etOthers.getText() == null ? "" : h.etOthers.getText().toString().trim();
            onSave.onSave(r, h.btnSave);
        });
        h.btnRemind.setOnClickListener(v -> { if (onRemind != null) onRemind.onRemind(r); });
    }
    @Override public int getItemCount() { return shown.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvNumber, tvRow, tvChipStatus, tvChipValid; Spinner spStatus, spValid;
        TextInputEditText etRemark1, etRemark2, etOthers;
        android.widget.Button btnCall, btnCopy, btnRemind; MaterialButton btnSave;
        Holder(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.tvNumber); tvRow = v.findViewById(R.id.tvRow);
            tvChipStatus = v.findViewById(R.id.tvChipStatus); tvChipValid = v.findViewById(R.id.tvChipValid);
            spStatus = v.findViewById(R.id.spStatus); spValid = v.findViewById(R.id.spValid);
            etRemark1 = v.findViewById(R.id.etRemark1); etRemark2 = v.findViewById(R.id.etRemark2);
            etOthers = v.findViewById(R.id.etOthers); btnCall = v.findViewById(R.id.btnCall);
            btnCopy = v.findViewById(R.id.btnCopy); btnRemind = v.findViewById(R.id.remindBtn);
            btnSave = v.findViewById(R.id.btnSave);
        }
    }
}
