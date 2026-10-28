package com.scanmart.pos;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

class ProductsPanel extends Panel {
    private final MainActivity a;
    private final LinearLayout list;
    private final EditText search;
    private final TextView count;

    ProductsPanel(MainActivity act) {
        super(act);
        a = act;
        setBackgroundColor(U.BG);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int p = U.dp(a, 12);
        top.setPadding(p, p, p, 0);
        search = U.input(a, "Search name or barcode", InputType.TYPE_CLASS_TEXT);
        android.widget.Button add = U.primary(a, "+ Add");
        top.addView(search, U.lp(0, -2, 1));
        top.addView(add, U.margins(U.lp(-2, -2, 0), a, 8, 0, 0, 0));
        root.addView(top);
        count = U.text(a, "", 12, U.MUTED, false);
        count.setPadding(p + 4, U.dp(a, 4), p, 0);
        root.addView(count);

        ScrollView sv = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(p, p, p, p);
        sv.addView(list);
        root.addView(sv, U.lp(-1, 0, 1));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            public void afterTextChanged(Editable e) {
                render();
            }
        });
        add.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.editProduct(a, a.db, null, null, new Dialogs.Saved() {
                    @Override
                    public void done(Product p) {
                        render();
                    }
                }, null);
            }
        });
    }

    @Override
    void onShow() {
        render();
    }

    private void render() {
        list.removeAllViews();
        List<Product> items = a.db.search(search.getText().toString());
        count.setText(items.size() + " products");
        if (items.isEmpty()) list.addView(U.centered(a, "No products found."));
        for (final Product p : items) {
            LinearLayout card = U.card(a);
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(a);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(U.text(a, p.name, 16, U.INK, true));
            left.addView(U.text(a, p.barcode + "  •  stock " + p.stock, 12,
                    p.stock <= 5 ? U.DANGER : U.MUTED, false));
            row.addView(left, U.lp(0, -2, 1));
            row.addView(U.text(a, Money.fmt(p.price), 18, U.PRIMARY_DARK, true));
            card.addView(row);
            card.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    options(p);
                }
            });
            list.addView(card, U.margins(U.lp(-1, -2, 0), a, 0, 0, 0, 10));
        }
    }

    private void options(final Product p) {
        new AlertDialog.Builder(a)
                .setTitle(p.name)
                .setItems(new String[]{"Add to cart", "Edit", "Delete"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    a.cart.add(p);
                                    android.widget.Toast.makeText(a, "Added to cart",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                } else if (which == 1) {
                                    Dialogs.editProduct(a, a.db, p, null, new Dialogs.Saved() {
                                        @Override
                                        public void done(Product np) {
                                            render();
                                        }
                                    }, null);
                                } else {
                                    confirmDelete(p);
                                }
                            }
                        })
                .show();
    }

    private void confirmDelete(final Product p) {
        new AlertDialog.Builder(a)
                .setTitle("Delete product?")
                .setMessage(p.name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.db.delete(p.barcode);
                        a.cart.setQty(p.barcode, 0);
                        render();
                    }
                })
                .show();
    }
}
