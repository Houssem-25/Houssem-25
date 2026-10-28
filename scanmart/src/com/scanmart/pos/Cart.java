package com.scanmart.pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Cart {
    private final Map<String, CartLine> lines = new LinkedHashMap<String, CartLine>();
    private final List<Runnable> listeners = new ArrayList<Runnable>();

    void listen(Runnable r) {
        listeners.add(r);
    }

    private void changed() {
        for (Runnable r : new ArrayList<Runnable>(listeners)) r.run();
    }

    void add(Product p) {
        CartLine l = lines.get(p.barcode);
        if (l == null) lines.put(p.barcode, new CartLine(p, 1));
        else l.qty++;
        changed();
    }

    void setQty(String barcode, int qty) {
        if (qty <= 0) lines.remove(barcode);
        else {
            CartLine l = lines.get(barcode);
            if (l != null) l.qty = qty;
        }
        changed();
    }

    void clear() {
        lines.clear();
        changed();
    }

    /** Re-reads prices from the database so edits made in Products are respected. */
    void refresh(Db db) {
        List<String> gone = new ArrayList<String>();
        for (CartLine l : lines.values()) {
            Product fresh = db.getProduct(l.product.barcode);
            if (fresh == null) gone.add(l.product.barcode);
            else {
                l.product.copyFrom(fresh);
            }
        }
        for (String g : gone) lines.remove(g);
    }

    List<CartLine> lines() {
        return new ArrayList<CartLine>(lines.values());
    }

    int itemCount() {
        int n = 0;
        for (CartLine l : lines.values()) n += l.qty;
        return n;
    }

    long total() {
        long t = 0;
        for (CartLine l : lines.values()) t += l.total();
        return t;
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }
}
