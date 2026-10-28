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
                            String barcodePrefill, String namePrefill, final Saved cb, final Runnable onCancel) {
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
            if (namePrefill != null) name.setText(namePrefill);
            stock.setText("0");
        }
        box.addView(code);
        box.addView(name);
        box.addView(price);
        box.addView(stock);

        final boolean pharma = Money.pharmacy() || (existing != null && existing.isPharma());
        final EditText dci = U.input(c, "DCI (generic name)", InputType.TYPE_CLASS_TEXT);
        final EditText dosage = U.input(c, "Dosage (e.g. 500 mg)", InputType.TYPE_CLASS_TEXT);
        final EditText form = U.input(c, "Form (e.g. tablet, syrup)", InputType.TYPE_CLASS_TEXT);
        final EditText lab = U.input(c, "Laboratory", InputType.TYPE_CLASS_TEXT);
        final EditText batch = U.input(c, "Batch / lot number", InputType.TYPE_CLASS_TEXT);
        final EditText expiry = U.input(c, "Expiry (MM/YYYY)", InputType.TYPE_CLASS_TEXT);
        final android.widget.CheckBox rx = new android.widget.CheckBox(c);
        rx.setText("Prescription required");
        final EditText tr = U.input(c, "Reference tariff / TR (" + Money.symbol() + ")",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView rateLbl = U.text(c, "Reimbursement (CNAS / CASNOS)", 13, U.MUTED, false);
        final android.widget.RadioGroup rate = new android.widget.RadioGroup(c);
        rate.setOrientation(LinearLayout.HORIZONTAL);
        int[] rates = {0, 80, 100};
        for (int i = 0; i < rates.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(c);
            rb.setText(rates[i] == 0 ? "None" : rates[i] + "%");
            rb.setId(200 + rates[i]);
            rate.addView(rb);
        }
        rate.check(200);
        if (pharma) {
            if (existing != null) {
                dci.setText(existing.dci);
                dosage.setText(existing.dosage);
                form.setText(existing.form);
                lab.setText(existing.lab);
                batch.setText(existing.batch);
                expiry.setText(Product.fmtExpiry(existing.expiry));
                rx.setChecked(existing.rx);
                if (existing.tr > 0) tr.setText(Money.plain(existing.tr));
                int r = existing.reimb;
                rate.check(200 + (r >= 100 ? 100 : r > 0 ? 80 : 0));
            }
            box.addView(dci);
            box.addView(dosage);
            box.addView(form);
            box.addView(lab);
            box.addView(batch);
            box.addView(expiry);
            box.addView(rx);
            box.addView(tr);
            box.addView(rateLbl);
            box.addView(rate);
        }

        final AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(existing == null ? "New product" : "Edit product")
                .setView(scroll(c, box))
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
                if (pharma) {
                    long ex = Product.parseExpiry(expiry.getText().toString());
                    if (ex < 0) {
                        Toast.makeText(c, "Expiry must look like 08/2027", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    p.dci = dci.getText().toString().trim();
                    p.dosage = dosage.getText().toString().trim();
                    p.form = form.getText().toString().trim();
                    p.lab = lab.getText().toString().trim();
                    p.batch = batch.getText().toString().trim();
                    p.expiry = ex;
                    p.rx = rx.isChecked();
                    String trText = tr.getText().toString().trim();
                    long trv = trText.length() == 0 ? 0 : Money.parse(trText);
                    if (trv < 0) {
                        Toast.makeText(c, "Enter a valid reference tariff", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    p.tr = trv;
                    p.reimb = rate.getCheckedRadioButtonId() - 200;
                } else if (existing != null) {
                    p.dci = existing.dci;
                    p.dosage = existing.dosage;
                    p.form = existing.form;
                    p.lab = existing.lab;
                    p.batch = existing.batch;
                    p.expiry = existing.expiry;
                    p.rx = existing.rx;
                    p.tr = existing.tr;
                    p.reimb = existing.reimb;
                }
                db.save(p);
                if (existing != null) existing.copyFrom(p);
                d.dismiss();
                if (cb != null) cb.done(p);
            }
        });
    }

    /** Choose the paying fund for the current sale (or cash) and record the insured's details. */
    static void insurance(final Context c, final Cart cart, final Runnable done) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = U.dp(c, 20);
        box.setPadding(pad, U.dp(c, 8), pad, 0);
        final android.widget.RadioGroup g = new android.widget.RadioGroup(c);
        android.widget.RadioButton cash = new android.widget.RadioButton(c);
        cash.setText("Cash (no insurance)");
        cash.setId(300);
        g.addView(cash);
        for (int i = 0; i < Reimb.FUNDS.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(c);
            rb.setText(Reimb.FUNDS[i] + " (Chifa card)");
            rb.setId(301 + i);
            g.addView(rb);
            if (Reimb.FUNDS[i].equals(cart.fund)) g.check(301 + i);
        }
        if (cart.fund.length() == 0) g.check(300);
        final EditText no = U.input(c, "Insured number (N\u00b0 s\u00e9curit\u00e9 sociale)",
                InputType.TYPE_CLASS_NUMBER);
        no.setText(cart.insured);
        final EditText who = U.input(c, "Patient name (optional)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        who.setText(cart.patient);
        final android.widget.CheckBox ex = new android.widget.CheckBox(c);
        ex.setText("100% coverage (chronic illness / exempt)");
        ex.setChecked(cart.exempt);
        box.addView(g);
        box.addView(no);
        box.addView(who);
        box.addView(ex);
        new AlertDialog.Builder(c).setTitle("Payment: insurance")
                .setView(scroll(c, box))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int id = g.getCheckedRadioButtonId();
                        String fund = id >= 301 ? Reimb.FUNDS[id - 301] : "";
                        cart.setInsurance(fund, no.getText().toString().trim(),
                                who.getText().toString().trim(), fund.length() > 0 && ex.isChecked());
                        if (done != null) done.run();
                    }
                }).show();
    }

    static android.widget.ScrollView scroll(Context c, View v) {
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(v);
        return sv;
    }

    interface Picked {
        void pick(Product p);
    }

    /** Search-and-pick list of existing products (used to link a scanned pack to a medicine). */
    static void pickProduct(final Context c, final Db db, String title, final Picked cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = U.dp(c, 16);
        box.setPadding(pad, U.dp(c, 8), pad, 0);
        final EditText q = U.input(c, "Search medicine name or DCI", InputType.TYPE_CLASS_TEXT);
        final android.widget.ListView lv = new android.widget.ListView(c);
        final java.util.List<Product> shown = new java.util.ArrayList<Product>();
        final android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<String>(c,
                android.R.layout.simple_list_item_1, new java.util.ArrayList<String>());
        lv.setAdapter(ad);
        box.addView(q);
        box.addView(lv, new LinearLayout.LayoutParams(-1, U.dp(c, 320)));
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                shown.clear();
                ad.clear();
                java.util.List<Product> all = db.search(q.getText().toString());
                for (int i = 0; i < all.size() && i < 60; i++) {
                    Product p = all.get(i);
                    shown.add(p);
                    String d = p.detail();
                    ad.add(p.name + (d.length() > 0 ? "\n" + d : ""));
                }
                ad.notifyDataSetChanged();
            }
        };
        q.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(android.text.Editable e) {
                refresh.run();
            }
        });
        final AlertDialog d = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setNegativeButton("Cancel", null).create();
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                d.dismiss();
                cb.pick(shown.get(pos));
            }
        });
        refresh.run();
        d.show();
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
        TextView lbl0 = U.text(c, "Shop type", 13, U.MUTED, false);
        final android.widget.RadioGroup mode = new android.widget.RadioGroup(c);
        android.widget.RadioButton rs = new android.widget.RadioButton(c);
        rs.setText("Supermarket / general store");
        rs.setId(101);
        android.widget.RadioButton rp = new android.widget.RadioButton(c);
        rp.setText("Pharmacy");
        rp.setId(102);
        mode.addView(rs);
        mode.addView(rp);
        mode.check(Money.pharmacy() ? 102 : 101);
        box.addView(lbl0);
        box.addView(mode);
        box.addView(lbl);
        box.addView(cur);
        box.addView(lbl2);
        box.addView(store);
        new AlertDialog.Builder(c)
                .setTitle("Settings")
                .setView(scroll(c, box))
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = cur.getText().toString().trim();
                        Money.setSymbol(c, s.length() == 0 ? "DA" : s);
                        Money.setPharmacy(c, mode.getCheckedRadioButtonId() == 102);
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
