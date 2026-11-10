package com.telesales.status;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.Intent;
import android.graphics.Color;
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

public class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnSave {
        void onSave(Row row, MaterialButton btn);
        /** Batch fix #3: clear-save hole Save-er age confirm dialog dekhabe kina. Default: na. */
        default boolean needsClearConfirm(Row row) { return false; }
    }
    /** v4.31: row-er "Mone koriye dao" — MainActivity somoy-bachai dialog khulbe. */
    public interface OnRemind { void onRemind(Row row); }
    private OnRemind onRemind;
    public void setOnRemind(OnRemind r) { onRemind = r; }
    /** v6.13: focus card + strip are header items of this RecyclerView
     *  (the RecyclerView is the only scroller now). MainActivity drives
     *  the focus card through these callbacks — the focused row is the
     *  single source of truth for which number is called. */
    public interface FocusActions {
        void onFocusSave(Row row, MaterialButton btn);
        boolean focusNeedsClearConfirm(Row row);
        void onFocusRemind(Row row);
        void onFocusProblem(Row row);
        void onStripTap(Row row);
    }
    private FocusActions focusActions;
    public void setFocusActions(FocusActions a) { focusActions = a; }
    /** v6.13 header state: focusRow = focused pending row (null = no focus
     *  header); stripRows = the next-10 rows for the strip. */
    private Row focusRow = null;
    private List<Row> stripRows = new ArrayList<>();
    private String focusHeaderSub = "";
    public void setFocusHeader(Row focus, List<Row> strip, String headerSub) {
        focusRow = focus;
        stripRows = strip == null ? new ArrayList<>() : new ArrayList<>(strip);
        focusHeaderSub = headerSub == null ? "" : headerSub;
        notifyDataSetChanged();
    }
    public void clearFocusHeader() {
        if (focusRow == null && stripRows.isEmpty()) return;
        focusRow = null; stripRows.clear(); focusHeaderSub = "";
        notifyDataSetChanged();
    }
    public Row getFocusRow() { return focusRow; }
    private int headerCount() {
        int n = 0;
        if (focusRow != null) n++;
        if (focusRow != null && !stripRows.isEmpty()) n++;
        return n;
    }
    public static final int TYPE_FOCUS = 0;
    public static final int TYPE_STRIP = 1;
    public static final int TYPE_ROW = 2;
    private List<Row> all = new ArrayList<>();
    private List<Row> shown = new ArrayList<>();
    private final OnSave onSave;

    public RowAdapter(OnSave onSave) { this.onSave = onSave; setHasStableIds(true); }
    /** Date-filtered called tab: log theke asa row-gulo (null = ekhono load hoyni, tokhon purono niyom). */
    private List<Row> calledRows = null;
    private String calledDate = null;
    public void setCalledRows(List<Row> rows, String date) { calledRows = rows == null ? null : new ArrayList<>(rows); calledDate = date; filter(lastQ); }
    public String getCalledDate() { return calledDate; }
    /** v4.26 + v5.4: Save-er sathe sathe row-ta called tab-er upore (Refresh charai).
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
            // v5.3: tab-er hishab savedStatus diye — draft/chip-e bodlano status diye noy.
            // Na hole chip chaplei (Save-er agei) row pending theke gayeb hoye jeto,
            // kintu log-e entry na thakay called tab-eo asto na.
            if (tab == 0) inTab = (r.savedStatus == null || r.savedStatus.isEmpty());
            else if (calledRows != null) inTab = true; // calledRows-e thaka manei oi date-e call hoyeche
            else inTab = (r.savedStatus != null && !r.savedStatus.isEmpty());
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
    // v6.9: focus card — MainActivity-ke pending row + history porar sujog (copy; kono behaviour bodol noy).
    public List<Row> getShownRows() { return new ArrayList<>(shown); }
    public java.util.List<Hist> getHistoryFor(String dialNum) {
        java.util.List<Hist> h = history.get(dialNum);
        return h == null ? null : new ArrayList<>(h);
    }


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
        h.statusChipConn.setTextColor(conn ? Color.WHITE : Color.parseColor("#1F2937"));
        h.statusChipNotConn.setBackgroundResource(notConn ? R.drawable.chip_rose : R.drawable.glass_field);
        h.statusChipNotConn.setTextColor(notConn ? Color.WHITE : Color.parseColor("#1F2937"));
    }
    private void refreshValidChips(Holder h, String valid) {
        boolean yes = "Yes".equals(valid);
        boolean no = "No".equals(valid);
        h.validChipYes.setBackgroundResource(yes ? R.drawable.chip_blue : R.drawable.glass_field);
        h.validChipYes.setTextColor(yes ? Color.WHITE : Color.parseColor("#1F2937"));
        h.validChipNo.setBackgroundResource(no ? R.drawable.chip_grey : R.drawable.glass_field);
        h.validChipNo.setTextColor(no ? Color.WHITE : Color.parseColor("#1F2937"));
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        LayoutInflater inf = LayoutInflater.from(p.getContext());
        if (t == TYPE_FOCUS) return new FocusHolder(inf.inflate(R.layout.layout_focus_card, p, false));
        if (t == TYPE_STRIP) return new StripHolder(inf.inflate(R.layout.item_strip_header, p, false));
        return new Holder(inf.inflate(R.layout.item_row, p, false));
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
        int type = getItemViewType(pos);
        if (type == TYPE_FOCUS) { bindFocus((FocusHolder) vh, focusRow); return; }
        if (type == TYPE_STRIP) { bindStrip((StripHolder) vh); return; }
        bindRow((Holder) vh, shown.get(pos - headerCount()));
    }
    private void bindRow(@NonNull Holder h, Row r) {
        h.tvNumber.setText(dialNumber(r.number));
        String timeTxt = (r.statusTime != null && !r.statusTime.isEmpty()) ? " • " + MainActivity.formatTimeBn(r.statusTime) : "";
        h.tvRow.setText((r.viewOnly ? "লগ থেকে" : "রো " + r.row) + timeTxt + (r.prevDay ? " • আগের দিনের কল" : ""));
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
        // v6.13: text AGE bosano, watcher PORE — na hole bind-er somoy
        // setText() watcher chaliye DraftStore-e ojotha lekhe (scroll-e
        // hajar bar). Age remove (upore hoyeche), tarpor setText, tarpor add.
        h.etRemark1.setText(r.remark1); h.etRemark2.setText(r.remark2); h.etOthers.setText(r.others);
        h.etRemark1.addTextChangedListener(h.wRemark1); h.etRemark2.addTextChangedListener(h.wRemark2); h.etOthers.addTextChangedListener(h.wOthers);
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
    @Override public int getItemCount() { return headerCount() + shown.size(); }
    @Override public int getItemViewType(int position) {
        if (focusRow != null && position == 0) return TYPE_FOCUS;
        if (focusRow != null && !stripRows.isEmpty() && position == 1) return TYPE_STRIP;
        return TYPE_ROW;
    }
    @Override public long getItemId(int position) {
        int type = getItemViewType(position);
        if (type == TYPE_FOCUS) return -1L;
        if (type == TYPE_STRIP) return -2L;
        Row r = shown.get(position - headerCount());
        long numHash = r == null || r.number == null ? 0L : (dialNumber(r.number).hashCode() & 0xffffffffL);
        return ((long) (r == null ? 0 : r.row) << 32) | numHash;
    }

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

    static class FocusHolder extends RecyclerView.ViewHolder {
        TextView headerSub, tvNumber, tvRow, tvHistory, statusChipConn, statusChipNotConn, validChipYes, validChipNo;
        TextInputEditText etRemark1, etRemark2, etOthers;
        TextWatcher wRemark1, wRemark2, wOthers;
        MaterialButton btnCall, btnCopy, btnSave, btnRemind, btnProblem;
        android.widget.Button phraseBtn1, phraseBtn2, phraseBtn3;
        FocusHolder(View v) {
            super(v);
            headerSub = v.findViewById(R.id.focusHeaderSub);
            tvNumber = v.findViewById(R.id.focusTvNumber); tvRow = v.findViewById(R.id.focusTvRow);
            tvHistory = v.findViewById(R.id.focusTvHistory);
            statusChipConn = v.findViewById(R.id.focusStatusChipConn); statusChipNotConn = v.findViewById(R.id.focusStatusChipNotConn);
            validChipYes = v.findViewById(R.id.focusValidChipYes); validChipNo = v.findViewById(R.id.focusValidChipNo);
            etRemark1 = v.findViewById(R.id.focusEtRemark1); etRemark2 = v.findViewById(R.id.focusEtRemark2);
            etOthers = v.findViewById(R.id.focusEtOthers); btnCall = v.findViewById(R.id.focusBtnCall);
            btnCopy = v.findViewById(R.id.focusBtnCopy); btnRemind = v.findViewById(R.id.focusBtnRemind);
            btnProblem = v.findViewById(R.id.focusBtnProblem);
            phraseBtn1 = v.findViewById(R.id.focusPhraseBtn1); phraseBtn2 = v.findViewById(R.id.focusPhraseBtn2);
            phraseBtn3 = v.findViewById(R.id.focusPhraseBtn3);
            btnSave = v.findViewById(R.id.focusBtnSave);
        }
    }

    static class StripHolder extends RecyclerView.ViewHolder {
        RecyclerView inner;
        NextStripAdapter innerAdapter;
        StripHolder(View v) {
            super(v);
            inner = v.findViewById(R.id.stripInner);
            inner.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(v.getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        }
    }

    private void bindStrip(StripHolder h) {
        if (h.innerAdapter == null) {
            h.innerAdapter = new NextStripAdapter(row -> { if (focusActions != null) focusActions.onStripTap(row); });
            h.inner.setAdapter(h.innerAdapter);
        }
        h.innerAdapter.setRows(stripRows);
    }

    private void refreshFocusStatusChips(FocusHolder h, String status) {
        boolean conn = "call connected".equals(status);
        boolean notConn = "not connected".equals(status);
        h.statusChipConn.setBackgroundResource(conn ? R.drawable.chip_green : R.drawable.glass_field);
        h.statusChipConn.setTextColor(conn ? Color.WHITE : Color.parseColor("#1F2937"));
        h.statusChipNotConn.setBackgroundResource(notConn ? R.drawable.chip_rose : R.drawable.glass_field);
        h.statusChipNotConn.setTextColor(notConn ? Color.WHITE : Color.parseColor("#1F2937"));
    }
    private void refreshFocusValidChips(FocusHolder h, String valid) {
        boolean yes = "Yes".equals(valid);
        boolean no = "No".equals(valid);
        h.validChipYes.setBackgroundResource(yes ? R.drawable.chip_blue : R.drawable.glass_field);
        h.validChipYes.setTextColor(yes ? Color.WHITE : Color.parseColor("#1F2937"));
        h.validChipNo.setBackgroundResource(no ? R.drawable.chip_grey : R.drawable.glass_field);
        h.validChipNo.setTextColor(no ? Color.WHITE : Color.parseColor("#1F2937"));
    }

    /** v6.13: focus card bind — MainActivity.bindFocusCard-er mirror
     *  (v6.9-er motoi duplicate; shared binder-e hat dewa jhukipurno).
     *  Ekhanei thake, karon focus card ekhon ei adapter-er header item. */
    private void bindFocus(FocusHolder h, Row r) {
        if (r == null) return;
        if (h.headerSub != null) h.headerSub.setText(focusHeaderSub);
        h.tvNumber.setText(dialNumber(r.number));
        String timeTxt = (r.statusTime != null && !r.statusTime.isEmpty()) ? " • " + MainActivity.formatTimeBn(r.statusTime) : "";
        h.tvRow.setText((r.viewOnly ? "লগ থেকে" : "রো " + r.row) + timeTxt + (r.prevDay ? " • আগের দিনের কল" : ""));
        java.util.List<Hist> hist = history.get(dialNumber(r.number));
        StringBuilder hb = new StringBuilder();
        int histShown = 0, histMore = 0;
        if (hist != null) {
            for (int i = hist.size() - 1; i >= 0; i--) {
                Hist he = hist.get(i);
                if (histShown < 3) { if (hb.length() > 0) hb.append(" • "); hb.append(he.label); histShown++; }
                else histMore++;
            }
        }
        if (histShown == 0) h.tvHistory.setVisibility(View.GONE);
        else { h.tvHistory.setText("আগের কল: " + hb.toString() + (histMore > 0 ? " +" + histMore + "টা" : "")); h.tvHistory.setVisibility(View.VISIBLE); }
        boolean ro = r.viewOnly;
        h.statusChipConn.setEnabled(!ro); h.statusChipNotConn.setEnabled(!ro);
        h.validChipYes.setEnabled(!ro); h.validChipNo.setEnabled(!ro);
        float chipAlpha = ro ? 0.55f : 1f;
        h.statusChipConn.setAlpha(chipAlpha); h.statusChipNotConn.setAlpha(chipAlpha);
        h.validChipYes.setAlpha(chipAlpha); h.validChipNo.setAlpha(chipAlpha);
        h.etRemark1.setEnabled(!ro); h.etRemark2.setEnabled(!ro); h.etOthers.setEnabled(!ro);
        h.phraseBtn1.setEnabled(!ro); h.phraseBtn2.setEnabled(!ro); h.phraseBtn3.setEnabled(!ro);
        h.btnSave.setEnabled(!ro);
        h.btnSave.setText(ro ? "শুধু দেখা যাবে" : "সেভ করুন");
        refreshFocusStatusChips(h, r.status);
        refreshFocusValidChips(h, r.valid);
        h.statusChipConn.setOnClickListener(v -> { if (r.viewOnly) return; r.status = "call connected".equals(r.status) ? "" : "call connected"; refreshFocusStatusChips(h, r.status); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        h.statusChipNotConn.setOnClickListener(v -> { if (r.viewOnly) return; r.status = "not connected".equals(r.status) ? "" : "not connected"; refreshFocusStatusChips(h, r.status); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        h.validChipYes.setOnClickListener(v -> { if (r.viewOnly) return; r.valid = "Yes".equals(r.valid) ? "" : "Yes"; refreshFocusValidChips(h, r.valid); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        h.validChipNo.setOnClickListener(v -> { if (r.viewOnly) return; r.valid = "No".equals(r.valid) ? "" : "No"; refreshFocusValidChips(h, r.valid); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); });
        if (h.wRemark1 != null) h.etRemark1.removeTextChangedListener(h.wRemark1);
        if (h.wRemark2 != null) h.etRemark2.removeTextChangedListener(h.wRemark2);
        if (h.wOthers != null) h.etOthers.removeTextChangedListener(h.wOthers);
        h.wRemark1 = new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable s) { r.remark1 = s.toString(); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); } };
        h.wRemark2 = new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable s) { r.remark2 = s.toString(); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); } };
        h.wOthers = new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable s) { r.others = s.toString(); DraftStore.save(h.itemView.getContext(), r.number, r.status, r.valid, r.remark1, r.remark2, r.others); } };
        h.etRemark1.setText(r.remark1); h.etRemark2.setText(r.remark2); h.etOthers.setText(r.others);
        h.etRemark1.addTextChangedListener(h.wRemark1); h.etRemark2.addTextChangedListener(h.wRemark2); h.etOthers.addTextChangedListener(h.wOthers);
        h.btnCall.setOnClickListener(v -> {
            String main = dialNumber(r.number);
            String other = h.etOthers.getText() == null ? "" : dialNumber(h.etOthers.getText().toString());
            if (!other.isEmpty() && !other.equals(main)) {
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
            r.remark1 = h.etRemark1.getText() == null ? "" : h.etRemark1.getText().toString().trim();
            r.remark2 = h.etRemark2.getText() == null ? "" : h.etRemark2.getText().toString().trim();
            r.others = h.etOthers.getText() == null ? "" : h.etOthers.getText().toString().trim();
            if (focusActions == null) return;
            if (focusActions.focusNeedsClearConfirm(r)) {
                new AlertDialog.Builder(h.itemView.getContext())
                    .setTitle("নিশ্চিত করুন")
                    .setMessage("এই রো-র আজকের হিসাব (লগ রো) মুছে যাবে। ঠিক আছে?")
                    .setPositiveButton("হ্যাঁ, মুছুন", (d, w) -> focusActions.onFocusSave(r, h.btnSave))
                    .setNegativeButton("না", null)
                    .show();
            } else {
                focusActions.onFocusSave(r, h.btnSave);
            }
        });
        h.btnRemind.setOnClickListener(v -> { if (focusActions != null) focusActions.onFocusRemind(r); });
        h.btnProblem.setOnClickListener(v -> { if (focusActions != null) focusActions.onFocusProblem(r); });
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
}
