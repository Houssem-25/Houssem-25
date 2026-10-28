package com.scanmart.pos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Talks to Open Food Facts (open, crowd-sourced barcode database). Blocking: use off the UI thread. */
class Catalog {
    interface Progress {
        void update(int pages, int found);
    }

    private static final String BASE = "https://world.openfoodfacts.org";

    private static String get(String url, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestProperty("User-Agent", "ScanMart/2.0 (Android; offline-first POS)");
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String nameOf(JSONObject o) {
        String name = o.optString("product_name", "").trim();
        if (name.length() == 0) name = o.optString("product_name_fr", "").trim();
        if (name.length() == 0) name = o.optString("generic_name", "").trim();
        if (name.length() == 0) return "";
        String brand = o.optString("brands", "");
        int comma = brand.indexOf(',');
        if (comma > 0) brand = brand.substring(0, comma);
        brand = brand.trim();
        if (brand.length() > 0 && !name.toLowerCase().contains(brand.toLowerCase())) {
            name = brand + " " + name;
        }
        String qty = o.optString("quantity", "").trim();
        if (qty.length() > 0 && !name.toLowerCase().contains(qty.toLowerCase())) name += " " + qty;
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    /** @return a display name for the barcode, or null if unknown / offline. */
    static String lookupName(String barcode) {
        try {
            String json = get(BASE + "/api/v2/product/" + URLEncoder.encode(barcode, "UTF-8")
                    + ".json?fields=product_name,product_name_fr,generic_name,brands,quantity", 5000, 7000);
            JSONObject root = new JSONObject(json);
            if (root.optInt("status", 0) != 1) return null;
            String n = nameOf(root.getJSONObject("product"));
            return n.length() == 0 ? null : n;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Downloads the most-scanned products sold in Algeria. Prices are not part of the open data,
     * so they are imported as 0 and must be set by the shop owner.
     */
    static List<Product> fetchAlgeria(int maxPages, Progress progress) throws Exception {
        String[] filters = {"countries_tags_en=algeria", "countries_tags=en:algeria"};
        List<Product> out = new ArrayList<Product>();
        Exception last = null;
        for (String filter : filters) {
            try {
                for (int page = 1; page <= maxPages; page++) {
                    String json = get(BASE + "/api/v2/search?" + filter
                            + "&fields=code,product_name,product_name_fr,generic_name,brands,quantity"
                            + "&sort_by=unique_scans_n&page_size=100&page=" + page, 12000, 30000);
                    JSONArray arr = new JSONObject(json).optJSONArray("products");
                    if (arr == null || arr.length() == 0) break;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String code = Db.normalize(o.optString("code", ""));
                        String name = nameOf(o);
                        if (code.length() < 6 || name.length() == 0) continue;
                        out.add(new Product(code, name, 0, 0));
                    }
                    if (progress != null) progress.update(page, out.size());
                    if (arr.length() < 100) break;
                }
            } catch (Exception e) {
                last = e;
            }
            if (!out.isEmpty()) return out;
        }
        if (last != null) throw last;
        return out;
    }
}
