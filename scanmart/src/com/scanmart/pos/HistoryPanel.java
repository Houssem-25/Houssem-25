package com.scanmart.pos;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

class HistoryPanel extends Panel {
    private final MainActivity a;
    private final LinearLayout list;
    private final TextView today;

    HistoryPanel(MainActivity act) {
        super(act);
        a = act;
        setBackgroundColor(U.BG);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(-1, -1));
        int p = U.dp(a, 12);

        LinearLayout head = U.card(a);
        head.addView(U.text(a, "Sales today", 13, U.MUTED, false));
        today = U.text(a, "", 28, U.PRIMARY_DARK, true);
        head.addView(today);
        root.addView(head, U.margins(U.lp(-1, -2, 0), a, 12, 12, 12, 0));

        ScrollView sv = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(p, p, p, p);
        sv.addView(list);
        root.addView(sv, U.lp(-1, 0, 1));
    }

    @Override
    void onShow() {
        today.setText(Money.fmt(a.db.todayTotal()));
        list.removeAllViews();
        List<Db.Sale> sales = a.db.sales(200);
        if (sales.isEmpty()) list.addView(U.centered(a, "No sales yet."));
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (final Db.Sale s : sales) {
            LinearLayout card = U.card(a);
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(a);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(U.text(a, "Receipt #" + s.id, 16, U.INK, true));
            left.addView(U.text(a, df.format(new Date(s.ts)) + "  •  " + s.items + " items", 12,
                    U.MUTED, false));
            row.addView(left, U.lp(0, -2, 1));
            row.addView(U.text(a, Money.fmt(s.total), 18, U.PRIMARY_DARK, true));
            card.addView(row);
            card.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Receipt.show(a, s.id, s.ts, a.db.saleItems(s.id));
                }
            });
            list.addView(card, U.margins(U.lp(-1, -2, 0), a, 0, 0, 0, 10));
        }
    }
}
