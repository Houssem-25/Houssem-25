package com.scanmart.pos;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

class Receipt {
    static String build(Context c, Db.Sale sale, List<Db.SaleItem> items) {
        long id = sale.id, ts = sale.ts;
        StringBuilder sb = new StringBuilder();
        sb.append(Dialogs.storeName(c)).append('\n');
        sb.append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(ts))).append('\n');
        sb.append("Receipt #").append(id).append("\n\n");
        long total = 0;
        for (Db.SaleItem i : items) {
            long line = i.price * i.qty;
            total += line;
            sb.append(i.name).append('\n');
            sb.append("  ").append(i.qty).append(" x ").append(Money.fmt(i.price))
                    .append("  =  ").append(Money.fmt(line)).append('\n');
        }
        sb.append("\nTOTAL: ").append(Money.fmt(total)).append('\n');
        if (sale.fund.length() > 0) {
            sb.append("\nInsurance: ").append(sale.fund).append('\n');
            if (sale.insured.length() > 0) sb.append("Insured no.: ").append(sale.insured).append('\n');
            if (sale.patient.length() > 0) sb.append("Patient: ").append(sale.patient).append('\n');
            sb.append(sale.fund).append(" pays: ").append(Money.fmt(sale.reimbursed)).append('\n');
            sb.append("PATIENT PAYS: ").append(Money.fmt(total - sale.reimbursed)).append('\n');
        }
        sb.append("\nThank you!");
        return sb.toString();
    }

    static void show(final Context c, Db.Sale sale, List<Db.SaleItem> items) {
        final String text = build(c, sale, items);
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(13);
        tv.setTextColor(U.INK);
        int p = U.dp(c, 20);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(c);
        sv.addView(tv);
        new AlertDialog.Builder(c)
                .setTitle("Receipt")
                .setView(sv)
                .setPositiveButton("Close", null)
                .setNeutralButton("Share", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, text);
                        c.startActivity(Intent.createChooser(i, "Share receipt"));
                    }
                })
                .show();
    }
}
