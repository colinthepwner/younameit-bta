package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;

/**
 * Which part of a sprite each pixel belongs to, derived by diffing two material variants of the
 * same vanilla sprite.
 *
 * <p>This is the trick that makes the art work without hand-drawn templates. An iron pickaxe and a
 * diamond pickaxe share one sprite geometry; the pixels that <em>differ</em> are the material, and
 * the pixels that are byte-identical are the parts that do not depend on it — the wooden stick on
 * a tool, the solid outline on an armour icon. Reading the shape out of the game's own art means
 * the sword's shorter, offset handle, the pickaxe's separate upper prongs and every other per-tool
 * quirk come along for free, and a resource pack that redraws the tools is followed rather than
 * fought.
 */
public final class SpriteMask {

    public static final byte NONE = 0;
    /** Identical across materials: the stick on a tool, the outline on armour. */
    public static final byte SHARED = 1;
    /** Differs across materials: the part that takes the material's colour. */
    public static final byte BODY = 2;

    public final int width;
    public final int height;
    private final byte[] kind;
    /** The reference sprite, kept so shared pixels and body shading can be copied out of it. */
    public final BufferedImage reference;
    /** {@link #reference}'s pixels in ARGB order, for the compositor's inner loop. */
    public final int[] referencePixels;
    /** Per-pixel brightness of the body relative to its own average, used to keep the shading. */
    private final float[] shading;
    /** Body pixels the reference sprite drew as its darkest shade — i.e. its outline. */
    private final boolean[] outline;
    /** Shared pixels the reference sprite drew near-white — i.e. its specular highlight. */
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

    /**
     * True where the reference sprite drew its specular highlight.
     *
     * <p>Vanilla paints armour highlights in near-white, and white is white whatever the armour is
     * made of — so those pixels come back byte-identical between the two reference materials and
     * land in {@link #SHARED} right alongside the black outline. Splitting that group by
     * brightness recovers the highlight exactly where the artist put it, which beats any
     * "brightest N body pixels" guess: that guess picked the evenly-lit top rim instead, and
     * painting all shared pixels one flat colour is what put hard dark bars across matte armour.
     */
    public boolean isHighlight(int x, int y) {
        return highlight[y * width + x];
    }

    public byte kindAt(int x, int y) {
        return kind[y * width + x];
    }

    /** 1.0 means "as bright as the average body pixel"; below is shadow, above is highlight. */
    public float shadingAt(int x, int y) {
        return shading[y * width + x];
    }

    /**
     * True where the reference sprite drew its own darkest shade — which is where its outline is.
     *
     * <p>Taking this from the artwork rather than from geometry matters on every thin part of a
     * tool. A pickaxe's upper prongs and the strip between them are only one or two pixels wide,
     * so a "touches a non-body pixel" test marks all of them as edge and the whole head turns into
     * flat outline colour with no material showing through. Vanilla itself keeps a lit centre
     * there, and reading the shade back reproduces exactly that.
     */
    public boolean isBodyEdge(int x, int y) {
        if (kindAt(x, y) != BODY) return false;
        return outline[y * width + x];
    }

    private static boolean notBody(byte[] kind, int w, int h, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return true;
        return kind[y * w + x] != BODY;
    }

    /** Returns null when the two sprites are not a usable pair. */
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

        // Vanilla sprites use a handful of flat shades; the darkest one is the outline. A quarter
        // of the range is wide enough to catch it plus any anti-aliased neighbours, and narrow
        // enough to leave the lit interior alone.
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
                // A single-shade sprite gives nothing to read, so fall back to the silhouette.
                outline[i] = usableSpread
                        ? lum <= threshold
                        : (notBody(kind, w, h, x - 1, y) || notBody(kind, w, h, x + 1, y)
                        || notBody(kind, w, h, x, y - 1) || notBody(kind, w, h, x, y + 1));
            }
        }

        // Split the shared pixels into the dark ones (outline and shadow) and the near-white ones
        // (the highlight). Vanilla's armour uses ~255 for the highlight against 18-45 for the
        // outline, so the two groups are nowhere near each other; the threshold is taken relative
        // to the sprite's own range with an absolute floor, so a resource pack that draws a
        // lighter outline does not turn the whole border into highlight.
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
            // Every shared pixel is the same tone, and it is a bright one — so the sprite has a
            // highlight and no outline. The worn-armour sheets are exactly this: a wrapped
            // texture needs no silhouette, so their only material-independent pixels are the
            // white specular ones (50 of them on layer 1, all at luminance 255). Requiring a
            // range to split left every one of those painted the dark outline colour, which is
            // why the worn models kept their lines on matte and never gleamed on shiny.
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
