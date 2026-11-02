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
    /** v4.19: Sheets/Drive HTTP call-er timeout — ager moto infinite wait noy, atke gele
     *  "saving..."-e jhulbe na; timeout hole caller retry/outbox-e jabe. */
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    /** Sob call eki poth-e khole: timeout + Authorization header ekhanei thake. */
    private static HttpURLConnection open(URL url, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private static String q(String tab) {
        return "'" + tab.replace("'", "''") + "'";
    }

    public static List<String> getTabs(String token, String sheetId) throws Exception {
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "?fields=sheets.properties.title");
        HttpURLConnection c = open(url, token);
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

    public static String findSpreadsheetByName(String token, String name) {
        try {
            String qq = URLEncoder.encode("name = '" + name.replace("'", "\\'") + "' and trashed = false", "UTF-8");
            URL url = new URL("https://www.googleapis.com/drive/v3/files?q=" + qq + "&orderBy=createdTime&fields=files(id,createdTime)");
            HttpURLConnection c = open(url, token);
                if (c.getResponseCode() != 200) return null;
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            JSONArray files = j.optJSONArray("files");
            if (files == null || files.length() == 0) return null;
            return files.getJSONObject(0).optString("id", null);
        } catch (Exception e) { return null; }
    }

    /** Drive files.get ownedByMe: login kora user-i file-er malik kina. Fail hole exception (caller fail-closed). */
    public static boolean isFileOwnedByMe(String token, String fileId) throws Exception {
        URL url = new URL("https://www.googleapis.com/drive/v3/files/" + URLEncoder.encode(fileId, "UTF-8") + "?fields=ownedByMe");
        HttpURLConnection c = open(url, token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Owner check failed: " + code + " " + readAll(c.getErrorStream()));
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        return j.optBoolean("ownedByMe", false);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    public static List<Row> getRows(String token, String sheetId, String tab) throws Exception {
        String range = URLEncoder.encode(q(tab) + "!A6:F", "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = open(url, token);
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
                if (a.isEmpty() && b.isEmpty() && cc.isEmpty() && d.isEmpty() && e.isEmpty() && f.isEmpty()) continue;
                out.add(new Row(6 + i, a, b, cc, d, e, f));
            }
        }
        return out;
    }

    private static void checkRowStillMatches(String token, String sheetId, String tab, Row row) throws Exception {
        String range = URLEncoder.encode(q(tab) + "!A" + row.row, "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = open(url, token);
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
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=RAW");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
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

    public static String createReportSpreadsheet(String token, String agentId) throws Exception {
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
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
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
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

    public static String createStatusLogSpreadsheet(String token, String agentId) throws Exception {
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONObject props = new JSONObject();
        props.put("title", "Telesales Status Log - " + agentId);
        body.put("properties", props);
        JSONArray sheets = new JSONArray();
        JSONObject s = new JSONObject();
        JSONObject p = new JSONObject();
        p.put("title", "Status Log");
        s.put("properties", p);
        sheets.put(s);
        body.put("sheets", sheets);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Status log create failed: " + code + " " + readAll(c.getErrorStream()));
        String id = new JSONObject(readAll(c.getInputStream())).optString("spreadsheetId");
        if (id.isEmpty()) throw new Exception("Status log ID paini");
        writeHeader(token, id, "Status Log", new String[]{"Date", "Number", "Status", "Valid"});
        return id;
    }

    /** v4.14 shared Status Log: age theke thaka "Telesales Status Log" file-e agent-er tab thakle kichu kore na, na thakle Sheets batchUpdate-e tab khule header bosay. Shared file kokhono toiri kore na, shudhu age theke thaka file-e tab jog kore. Tab toiri / pora age thekei thaka spreadsheets scope-e hoy, tai notun OAuth scope lage na. Main "Telesales (Team-03)" sheet-e kono tab jog kore na. */
    public static void ensureSheetTab(String token, String spreadsheetId, String title) throws Exception {
        List<String> tabs = getTabs(token, spreadsheetId);
        for (String t : tabs) if (title.equals(t)) return;
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray reqs = new JSONArray();
        JSONObject req = new JSONObject();
        JSONObject add = new JSONObject();
        JSONObject props = new JSONObject();
        props.put("title", title);
        add.put("properties", props);
        req.put("addSheet", add);
        reqs.put(req);
        body.put("requests", reqs);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) {
            String err = readAll(c.getErrorStream());
            // v4.19: duijon device ek sathe tab banale ekjon "already exists" pabe — eta
            // vul noy, tab ekhon ache mane-i sofol; header abar lekha hobe na.
            if (err != null && err.toLowerCase().contains("already exists")) return;
            throw new Exception("Status log tab create failed: " + code + " " + err);
        }
        writeHeader(token, spreadsheetId, title, new String[]{"Date", "Number", "Status", "Valid"});
        try { formatStatusLogTab(token, spreadsheetId, title); } catch (Exception ignored) { /* format na bosleo tab + header thik thakbe */ }
    }

    /** v4.19: notun agent tab-er professional look — bold sada header, dark fill, row 1 freeze,
     *  header filter, readable width — jate hate-banano bdxsukuna30 tab-er moto dekhay. */
    private static void formatStatusLogTab(String token, String spreadsheetId, String title) throws Exception {
        int gid = getSheetNumericId(token, spreadsheetId, title);
        JSONObject dark = new JSONObject(); dark.put("red", 0.22); dark.put("green", 0.25); dark.put("blue", 0.29);
        JSONObject white = new JSONObject(); white.put("red", 1.0); white.put("green", 1.0); white.put("blue", 1.0);
        JSONArray reqs = new JSONArray();
        JSONObject freeze = new JSONObject();
        JSONObject freezeProps = new JSONObject(); freezeProps.put("sheetId", gid);
        JSONObject grid = new JSONObject(); grid.put("frozenRowCount", 1); freezeProps.put("gridProperties", grid);
        JSONObject freezeReq = new JSONObject(); freezeReq.put("properties", freezeProps); freezeReq.put("fields", "gridProperties.frozenRowCount");
        freeze.put("updateSheetProperties", freezeReq); reqs.put(freeze);
        JSONObject fmt = new JSONObject();
        JSONObject range = new JSONObject(); range.put("sheetId", gid); range.put("startRowIndex", 0); range.put("endRowIndex", 1);
        JSONObject cellFmt = new JSONObject(); cellFmt.put("backgroundColor", dark);
        JSONObject textFmt = new JSONObject(); textFmt.put("bold", true); textFmt.put("foregroundColor", white); cellFmt.put("textFormat", textFmt);
        JSONObject cell = new JSONObject(); cell.put("userEnteredFormat", cellFmt);
        JSONObject repeat = new JSONObject(); repeat.put("range", range); repeat.put("cell", cell); repeat.put("fields", "userEnteredFormat(backgroundColor,textFormat)");
        fmt.put("repeatCell", repeat); reqs.put(fmt);
        JSONObject filter = new JSONObject();
        JSONObject filterRange = new JSONObject(); filterRange.put("sheetId", gid); filterRange.put("startRowIndex", 0);
        JSONObject filterObj = new JSONObject(); filterObj.put("range", filterRange); filterObj.put("title", title);
        filter.put("filter", filterObj);
        JSONObject filterReq = new JSONObject(); filterReq.put("setBasicFilter", filter); reqs.put(filterReq);
        int[] widths = {140, 160, 150};
        for (int i = 0; i < widths.length; i++) {
            JSONObject dimRange = new JSONObject(); dimRange.put("sheetId", gid); dimRange.put("dimension", "COLUMNS"); dimRange.put("startIndex", i); dimRange.put("endIndex", i + 1);
            JSONObject dimProps = new JSONObject(); dimProps.put("pixelSize", widths[i]);
            JSONObject dimReq = new JSONObject(); dimReq.put("range", dimRange); dimReq.put("properties", dimProps); dimReq.put("fields", "pixelSize");
            JSONObject dim = new JSONObject(); dim.put("updateDimensionProperties", dimReq); reqs.put(dim);
        }
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate");
        HttpURLConnection conn = open(url, token);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject(); body.put("requests", reqs);
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("Tab format failed: " + code + " " + readAll(conn.getErrorStream()));
    }

    /** Ekta range-e (jemon Registry!B5:C5) RAW diye lekha — formula hisebe noy, literal thakbe. */
    public static void updateCells(String token, String sheetId, String rangePlain, String[][] vals) throws Exception {
        String range = URLEncoder.encode(rangePlain, "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=RAW");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray values = new JSONArray();
        for (String[] row : vals) { JSONArray one = new JSONArray(); for (String s : row) one.put(s == null ? "" : s); values.put(one); }
        body.put("values", values);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Cell update failed: " + code + " " + readAll(c.getErrorStream()));
    }

    /** v4.19 REGISTRY (PHASED Drive-scope removal): shared "Telesales Status Log" file-er
     *  vitore "Registry" tab-e Agent ID -> Report file ID (+ legacy log file ID) thake.
     *  Registry kokhono main "Telesales (Team-03)" sheet-e banano hobe na — shudhu shared
     *  log file-e. v4.19-e drive.readonly scope ekhono chawa hoy (sign-in request bodlano
     *  hoyni); registry ekhon backfill-only — Drive search fallback cholbe, ar Drive search
     *  je file pabe tar ID registry-te likhe rakha hobe. Scope sora hobe porer version-e,
     *  registry coverage confirm howar pore. */
    public static final String REGISTRY_TAB = "Registry";

    public static void ensureRegistryTab(String token, String spreadsheetId) throws Exception {
        List<String> tabs = getTabs(token, spreadsheetId);
        for (String t : tabs) if (REGISTRY_TAB.equals(t)) return;
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray reqs = new JSONArray();
        JSONObject req = new JSONObject();
        JSONObject add = new JSONObject();
        JSONObject props = new JSONObject();
        props.put("title", REGISTRY_TAB);
        add.put("properties", props);
        req.put("addSheet", add);
        reqs.put(req);
        body.put("requests", reqs);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) {
            String err = readAll(c.getErrorStream());
            if (err != null && err.toLowerCase().contains("already exists")) return;
            throw new Exception("Registry tab create failed: " + code + " " + err);
        }
        writeHeader(token, spreadsheetId, REGISTRY_TAB, new String[]{"Agent ID", "Report File ID", "Legacy Log File ID"});
    }

    /** Registry theke ekjon agent-er row: {reportId, legacyId} (jeta nei seta ""), na pele null. */
    public static String[] getRegistryEntry(String token, String spreadsheetId, String agentId) {
        try {
            List<String[]> rows = getReportRows(token, spreadsheetId, REGISTRY_TAB);
            for (String[] r : rows) {
                if (r.length > 0 && agentId.equals(r[0])) {
                    return new String[]{r.length > 1 ? r[1] : "", r.length > 2 ? r[2] : ""};
                }
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** Registry-te agent-er row jog/update: thakle khali ghor puron kore, na thakle notun row. */
    public static void upsertRegistryEntry(String token, String spreadsheetId, String agentId, String reportId, String legacyId) throws Exception {
        ensureRegistryTab(token, spreadsheetId);
        List<String[]> rows = getReportRows(token, spreadsheetId, REGISTRY_TAB);
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length > 0 && agentId.equals(r[0])) {
                String oldRep = r.length > 1 ? r[1] : "";
                String oldLeg = r.length > 2 ? r[2] : "";
                String newRep = (reportId != null && !reportId.isEmpty()) ? reportId : oldRep;
                String newLeg = (legacyId != null && !legacyId.isEmpty()) ? legacyId : oldLeg;
                if (newRep.equals(oldRep) && newLeg.equals(oldLeg)) return;
                updateCells(token, spreadsheetId, REGISTRY_TAB + "!B" + (i + 2) + ":C" + (i + 2), new String[][]{{newRep, newLeg}});
                return;
            }
        }
        appendReportRow(token, spreadsheetId, REGISTRY_TAB, new String[]{agentId, reportId == null ? "" : reportId, legacyId == null ? "" : legacyId});
    }

    public static List<String[]> getReportRows(String token, String sheetId, String tab) throws Exception {
        String range = URLEncoder.encode(tab + "!A2:Z", "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = open(url, token);
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

    public static void appendReportRow(String token, String sheetId, String tab, String[] vals) throws Exception {
        String range = URLEncoder.encode(tab + "!A:Z", "UTF-8") + ":append";
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=RAW");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
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

    /** Ekta tab-er seshe onekgulo row ekbare jog kore (shared-tab migration-er jonno). */
    public static void appendReportRows(String token, String sheetId, String tab, List<String[]> rows) throws Exception {
        if (rows == null || rows.isEmpty()) return;
        String range = URLEncoder.encode(tab + "!A:Z", "UTF-8") + ":append";
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=RAW");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray values = new JSONArray();
        for (String[] vals : rows) {
            JSONArray one = new JSONArray();
            for (String s : vals) one.put(s == null ? "" : s);
            values.put(one);
        }
        body.put("values", values);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Report add failed: " + code + " " + readAll(c.getErrorStream()));
    }

    public static void appendNumbers(String token, String sheetId, String tab, List<String> numbers) throws Exception {
        String range = URLEncoder.encode(q(tab) + "!A6:A", "UTF-8") + ":append";
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range + "?valueInputOption=RAW");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray values = new JSONArray();
        for (String n : numbers) { JSONArray one = new JSONArray(); one.put(n); values.put(one); }
        body.put("values", values);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Number add failed: " + code + " " + readAll(c.getErrorStream()));
    }

    public static int getSheetNumericId(String token, String spreadsheetId, String tab) throws Exception {
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "?fields=sheets.properties");
        HttpURLConnection c = open(url, token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Sheet info load failed: " + code + " " + readAll(c.getErrorStream()));
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        JSONArray sheets = j.optJSONArray("sheets");
        if (sheets != null) {
            for (int i = 0; i < sheets.length(); i++) {
                JSONObject p = sheets.getJSONObject(i).optJSONObject("properties");
                if (p != null && tab.equals(p.optString("title"))) return p.optInt("sheetId");
            }
        }
        throw new Exception("Tab pai ni: " + tab);
    }

    /** Ekta cell-er prothom value (delete-er age row bodle geche kina jachai-er jonno). */
    public static String getFirstCell(String token, String sheetId, String tab, String a1) throws Exception {
        String range = URLEncoder.encode(q(tab) + "!" + a1, "UTF-8");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + sheetId + "/values/" + range);
        HttpURLConnection c = open(url, token);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Cell read failed: " + code + " " + readAll(c.getErrorStream()));
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        JSONArray values = j.optJSONArray("values");
        if (values == null || values.length() == 0) return "";
        JSONArray v = values.getJSONArray(0);
        return v.length() == 0 ? "" : v.optString(0);
    }

    public static void deleteReportRow(String token, String spreadsheetId, String tab, int sheetRow) throws Exception {
        int gid = getSheetNumericId(token, spreadsheetId, tab);
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate");
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray reqs = new JSONArray();
        JSONObject req = new JSONObject();
        JSONObject del = new JSONObject();
        JSONObject range = new JSONObject();
        range.put("sheetId", gid);
        range.put("dimension", "ROWS");
        range.put("startIndex", sheetRow - 1);
        range.put("endIndex", sheetRow);
        del.put("range", range);
        req.put("deleteDimension", del);
        reqs.put(req);
        body.put("requests", reqs);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.flush(); os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Report delete failed: " + code + " " + readAll(c.getErrorStream()));
    }
}
