package com.scanmart.pos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Dimmed camera overlay with a scan window, corner brackets and a red guide line. */
class ScanOverlay extends View {
    private final Paint scrim = new Paint();
    private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF win = new RectF();

    ScanOverlay(Context c) {
        super(c);
        scrim.setColor(Color.argb(120, 0, 0, 0));
        corner.setColor(Color.WHITE);
        corner.setStyle(Paint.Style.STROKE);
        corner.setStrokeWidth(U.dp(c, 4));
        corner.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(Color.argb(200, 255, 59, 48));
        line.setStrokeWidth(U.dp(c, 2));
    }

    RectF window() {
        return win;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float ww = w * 0.84f;
        float wh = ww * 0.52f;
        float cx = w / 2f;
        float cy = h * 0.36f;
        win.set(cx - ww / 2, cy - wh / 2, cx + ww / 2, cy + wh / 2);
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        cv.drawRect(0, 0, w, win.top, scrim);
        cv.drawRect(0, win.bottom, w, h, scrim);
        cv.drawRect(0, win.top, win.left, win.bottom, scrim);
        cv.drawRect(win.right, win.top, w, win.bottom, scrim);
        float L = U.dp(getContext(), 26);
        // corners
        cv.drawLine(win.left, win.top, win.left + L, win.top, corner);
        cv.drawLine(win.left, win.top, win.left, win.top + L, corner);
        cv.drawLine(win.right, win.top, win.right - L, win.top, corner);
        cv.drawLine(win.right, win.top, win.right, win.top + L, corner);
        cv.drawLine(win.left, win.bottom, win.left + L, win.bottom, corner);
        cv.drawLine(win.left, win.bottom, win.left, win.bottom - L, corner);
        cv.drawLine(win.right, win.bottom, win.right - L, win.bottom, corner);
        cv.drawLine(win.right, win.bottom, win.right, win.bottom - L, corner);
        float cy = (win.top + win.bottom) / 2;
        cv.drawLine(win.left + L / 2, cy, win.right - L / 2, cy, line);
    }
}
