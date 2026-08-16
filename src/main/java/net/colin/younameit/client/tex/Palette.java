package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;

public final class Palette {

    public final int main;

    public final int shade;

    public final int secondary;

    public final BufferedImage solid;

    public final int[] solidPixels;
    public final int solidWidth;
    public final int solidHeight;

    private Palette(int main, int shade, int secondary, BufferedImage solid) {
        this.main = main;
        this.shade = shade;
        this.secondary = secondary;
        this.solid = solid;
        this.solidPixels = Raster.read(solid);
        this.solidWidth = solid.getWidth();
        this.solidHeight = solid.getHeight();
    }

    public static Palette of(BufferedImage source) {
        return of(source, 0L);
    }

    public static Palette of(BufferedImage source, long seed) {
        return of(source, seed, true);
    }

    public static Palette of(BufferedImage source, long seed, boolean allowScatter) {
        BufferedImage solid = solidify(source, 16, 16, seed, allowScatter);

        BufferedImage histSrc = source == null ? solid : source;
        int[] px = Raster.read(histSrc);
        int hw = histSrc.getWidth(), hh = histSrc.getHeight();
        if (hh > hw && hh % hw == 0) hh = hw;

        int[] count = new int[4096];
        int[] sr = new int[4096], sg = new int[4096], sb = new int[4096];
        int counted = 0;

        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < hw; x++) {
                int p = px[y * histSrc.getWidth() + x];
                if (((p >>> 24) & 0xFF) < 16) continue;

                if (touchesTransparent(px, histSrc.getWidth(), hw, hh, x, y)) continue;

                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                int bucket = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                count[bucket]++;
                sr[bucket] += r;
                sg[bucket] += g;
                sb[bucket] += b;
                counted++;
            }
        }

        if (counted == 0) {
            for (int p : px) {
                if (((p >>> 24) & 0xFF) < 16) continue;
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                int bucket = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                count[bucket]++;
                sr[bucket] += r;
                sg[bucket] += g;
                sb[bucket] += b;
            }
        }

        int best = pickBucket(count, sr, sg, sb, -1);
        int main = best < 0 ? 0xFF888888 : average(sr, sg, sb, count, best);

        int secondBucket = pickBucket(count, sr, sg, sb, best);
        int secondary;
        if (secondBucket < 0) {
            secondary = darken(main, 0.72F);
        } else {
            int candidate = average(sr, sg, sb, count, secondBucket);
            secondary = isDistinct(main, candidate) ? candidate : darken(main, 0.72F);

            if (luminance(secondary) > luminance(main)) {
                secondary = darken(secondary, 0.7F);
            }
        }

        return new Palette(main, darken(main, 0.46F), secondary, solid);
    }

    private static boolean touchesTransparent(int[] px, int stride, int w, int h, int x, int y) {
        return isClear(px, stride, w, h, x - 1, y) || isClear(px, stride, w, h, x + 1, y)
                || isClear(px, stride, w, h, x, y - 1) || isClear(px, stride, w, h, x, y + 1);
    }

    private static boolean isClear(int[] px, int stride, int w, int h, int x, int y) {

        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        return ((px[y * stride + x] >>> 24) & 0xFF) < 16;
    }

    private static int pickBucket(int[] count, int[] sr, int[] sg, int[] sb, int exclude) {
        int best = -1;
        long bestScore = -1;
        for (int i = 0; i < count.length; i++) {
            int c = count[i];
            if (c == 0 || i == exclude) continue;
            int r = sr[i] / c, g = sg[i] / c, b = sb[i] / c;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));

            long score = (long) c * (100 + (max - min) * 2);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static int average(int[] sr, int[] sg, int[] sb, int[] count, int bucket) {
        int c = count[bucket];
        return 0xFF000000 | ((sr[bucket] / c) << 16) | ((sg[bucket] / c) << 8) | (sb[bucket] / c);
    }

    private static boolean isDistinct(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return dr + dg + db >= 40;
    }

    private static float luminance(int argb) {
        return 0.299F * ((argb >> 16) & 0xFF) + 0.587F * ((argb >> 8) & 0xFF) + 0.114F * (argb & 0xFF);
    }

    public static int darken(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static BufferedImage solidify(BufferedImage src, int w, int h) {
        return solidify(src, w, h, 0L, true);
    }

    public static BufferedImage solidify(BufferedImage src, int w, int h, long seed, boolean allowScatter) {
        BufferedImage out = Raster.create(w, h);
        int[] dst = Raster.pixels(out);

        if (src == null || src.getWidth() == 0 || src.getHeight() == 0) {
            java.util.Arrays.fill(dst, 0xFF888888);
            return out;
        }

        int sw = src.getWidth(), sh = src.getHeight();

        if (sh > sw && sh % sw == 0) sh = sw;
        int[] s = Raster.read(src);
        int stride = src.getWidth();

        int srcOpaque = 0;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                if (((s[y * stride + x] >>> 24) & 0xFF) >= 16) srcOpaque++;
            }
        }
        if (srcOpaque == 0) {
            java.util.Arrays.fill(dst, 0xFF888888);
            return out;
        }

        long area = (long) sw * sh;
        if (allowScatter && srcOpaque < area * 92 / 100) {
            scatter(dst, w, h, stripSilhouette(s, stride, sw, sh, srcOpaque), stride, sw, sh, seed);
        } else {
            for (int y = 0; y < h; y++) {
                int sy = sh >= h ? y * sh / h : y % sh;
                int srcRow = sy * stride;
                int dstRow = y * w;
                for (int x = 0; x < w; x++) {
                    int sx = sw >= w ? x * sw / w : x % sw;
                    dst[dstRow + x] = s[srcRow + sx];
                }
            }
        }

        int opaqueCount = 0;
        for (int p : dst) if (((p >>> 24) & 0xFF) >= 16) opaqueCount++;
        if (opaqueCount == 0) {
            java.util.Arrays.fill(dst, 0xFF888888);
            return out;
        }
        if (opaqueCount < dst.length) grow(dst, w, h);
        return out;
    }

    private static int[] stripSilhouette(int[] s, int stride, int sw, int sh, int srcOpaque) {
        int[] out = new int[s.length];
        System.arraycopy(s, 0, out, 0, s.length);
        int kept = 0;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int i = y * stride + x;
                if (((s[i] >>> 24) & 0xFF) < 16) continue;
                if (isClear(s, stride, sw, sh, x - 1, y) || isClear(s, stride, sw, sh, x + 1, y)
                        || isClear(s, stride, sw, sh, x, y - 1) || isClear(s, stride, sw, sh, x, y + 1)) {
                    out[i] = 0;
                } else {
                    kept++;
                }
            }
        }
        return kept >= Math.max(3, srcOpaque / 4) ? out : s;
    }

    private static void scatter(int[] dst, int w, int h, int[] s, int stride, int sw, int sh, long seed) {

        int minX = sw, minY = sh, maxX = -1, maxY = -1;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                if (((s[y * stride + x] >>> 24) & 0xFF) < 16) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX) return;
        int tw = maxX - minX + 1, th = maxY - minY + 1;

        java.util.Random rng = new java.util.Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);

        int rounds = Math.max(6, (w * h) / Math.max(1, tw * th) * 3);
        for (int i = 0; i < rounds; i++) {
            stamp(dst, w, h, s, stride, minX, minY, tw, th,
                    rng.nextInt(w), rng.nextInt(h), rng.nextInt(8), true);
        }

        for (int attempt = 0; attempt < 400; attempt++) {
            int hole = -1;
            for (int i = 0; i < dst.length; i++) {
                if (((dst[i] >>> 24) & 0xFF) < 16) { hole = i; break; }
            }
            if (hole < 0) break;
            int hx = hole % w, hy = hole / w;

            int ox = Math.floorMod(hx - tw / 2 + rng.nextInt(3) - 1, w);
            int oy = Math.floorMod(hy - th / 2 + rng.nextInt(3) - 1, h);
            stamp(dst, w, h, s, stride, minX, minY, tw, th, ox, oy, rng.nextInt(8), false);
        }
    }

    private static void stamp(int[] dst, int w, int h, int[] s, int stride,
                              int cropX, int cropY, int tw, int th,
                              int ox, int oy, int orientation, boolean overwrite) {
        int turns = orientation & 3;
        boolean mirror = (orientation & 4) != 0;

        int outW = (turns & 1) == 0 ? tw : th;
        int outH = (turns & 1) == 0 ? th : tw;

        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                int sx, sy;
                switch (turns) {
                    case 1:  sx = y;              sy = th - 1 - x;      break;
                    case 2:  sx = tw - 1 - x;     sy = th - 1 - y;      break;
                    case 3:  sx = tw - 1 - y;     sy = x;               break;
                    default: sx = x;              sy = y;               break;
                }
                if (mirror) sx = tw - 1 - sx;
                if (sx < 0 || sy < 0 || sx >= tw || sy >= th) continue;

                int p = s[(cropY + sy) * stride + (cropX + sx)];
                if (((p >>> 24) & 0xFF) < 16) continue;

                int di = ((oy + y) % h) * w + ((ox + x) % w);
                if (!overwrite && ((dst[di] >>> 24) & 0xFF) >= 16) continue;
                dst[di] = 0xFF000000 | (p & 0xFFFFFF);
            }
        }
    }

    private static void grow(int[] px, int w, int h) {
        int[] next = new int[px.length];
        for (int pass = 0; pass < 64; pass++) {
            System.arraycopy(px, 0, next, 0, px.length);
            boolean changed = false;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (((px[i] >>> 24) & 0xFF) >= 16) continue;

                    int r = 0, g = 0, b = 0, n = 0;
                    int p;
                    if (x > 0)      { p = px[i - 1];     if (((p >>> 24) & 0xFF) >= 16) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; } }
                    if (x < w - 1)  { p = px[i + 1];     if (((p >>> 24) & 0xFF) >= 16) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; } }
                    if (y > 0)      { p = px[i - w];     if (((p >>> 24) & 0xFF) >= 16) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; } }
                    if (y < h - 1)  { p = px[i + w];     if (((p >>> 24) & 0xFF) >= 16) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; } }

                    if (n == 0) continue;
                    next[i] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
                    changed = true;
                }
            }

            System.arraycopy(next, 0, px, 0, px.length);
            if (!changed) break;

            boolean done = true;
            for (int p : px) {
                if (((p >>> 24) & 0xFF) < 16) { done = false; break; }
            }
            if (done) break;
        }

        for (int i = 0; i < px.length; i++) {
            if (((px[i] >>> 24) & 0xFF) < 16) px[i] = 0xFF888888;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
