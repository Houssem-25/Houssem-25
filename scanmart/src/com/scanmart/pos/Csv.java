package com.scanmart.pos;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * CSV import/export. Import is header-driven (English or French column names, "," or ";"
 * separated) so a spreadsheet exported from the national nomenclature works as-is; without a
 * header it expects barcode,name,price,stock,dci,dosage,form,lab,expiry,batch,rx.
 */
class Csv {
    static final String HEADER = "barcode,name,price,stock,dci,dosage,form,lab,expiry,batch,rx";
    private static final String[] COLS = {"barcode", "name", "price", "stock", "dci", "dosage",
            "form", "lab", "expiry", "batch", "rx"};

    static String export(List<Product> items) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Product p : items) {
            sb.append(esc(p.barcode)).append(',').append(esc(p.name)).append(',')
                    .append(Money.plain(p.price)).append(',').append(p.stock).append(',')
                    .append(esc(p.dci)).append(',').append(esc(p.dosage)).append(',')
                    .append(esc(p.form)).append(',').append(esc(p.lab)).append(',')
                    .append(Product.fmtExpiry(p.expiry)).append(',').append(esc(p.batch)).append(',')
                    .append(p.rx ? 1 : 0).append('\n');
        }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static final Map<String, String> SYN = new HashMap<String, String>();

    private static void syn(String col, String... names) {
        for (String n : names) SYN.put(n, col);
    }

    static {
        syn("barcode", "barcode", "code", "codebarre", "codebarres", "ean", "gtin", "cip",
                "codecip", "codeproduit", "nenregistrement", "numeroenregistrement");
        syn("name", "name", "nom", "denomination", "denominationcommerciale", "nomcommercial",
                "marque", "designation", "produit", "medicament");
        syn("price", "price", "prix", "ppa", "tarif", "tarifdereference", "prixdevente", "pv",
                "prixpublic", "prixttc");
        syn("stock", "stock", "qte", "quantite", "qty");
        syn("dci", "dci", "denominationcommuneinternationale", "principeactif", "substance",
                "generique");
        syn("dosage", "dosage", "dose");
        syn("form", "form", "forme", "formegalenique", "presentation");
        syn("lab", "lab", "labo", "laboratoire", "laboratoires", "laboratoiredetenteur",
                "laboratoiresdetenteurs", "fabricant", "detenteur");
        syn("expiry", "expiry", "exp", "peremption", "dateperemption", "dateexpiration",
                "expiration");
        syn("batch", "batch", "lot", "numerolot", "nlot");
        syn("rx", "rx", "ordonnance", "prescription", "liste", "surordonnance");
    }

    private static String key(String h) {
        String n = Normalizer.normalize(h, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static boolean truthy(String s) {
        String t = s.trim().toLowerCase(Locale.US);
        if (t.length() == 0) return false;
        return !(t.equals("0") || t.equals("non") || t.equals("no") || t.equals("false")
                || t.equals("-") || t.equals("n") || t.equals("faux"));
    }

    private static long price(String s) {
        String t = s.replaceAll("[^0-9,.\\-]", "");
        if (t.indexOf(',') >= 0 && t.indexOf('.') >= 0) t = t.replace(",", "");
        long v = Money.parse(t);
        return v < 0 ? 0 : v;
    }

    /** Result of an import: rows parsed plus the number of rows skipped. */
    static class Result {
        final List<Product> items = new ArrayList<Product>();
        int skipped;
    }

    static Result parse(String text) {
        Result res = new Result();
        if (text.length() > 0 && text.charAt(0) == '﻿') text = text.substring(1);
        String[] lines = text.split("\r?\n");
        int first = 0;
        while (first < lines.length && lines[first].trim().length() == 0) first++;
        if (first >= lines.length) return res;

        char delim = count(lines[first], ';') > count(lines[first], ',') ? ';' : ',';
        List<String> head = fields(lines[first], delim);
        Map<String, Integer> idx = new HashMap<String, Integer>();
        for (int i = 0; i < head.size(); i++) {
            String col = SYN.get(key(head.get(i)));
            if (col != null && !idx.containsKey(col)) idx.put(col, i);
        }
        int start;
        if (idx.containsKey("name") || idx.containsKey("barcode")) {
            start = first + 1; // header row present
        } else {
            start = first; // positional
            idx.clear();
            for (int i = 0; i < COLS.length; i++) idx.put(COLS[i], i);
        }

        for (int li = start; li < lines.length; li++) {
            if (lines[li].trim().length() == 0) continue;
            List<String> f = fields(lines[li], delim);
            String name = get(f, idx, "name").trim();
            if (name.length() == 0) {
                res.skipped++;
                continue;
            }
            Product p0 = new Product(null, name, 0, 0);
            p0.dci = get(f, idx, "dci").trim();
            p0.dosage = get(f, idx, "dosage").trim();
            p0.form = get(f, idx, "form").trim();
            p0.lab = get(f, idx, "lab").trim();
            String code = Db.normalize(get(f, idx, "barcode"));
            if (code.length() == 0) {
                CRC32 crc = new CRC32();
                crc.update((name + "|" + p0.dosage + "|" + p0.form + "|" + p0.lab)
                        .toLowerCase(Locale.US).getBytes());
                code = String.format(Locale.US, "DZ-%08X", crc.getValue());
            }
            Product p = new Product(code, name, price(get(f, idx, "price")), 0);
            try {
                p.stock = Math.max(0, Integer.parseInt(get(f, idx, "stock").trim()));
            } catch (NumberFormatException ignored) {
            }
            p.dci = p0.dci;
            p.dosage = p0.dosage;
            p.form = p0.form;
            p.lab = p0.lab;
            p.batch = get(f, idx, "batch").trim();
            long ex = Product.parseExpiry(get(f, idx, "expiry"));
            p.expiry = ex > 0 ? ex : 0;
            p.rx = truthy(get(f, idx, "rx"));
            res.items.add(p);
        }
        return res;
    }

    private static String get(List<String> f, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        return i == null || i >= f.size() ? "" : f.get(i);
    }

    private static int count(String s, char c) {
        int n = 0;
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') q = !q;
            else if (ch == c && !q) n++;
        }
        return n;
    }

    private static List<String> fields(String line, char delim) {
        List<String> f = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (q) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else q = false;
                } else cur.append(ch);
            } else if (ch == '"') q = true;
            else if (ch == delim) {
                f.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        f.add(cur.toString());
        return f;
    }
}
