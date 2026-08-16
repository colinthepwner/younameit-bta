package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;

public final class SpriteMask {

    public static final byte NONE = 0;

    public static final byte SHARED = 1;

    public static final byte BODY = 2;

    public final int width;
    public final int height;
    private final byte[] kind;

    public final BufferedImage reference;

    public final int[] referencePixels;

    private final float[] shading;

    private final boolean[] outline;

    private final boolean[] highlight;

    private SpriteMask(int width, int height, byte[] kind, BufferedImage reference,
                       float[] shading, boolean[] outline, boolean[] highlight) {
        this.width = width;
        this.height = height;
        this.kind = kind;
        this.reference = reference;
        this.referencePixels = Raster.read(reference);
        this.shading = shading;
        this.outline = outline;
        this.highlight = highlight;
    }

    public boolean isHighlight(int x, int y) {
        return highlight[y * width + x];
    }

    public byte kindAt(int x, int y) {
        return kind[y * width + x];
    }

    public float shadingAt(int x, int y) {
        return shading[y * width + x];
    }

    public boolean isBodyEdge(int x, int y) {
        if (kindAt(x, y) != BODY) return false;
        return outline[y * width + x];
    }

    private static boolean notBody(byte[] kind, int w, int h, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return true;
        return kind[y * w + x] != BODY;
    }

    public static SpriteMask derive(BufferedImage a, BufferedImage b) {
        if (a == null || b == null) return null;
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return null;

        int w = a.getWidth(), h = a.getHeight();
        int[] pa = Raster.read(a);
        int[] pb = Raster.read(b);
        byte[] kind = new byte[w * h];
        float[] shading = new float[w * h];

        double lumSum = 0;
        int bodyCount = 0;
        for (int i = 0; i < kind.length; i++) {
            int p = pa[i];
            if (((p >>> 24) & 0xFF) < 16) {
                kind[i] = NONE;
            } else if (p == pb[i]) {
                kind[i] = SHARED;
            } else {
                kind[i] = BODY;
                lumSum += luminance(p);
                bodyCount++;
            }
        }
        if (bodyCount == 0) return null;

        float average = (float) Math.max(1.0, lumSum / bodyCount);
        float minLum = Float.MAX_VALUE, maxLum = -Float.MAX_VALUE;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] != BODY) continue;
            float l = luminance(pa[i]);
            if (l < minLum) minLum = l;
            if (l > maxLum) maxLum = l;
        }

        boolean[] outline = new boolean[w * h];
        float spread = maxLum - minLum;
        boolean usableSpread = spread > 12.0F;
        float threshold = minLum + spread * 0.25F;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (kind[i] != BODY) {
                    shading[i] = 1.0F;
                    continue;
                }
                float lum = luminance(pa[i]);
                shading[i] = lum / average;

                outline[i] = usableSpread
                        ? lum <= threshold
                        : (notBody(kind, w, h, x - 1, y) || notBody(kind, w, h, x + 1, y)
                        || notBody(kind, w, h, x, y - 1) || notBody(kind, w, h, x, y + 1));
            }
        }

        float sharedMin = Float.MAX_VALUE, sharedMax = -Float.MAX_VALUE;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] != SHARED) continue;
            float l = luminance(pa[i]);
            if (l < sharedMin) sharedMin = l;
            if (l > sharedMax) sharedMax = l;
        }
        boolean[] highlight = new boolean[w * h];
        if (sharedMax > sharedMin) {
            float cut = Math.max(120.0F, sharedMin + (sharedMax - sharedMin) * 0.6F);
            for (int i = 0; i < kind.length; i++) {
                highlight[i] = kind[i] == SHARED && luminance(pa[i]) >= cut;
            }
        } else if (sharedMax >= 120.0F) {

            for (int i = 0; i < kind.length; i++) {
                highlight[i] = kind[i] == SHARED;
            }
        }

        return new SpriteMask(w, h, kind, a, shading, outline, highlight);
    }

    private static float luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return 0.299F * r + 0.587F * g + 0.114F * b;
    }
}
