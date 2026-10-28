package com.scanmart.pos;

import java.util.ArrayList;
import java.util.List;

class Csv {
    static String export(List<Product> items) {
        StringBuilder sb = new StringBuilder("barcode,name,price,stock\n");
        for (Product p : items) {
            sb.append(esc(p.barcode)).append(',').append(esc(p.name)).append(',')
                    .append(Money.plain(p.price)).append(',').append(p.stock).append('\n');
        }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Parses barcode,name,price,stock rows; bad rows are skipped. A header row is ignored. */
    static List<Product> parse(String text) {
        List<Product> out = new ArrayList<Product>();
        for (String line : text.split("\r?\n")) {
            if (line.trim().length() == 0) continue;
            List<String> f = fields(line);
            if (f.size() < 2) continue;
            String code = Db.normalize(f.get(0));
            String name = f.get(1).trim();
            if (code.length() == 0 || name.length() == 0 || code.equalsIgnoreCase("barcode")) continue;
            long price = f.size() > 2 ? Money.parse(f.get(2)) : 0;
            if (price < 0) price = 0;
            int stock = 0;
            if (f.size() > 3) {
                try {
                    stock = Math.max(0, Integer.parseInt(f.get(3).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            out.add(new Product(code, name, price, stock));
        }
        return out;
    }

    private static List<String> fields(String line) {
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
            else if (ch == ',' || ch == ';') {
                f.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        f.add(cur.toString());
        return f;
    }
}
