package com.scanmart.pos;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    Db db;
    final Cart cart = new Cart();

    private Panel[] panels;
    private Button[] tabs;
    private final String[] labels = {"Scan", "Cart", "Products", "Sales"};
    private int current = -1;
    private ScanPanel scan;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Money.load(this);
        db = new Db(this);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setStatusBarColor(U.PRIMARY_DARK);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(U.BG);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(U.PRIMARY);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(U.dp(this, 16), U.dp(this, 8), U.dp(this, 8), U.dp(this, 8));
        TextView title = U.text(this, "ScanMart", 20, Color.WHITE, true);
        Button gear = U.button(this, "Settings", Color.TRANSPARENT, Color.WHITE);
        header.addView(title, U.lp(0, -2, 1));
        header.addView(gear);
        root.addView(header, U.lp(-1, -2, 0));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.settings(MainActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        panels[current].onShow();
                    }
                });
            }
        });

        scan = new ScanPanel(this);
        panels = new Panel[]{scan, new CartPanel(this), new ProductsPanel(this),
                new HistoryPanel(this)};
        FrameLayout content = new FrameLayout(this);
        for (Panel p : panels) {
            p.setVisibility(View.GONE);
            content.addView(p, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(content, U.lp(-1, 0, 1));

        // bottom nav
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(U.dp(this, 8));
        nav.setPadding(U.dp(this, 6), U.dp(this, 6), U.dp(this, 6), U.dp(this, 6));
        tabs = new Button[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button t = U.button(this, labels[i], Color.TRANSPARENT, U.MUTED);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });
            tabs[i] = t;
            nav.addView(t, U.margins(U.lp(0, -2, 1), this, 2, 0, 2, 0));
        }
        root.addView(nav, U.lp(-1, -2, 0));
        setContentView(root);

        cart.listen(new Runnable() {
            @Override
            public void run() {
                updateTabs();
            }
        });
        select(0);
    }

    private void select(int i) {
        if (current == i) return;
        if (current >= 0) {
            panels[current].onHide();
            panels[current].setVisibility(View.GONE);
        }
        current = i;
        panels[i].setVisibility(View.VISIBLE);
        panels[i].onShow();
        updateTabs();
    }

    private void updateTabs() {
        for (int i = 0; i < tabs.length; i++) {
            String label = labels[i];
            if (i == 1 && cart.itemCount() > 0) label += " (" + cart.itemCount() + ")";
            tabs[i].setText(label);
            boolean sel = i == current;
            tabs[i].setTextColor(sel ? Color.WHITE : U.MUTED);
            tabs[i].setBackground(U.rounded(sel ? U.PRIMARY : Color.TRANSPARENT, 10, this));
        }
    }

    void requestCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        scan.onPermissionResult(ok);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current >= 0) panels[current].onShow();
    }

    @Override
    protected void onPause() {
        if (current >= 0) panels[current].onHide();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scan.destroy();
        db.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (current != 0) select(0);
        else super.onBackPressed();
    }
}
