package com.telesales.status;

public class Row {
    public int row;
    public String number, status, valid, remark1, remark2, others;
    /** v5.3: sheet-e save howa status — tab-er hishab eita diye; chip/draft-e bodlano
     *  status (r.status) diye noy. Save na howa porjonto row tab bodlabe na. */
    public String savedStatus = "";
    /** Log theke asa view-only row (main sheet-e nei) — Save hobena, sudhu dekha jabe. */
    public boolean viewOnly = false;
    public Row(int row, String number, String status, String valid, String remark1, String remark2, String others) {
        this.row = row; this.number = number == null ? "" : number;
        this.status = status == null ? "" : status; this.valid = valid == null ? "" : valid;
        this.remark1 = remark1 == null ? "" : remark1; this.remark2 = remark2 == null ? "" : remark2;
        this.others = others == null ? "" : others;
        this.savedStatus = this.status;
    }
}
