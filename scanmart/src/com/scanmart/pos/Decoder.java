package com.scanmart.pos;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes barcodes from NV21 camera frames. Not thread-safe: use from one thread only.
 * Phone cameras deliver landscape frames, so a barcode held horizontally on a portrait screen
 * is vertical in the frame; ZXing cannot rotate YUV sources, so we rotate the luminance plane
 * ourselves and try both orientations.
 */
class Decoder {
    private final MultiFormatReader reader = new MultiFormatReader();
    private byte[] rotated;

    Decoder() {
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        List<BarcodeFormat> fmts = new ArrayList<BarcodeFormat>();
        fmts.add(BarcodeFormat.EAN_13);
        fmts.add(BarcodeFormat.EAN_8);
        fmts.add(BarcodeFormat.UPC_A);
        fmts.add(BarcodeFormat.UPC_E);
        fmts.add(BarcodeFormat.CODE_128);
        fmts.add(BarcodeFormat.CODE_39);
        fmts.add(BarcodeFormat.CODE_93);
        fmts.add(BarcodeFormat.ITF);
        fmts.add(BarcodeFormat.CODABAR);
        fmts.add(BarcodeFormat.QR_CODE);
        fmts.add(BarcodeFormat.DATA_MATRIX);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, fmts);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    /** @return the decoded text, or null when no barcode was found. */
    String decode(byte[] nv21, int w, int h) {
        // 1) rotated 90 degrees (typical: portrait phone, horizontal barcode)
        int n = w * h;
        if (rotated == null || rotated.length != n) rotated = new byte[n];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                rotated[x * h + (h - 1 - y)] = nv21[row + x];
            }
        }
        String t = attempt(rotated, h, w);
        if (t != null) return t;
        // 2) as delivered (landscape phone, or barcode held vertically)
        return attempt(nv21, w, h);
    }

    private String attempt(byte[] y, int w, int h) {
        try {
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false);
            Result r = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
            return r.getText();
        } catch (Exception e) {
            return null;
        } finally {
            reader.reset();
        }
    }
}
