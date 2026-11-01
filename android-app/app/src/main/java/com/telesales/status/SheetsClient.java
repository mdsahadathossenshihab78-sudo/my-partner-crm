package com.telesales.status;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SheetsClient {
    public static final String TAB = "bdxsukuna30";

    private static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    public static List<Row> getRows(String token, String sheetId) throws Exception {
        // Open-ended range: 55 row-e atke thakbe na — notun number jog hole
        // (56, 57, ...) setao asbe. Sheet joto boro hobe list toto boro hobe.
        String range = URLEncoder.encode(TAB + "!A6:F", "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("Authorization", "Bearer " + token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Sheet load failed: " + code + " " + readAll(c.getErrorStream()));
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        List<Row> out = new ArrayList<>();
        JSONArray values = j.optJSONArray("values");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONArray v = values.getJSONArray(i);
                String a = v.length() > 0 ? v.optString(0) : "";
                String b = v.length() > 1 ? v.optString(1) : "";
                String cc = v.length() > 2 ? v.optString(2) : "";
                String d = v.length() > 3 ? v.optString(3) : "";
                String e = v.length() > 4 ? v.optString(4) : "";
                String f = v.length() > 5 ? v.optString(5) : "";
                // Puro khali row (number delete/clear hoye gele) app-e dekhabe na.
                // Row number index theke hisab hoy, tai porer row-gulor number thik thakbe.
                if (a.isEmpty() && b.isEmpty() && cc.isEmpty() && d.isEmpty() && e.isEmpty() && f.isEmpty()) continue;
                out.add(new Row(6 + i, a, b, cc, d, e, f));
            }
        }
        return out;
    }

    /** Save-er age check: sheet-e oi row-er number ekhono eki ache kina.
     *  Team-er keu majhkhane row delete korle row sore jay — tokhon vul row-e
     *  save na hoye user-ke Refresh korte bola hobe. */
    private static void checkRowStillMatches(String token, String sheetId, Row row) throws Exception {
        String range = URLEncoder.encode(TAB + "!A" + row.row, "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("Authorization", "Bearer " + token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Sheet check failed: " + code);
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        JSONArray values = j.optJSONArray("values");
        String current = "";
        if (values != null && values.length() > 0) {
            JSONArray v = values.getJSONArray(0);
            if (v.length() > 0) current = v.optString(0);
        }
        if (!current.trim().equals(row.number.trim())) {
            throw new Exception("Sheet-e row bodle geche — Refresh kore abar save korun");
        }
    }

    public static void updateRow(String token, String sheetId, Row row) throws Exception {
        checkRowStillMatches(token, sheetId, row);
        String range = URLEncoder.encode(TAB + "!B" + row.row + ":F" + row.row, "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=USER_ENTERED");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray vals = new JSONArray();
        JSONArray one = new JSONArray();
        one.put(row.status); one.put(row.valid); one.put(row.remark1); one.put(row.remark2); one.put(row.others);
        vals.put(one); body.put("values", vals);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Sheet save failed: " + code + " " + readAll(c.getErrorStream()));
    }
}
