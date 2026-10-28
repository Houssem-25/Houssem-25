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
    private TextView todayCount, todaySub, weekTotal, weekSub;

    HistoryPanel(MainActivity act) {
        super(act);
        a = act;
        setBackgroundColor(U.BG);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(-1, -1));
        int p = U.dp(a, 12);

        LinearLayout stats = new LinearLayout(a);
        stats.addView(statCard("Today", todayCount = U.text(a, "", 22, U.PRIMARY_DARK, true),
                todaySub = U.text(a, "", 12, U.MUTED, false)),
                U.margins(U.lp(0, -2, 1), a, 0, 0, 6, 0));
        stats.addView(statCard("Last 7 days", weekTotal = U.text(a, "", 22, U.PRIMARY_DARK, true),
                weekSub = U.text(a, "", 12, U.MUTED, false)),
                U.margins(U.lp(0, -2, 1), a, 6, 0, 0, 0));
        root.addView(stats, U.margins(U.lp(-1, -2, 0), a, 12, 12, 12, 0));

        ScrollView sv = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(p, p, p, p);
        sv.addView(list);
        root.addView(sv, U.lp(-1, 0, 1));
    }

    private LinearLayout statCard(String label, TextView big, TextView sub) {
        LinearLayout c = U.card(a);
        c.addView(U.text(a, label, 12, U.MUTED, false));
        c.addView(big);
        c.addView(sub);
        return c;
    }

    @Override
    void onShow() {
        long[] t = a.db.salesSince(0);
        long[] w = a.db.salesSince(6);
        todayCount.setText(Money.fmt(t[1]));
        todaySub.setText(t[0] + " sales");
        weekTotal.setText(Money.fmt(w[1]));
        weekSub.setText(w[0] + " sales");
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
