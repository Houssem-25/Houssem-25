package com.scanmart.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

class Db extends SQLiteOpenHelper {
    static class Sale {
        long id;
        long ts;
        long total;
        int items;
        String fund = "";
        String insured = "";
        String patient = "";
        long reimbursed;
    }

    static class SaleItem {
        String barcode;
        String name;
        long price;
        int qty;
        String dci = "";
        long tr;
        int rate;
        long reimb; // line total paid by the insurer
    }

    Db(Context c) {
        super(c, "scanmart.db", null, 4);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (barcode TEXT PRIMARY KEY, name TEXT NOT NULL, "
                + "price INTEGER NOT NULL, stock INTEGER NOT NULL DEFAULT 0, updated INTEGER, "
                + "dci TEXT, dosage TEXT, form TEXT, lab TEXT, batch TEXT, "
                + "expiry INTEGER NOT NULL DEFAULT 0, rx INTEGER NOT NULL DEFAULT 0, "
                + "tr INTEGER NOT NULL DEFAULT 0, reimb INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, "
                + "total INTEGER NOT NULL, fund TEXT NOT NULL DEFAULT '', insured TEXT, patient TEXT, "
                + "reimbursed INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "sale_id INTEGER NOT NULL, barcode TEXT, name TEXT, price INTEGER, qty INTEGER, "
                + "dci TEXT, tr INTEGER NOT NULL DEFAULT 0, rate INTEGER NOT NULL DEFAULT 0, "
                + "reimb INTEGER NOT NULL DEFAULT 0)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL("DELETE FROM products WHERE barcode LIKE '600100000%'");
        if (oldV < 3) {
            String[] text = {"dci", "dosage", "form", "lab", "batch"};
            for (String col : text) db.execSQL("ALTER TABLE products ADD COLUMN " + col + " TEXT");
            db.execSQL("ALTER TABLE products ADD COLUMN expiry INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE products ADD COLUMN rx INTEGER NOT NULL DEFAULT 0");
        }
        if (oldV < 4) {
            db.execSQL("ALTER TABLE products ADD COLUMN tr INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE products ADD COLUMN reimb INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sales ADD COLUMN fund TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE sales ADD COLUMN insured TEXT");
            db.execSQL("ALTER TABLE sales ADD COLUMN patient TEXT");
            db.execSQL("ALTER TABLE sales ADD COLUMN reimbursed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sale_items ADD COLUMN dci TEXT");
            db.execSQL("ALTER TABLE sale_items ADD COLUMN tr INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sale_items ADD COLUMN rate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sale_items ADD COLUMN reimb INTEGER NOT NULL DEFAULT 0");
        }
    }

    // ---- barcode helpers -------------------------------------------------

    /** Canonical form: digits only; 12-digit UPC-A becomes 13-digit EAN-13. */
    static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        boolean digits = s.length() > 0;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                digits = false;
                break;
            }
        }
        if (digits && s.length() == 12) return "0" + s;
        return s;
    }

    static String ean13(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = first12.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return first12 + ((10 - sum % 10) % 10);
    }

    private void seed(SQLiteDatabase db) {
        // Only well-known real barcodes are bundled; the Algerian catalog is imported from
        // Open Food Facts inside the app (Products > Tools).
        Object[][] items = {
                {"5449000000996", "Coca-Cola Original 330ml", 6000, 0},
                {"3017620422003", "Nutella 400g", 65000, 0},
        };
        long now = System.currentTimeMillis();
        for (Object[] it : items) {
            ContentValues v = new ContentValues();
            v.put("barcode", (String) it[0]);
            v.put("name", (String) it[1]);
            v.put("price", ((Integer) it[2]).longValue());
            v.put("stock", (Integer) it[3]);
            v.put("updated", now);
            db.insert("products", null, v);
        }
    }

    // ---- products --------------------------------------------------------

    private static final String COLS = "barcode, name, price, stock, dci, dosage, form, lab, "
            + "batch, expiry, rx, tr, reimb";

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Product read(Cursor c) {
        Product p = new Product(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3));
        p.dci = nz(c.getString(4));
        p.dosage = nz(c.getString(5));
        p.form = nz(c.getString(6));
        p.lab = nz(c.getString(7));
        p.batch = nz(c.getString(8));
        p.expiry = c.getLong(9);
        p.rx = c.getInt(10) != 0;
        p.tr = c.getLong(11);
        p.reimb = c.getInt(12);
        return p;
    }

    private static ContentValues values(Product p) {
        ContentValues v = new ContentValues();
        v.put("barcode", p.barcode);
        v.put("name", p.name);
        v.put("price", p.price);
        v.put("stock", p.stock);
        v.put("updated", System.currentTimeMillis());
        v.put("dci", p.dci);
        v.put("dosage", p.dosage);
        v.put("form", p.form);
        v.put("lab", p.lab);
        v.put("batch", p.batch);
        v.put("expiry", p.expiry);
        v.put("rx", p.rx ? 1 : 0);
        v.put("tr", p.tr);
        v.put("reimb", p.reimb);
        return v;
    }

    Product getProduct(String barcode) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM products WHERE barcode = ?",
                new String[]{normalize(barcode)});
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    List<Product> search(String q) {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM products WHERE name LIKE ? OR barcode LIKE ? "
                        + "OR dci LIKE ? ORDER BY name COLLATE NOCASE",
                new String[]{like, like, like});
        List<Product> out = new ArrayList<Product>();
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    int productCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM products", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    void save(Product p) {
        getWritableDatabase().insertWithOnConflict("products", null, values(p),
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Products with the same active substance, for substitution suggestions. */
    List<Product> sameDci(String dci, String exceptBarcode) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM products WHERE dci = ? COLLATE NOCASE AND barcode != ? "
                        + "ORDER BY price", new String[]{dci, exceptBarcode});
        List<Product> out = new ArrayList<Product>();
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** Re-keys a product (links a scanned pack barcode to a listed medicine). */
    boolean rename(String oldCode, String newCode) {
        if (getProduct(newCode) != null) return false;
        ContentValues v = new ContentValues();
        v.put("barcode", newCode);
        return getWritableDatabase().update("products", v, "barcode = ?", new String[]{oldCode}) == 1;
    }

    /** Inserts products whose barcode is not yet known; returns how many were new. */
    int insertMissing(List<Product> items) {
        SQLiteDatabase db = getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try {
            for (Product p : items) {
                if (db.insertWithOnConflict("products", null, values(p),
                        SQLiteDatabase.CONFLICT_IGNORE) != -1)
                    added++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return added;
    }

    /** Insert or overwrite (used by CSV import). */
    void saveAll(List<Product> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Product p : items) save(p);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Product> all() {
        return search("");
    }

    int count(String where) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM products WHERE " + where, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    void delete(String barcode) {
        getWritableDatabase().delete("products", "barcode = ?", new String[]{barcode});
    }

    // ---- sales -----------------------------------------------------------

    long checkout(List<CartLine> lines, String fund, String insured, String patient,
                  boolean exempt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long total = 0, reimbursed = 0;
            boolean insuredSale = fund != null && fund.length() > 0;
            for (CartLine l : lines) {
                total += l.total();
                if (insuredSale) reimbursed += Reimb.perUnit(l.product, exempt) * l.qty;
            }
            ContentValues s = new ContentValues();
            s.put("ts", System.currentTimeMillis());
            s.put("total", total);
            s.put("fund", insuredSale ? fund : "");
            s.put("insured", insured);
            s.put("patient", patient);
            s.put("reimbursed", reimbursed);
            long saleId = db.insert("sales", null, s);
            for (CartLine l : lines) {
                ContentValues v = new ContentValues();
                v.put("sale_id", saleId);
                v.put("barcode", l.product.barcode);
                v.put("name", l.product.name);
                v.put("price", l.product.price);
                v.put("qty", l.qty);
                v.put("dci", l.product.dci);
                v.put("tr", l.product.tr);
                v.put("rate", insuredSale ? Reimb.rate(l.product, exempt) : 0);
                v.put("reimb", insuredSale ? Reimb.perUnit(l.product, exempt) * l.qty : 0);
                db.insert("sale_items", null, v);
                db.execSQL("UPDATE products SET stock = MAX(stock - ?, 0) WHERE barcode = ?",
                        new Object[]{l.qty, l.product.barcode});
            }
            db.setTransactionSuccessful();
            return saleId;
        } finally {
            db.endTransaction();
        }
    }

    private List<Sale> querySales(String where, String[] args, int limit) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.id, s.ts, s.total, COALESCE(SUM(i.qty), 0), s.fund, s.insured, s.patient, "
                        + "s.reimbursed FROM sales s LEFT JOIN sale_items i ON i.sale_id = s.id "
                        + where + " GROUP BY s.id ORDER BY s.id DESC LIMIT " + limit, args);
        List<Sale> out = new ArrayList<Sale>();
        try {
            while (c.moveToNext()) {
                Sale s = new Sale();
                s.id = c.getLong(0);
                s.ts = c.getLong(1);
                s.total = c.getLong(2);
                s.items = c.getInt(3);
                s.fund = nz(c.getString(4));
                s.insured = nz(c.getString(5));
                s.patient = nz(c.getString(6));
                s.reimbursed = c.getLong(7);
                out.add(s);
            }
        } finally {
            c.close();
        }
        return out;
    }

    List<Sale> sales(int limit) {
        return querySales("", null, limit);
    }

    Sale getSale(long id) {
        List<Sale> l = querySales("WHERE s.id = ?", new String[]{String.valueOf(id)}, 1);
        return l.isEmpty() ? null : l.get(0);
    }

    List<SaleItem> saleItems(long saleId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT barcode, name, price, qty, dci, tr, rate, reimb FROM sale_items "
                        + "WHERE sale_id = ? ORDER BY id", new String[]{String.valueOf(saleId)});
        List<SaleItem> out = new ArrayList<SaleItem>();
        try {
            while (c.moveToNext()) {
                SaleItem i = new SaleItem();
                i.barcode = c.getString(0);
                i.name = c.getString(1);
                i.price = c.getLong(2);
                i.qty = c.getInt(3);
                i.dci = nz(c.getString(4));
                i.tr = c.getLong(5);
                i.rate = c.getInt(6);
                i.reimb = c.getLong(7);
                out.add(i);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Amount the given fund still owes since the timestamp (insured sales only). */
    long receivable(String fund, long since) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(reimbursed), 0) FROM sales WHERE fund = ? AND ts >= ?",
                new String[]{fund, String.valueOf(since)});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /** One CSV line per insured sale item in [since, until): the basis of a tiers-payant invoice. */
    String claimsCsv(long since, long until) {
        StringBuilder sb = new StringBuilder("date,receipt,fund,insured_number,patient,product,"
                + "barcode,dci,qty,unit_price,reference_tariff,rate_percent,insurer_pays,patient_pays\n");
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.ts, s.id, s.fund, s.insured, s.patient, i.name, i.barcode, i.dci, i.qty, "
                        + "i.price, i.tr, i.rate, i.reimb FROM sales s JOIN sale_items i "
                        + "ON i.sale_id = s.id WHERE s.fund != '' AND s.ts >= ? AND s.ts < ? "
                        + "ORDER BY s.fund, s.ts, s.id, i.id",
                new String[]{String.valueOf(since), String.valueOf(until)});
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.US);
        try {
            while (c.moveToNext()) {
                long line = c.getLong(9) * c.getInt(8);
                sb.append(df.format(new java.util.Date(c.getLong(0)))).append(',')
                        .append(c.getLong(1)).append(',').append(Csv.esc(nz(c.getString(2))))
                        .append(',').append(Csv.esc(nz(c.getString(3)))).append(',')
                        .append(Csv.esc(nz(c.getString(4)))).append(',')
                        .append(Csv.esc(nz(c.getString(5)))).append(',')
                        .append(Csv.esc(nz(c.getString(6)))).append(',')
                        .append(Csv.esc(nz(c.getString(7)))).append(',').append(c.getInt(8))
                        .append(',').append(Money.plain(c.getLong(9))).append(',')
                        .append(Money.plain(c.getLong(10))).append(',').append(c.getInt(11))
                        .append(',').append(Money.plain(c.getLong(12))).append(',')
                        .append(Money.plain(line - c.getLong(12))).append('\n');
            }
        } finally {
            c.close();
        }
        return sb.toString();
    }

    /** [count, total] of sales since the given number of days ago (0 = since midnight today). */
    long[] salesSince(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM sales WHERE ts >= ?",
                new String[]{String.valueOf(cal.getTimeInMillis())});
        try {
            return c.moveToFirst() ? new long[]{c.getLong(0), c.getLong(1)} : new long[]{0, 0};
        } finally {
            c.close();
        }
    }

    long todayTotal() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(total), 0) FROM sales WHERE ts >= ?",
                new String[]{String.valueOf(cal.getTimeInMillis())});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }
}
