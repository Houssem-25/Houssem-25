package com.scanmart.pos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building the UI in code. */
class U {
    static final int PRIMARY = Color.parseColor("#1B7F5C");
    static final int PRIMARY_DARK = Color.parseColor("#12604A");
    static final int BG = Color.parseColor("#F4F6F5");
    static final int INK = Color.parseColor("#1D2723");
    static final int MUTED = Color.parseColor("#6B7772");
    static final int DANGER = Color.parseColor("#C0392B");
    static final int LINE = Color.parseColor("#DDE3E0");

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(bg, 10, c));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        return b;
    }

    static Button primary(Context c, String label) {
        return button(c, label, PRIMARY, Color.WHITE);
    }

    static Button secondary(Context c, String label) {
        return button(c, label, Color.parseColor("#E3EAE7"), INK);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, int l, int t,
                                             int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static EditText input(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextColor(INK);
        return e;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(Color.WHITE, 14, c));
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setElevation(dp(c, 3));
        return l;
    }

    static TextView centered(Context c, String s) {
        TextView t = text(c, s, 15, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 24), dp(c, 48), dp(c, 24), dp(c, 24));
        return t;
    }

    static final int[] AVATARS = {0xFF1B7F5C, 0xFF2E86AB, 0xFFB5651D, 0xFF7B5EA7, 0xFFC0392B,
            0xFF3D7A8C, 0xFF8E6C2A, 0xFF4F6D3A};

    static TextView avatar(Context c, String name) {
        TextView t = new TextView(c);
        String n = name == null ? "" : name.trim();
        t.setText(n.length() == 0 ? "?" : n.substring(0, 1).toUpperCase());
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        int color = AVATARS[Math.abs(n.hashCode()) % AVATARS.length];
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        t.setBackground(d);
        return t;
    }

    static TextView chip(Context c, String label, boolean selected) {
        TextView t = text(c, label, 13, selected ? Color.WHITE : INK, true);
        t.setBackground(rounded(selected ? PRIMARY : Color.parseColor("#E3EAE7"), 18, c));
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
