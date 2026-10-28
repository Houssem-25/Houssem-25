package com.scanmart.pos;

import android.content.Context;
import android.content.SharedPreferences;

import java.math.BigDecimal;
import java.util.Locale;

class Money {
    private static String symbol = "DA";

    static void load(Context c) {
        symbol = prefs(c).getString("currency", "DA");
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("scanmart", Context.MODE_PRIVATE);
    }

    static void setSymbol(Context c, String s) {
        symbol = s;
        prefs(c).edit().putString("currency", s).apply();
    }

    static String symbol() {
        return symbol;
    }

    /** Dinar-style ("1200 DA") for alphabetic symbols, prefix style ("$1.20") otherwise. */
    static String fmt(long cents) {
        long abs = Math.abs(cents);
        boolean suffix = symbol.length() > 0 && Character.isLetter(symbol.charAt(0));
        String sign = cents < 0 ? "-" : "";
        if (suffix) {
            String num = abs % 100 == 0 ? String.valueOf(abs / 100)
                    : String.format(Locale.US, "%d.%02d", abs / 100, abs % 100);
            return sign + num + " " + symbol;
        }
        return sign + symbol + String.format(Locale.US, "%d.%02d", abs / 100, abs % 100);
    }

    /** Parses user text such as "3", "3.5" or "3,50"; returns -1 when invalid. */
    static long parse(String text) {
        if (text == null) return -1;
        String t = text.trim().replace(',', '.');
        if (t.length() == 0) return -1;
        try {
            BigDecimal v = new BigDecimal(t);
            if (v.signum() < 0 || v.scale() > 2 && v.stripTrailingZeros().scale() > 2) return -1;
            return v.movePointRight(2).longValueExact();
        } catch (Exception e) {
            return -1;
        }
    }

    static String plain(long cents) {
        return String.format(Locale.US, "%d.%02d", cents / 100, cents % 100);
    }
}
