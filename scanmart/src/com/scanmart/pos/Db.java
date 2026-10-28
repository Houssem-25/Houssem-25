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
    }

    static class SaleItem {
        String barcode;
        String name;
        long price;
        int qty;
    }

    Db(Context c) {
        super(c, "scanmart.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (barcode TEXT PRIMARY KEY, name TEXT NOT NULL, "
                + "price INTEGER NOT NULL, stock INTEGER NOT NULL DEFAULT 0, updated INTEGER)");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, "
                + "total INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "sale_id INTEGER NOT NULL, barcode TEXT, name TEXT, price INTEGER, qty INTEGER)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
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
        Object[][] items = {
                {"5449000000996", "Coca-Cola Original 330ml", 99, 120},
                {"3017620422003", "Nutella Hazelnut Spread 400g", 449, 40},
                {ean13("600100000001"), "Whole Milk 1L", 129, 80},
                {ean13("600100000002"), "White Bread Loaf", 189, 50},
                {ean13("600100000003"), "Eggs (12 pack)", 349, 60},
                {ean13("600100000004"), "Basmati Rice 1kg", 275, 70},
                {ean13("600100000005"), "Spaghetti 500g", 119, 90},
                {ean13("600100000006"), "Extra Virgin Olive Oil 500ml", 799, 30},
                {ean13("600100000007"), "Red Apples 1kg", 299, 45},
                {ean13("600100000008"), "Bananas 1kg", 189, 55},
                {ean13("600100000009"), "Mineral Water 1.5L", 79, 200},
                {ean13("600100000010"), "Orange Juice 1L", 249, 65},
                {ean13("600100000011"), "Milk Chocolate Bar 100g", 159, 100},
                {ean13("600100000012"), "Ground Coffee 250g", 599, 35},
                {ean13("600100000013"), "White Sugar 1kg", 139, 75},
                {ean13("600100000014"), "Table Salt 750g", 69, 85},
                {ean13("600100000015"), "Cheddar Cheese 200g", 379, 40},
                {ean13("600100000016"), "Chicken Breast 500g", 649, 25},
                {ean13("600100000017"), "Tomato Sauce 400g", 129, 60},
                {ean13("600100000018"), "Potato Chips 150g", 229, 90},
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

    private static Product read(Cursor c) {
        return new Product(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3));
    }

    Product getProduct(String barcode) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT barcode, name, price, stock FROM products WHERE barcode = ?",
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
                "SELECT barcode, name, price, stock FROM products "
                        + "WHERE name LIKE ? OR barcode LIKE ? ORDER BY name COLLATE NOCASE",
                new String[]{like, like});
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
        ContentValues v = new ContentValues();
        v.put("barcode", p.barcode);
        v.put("name", p.name);
        v.put("price", p.price);
        v.put("stock", p.stock);
        v.put("updated", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("products", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    void delete(String barcode) {
        getWritableDatabase().delete("products", "barcode = ?", new String[]{barcode});
    }

    // ---- sales -----------------------------------------------------------

    long checkout(List<CartLine> lines) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long total = 0;
            for (CartLine l : lines) total += l.total();
            ContentValues s = new ContentValues();
            s.put("ts", System.currentTimeMillis());
            s.put("total", total);
            long saleId = db.insert("sales", null, s);
            for (CartLine l : lines) {
                ContentValues v = new ContentValues();
                v.put("sale_id", saleId);
                v.put("barcode", l.product.barcode);
                v.put("name", l.product.name);
                v.put("price", l.product.price);
                v.put("qty", l.qty);
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

    List<Sale> sales(int limit) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.id, s.ts, s.total, COALESCE(SUM(i.qty), 0) FROM sales s "
                        + "LEFT JOIN sale_items i ON i.sale_id = s.id "
                        + "GROUP BY s.id ORDER BY s.id DESC LIMIT " + limit, null);
        List<Sale> out = new ArrayList<Sale>();
        try {
            while (c.moveToNext()) {
                Sale s = new Sale();
                s.id = c.getLong(0);
                s.ts = c.getLong(1);
                s.total = c.getLong(2);
                s.items = c.getInt(3);
                out.add(s);
            }
        } finally {
            c.close();
        }
        return out;
    }

    List<SaleItem> saleItems(long saleId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT barcode, name, price, qty FROM sale_items WHERE sale_id = ? ORDER BY id",
                new String[]{String.valueOf(saleId)});
        List<SaleItem> out = new ArrayList<SaleItem>();
        try {
            while (c.moveToNext()) {
                SaleItem i = new SaleItem();
                i.barcode = c.getString(0);
                i.name = c.getString(1);
                i.price = c.getLong(2);
                i.qty = c.getInt(3);
                out.add(i);
            }
        } finally {
            c.close();
        }
        return out;
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
