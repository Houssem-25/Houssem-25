package com.scanmart.pos;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

class Dialogs {
    interface Saved {
        void done(Product p);
    }

    /** Create (existing == null) or edit a product. */
    static void editProduct(final Context c, final Db db, final Product existing,
                            String barcodePrefill, final Saved cb, final Runnable onCancel) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = U.dp(c, 20);
        box.setPadding(pad, U.dp(c, 8), pad, 0);

        final EditText code = U.input(c, "Barcode", InputType.TYPE_CLASS_TEXT);
        final EditText name = U.input(c, "Product name",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText price = U.input(c, "Price (" + Money.symbol() + ")",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText stock = U.input(c, "Stock quantity", InputType.TYPE_CLASS_NUMBER);

        if (existing != null) {
            code.setText(existing.barcode);
            code.setEnabled(false);
            name.setText(existing.name);
            price.setText(Money.plain(existing.price));
            stock.setText(String.valueOf(existing.stock));
        } else {
            if (barcodePrefill != null) code.setText(barcodePrefill);
            stock.setText("0");
        }
        box.addView(code);
        box.addView(name);
        box.addView(price);
        box.addView(stock);

        final AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(existing == null ? "New product" : "Edit product")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (onCancel != null) onCancel.run();
            }
        });
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
            }
        });
        d.show();
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.cancel();
            }
        });
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String bc = Db.normalize(code.getText().toString());
                String nm = name.getText().toString().trim();
                long pr = Money.parse(price.getText().toString());
                int st;
                try {
                    String s = stock.getText().toString().trim();
                    st = s.length() == 0 ? 0 : Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    st = -1;
                }
                if (bc.length() == 0) {
                    Toast.makeText(c, "Enter a barcode", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (nm.length() == 0) {
                    Toast.makeText(c, "Enter a product name", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (pr < 0) {
                    Toast.makeText(c, "Enter a valid price, e.g. 2.50", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (st < 0) {
                    Toast.makeText(c, "Enter a valid stock number", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (existing == null && db.getProduct(bc) != null) {
                    Toast.makeText(c, "That barcode already exists", Toast.LENGTH_SHORT).show();
                    return;
                }
                Product p = new Product(bc, nm, pr, st);
                db.save(p);
                d.dismiss();
                if (cb != null) cb.done(p);
            }
        });
    }

    static void settings(final Context c, final Runnable onChanged) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = U.dp(c, 20);
        box.setPadding(pad, U.dp(c, 8), pad, 0);
        TextView lbl = U.text(c, "Currency symbol", 13, U.MUTED, false);
        final EditText cur = U.input(c, "$", InputType.TYPE_CLASS_TEXT);
        cur.setText(Money.symbol());
        TextView lbl2 = U.text(c, "Store name (printed on receipts)", 13, U.MUTED, false);
        final EditText store = U.input(c, "ScanMart", InputType.TYPE_CLASS_TEXT);
        store.setText(storeName(c));
        box.addView(lbl);
        box.addView(cur);
        box.addView(lbl2);
        box.addView(store);
        new AlertDialog.Builder(c)
                .setTitle("Settings")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = cur.getText().toString().trim();
                        Money.setSymbol(c, s.length() == 0 ? "$" : s);
                        String n = store.getText().toString().trim();
                        Money.prefs(c).edit().putString("store", n.length() == 0 ? "ScanMart" : n)
                                .apply();
                        if (onChanged != null) onChanged.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static String storeName(Context c) {
        return Money.prefs(c).getString("store", "ScanMart");
    }
}
