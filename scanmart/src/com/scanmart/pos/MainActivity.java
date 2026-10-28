package com.scanmart.pos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
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
    private final java.util.Set<String> confirmedRx = new java.util.HashSet<String>();
    final Cart cart = new Cart();

    private Panel[] panels;
    private Button[] tabs;
    private final String[] labels = {"\uD83D\uDCF7 Scan", "\uD83D\uDED2 Cart", "\uD83D\uDCE6 Products", "\uD83D\uDCCA Sales"};
    private int current = -1;
    private ScanPanel scan;
    private TextView title;

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
        title = U.text(this, "ScanMart", 20, Color.WHITE, true);
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
                        applyMode();
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
                if (cart.isEmpty()) confirmedRx.clear();
                updateTabs();
            }
        });
        select(0);
        applyMode();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (!Money.modeChosen(MainActivity.this)) chooseMode();
                else afterMode();
            }
        });
    }

    private void applyMode() {
        title.setText(Money.pharmacy() ? "ScanMart Pharmacy" : "ScanMart");
    }

    private void chooseMode() {
        new AlertDialog.Builder(this)
                .setTitle("What kind of shop is this?")
                .setMessage("You can change this later in Settings.")
                .setCancelable(false)
                .setPositiveButton("Supermarket", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Money.setPharmacy(MainActivity.this, false);
                        applyMode();
                        afterMode();
                    }
                })
                .setNegativeButton("Pharmacy", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Money.setPharmacy(MainActivity.this, true);
                        applyMode();
                        panels[current].onShow();
                        afterMode();
                    }
                })
                .show();
    }

    private void afterMode() {
        if (Money.pharmacy()) {
            if (db.count("1 = 1") <= 2 && !Money.prefs(this).getBoolean("pharma_hint", false)) {
                Money.prefs(this).edit().putBoolean("pharma_hint", true).apply();
                new AlertDialog.Builder(this)
                        .setTitle("Load your medicine list")
                        .setMessage("Go to Products > Tools > \"Import medicines from CSV file\" "
                                + "and pick a spreadsheet saved as CSV. Columns are matched by "
                                + "name (English or French): code/barcode, nom/denomination, "
                                + "prix/ppa, stock, DCI, dosage, forme, laboratoire, "
                                + "peremption, lot, ordonnance.\n\nFiles without barcodes "
                                + "(like the national nomenclature) work too: the first time you "
                                + "scan a pack, choose \"Link to medicine\".")
                        .setPositiveButton("OK", null).show();
            } else {
                expiryAlert();
            }
        } else if (!Money.prefs(this).getBoolean("catalog_asked", false)) {
            offerCatalog();
        }
    }

    private void expiryAlert() {
        long limit = System.currentTimeMillis() + Product.SOON_MS;
        final int expired = db.count("expiry > 0 AND expiry < " + System.currentTimeMillis()
                + " AND stock > 0");
        int soon = db.count("expiry > 0 AND expiry < " + limit + " AND stock > 0") - expired;
        if (expired + soon == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Expiry check")
                .setMessage(expired + " expired and " + soon + " expiring within 90 days (in stock).")
                .setNegativeButton("Later", null)
                .setPositiveButton("View", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        select(2);
                        ((ProductsPanel) panels[2]).setFilter(3);
                    }
                }).show();
    }

    // ---- helpers shared by panels ----------------------------------------

    /** Adds to the cart; products without a price are sent to the editor first. */
    void addToCart(final Product p, final Runnable after) {
        if (p.expiryState() == 2) {
            new AlertDialog.Builder(this).setTitle("Expired medicine")
                    .setMessage(p.name + " expired on " + Product.fmtExpiry(p.expiry)
                            + ". It cannot be sold.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (p.rx && !confirmedRx.contains(p.barcode)) {
            new AlertDialog.Builder(this).setTitle("Prescription required")
                    .setMessage(p.name + " is sold on prescription only. Have you checked the "
                            + "prescription?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Prescription checked", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            confirmedRx.add(p.barcode);
                            addToCart(p, after);
                        }
                    }).show();
            return;
        }
        if (p.expiryState() == 1) {
            Toast.makeText(this, "Warning: expires " + Product.fmtExpiry(p.expiry),
                    Toast.LENGTH_LONG).show();
        }
        if (p.price <= 0) {
            Toast.makeText(this, "Set a price first", Toast.LENGTH_SHORT).show();
            Dialogs.editProduct(this, db, p, null, null, new Dialogs.Saved() {
                @Override
                public void done(Product np) {
                    if (np.price > 0) cart.add(np);
                    if (after != null) after.run();
                }
            }, null);
            return;
        }
        cart.add(p);
        if (after != null) after.run();
    }

    private void offerCatalog() {
        Money.prefs(this).edit().putBoolean("catalog_asked", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Load Algerian products?")
                .setMessage("ScanMart can download the most-scanned products sold in Algeria "
                        + "(names and barcodes from the open Open Food Facts database) so that "
                        + "scanning works right away. Prices are not public, so you set them "
                        + "yourself the first time you scan each item.\n\nNeeds internet. "
                        + "You can also do this later from Products > Tools.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        importCatalog();
                    }
                })
                .show();
    }

    void importCatalog() {
        final TextView msg = U.text(this, "Connecting...", 15, U.INK, false);
        msg.setPadding(U.dp(this, 24), U.dp(this, 20), U.dp(this, 24), U.dp(this, 8));
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Downloading catalog")
                .setView(msg)
                .setCancelable(false)
                .show();
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                int added = 0, total = 0;
                try {
                    List<Product> items = Catalog.fetchAlgeria(10, new Catalog.Progress() {
                        @Override
                        public void update(final int pages, final int found) {
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    msg.setText("Page " + pages + " - " + found + " products found...");
                                }
                            });
                        }
                    });
                    total = items.size();
                    added = db.insertMissing(items);
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String err = error;
                final int fAdded = added, fTotal = total;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        dlg.dismiss();
                        String text;
                        if (err != null) {
                            text = "Could not download the catalog (" + err + "). Check your "
                                    + "internet connection and try again from Products > Tools.";
                        } else if (fTotal == 0) {
                            text = "No products were returned. Try again later.";
                        } else {
                            text = fAdded + " new products added (" + (fTotal - fAdded)
                                    + " were already in your database).\n\nOpen Products > "
                                    + "\"No price\" to set prices.";
                        }
                        new AlertDialog.Builder(MainActivity.this).setTitle("Catalog")
                                .setMessage(text).setPositiveButton("OK", null).show();
                        panels[current].onShow();
                    }
                });
            }
        }).start();
    }

    private static final int REQ_EXPORT = 11, REQ_IMPORT = 12;

    void exportCsv() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "scanmart-products.csv");
        startActivityForResult(i, REQ_EXPORT);
    }

    void pickCsv() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    /** UTF-8 when valid, otherwise Windows-1252 (common for spreadsheets saved as CSV). */
    private static String decode(byte[] data) throws Exception {
        try {
            return java.nio.charset.Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(data, "windows-1252");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                try {
                    out.write(Csv.export(db.all()).getBytes("UTF-8"));
                } finally {
                    out.close();
                }
                Toast.makeText(this, "Products exported", Toast.LENGTH_LONG).show();
            } else if (req == REQ_IMPORT) {
                InputStream in = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                } finally {
                    in.close();
                }
                Csv.Result r = Csv.parse(decode(buf.toByteArray()));
                db.saveAll(r.items);
                Toast.makeText(this, r.items.size() + " products imported"
                        + (r.skipped > 0 ? " (" + r.skipped + " rows skipped: no name)" : ""),
                        Toast.LENGTH_LONG).show();
                panels[current].onShow();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
