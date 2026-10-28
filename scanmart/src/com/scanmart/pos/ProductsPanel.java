package com.scanmart.pos;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

class ProductsPanel extends Panel {
    private final MainActivity a;
    private final ListView lv;
    private final EditText search;
    private final TextView empty;
    private final TextView[] chips = new TextView[4];
    private final String[] chipNames = {"All", "No price", "Low stock", "Expiring"};
    private final List<Product> items = new ArrayList<Product>();
    private int filter = 0;

    private final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() {
            return items.size();
        }

        public Object getItem(int i) {
            return items.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View convert, ViewGroup parent) {
            return row(items.get(i));
        }
    };

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
        android.widget.Button add = U.primary(a, "+");
        android.widget.Button tools = U.secondary(a, "Tools");
        top.addView(search, U.lp(0, -2, 1));
        top.addView(add, U.margins(U.lp(U.dp(a, 48), -2, 0), a, 8, 0, 0, 0));
        top.addView(tools, U.margins(U.lp(-2, -2, 0), a, 6, 0, 0, 0));
        root.addView(top);

        LinearLayout chipRow = new LinearLayout(a);
        chipRow.setPadding(p, U.dp(a, 8), p, U.dp(a, 4));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            chips[i] = U.chip(a, chipNames[i], i == 0);
            chips[i].setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    filter = idx;
                    render();
                }
            });
            chipRow.addView(chips[i], U.margins(U.lp(-2, -2, 0), a, 0, 0, 8, 0));
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chipRow);
        root.addView(hs);

        FrameLayoutHolder holder = new FrameLayoutHolder(a);
        lv = new ListView(a);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, U.dp(a, 8));
        lv.setAdapter(adapter);
        empty = U.centered(a, "No products found.");
        empty.setVisibility(GONE);
        holder.addView(lv, new LayoutParams(-1, -1));
        holder.addView(empty, new LayoutParams(-1, -2, Gravity.TOP));
        root.addView(holder, U.lp(-1, 0, 1));

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
                Dialogs.editProduct(a, a.db, null, null, null, new Dialogs.Saved() {
                    @Override
                    public void done(Product p) {
                        render();
                    }
                }, null);
            }
        });
        tools.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                tools();
            }
        });
    }

    /** Plain FrameLayout (kept as a named class so Panel's LayoutParams resolve simply). */
    private static class FrameLayoutHolder extends android.widget.FrameLayout {
        FrameLayoutHolder(android.content.Context c) {
            super(c);
        }
    }

    @Override
    void onShow() {
        render();
    }

    void setFilter(int f) {
        filter = f;
        render();
    }

    void render() {
        List<Product> found = a.db.search(search.getText().toString());
        items.clear();
        for (Product p : found) {
            if (filter == 1 && p.price > 0) continue;
            if (filter == 2 && !(p.stock >= 1 && p.stock <= 5)) continue;
            if (filter == 3 && p.expiryState() == 0) continue;
            items.add(p);
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        int noPrice = a.db.count("price = 0");
        int low = a.db.count("stock BETWEEN 1 AND 5");
        int all = a.db.count("1 = 1");
        int exp = a.db.count("expiry > 0 AND expiry < " + (System.currentTimeMillis() + Product.SOON_MS));
        String[] labels = {"All " + all, "No price " + noPrice, "Low stock " + low, "Expiring " + exp};
        chips[3].setVisibility(Money.pharmacy() ? VISIBLE : GONE);
        if (!Money.pharmacy() && filter == 3) filter = 0;
        for (int i = 0; i < 4; i++) {
            boolean sel = i == filter;
            chips[i].setText(labels[i]);
            chips[i].setTextColor(sel ? Color.WHITE : U.INK);
            chips[i].setBackground(U.rounded(sel ? U.PRIMARY : Color.parseColor("#E3EAE7"), 18, a));
        }
    }

    private View row(final Product p) {
        LinearLayout wrap = new LinearLayout(a);
        wrap.setPadding(U.dp(a, 12), U.dp(a, 4), U.dp(a, 12), U.dp(a, 4));
        LinearLayout card = new LinearLayout(a);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(U.rounded(Color.WHITE, 14, a));
        card.setPadding(U.dp(a, 12), U.dp(a, 10), U.dp(a, 14), U.dp(a, 10));
        card.setElevation(U.dp(a, 2));
        int s = U.dp(a, 42);
        card.addView(U.avatar(a, p.name), new LinearLayout.LayoutParams(s, s));
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(U.dp(a, 12), 0, U.dp(a, 8), 0);
        TextView name = U.text(a, p.name, 15, U.INK, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(name);
        boolean low = p.stock >= 1 && p.stock <= 5;
        String detail = p.detail();
        if (detail.length() > 0) mid.addView(U.text(a, detail, 12, U.PRIMARY_DARK, false));
        mid.addView(U.text(a, p.barcode + "  •  stock " + p.stock, 12,
                low ? U.DANGER : U.MUTED, false));
        if (p.expiry > 0 || p.rx) {
            int st = p.expiryState();
            String tag = (p.rx ? "Rx  " : "") + (p.expiry > 0 ? "exp " + Product.fmtExpiry(p.expiry)
                    + (st == 2 ? " EXPIRED" : st == 1 ? " (soon)" : "") : "");
            mid.addView(U.text(a, tag, 12, st == 2 ? U.DANGER : st == 1
                    ? Color.parseColor("#B5651D") : U.MUTED, st > 0));
        }
        card.addView(mid, U.lp(0, -2, 1));
        if (p.price > 0) card.addView(U.text(a, Money.fmt(p.price), 16, U.PRIMARY_DARK, true));
        else card.addView(U.text(a, "Set price", 13, Color.parseColor("#B5651D"), true));
        card.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                options(p);
            }
        });
        wrap.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private void tools() {
        final boolean ph = Money.pharmacy();
        String[] labels = ph
                ? new String[]{"Import medicines from CSV file", "Export products to CSV file"}
                : new String[]{"Import Algerian product catalog (online)",
                "Import products from CSV file", "Export products to CSV file"};
        new AlertDialog.Builder(a)
                .setTitle("Tools")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (!ph) which++;
                        if (which == 0) a.importCatalog();
                        else if (which == 1) a.pickCsv();
                        else a.exportCsv();
                    }
                })
                .show();
    }

    private void options(final Product p) {
        new AlertDialog.Builder(a)
                .setTitle(p.name)
                .setItems(new String[]{"Add to cart", "Edit name / price / stock", "Delete"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    a.addToCart(p, new Runnable() {
                                        @Override
                                        public void run() {
                                            render();
                                        }
                                    });
                                } else if (which == 1) {
                                    Dialogs.editProduct(a, a.db, p, null, null, new Dialogs.Saved() {
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
