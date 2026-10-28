package com.scanmart.pos;
import com.google.zxing.*;
import com.google.zxing.common.*;
import java.util.*;

public class DecodeTest {
    public static void main(String[] a) throws Exception {
        Decoder dec = new Decoder();
        String[][] cases = {{"5449000000996", "EAN_13"}, {"3017620422003", "EAN_13"}, {"036000291452", "UPC_A"}, {"96385074", "EAN_8"}, {"ABC-12345", "CODE_128"}};
        int ok = 0, total = 0;
        for (String[] c : cases) {
            BarcodeFormat f = BarcodeFormat.valueOf(c[1]);
            BitMatrix m = new MultiFormatWriter().encode(c[0], f, f == BarcodeFormat.CODE_128 ? 300 : 260, 100);
            for (int rot = 0; rot < 2; rot++) {
                int w = m.getWidth(), h = m.getHeight();
                int W = rot == 0 ? w + 120 : h + 120, H = rot == 0 ? h + 300 : w + 100; // padded "camera frame"
                byte[] y = new byte[W * H];
                Arrays.fill(y, (byte) 200);
                for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
                    int px = rot == 0 ? i + 60 : j + 60, py = rot == 0 ? j + 150 : i + 50;
                    y[py * W + px] = (byte) (m.get(i, j) ? 20 : 235);
                }
                byte[] nv21 = Arrays.copyOf(y, W * H * 3 / 2);
                total++;
                try {
                    String tx = dec.decode(nv21, W, H);
                    if (tx == null) throw new Exception("not found");
                    boolean good = tx.equals(c[0]);
                    System.out.println((good ? "OK   " : "DIFF ") + c[1] + " rot" + rot * 90 + " -> " + tx);
                    if (good) ok++;
                } catch (Exception e) { System.out.println("FAIL " + c[1] + " rot" + rot * 90 + " " + e); }
            }
        }
        System.out.println(ok + "/" + total);
    }
}
