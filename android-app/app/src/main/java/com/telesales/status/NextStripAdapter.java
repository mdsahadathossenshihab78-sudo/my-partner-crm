package com.telesales.status;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

// v6.9: focus card-er niche "পরের নাম্বারগুলো" strip — tap korle oi number focus hobe.
public class NextStripAdapter extends RecyclerView.Adapter<NextStripAdapter.Holder> {
    public interface OnStripTap { void onStripTap(Row row); }
    private List<Row> rows = new ArrayList<>();
    private final OnStripTap onStripTap;

    public NextStripAdapter(OnStripTap onStripTap) { this.onStripTap = onStripTap; }

    public void setRows(List<Row> rows) {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_next_number, p, false);
        return new Holder(v);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
        Row r = rows.get(pos);
        h.tvNumber.setText(RowAdapter.dialNumber(r.number));
        h.tvSub.setText("বাকি");
        h.itemView.setOnClickListener(v -> { if (onStripTap != null) onStripTap.onStripTap(r); });
    }
    @Override public int getItemCount() { return rows.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvNumber, tvSub;
        Holder(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.nextTvNumber);
            tvSub = v.findViewById(R.id.nextTvSub);
        }
    }
}
