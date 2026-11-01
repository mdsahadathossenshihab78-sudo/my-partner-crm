package com.telesales.status;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
    public interface OnSave { void onSave(Row row, MaterialButton btn); }
    private List<Row> all = new ArrayList<>();
    private List<Row> shown = new ArrayList<>();
    private final OnSave onSave;
    private final String[] statusOpts = {"", "call connected", "not connected"};
    private final String[] statusLabels = {"— খালি —", "call connected", "not connected"};
    private final String[] validOpts = {"", "Yes", "No"};
    private final String[] validLabels = {"— খালি —", "Yes", "No"};

    public RowAdapter(OnSave onSave) { this.onSave = onSave; }

    public void setRows(List<Row> rows) {
        all = new ArrayList<>(rows); shown = new ArrayList<>(rows); notifyDataSetChanged();
    }
    public void filter(String q) {
        shown.clear();
        if (q == null || q.trim().isEmpty()) shown.addAll(all);
        else for (Row r : all) if (r.number.contains(q.trim())) shown.add(r);
        notifyDataSetChanged();
    }
    public List<Row> getAll() { return all; }

    private int indexOf(String[] opts, String v) {
        for (int i = 0; i < opts.length; i++) if (opts[i].equals(v)) return i;
        return 0;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false);
        return new Holder(v);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
        Row r = shown.get(pos);
        h.tvNumber.setText(r.number);
        h.tvRow.setText("রো " + r.row);
        ArrayAdapter<String> sa = new ArrayAdapter<>(h.itemView.getContext(), android.R.layout.simple_spinner_item, statusLabels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        h.spStatus.setAdapter(sa); h.spStatus.setSelection(indexOf(statusOpts, r.status));
        ArrayAdapter<String> va = new ArrayAdapter<>(h.itemView.getContext(), android.R.layout.simple_spinner_item, validLabels);
        va.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        h.spValid.setAdapter(va); h.spValid.setSelection(indexOf(validOpts, r.valid));
        h.etRemark1.setText(r.remark1); h.etRemark2.setText(r.remark2); h.etOthers.setText(r.others);
        h.btnCall.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + r.number));
            h.itemView.getContext().startActivity(i);
        });
        h.btnSave.setOnClickListener(v -> {
            r.status = statusOpts[h.spStatus.getSelectedItemPosition()];
            r.valid = validOpts[h.spValid.getSelectedItemPosition()];
            r.remark1 = h.etRemark1.getText() == null ? "" : h.etRemark1.getText().toString().trim();
            r.remark2 = h.etRemark2.getText() == null ? "" : h.etRemark2.getText().toString().trim();
            r.others = h.etOthers.getText() == null ? "" : h.etOthers.getText().toString().trim();
            onSave.onSave(r, h.btnSave);
        });
    }
    @Override public int getItemCount() { return shown.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvNumber, tvRow; Spinner spStatus, spValid;
        TextInputEditText etRemark1, etRemark2, etOthers;
        android.widget.Button btnCall; MaterialButton btnSave;
        Holder(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.tvNumber); tvRow = v.findViewById(R.id.tvRow);
            spStatus = v.findViewById(R.id.spStatus); spValid = v.findViewById(R.id.spValid);
            etRemark1 = v.findViewById(R.id.etRemark1); etRemark2 = v.findViewById(R.id.etRemark2);
            etOthers = v.findViewById(R.id.etOthers); btnCall = v.findViewById(R.id.btnCall);
            btnSave = v.findViewById(R.id.btnSave);
        }
    }
}
