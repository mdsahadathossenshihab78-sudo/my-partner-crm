package com.telesales.status;

public class Row {
    public int row;
    public String number, status, valid, remark1, remark2, others;
    /** v5.3: sheet-e save howa status — tab-er hishab eita diye; chip/draft-e bodlano
     *  status (r.status) diye noy. Save na howa porjonto row tab bodlabe na. */
    public String savedStatus = "";
    /** Log theke asa view-only row (main sheet-e nei) — Save hobena, sudhu dekha jabe. */
    public boolean viewOnly = false;
    /** v5.5: status-ta kokhon dewa hoyeche ("HH:mm", Dhaka) — log-er Time column theke; khali mane somoy jana nei. */
    public String statusTime = "";
    /** v5.6 (35-rows fix): sheet-e status ache kintu ajker log-e entry nei (ager diner call)
     *  — called tab-er seshe "আগের দিনের কল" badge soho dekhabe; Save-e ajker log-e uthbe. */
    public boolean prevDay = false;
    public Row(int row, String number, String status, String valid, String remark1, String remark2, String others) {
        this.row = row; this.number = number == null ? "" : number;
        this.status = status == null ? "" : status; this.valid = valid == null ? "" : valid;
        this.remark1 = remark1 == null ? "" : remark1; this.remark2 = remark2 == null ? "" : remark2;
        this.others = others == null ? "" : others;
        this.savedStatus = this.status;
    }
}
