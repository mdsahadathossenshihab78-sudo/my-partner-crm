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
    /** Tab nam-e single-quote thakle API range-e escape korte hoy. */
    private static String q(String tab) {
        return "'" + tab.replace("'", "''") + "'";
    }

    /** Sheet-er sob tab-er nam ferot dey (Agent ID check korar jonno). */
    public static List<String> getTabs(String token, String sheetId) throws Exception {
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "?fields=sheets.properties.title");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("Authorization", "Bearer " + token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Sheet list load failed: " + code + " " + readAll(c.getErrorStream()));
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        List<String> out = new ArrayList<>();
        JSONArray sheets = j.optJSONArray("sheets");
        if (sheets != null) {
            for (int i = 0; i < sheets.length(); i++) {
                JSONObject p = sheets.getJSONObject(i).optJSONObject("properties");
                if (p != null) out.add(p.optString("title"));
            }
        }
        return out;
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    public static List<Row> getRows(String token, String sheetId, String tab) throws Exception {
        // Open-ended range: 55 row-e atke thakbe na — notun number jog hole
        // (56, 57, ...) setao asbe. Sheet joto boro hobe list toto boro hobe.
        String range = URLEncoder.encode(q(tab) + "!A6:F", "UTF-8");
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
    private static void checkRowStillMatches(String token, String sheetId, String tab, Row row) throws Exception {
        String range = URLEncoder.encode(q(tab) + "!A" + row.row, "UTF-8");
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

    public static void updateRow(String token, String sheetId, String tab, Row row) throws Exception {
        checkRowStillMatches(token, sheetId, tab, row);
        String range = URLEncoder.encode(q(tab) + "!B" + row.row + ":F" + row.row, "UTF-8");
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

    // ---------- Report feature: SEPARATE spreadsheet (numbers sheet-e kono tab khola hobe na) ----------
    /** Notun spreadsheet banay + 4 ta tab-e header bosay, tar ID ferot dey. */
    public static String createReportSpreadsheet(String token, String agentId) throws Exception {
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONObject props = new JSONObject();
        props.put("title", "Telesales Report - " + agentId);
        body.put("properties", props);
        JSONArray sheets = new JSONArray();
        for (String t : new String[]{"Registration", "FTD", "Invalid", "Problems"}) {
            JSONObject s = new JSONObject();
            JSONObject p = new JSONObject();
            p.put("title", t);
            s.put("properties", p);
            sheets.put(s);
        }
        body.put("sheets", sheets);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Report sheet create failed: " + code + " " + readAll(c.getErrorStream()));
        String id = new JSONObject(readAll(c.getInputStream())).optString("spreadsheetId");
        if (id.isEmpty()) throw new Exception("Report sheet ID paini");
        writeHeader(token, id, "Registration", new String[]{"Date", "Number"});
        writeHeader(token, id, "FTD", new String[]{"Date", "Number", "Amount"});
        writeHeader(token, id, "Invalid", new String[]{"Date", "Number", "Reason"});
        writeHeader(token, id, "Problems", new String[]{"Date", "Problem"});
        return id;
    }

    private static void writeHeader(String token, String sheetId, String tab, String[] header) throws Exception {
        String range = URLEncoder.encode(tab + "!A1", "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=RAW");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray vals = new JSONArray();
        JSONArray one = new JSONArray();
        for (String h : header) one.put(h);
        vals.put(one); body.put("values", vals);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Header write failed: " + code + " " + readAll(c.getErrorStream()));
    }

    /** Ekta tab-er sob row (header bad diye) ferot dey, protita row String[] akare. */
    public static List<String[]> getReportRows(String token, String sheetId, String tab) throws Exception {
        String range = URLEncoder.encode(tab + "!A2:Z", "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("Authorization", "Bearer " + token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Report load failed: " + code + " " + readAll(c.getErrorStream()));
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        List<String[]> out = new ArrayList<>();
        JSONArray values = j.optJSONArray("values");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONArray v = values.getJSONArray(i);
                String[] row = new String[v.length()];
                for (int k = 0; k < v.length(); k++) row[k] = v.optString(k);
                out.add(row);
            }
        }
        return out;
    }

    /** Ekta tab-er seshe notun row jog kore. */
    public static void appendReportRow(String token, String sheetId, String tab, String[] vals) throws Exception {
        String range = URLEncoder.encode(tab + "!A:Z", "UTF-8") + ":append";
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=USER_ENTERED");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray values = new JSONArray();
        JSONArray one = new JSONArray();
        for (String s : vals) one.put(s);
        values.put(one); body.put("values", values);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Report add failed: " + code + " " + readAll(c.getErrorStream()));
    }
}
