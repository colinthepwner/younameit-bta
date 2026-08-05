package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;

/**
 * The two colours a set is built from — the material's dominant colour and a darker shade for
 * outlines — plus a gap-free copy of the source texture to layer over the interior.
 *
 * <p>Dominant is found by bucketing the opaque pixels and averaging the winning bucket, rather
 * than averaging the whole image. Averaging turns a red-and-white brick into pink, which is
 * exactly the failure the outline is supposed to make obvious.
 */
public final class Palette {

    /** The material's dominant colour, taken from its interior only. */
    public final int main;
    /** The flat colour used for outlines. Always derived from {@link #main}, never from the art. */
    public final int shade;
    /**
     * The material's <em>own</em> secondary shading tone — gold's orange against its yellow, a
     * brick's darker red. Used to shade the body of the gear so a generated set carries the same
     * two-tone character the source has. Falls back to a darkened {@link #main} when the source
     * is essentially one flat colour.
     */
    public final int secondary;
    /** A fully opaque 16x16 version of the source, tiled and hole-filled. */
    public final BufferedImage solid;
    /** {@link #solid}'s backing pixels, for the compositor's inner loop. */
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

    /**
     * @param seed makes the scatter fill reproducible. It must be derived from the material's id
     *             rather than a clock or {@code Math.random}, because the texture is regenerated
     *             on every resource reload and a set whose gear reshuffles each time you change
     *             resource pack would be obviously wrong.
     */
    public static Palette of(BufferedImage source, long seed) {
        return of(source, seed, true);
    }

    /**
     * @param allowScatter true for loose items, whose sprite is one object that should be piled
     *                     up; false for blocks, whose texture is already a tile and would lose its
     *                     structure if scattered — glass is the clear case, a frame with a hollow
     *                     middle that reads as noise once shuffled. Very sparse block textures
     *                     (saplings, torches) still scatter regardless.
     */
    public static Palette of(BufferedImage source, long seed, boolean allowScatter) {
        BufferedImage solid = solidify(source, 16, 16, seed, allowScatter);

        // The colour is taken from the ORIGINAL pixels, not from the filled canvas. Filling
        // invents pixels — averaged edges, repeated stamps — and letting those into the
        // histogram drags the result away from the material's real colour, which is why a
        // feather and a bone were coming out darker than they look.
        BufferedImage histSrc = source == null ? solid : source;
        int[] px = Raster.read(histSrc);
        int hw = histSrc.getWidth(), hh = histSrc.getHeight();
        if (hh > hw && hh % hw == 0) hh = hw;

        // 4 bits per channel is coarse enough to group a material's shading into one bucket,
        // fine enough to keep distinct materials apart.
        int[] count = new int[4096];
        int[] sr = new int[4096], sg = new int[4096], sb = new int[4096];
        int counted = 0;

        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < hw; x++) {
                int p = px[y * histSrc.getWidth() + x];
                if (((p >>> 24) & 0xFF) < 16) continue;
                // Skip the sprite's own silhouette. An item is drawn with a dark ring around it,
                // and counting that ring is what kept dragging the "main" colour towards black —
                // a gold ingot would report its outline rather than its gold. A block tile has no
                // transparent neighbours at all, so nothing is skipped there.
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

        // A sprite thin enough to be all silhouette (a stick, a string) leaves nothing behind;
        // fall back to every opaque pixel rather than reporting grey.
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

        // The material's own second tone: the next most prominent bucket that is actually a
        // different colour, preferring a darker one because that is what reads as shading.
        int secondBucket = pickBucket(count, sr, sg, sb, best);
        int secondary;
        if (secondBucket < 0) {
            secondary = darken(main, 0.72F);
        } else {
            int candidate = average(sr, sg, sb, count, secondBucket);
            secondary = isDistinct(main, candidate) ? candidate : darken(main, 0.72F);
            // Shading has to read as shading. A lighter second tone is a highlight, not a shade,
            // so it is folded back down rather than used to brighten the body.
            if (luminance(secondary) > luminance(main)) {
                secondary = darken(secondary, 0.7F);
            }
        }

        // Outline is deliberately a touch darker than a plain "half the main colour" would give,
        // so the silhouette reads cleanly against the body at 16x16. This does not disturb the
        // highlight: shineColour renormalises saturation before pushing to white, so how dark the
        // outline is has no bearing on the tint it produces.
        return new Palette(main, darken(main, 0.46F), secondary, solid);
    }

    /** True when any 4-neighbour is transparent, i.e. the pixel is on the sprite's silhouette. */
    private static boolean touchesTransparent(int[] px, int stride, int w, int h, int x, int y) {
        return isClear(px, stride, w, h, x - 1, y) || isClear(px, stride, w, h, x + 1, y)
                || isClear(px, stride, w, h, x, y - 1) || isClear(px, stride, w, h, x, y + 1);
    }

    private static boolean isClear(int[] px, int stride, int w, int h, int x, int y) {
        // Off the edge of a full tile is not "transparent" — otherwise a solid block texture
        // would have its whole border skipped for no reason.
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        return ((px[y * stride + x] >>> 24) & 0xFF) < 16;
    }

    /** Highest-scoring bucket, optionally excluding one already chosen. */
    private static int pickBucket(int[] count, int[] sr, int[] sg, int[] sb, int exclude) {
        int best = -1;
        long bestScore = -1;
        for (int i = 0; i < count.length; i++) {
            int c = count[i];
            if (c == 0 || i == exclude) continue;
            int r = sr[i] / c, g = sg[i] / c, b = sb[i] / c;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            // Prefer frequent buckets, but nudge towards saturated ones so a mostly-grey texture
            // with a coloured motif does not read as flat grey.
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

    /** Far enough apart to be worth treating as a second tone rather than noise. */
    private static boolean isDistinct(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return dr + dg + db >= 40;
    }

    private static float luminance(int argb) {
        return 0.299F * ((argb >> 16) & 0xFF) + 0.587F * ((argb >> 8) & 0xFF) + 0.114F * (argb & 0xFF);
    }

    /** A darker version — reads as a shadow rather than a different colour. */
    public static int darken(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /**
     * Produces a fully opaque w x h image from a source that may be smaller, larger, or full of
     * holes — a sapling or a torch has far more transparent pixels than solid ones.
     *
     * <p>Gaps are filled with the texture itself rather than a flat colour: first by tiling, then,
     * for anything still transparent, by taking the nearest opaque pixel.
     */
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
        // Animated textures ship as a vertical strip; use the first frame only.
        if (sh > sw && sh % sw == 0) sh = sw;
        int[] s = Raster.read(src);
        int stride = src.getWidth();

        // How much of the source is actually drawn on.
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

        // A block face tiles straight across; a loose item's sprite is scattered, because
        // stretching one lonely feather over a pickaxe head reads as a smear rather than as a
        // material.
        //
        // The caller decides, rather than a transparency threshold, because transparency cannot
        // tell the two apart: glass is 27% opaque, a feather 21% and a bone 17%, so any cutoff
        // that piles up bones also shreds the glass tile. What actually differs is that a block
        // texture is already a tile with structure that repeats, and an item sprite is a single
        // object sitting on an empty field.
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

    /**
     * Returns a copy of the sprite with its silhouette pixels cleared, so scattering piles up the
     * material rather than the drawing of it.
     *
     * <p>An item sprite is drawn with a dark ring around the outside. Stamping that ring dozens of
     * times over a 16x16 canvas fills the result with dark speckle — bone came out as grey noise
     * rather than bone. Dropping the ring and stamping only the interior leaves the material's
     * actual colour behind.
     *
     * <p>Skipped when the sprite is thin enough that its outline is most of it (a stick, a piece
     * of string), since there would be nothing left to stamp.
     */
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

    /**
     * Fills the canvas by stamping the sprite over and over at random orientations until there is
     * no empty space left — a pile of the thing rather than one stretched copy of it.
     *
     * <p>Orientations are the eight square symmetries (four rotations, each optionally mirrored)
     * rather than arbitrary angles: at 16x16 an arbitrary rotation needs interpolation, and
     * interpolating pixel art turns crisp material into mush. Stamps wrap around the edges, so the
     * result also tiles seamlessly.
     *
     * <p>Two phases. The first stamps freely and lets later copies overlap earlier ones, which is
     * what gives the layered look; the second targets whatever is still empty and only fills
     * holes, which is what guarantees the coverage.
     */
    private static void scatter(int[] dst, int w, int h, int[] s, int stride, int sw, int sh, long seed) {
        // Crop to the drawn part first — stamping a sprite's empty margin just wastes passes.
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

        // Phase 1: free stamps, overlapping, for the layered pile.
        int rounds = Math.max(6, (w * h) / Math.max(1, tw * th) * 3);
        for (int i = 0; i < rounds; i++) {
            stamp(dst, w, h, s, stride, minX, minY, tw, th,
                    rng.nextInt(w), rng.nextInt(h), rng.nextInt(8), true);
        }

        // Phase 2: aim at what is still empty, and only fill holes.
        for (int attempt = 0; attempt < 400; attempt++) {
            int hole = -1;
            for (int i = 0; i < dst.length; i++) {
                if (((dst[i] >>> 24) & 0xFF) < 16) { hole = i; break; }
            }
            if (hole < 0) break;
            int hx = hole % w, hy = hole / w;
            // Land the stamp roughly over the hole, jittered so the fill does not become a grid.
            int ox = Math.floorMod(hx - tw / 2 + rng.nextInt(3) - 1, w);
            int oy = Math.floorMod(hy - th / 2 + rng.nextInt(3) - 1, h);
            stamp(dst, w, h, s, stride, minX, minY, tw, th, ox, oy, rng.nextInt(8), false);
        }
    }

    /**
     * Blits one oriented copy of the cropped sprite at (ox, oy), wrapping at the canvas edges.
     *
     * @param orientation 0-7: bits 0-1 are quarter turns, bit 2 mirrors
     * @param overwrite   true to draw over existing pixels, false to fill only empty ones
     */
    private static void stamp(int[] dst, int w, int h, int[] s, int stride,
                              int cropX, int cropY, int tw, int th,
                              int ox, int oy, int orientation, boolean overwrite) {
        int turns = orientation & 3;
        boolean mirror = (orientation & 4) != 0;
        // A quarter or three-quarter turn swaps the footprint's width and height.
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

    /**
     * Fills transparent pixels with the texture's own colour by repeatedly growing the opaque
     * region outwards, each new pixel taking the average of the opaque neighbours it touches.
     *
     * <p>A sapling, a feather or a bone is mostly empty space, and the obvious fix — copy the
     * nearest opaque pixel — produces a checkerboard of high-frequency noise, because adjacent
     * holes resolve to different neighbours. Averaging as the region grows smears the material
     * outwards smoothly instead, which is what "fill the whitespace with itself" ought to look
     * like once it is stretched over a pickaxe head.
     */
    private static void grow(int[] px, int w, int h) {
        int[] next = new int[px.length];
        for (int pass = 0; pass < 64; pass++) {
            System.arraycopy(px, 0, next, 0, px.length);
            boolean changed = false;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (((px[i] >>> 24) & 0xFF) >= 16) continue;

                    // Unrolled rather than looped: four bounds-checked reads, summed inline.
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

        // Anything still empty after that had no opaque pixel anywhere to grow from.
        for (int i = 0; i < px.length; i++) {
            if (((px[i] >>> 24) & 0xFF) < 16) px[i] = 0xFF888888;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
