package com.scanmart.pos;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Product {
    final String barcode;
    String name;
    long price; // minor units (cents)
    int stock;

    // pharmacy fields (empty for ordinary goods)
    String dci = "";      // generic / active substance (DCI)
    String dosage = "";
    String form = "";
    String lab = "";
    String batch = "";
    long expiry;          // end of the expiry month/day, millis; 0 = none
    boolean rx;           // prescription required
    long tr;              // reference tariff (tarif de reference), minor units; 0 = none
    int reimb;            // reimbursement rate in percent (0 = not reimbursable)

    Product(String barcode, String name, long price, int stock) {
        this.barcode = barcode;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    void copyFrom(Product o) {
        name = o.name;
        price = o.price;
        stock = o.stock;
        dci = o.dci;
        dosage = o.dosage;
        form = o.form;
        lab = o.lab;
        batch = o.batch;
        expiry = o.expiry;
        rx = o.rx;
        tr = o.tr;
        reimb = o.reimb;
    }

    boolean isPharma() {
        return dci.length() > 0 || dosage.length() > 0 || form.length() > 0 || lab.length() > 0
                || expiry > 0 || rx || tr > 0 || reimb > 0;
    }

    /** "DCI • dosage • form" for the parts that are filled in. */
    String detail() {
        StringBuilder sb = new StringBuilder();
        String[] parts = {dci, dosage, form};
        for (String p : parts) {
            if (p.length() == 0) continue;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(p);
        }
        return sb.toString();
    }

    static final long SOON_MS = 90L * 24 * 3600 * 1000;

    /** 0 = fine or no date, 1 = expires within 90 days, 2 = expired. */
    int expiryState() {
        if (expiry <= 0) return 0;
        long now = System.currentTimeMillis();
        if (expiry < now) return 2;
        return expiry < now + SOON_MS ? 1 : 0;
    }

    private static final Pattern MY = Pattern.compile("^(\\d{1,2})[/\\-.](\\d{4}|\\d{2})$");
    private static final Pattern YM = Pattern.compile("^(\\d{4})[/\\-.](\\d{1,2})$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})$");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})$");

    /** @return 0 for empty text, -1 when invalid, otherwise millis at the end of that day/month. */
    static long parseExpiry(String text) {
        if (text == null) return 0;
        String s = text.trim();
        if (s.length() == 0) return 0;
        int y, m, d = 0;
        Matcher x;
        if ((x = MY.matcher(s)).matches()) {
            m = Integer.parseInt(x.group(1));
            y = Integer.parseInt(x.group(2));
            if (y < 100) y += 2000;
        } else if ((x = YM.matcher(s)).matches()) {
            y = Integer.parseInt(x.group(1));
            m = Integer.parseInt(x.group(2));
        } else if ((x = DMY.matcher(s)).matches()) {
            d = Integer.parseInt(x.group(1));
            m = Integer.parseInt(x.group(2));
            y = Integer.parseInt(x.group(3));
        } else if ((x = YMD.matcher(s)).matches()) {
            y = Integer.parseInt(x.group(1));
            m = Integer.parseInt(x.group(2));
            d = Integer.parseInt(x.group(3));
        } else {
            return -1;
        }
        if (m < 1 || m > 12 || y < 2000 || y > 2100) return -1;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, 1, 23, 59, 59);
        int last = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (d == 0) d = last;
        if (d < 1 || d > last) return -1;
        c.set(Calendar.DAY_OF_MONTH, d);
        return c.getTimeInMillis();
    }

    static String fmtExpiry(long t) {
        if (t <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(java.util.Locale.US, "%02d/%d", c.get(Calendar.MONTH) + 1,
                c.get(Calendar.YEAR));
    }
}
