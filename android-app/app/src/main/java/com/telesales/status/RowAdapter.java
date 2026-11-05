package com.telesales.status;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
    public interface OnSave {
        void onSave(Row row, MaterialButton btn);
        /** Batch fix #3: clear-save hole Save-er age confirm dialog dekhabe kina. Default: na. */
        default boolean needsClearConfirm(Row row) { return false; }
    }
    /** v4.31: row-er "Mone koriye dao" — MainActivity somoy-bachai dialog khulbe. */
    public interface OnRemind { void onRemind(Row row); }
    private OnRemind onRemind;
    public void setOnRemind(OnRemind r) { onRemind = r; }
    private List<Row> all = new ArrayList<>();
    private List<Row> shown = new ArrayList<>();
    private final OnSave onSave;

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
    /** Batch feature #1: dashboard tile tap-er type filter (called tab-e projojjo). */
    private String typeFilter = null;
    /** Batch feature #2: ager call-er itihas — number -> date-sort kora entry list. */
    public static class Hist { public final String date, label; public Hist(String d, String l) { date = d; label = l; } }
    private java.util.Map<String, java.util.List<Hist>> history = new java.util.HashMap<>();
    public void setHistory(java.util.Map<String, java.util.List<Hist>> h) { history = h == null ? new java.util.HashMap<>() : h; notifyDataSetChanged(); }
    private String lastQ = "";
    public void setTab(int t) { typeFilter = null; tab = t; filter(lastQ); }
    public void setTypeFilter(String t) { typeFilter = t; filter(lastQ); }
    private boolean matchesTypeFilter(Row r) {
        if (typeFilter == null) return true;
        String s = r.status == null ? "" : r.status;
        String v = r.valid == null ? "" : r.valid;
        if ("connected".equals(typeFilter)) return "call connected".equalsIgnoreCase(s);
        if ("notconnected".equals(typeFilter)) return "not connected".equalsIgnoreCase(s);
        if ("valid".equals(typeFilter)) return "Yes".equals(v);
        if ("invalid".equals(typeFilter)) return "not connected".equalsIgnoreCase(s) && "No".equalsIgnoreCase(v);
        return true;
    }
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
        // Batch fix #13: number charao remark1/remark2/others-e khunja jabe (case-insensitive).
        String qt = lastQ.trim().toLowerCase(java.util.Locale.ROOT);
        List<Row> src = (tab == 1 && calledRows != null) ? calledRows : all;
        for (Row r : src) {
            boolean inTab;
            if (tab == 0) inTab = (r.status == null || r.status.isEmpty());
            else if (calledRows != null) inTab = true; // calledRows-e thaka manei oi date-e call hoyeche
            else inTab = (r.status != null && !r.status.isEmpty());
            if (!inTab) continue;
            if (tab == 1 && !matchesTypeFilter(r)) continue;
            if (qn.isEmpty() || r.number.contains(qn) || dialNumber(r.number).contains(qn)
                    || (!qt.isEmpty() && ((r.remark1 != null && r.remark1.toLowerCase(java.util.Locale.ROOT).contains(qt))
                    || (r.remark2 != null && r.remark2.toLowerCase(java.util.Locale.ROOT).contains(qt))
                    || (r.others != null && r.others.toLowerCase(java.util.Locale.ROOT).contains(qt))))) shown.add(r);
        }
        notifyDataSetChanged();
    }
    public List<Row> getAll() { return all; }


    /** v4.44: ek tap-e chip — dialog/spinner nei. Chip chaplei value bosbe;
     *  (v4.43 dialog-e crash chilo: status "call connected" hole withUnknown
     *  labels-e 4th item "call connected" jog korto, kintu opts-e na — oi 4th
     *  item-e chaple opts[3] out-of-bounds-e app crash korto. Tai dialog
     *  ar kokhono firot anbe na; chip-e ei somossa nei.)
     *  select kora chip-e abar chaple khali hobe (toggle off). Sheet-er ochena
     *  value thakle kono chip select hobe na, kintu Save-e ager motoi thakbe —
     *  chip na chaple r.status/r.valid bodlay na (silent data loss nei). */
    private void refreshStatusChips(Holder h, String status) {
        boolean conn = "call connected".equals(status);
        boolean notConn = "not connected".equals(status);
        h.statusChipConn.setBackgroundResource(conn ? R.drawable.chip_green : R.drawable.glass_field);
        h.statusChipNotConn.setBackgroundResource(notConn ? R.drawable.chip_rose : R.drawable.glass_field);
    }
    private void refreshValidChips(Holder h, String valid) {
        boolean yes = "Yes".equals(valid);
        boolean no = "No".equals(valid);
        h.validChipYes.setBackgroundResource(yes ? R.drawable.chip_blue : R.drawable.glass_field);
        h.validChipNo.setBackgroundResource(no ? R.drawable.chip_grey : R.drawable.glass_field);
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false);
        return new Holder(v);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
        Row r = shown.get(pos);
        h.tvNumber.setText(dialNumber(r.number));
        h.tvRow.setText(r.viewOnly ? "লগ থেকে" : "রো " + r.row);
        // Batch feature #2: ager call — called tab-e oi din-er ager gulo, pending tab-e sob
        // (notun theke purono, prothom 3ta + baki sonkha).
        java.util.List<Hist> hist = history.get(dialNumber(r.number));
        StringBuilder hb = new StringBuilder();
        int histShown = 0, histMore = 0;
        if (hist != null) {
            for (int i = hist.size() - 1; i >= 0; i--) {
                Hist he = hist.get(i);
                if (tab == 1 && calledDate != null && he.date.compareTo(calledDate) >= 0) continue;
                if (histShown < 3) { if (hb.length() > 0) hb.append(" • "); hb.append(he.label); histShown++; }
                else histMore++;
            }
        }
        if (histShown == 0) h.tvHistory.setVisibility(View.GONE);
        else { h.tvHistory.setText("আগের কল: " + hb.toString() + (histMore > 0 ? " +" + histMore + "টা" : "")); h.tvHistory.setVisibility(View.VISIBLE); }
        h.statusChipConn.setEnabled(!r.viewOnly); h.statusChipNotConn.setEnabled(!r.viewOnly);
        h.validChipYes.setEnabled(!r.viewOnly); h.validChipNo.setEnabled(!r.viewOnly);
        float chipAlpha = r.viewOnly ? 0.55f : 1f;
        h.statusChipConn.setAlpha(chipAlpha); h.statusChipNotConn.setAlpha(chipAlpha);
        h.validChipYes.setAlpha(chipAlpha); h.validChipNo.setAlpha(chipAlpha);
        h.etRemark1.setEnabled(!r.viewOnly); h.etRemark2.setEnabled(!r.viewOnly); h.etOthers.setEnabled(!r.viewOnly);
        h.phraseBtn1.setEnabled(!r.viewOnly); h.phraseBtn2.setEnabled(!r.viewOnly); h.phraseBtn3.setEnabled(!r.viewOnly);
        h.btnSave.setEnabled(!r.viewOnly);
        h.btnSave.setText(r.viewOnly ? "শুধু দেখা যাবে" : "সেভ করুন");
        // v4.44: status/valid ekhon ek-tap chip — dialog/spinner nei, chip chaplei bosbe.
        refreshStatusChips(h, r.status);
        refreshValidChips(h, r.valid);
        h.statusChipConn.setOnClickListener(v -> { if (r.viewOnly) return; r.status = "call connected".equals(r.status) ? "" : "call connected"; refreshStatusChips(h, r.status); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        h.statusChipNotConn.setOnClickListener(v -> { if (r.viewOnly) return; r.status = "not connected".equals(r.status) ? "" : "not connected"; refreshStatusChips(h, r.status); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        h.validChipYes.setOnClickListener(v -> { if (r.viewOnly) return; r.valid = "Yes".equals(r.valid) ? "" : "Yes"; refreshValidChips(h, r.valid); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        h.validChipNo.setOnClickListener(v -> { if (r.viewOnly) return; r.valid = "No".equals(r.valid) ? "" : "No"; refreshValidChips(h, r.valid); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        // Batch fix #1: lekha sathe sathe Row-te joma (Save-er age scroll/search korleo harabe na).
        // Purono watcher age sorano — na hole purono row-te bhul lekha jeto.
        if (h.wRemark1 != null) h.etRemark1.removeTextChangedListener(h.wRemark1);
        if (h.wRemark2 != null) h.etRemark2.removeTextChangedListener(h.wRemark2);
        if (h.wOthers != null) h.etOthers.removeTextChangedListener(h.wOthers);
        h.wRemark1 = new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable s) { r.remark1 = s.toString(); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); } };
        h.wRemark2 = new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable s) { r.remark2 = s.toString(); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); } };
        h.wOthers = new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable s) { r.others = s.toString(); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); } };
        h.etRemark1.addTextChangedListener(h.wRemark1); h.etRemark2.addTextChangedListener(h.wRemark2); h.etOthers.addTextChangedListener(h.wOthers);
        h.etRemark1.setText(r.remark1); h.etRemark2.setText(r.remark2); h.etOthers.setText(r.others);
        h.btnCall.setOnClickListener(v -> {
            String main = dialNumber(r.number);
            String other = h.etOthers.getText() == null ? "" : dialNumber(h.etOthers.getText().toString());
            if (!other.isEmpty() && !other.equals(main)) {
                // v4.36: অন্যান্য নাম্বার থাকলে দুইটাই দেখাবে, যেটা বাছবে সেটাই ডায়াল হবে।
                final String[] nums = {main, other};
                new AlertDialog.Builder(h.itemView.getContext())
                    .setTitle("কোন নাম্বারে কল করবেন?")
                    .setItems(nums, (d, w) -> {
                        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + nums[w]));
                        Intent chooser = Intent.createChooser(dial, "কল করার অ্যাপ বেছে নিন");
                        h.itemView.getContext().startActivity(chooser);
                    })
                    .setNegativeButton("বাতিল", null)
                    .show();
            } else {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + main));
                Intent chooser = Intent.createChooser(dial, "কল করার অ্যাপ বেছে নিন");
                h.itemView.getContext().startActivity(chooser);
            }
        });
        h.btnCopy.setOnClickListener(v -> {
            Context ctx = h.itemView.getContext();
            String main = dialNumber(r.number);
            String other = h.etOthers.getText() == null ? "" : dialNumber(h.etOthers.getText().toString());
            if (!other.isEmpty() && !other.equals(main)) {
                // v4.37: দুই নাম্বার থাকলে জিজ্ঞেস করবে কোনটা কপি করবে, বাছলেই সেটা কপি হবে।
                final String[] nums = {main, other};
                new AlertDialog.Builder(ctx)
                    .setTitle("কোনটা কপি করবেন?")
                    .setItems(nums, (d, w) -> {
                        try {
                            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("Number", nums[w]));
                            Toast.makeText(ctx, "কপি হয়েছে: " + nums[w], Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(ctx, "কপি হয়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("বাতিল", null)
                    .show();
            } else {
                try {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Number", main));
                    Toast.makeText(ctx, "কপি হয়েছে: " + main, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(ctx, "কপি হয়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show();
                }
            }
        });
        h.btnSave.setOnClickListener(v -> {
            // v4.44: status/valid chip thekei Row-te live thake — save-er somoy
            // ar field theke pora lage na (setai kokhono purono value dito).
            r.remark1 = h.etRemark1.getText() == null ? "" : h.etRemark1.getText().toString().trim();
            r.remark2 = h.etRemark2.getText() == null ? "" : h.etRemark2.getText().toString().trim();
            r.others = h.etOthers.getText() == null ? "" : h.etOthers.getText().toString().trim();
            // Batch fix #3: clear-save hole age confirm, na hole soja Save.
            if (onSave.needsClearConfirm(r)) {
                new AlertDialog.Builder(h.itemView.getContext())
                    .setTitle("নিশ্চিত করুন")
                    .setMessage("এই রো-র আজকের হিসাব (লগ রো) মুছে যাবে। ঠিক আছে?")
                    .setPositiveButton("হ্যাঁ, মুছুন", (d, w) -> onSave.onSave(r, h.btnSave))
                    .setNegativeButton("না", null)
                    .show();
            } else {
                onSave.onSave(r, h.btnSave);
            }
        });
        h.btnRemind.setOnClickListener(v -> { if (onRemind != null) onRemind.onRemind(r); });
        // Batch feature #6: phrase tap -> 1st remark-e jog (khali thakle soja, na hole space diye).
        android.view.View.OnClickListener phraseTap = v -> {
            String phrase = (String) v.getTag();
            if (phrase == null) return;
            String cur = h.etRemark1.getText() == null ? "" : h.etRemark1.getText().toString();
            h.etRemark1.setText(cur.isEmpty() ? phrase : cur + " " + phrase);
            if (h.etRemark1.getText() != null) h.etRemark1.setSelection(h.etRemark1.getText().length());
        };
        h.phraseBtn1.setTag("বিকেলে করবে"); h.phraseBtn2.setTag("ফোন বন্ধ"); h.phraseBtn3.setTag("পরে কল করতে বলেছে");
        h.phraseBtn1.setOnClickListener(phraseTap); h.phraseBtn2.setOnClickListener(phraseTap); h.phraseBtn3.setOnClickListener(phraseTap);
    }
    @Override public int getItemCount() { return shown.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvNumber, tvRow, tvHistory, statusChipConn, statusChipNotConn, validChipYes, validChipNo;
        TextInputEditText etRemark1, etRemark2, etOthers;
        TextWatcher wRemark1, wRemark2, wOthers;
        android.widget.Button btnCall, btnCopy, btnRemind; MaterialButton btnSave;
        android.widget.Button phraseBtn1, phraseBtn2, phraseBtn3;
        Holder(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.tvNumber); tvRow = v.findViewById(R.id.tvRow);
            tvHistory = v.findViewById(R.id.tvHistory);
            statusChipConn = v.findViewById(R.id.statusChipConn); statusChipNotConn = v.findViewById(R.id.statusChipNotConn);
            validChipYes = v.findViewById(R.id.validChipYes); validChipNo = v.findViewById(R.id.validChipNo);
            etRemark1 = v.findViewById(R.id.etRemark1); etRemark2 = v.findViewById(R.id.etRemark2);
            etOthers = v.findViewById(R.id.etOthers); btnCall = v.findViewById(R.id.btnCall);
            btnCopy = v.findViewById(R.id.btnCopy); btnRemind = v.findViewById(R.id.remindBtn);
            phraseBtn1 = v.findViewById(R.id.phraseBtn1); phraseBtn2 = v.findViewById(R.id.phraseBtn2);
            phraseBtn3 = v.findViewById(R.id.phraseBtn3);
            btnSave = v.findViewById(R.id.btnSave);
        }
    }
}
