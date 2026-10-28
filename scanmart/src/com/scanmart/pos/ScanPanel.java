package com.scanmart.pos;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;


import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ScanPanel extends Panel implements TextureView.SurfaceTextureListener,
        Camera.PreviewCallback {
    private final MainActivity a;
    private final TextureView texture;
    private final LinearLayout permBox;
    private final TextView permText;
    private final TextView hint;
    private final LinearLayout card;
    private final TextView cardName;
    private final TextView cardPrice;
    private final TextView cardInfo;
    private final android.widget.Button addBtn;
    private final android.widget.Button torchBtn;

    private Camera camera;
    private Camera.Size size;
    private SurfaceTexture surface;
    private boolean active;
    private boolean paused;
    private boolean torchOn;
    private volatile boolean busy;
    private Product shown;
    private String lastCode;
    private long lastTime;
    private boolean autoAdd;
    private boolean asked;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Decoder decoder = new Decoder();
    private ToneGenerator tone;

    ScanPanel(MainActivity act) {
        super(act);
        a = act;
        setBackgroundColor(Color.BLACK);
        autoAdd = Money.prefs(a).getBoolean("autoadd", false);

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Exception e) {
            tone = null;
        }

        texture = new TextureView(a);
        texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(-1, -1));
        addView(new ScanOverlay(a), new LayoutParams(-1, -1));

        // top controls
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(U.dp(a, 12), U.dp(a, 10), U.dp(a, 12), 0);
        CheckBox auto = new CheckBox(a);
        auto.setText("Auto-add to cart");
        auto.setTextColor(Color.WHITE);
        auto.setChecked(autoAdd);
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                autoAdd = checked;
                Money.prefs(a).edit().putBoolean("autoadd", checked).apply();
            }
        });
        torchBtn = U.button(a, "Torch", Color.argb(140, 0, 0, 0), Color.WHITE);
        top.addView(auto, U.lp(0, -2, 1));
        top.addView(torchBtn);
        addView(top, new LayoutParams(-1, -2, Gravity.TOP));
        torchBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTorch();
            }
        });

        // bottom area
        LinearLayout bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(U.dp(a, 12), 0, U.dp(a, 12), U.dp(a, 12));
        hint = U.text(a, "Point the camera at a barcode", 15, Color.WHITE, true);
        hint.setGravity(Gravity.CENTER);
        bottom.addView(hint, U.margins(U.lp(-1, -2, 0), a, 0, 0, 0, 10));

        card = U.card(a);
        cardName = U.text(a, "", 18, U.INK, true);
        cardPrice = U.text(a, "", 34, U.PRIMARY_DARK, true);
        cardInfo = U.text(a, "", 13, U.MUTED, false);
        card.addView(cardName);
        card.addView(cardPrice);
        card.addView(cardInfo);
        LinearLayout row = new LinearLayout(a);
        addBtn = U.primary(a, "Add to cart");
        android.widget.Button close = U.secondary(a, "Dismiss");
        row.addView(close, U.margins(U.lp(0, -2, 1), a, 0, 10, 6, 0));
        row.addView(addBtn, U.margins(U.lp(0, -2, 2), a, 6, 10, 0, 0));
        card.addView(row);
        card.setVisibility(GONE);
        bottom.addView(card, U.margins(U.lp(-1, -2, 0), a, 0, 0, 0, 10));

        android.widget.Button manual = U.button(a, "Type barcode manually",
                Color.argb(170, 0, 0, 0), Color.WHITE);
        bottom.addView(manual, U.lp(-1, -2, 0));
        addView(bottom, new LayoutParams(-1, -2, Gravity.BOTTOM));

        addBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (shown != null) {
                    a.addToCart(shown, new Runnable() {
                        @Override
                        public void run() {
                            updateCard();
                        }
                    });
                }
            }
        });
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCard();
            }
        });
        manual.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                manualEntry();
            }
        });

        // permission message
        permBox = new LinearLayout(a);
        permBox.setOrientation(LinearLayout.VERTICAL);
        permBox.setGravity(Gravity.CENTER);
        permBox.setBackgroundColor(Color.parseColor("#22302B"));
        permBox.setPadding(U.dp(a, 32), 0, U.dp(a, 32), 0);
        permText = U.text(a, "Camera permission is needed to scan barcodes.", 16, Color.WHITE,
                false);
        permText.setGravity(Gravity.CENTER);
        android.widget.Button grant = U.primary(a, "Allow camera");
        permBox.addView(permText);
        permBox.addView(grant, U.margins(U.lp(-2, -2, 0), a, 0, 16, 0, 0));
        permBox.setVisibility(GONE);
        addView(permBox, new LayoutParams(-1, -1));
        grant.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (asked && !a.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", a.getPackageName(), null));
                    a.startActivity(i);
                } else {
                    asked = true;
                    a.requestCameraPermission();
                }
            }
        });
    }

    // ---- lifecycle -------------------------------------------------------

    @Override
    void onShow() {
        active = true;
        tryStart();
    }

    @Override
    void onHide() {
        active = false;
        stopCamera();
    }

    void destroy() {
        stopCamera();
        exec.shutdownNow();
        if (tone != null) tone.release();
    }

    void tryStart() {
        if (!active) return;
        if (a.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permBox.setVisibility(VISIBLE);
            if (!asked) {
                asked = true;
                a.requestCameraPermission();
            }
            return;
        }
        permBox.setVisibility(GONE);
        if (surface != null && camera == null) openCamera();
    }

    void onPermissionResult(boolean granted) {
        if (granted) {
            tryStart();
        } else {
            permText.setText("Camera access was denied. Enable it for ScanMart in system "
                    + "settings, or type barcodes manually.");
            permBox.setVisibility(VISIBLE);
        }
    }

    // ---- surface ---------------------------------------------------------

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = st;
        tryStart();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        applyTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        surface = null;
        stopCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    // ---- camera ----------------------------------------------------------

    private void openCamera() {
        try {
            int id = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            int n = Camera.getNumberOfCameras();
            for (int i = 0; i < n; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    id = i;
                    break;
                }
            }
            if (id < 0 && n > 0) {
                id = 0;
                Camera.getCameraInfo(0, info);
            }
            if (id < 0) {
                hint.setText("No camera found - type the barcode instead");
                return;
            }
            Camera cam = Camera.open(id);
            Camera.Parameters p = cam.getParameters();
            size = pickSize(p.getSupportedPreviewSizes());
            p.setPreviewSize(size.width, size.height);
            p.setPreviewFormat(ImageFormat.NV21);
            List<String> modes = p.getSupportedFocusModes();
            if (modes != null) {
                if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }
            cam.setParameters(p);
            int rot = a.getWindowManager().getDefaultDisplay().getRotation() * 90;
            int orient = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                    ? (360 - (info.orientation + rot) % 360) % 360
                    : (info.orientation - rot + 360) % 360;
            cam.setDisplayOrientation(orient);
            orientation = orient;
            cam.setPreviewTexture(surface);
            int buf = size.width * size.height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            cam.addCallbackBuffer(new byte[buf]);
            cam.addCallbackBuffer(new byte[buf]);
            cam.setPreviewCallbackWithBuffer(this);
            camera = cam;
            busy = false;
            cam.startPreview();
            applyTransform();
            torchOn = false;
            hint.setText("Point the camera at a barcode");
        } catch (Exception e) {
            stopCamera();
            hint.setText("Could not open the camera - type the barcode instead");
        }
    }

    private int orientation = 90;

    private Camera.Size pickSize(List<Camera.Size> sizes) {
        int vw = getWidth(), vh = getHeight();
        double target = (vw > 0 && vh > 0) ? (double) Math.max(vw, vh) / Math.min(vw, vh) : 16.0 / 9;
        Camera.Size best = null;
        double bestDiff = 0;
        for (Camera.Size s : sizes) {
            if (s.width > 1280 || s.height > 960) continue;
            double diff = Math.abs((double) s.width / s.height - target);
            if (best == null || diff < bestDiff - 0.05
                    || (Math.abs(diff - bestDiff) <= 0.05
                    && s.width * s.height > best.width * best.height)) {
                best = s;
                bestDiff = diff;
            }
        }
        if (best == null) {
            for (Camera.Size s : sizes) {
                if (best == null || s.width * s.height < best.width * best.height) best = s;
            }
        }
        return best;
    }

    /** Scales the preview so it fills the view without distortion (centre crop). */
    private void applyTransform() {
        if (size == null) return;
        int vw = texture.getWidth(), vh = texture.getHeight();
        if (vw == 0 || vh == 0) return;
        boolean swap = orientation == 90 || orientation == 270;
        float pw = swap ? size.height : size.width;
        float ph = swap ? size.width : size.height;
        float pa = pw / ph;
        float va = (float) vw / vh;
        Matrix m = new Matrix();
        if (pa > va) m.setScale(pa / va, 1f, vw / 2f, vh / 2f);
        else m.setScale(1f, va / pa, vw / 2f, vh / 2f);
        texture.setTransform(m);
    }

    private void stopCamera() {
        Camera cam = camera;
        camera = null;
        busy = false;
        if (cam != null) {
            try {
                cam.setPreviewCallbackWithBuffer(null);
                cam.stopPreview();
            } catch (Exception ignored) {
            }
            try {
                cam.release();
            } catch (Exception ignored) {
            }
        }
        torchOn = false;
    }

    private void toggleTorch() {
        Camera cam = camera;
        if (cam == null) return;
        try {
            Camera.Parameters p = cam.getParameters();
            List<String> modes = p.getSupportedFlashModes();
            if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                android.widget.Toast.makeText(a, "No torch on this device",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            torchOn = !torchOn;
            p.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH
                    : Camera.Parameters.FLASH_MODE_OFF);
            cam.setParameters(p);
        } catch (Exception ignored) {
        }
    }

    // ---- decoding --------------------------------------------------------

    @Override
    public void onPreviewFrame(final byte[] data, final Camera cam) {
        if (busy || paused || cam != camera || size == null) {
            try {
                cam.addCallbackBuffer(data);
            } catch (Exception ignored) {
            }
            return;
        }
        busy = true;
        final int w = size.width, h = size.height;
        try {
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    String text = decoder.decode(data, w, h);
                    final String found = text;
                    post(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            if (cam == camera) {
                                try {
                                    cam.addCallbackBuffer(data);
                                } catch (Exception ignored) {
                                }
                            }
                            if (found != null && found.trim().length() > 0 && active) onCode(found);
                        }
                    });
                }
            });
        } catch (Exception e) {
            busy = false;
        }
    }

    // ---- result handling -------------------------------------------------

    private void onCode(String raw) {
        String code = Db.normalize(raw);
        long now = System.currentTimeMillis();
        if (code.equals(lastCode) && now - lastTime < 2500 && card.getVisibility() == VISIBLE) {
            lastTime = now;
            return;
        }
        lastCode = code;
        lastTime = now;
        feedback();
        lookup(code);
    }

    private void lookup(final String code) {
        Product p = a.db.getProduct(code);
        if (p != null) {
            showCard(p);
            if (autoAdd) {
                a.addToCart(p, new Runnable() {
                    @Override
                    public void run() {
                        updateCard();
                    }
                });
            }
            return;
        }
        paused = true;
        hint.setVisibility(VISIBLE);
        hint.setText("Looking up " + code + " online...");
        final String c = code;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String found = Catalog.lookupName(c);
                post(new Runnable() {
                    @Override
                    public void run() {
                        hint.setText("Point the camera at a barcode");
                        if (card.getVisibility() == VISIBLE) hint.setVisibility(GONE);
                        askAdd(c, found);
                    }
                });
            }
        }).start();
    }

    private void askAdd(final String code, final String foundName) {
        new AlertDialog.Builder(a)
                .setTitle("New product")
                .setMessage((foundName != null ? foundName + "\n\n" : "")
                        + "Barcode " + code + " is not in your database yet."
                        + (foundName != null ? " Name found online - add it and set a price?"
                        : " Add it now?"))
                .setNegativeButton("Not now", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        paused = false;
                    }
                })
                .setPositiveButton("Add product", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Dialogs.editProduct(a, a.db, null, code, foundName, new Dialogs.Saved() {
                            @Override
                            public void done(Product np) {
                                paused = false;
                                showCard(np);
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                paused = false;
                            }
                        });
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        paused = false;
                    }
                })
                .show();
    }

    private void showCard(Product p) {
        shown = p;
        card.setVisibility(VISIBLE);
        hint.setVisibility(GONE);
        card.setAlpha(0f);
        card.setTranslationY(U.dp(a, 36));
        card.animate().alpha(1f).translationY(0).setDuration(200).start();
        updateCard();
    }

    private void updateCard() {
        Product p = shown;
        if (p == null) return;
        cardName.setText(p.name);
        cardPrice.setText(p.price > 0 ? Money.fmt(p.price) : "Price not set");
        cardPrice.setTextColor(p.price > 0 ? U.PRIMARY_DARK : Color.parseColor("#B5651D"));
        int inCart = 0;
        for (CartLine l : a.cart.lines()) {
            if (l.product.barcode.equals(p.barcode)) inCart = l.qty;
        }
        cardInfo.setText(p.barcode + "  •  " + (p.stock > 0 ? p.stock + " in stock" : "out of stock")
                + (inCart > 0 ? "  •  " + inCart + " in cart" : ""));
        addBtn.setText(p.price <= 0 ? "Set price" : inCart > 0 ? "Add another" : "Add to cart");
    }

    private void hideCard() {
        shown = null;
        lastCode = null;
        card.setVisibility(GONE);
        hint.setVisibility(VISIBLE);
    }

    private void manualEntry() {
        final EditText e = U.input(a, "Barcode number", InputType.TYPE_CLASS_TEXT);
        LinearLayout box = new LinearLayout(a);
        int pad = U.dp(a, 20);
        box.setPadding(pad, U.dp(a, 8), pad, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        paused = true;
        new AlertDialog.Builder(a)
                .setTitle("Enter barcode")
                .setView(box)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        paused = false;
                    }
                })
                .setPositiveButton("Look up", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        paused = false;
                        String code = Db.normalize(e.getText().toString());
                        if (code.length() > 0) {
                            lastCode = null;
                            lookup(code);
                        }
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        paused = false;
                    }
                })
                .show();
    }

    private void feedback() {
        try {
            if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {
        }
        try {
            Vibrator v = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(50);
        } catch (Exception ignored) {
        }
    }
}
