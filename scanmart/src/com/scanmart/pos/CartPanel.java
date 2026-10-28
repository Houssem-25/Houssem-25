package com.scanmart.pos;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

class CartPanel extends Panel {
    private final MainActivity a;
    private final LinearLayout list;
    private final TextView totalView;
    private final TextView countView;
    private final android.widget.Button checkoutBtn;
    private final android.widget.Button clearBtn;

    CartPanel(MainActivity act) {
        super(act);
        a = act;
        setBackgroundColor(U.BG);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        int p = U.dp(a, 12);
        list.setPadding(p, p, p, p);
        sv.addView(list);
        root.addView(sv, U.lp(-1, 0, 1));

        LinearLayout foot = new LinearLayout(a);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(Color.WHITE);
        foot.setElevation(U.dp(a, 6));
        foot.setPadding(p + 4, p, p + 4, p);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        countView = U.text(a, "", 14, U.MUTED, false);
        totalView = U.text(a, "", 26, U.INK, true);
        row.addView(countView, U.lp(0, -2, 1));
        row.addView(totalView);
        foot.addView(row);
        LinearLayout btns = new LinearLayout(a);
        clearBtn = U.secondary(a, "Clear");
        checkoutBtn = U.primary(a, "Checkout");
        btns.addView(clearBtn, U.margins(U.lp(0, -2, 1), a, 0, 10, 6, 0));
        btns.addView(checkoutBtn, U.margins(U.lp(0, -2, 2), a, 6, 10, 0, 0));
        foot.addView(btns);
        root.addView(foot, U.lp(-1, -2, 0));

        clearBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                a.cart.clear();
            }
        });
        checkoutBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmCheckout();
            }
        });
        a.cart.listen(new Runnable() {
            @Override
            public void run() {
                render();
            }
        });
        render();
    }

    @Override
    void onShow() {
        a.cart.refresh(a.db);
        render();
    }

    private void confirmCheckout() {
        if (a.cart.isEmpty()) return;
        new AlertDialog.Builder(a)
                .setTitle("Complete sale?")
                .setMessage(a.cart.itemCount() + " items, total " + Money.fmt(a.cart.total()))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Charge", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        List<CartLine> lines = a.cart.lines();
                        long id = a.db.checkout(lines);
                        a.cart.clear();
                        Toast.makeText(a, "Sale saved", Toast.LENGTH_SHORT).show();
                        Receipt.show(a, id, System.currentTimeMillis(), a.db.saleItems(id));
                    }
                })
                .show();
    }

    private void render() {
        list.removeAllViews();
        List<CartLine> lines = a.cart.lines();
        if (lines.isEmpty()) {
            list.addView(U.centered(a, "Cart is empty.\nScan a product and tap \"Add to cart\"."));
        }
        for (final CartLine l : lines) {
            LinearLayout card = U.card(a);
            card.addView(U.text(a, l.product.name, 16, U.INK, true));
            String detail = l.product.detail();
            card.addView(U.text(a, Money.fmt(l.product.price) + " each"
                    + (detail.length() > 0 ? "  \u2022  " + detail : "")
                    + (l.product.rx ? "  \u2022  Rx" : ""), 13, U.MUTED, false));
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.Button minus = U.secondary(a, "-");
            android.widget.Button plus = U.secondary(a, "+");
            TextView qty = U.text(a, String.valueOf(l.qty), 18, U.INK, true);
            qty.setGravity(Gravity.CENTER);
            int w = U.dp(a, 44);
            row.addView(minus, new LinearLayout.LayoutParams(w, U.dp(a, 40)));
            row.addView(qty, new LinearLayout.LayoutParams(U.dp(a, 44), -2));
            row.addView(plus, new LinearLayout.LayoutParams(w, U.dp(a, 40)));
            View spacer = new View(a);
            row.addView(spacer, U.lp(0, 1, 1));
            row.addView(U.text(a, Money.fmt(l.total()), 18, U.PRIMARY_DARK, true));
            card.addView(row, U.margins(U.lp(-1, -2, 0), a, 0, 8, 0, 0));
            minus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.cart.setQty(l.product.barcode, l.qty - 1);
                }
            });
            plus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.cart.setQty(l.product.barcode, l.qty + 1);
                }
            });
            list.addView(card, U.margins(U.lp(-1, -2, 0), a, 0, 0, 0, 10));
        }
        countView.setText(a.cart.itemCount() + " items");
        totalView.setText(Money.fmt(a.cart.total()));
        checkoutBtn.setEnabled(!a.cart.isEmpty());
        clearBtn.setEnabled(!a.cart.isEmpty());
        checkoutBtn.setAlpha(a.cart.isEmpty() ? 0.45f : 1f);
        clearBtn.setAlpha(a.cart.isEmpty() ? 0.45f : 1f);
    }
}
