package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;

/**
 * Paints a mask with a material's palette.
 *
 * <p>The rules, straight from the brief:
 * <ul>
 *   <li>the outline is the shade colour, flat and solid;</li>
 *   <li>the interior is the main colour with the source texture layered faintly over it, so
 *       brickwork reads as brick in the pickaxe head but never in the outline;</li>
 *   <li>a tool's stick is copied through untouched;</li>
 *   <li>armour shows considerably more of the source texture, because there is more room;</li>
 *   <li>a specular highlight is added only to materials that would actually catch the light.</li>
 * </ul>
 *
 * <p>Works on raw int arrays rather than {@code getRGB}/{@code setRGB}; see {@link Raster}.
 */
public final class Composer {
    private Composer() {}

    /** How much of the source texture shows through on a tool head. */
    private static final float TOOL_TEXTURE_BLEND = 0.28F;
    /** Armour is larger, so the source texture is allowed to dominate more. */
    private static final float ARMOR_TEXTURE_BLEND = 0.62F;

    /**
     * Upper bound on the reference sprite's shading.
     *
     * <p>Vanilla's iron and diamond sprites are polished metal, so their brightest pixels are a
     * specular highlight. Copying that ramp onto brick or sand gives every material a gleam it
     * has no business having. A matte material is therefore clamped at exactly 1.0 — its shading
     * may only ever darken, never brighten. Anything above 1.0 reproduces the iron sprite's
     * highlight streaks as visibly lighter lines, which is what "matte armour still has lines
     * where the shine would be" was pointing at; 1.12 was still enough to show them.
     */
    private static final float MATTE_MAX_SHADING = 1.0F;
    private static final float SHINY_MAX_SHADING = 1.55F;

    /**
     * A tool: outline shaded, head tinted and faintly textured, stick left alone.
     *
     * @param orientation which of the eight square symmetries the source texture is sampled
     *                    through. Giving each piece its own turn stops a five-tool set from
     *                    looking like the same stamp five times, and costs nothing: the rotation
     *                    happens while sampling, so no extra image is built.
     */
    public static BufferedImage tool(SpriteMask mask, Palette palette, boolean shiny, boolean flat, int orientation) {
        return paint(mask, palette, flat ? 0.0F : TOOL_TEXTURE_BLEND, true, true, shiny, orientation);
    }

    /**
     * An armour item icon.
     *
     * <p>Unlike a tool, armour sprites already carry their own solid outline as the ring of pixels
     * that is identical between materials, so those become the shade colour and the body is left
     * alone. Adding a second, geometric outline on top of that one is what made the first pass
     * look inside-out: every piece ended up with a two-pixel border and only a sliver of the
     * source texture surviving in the middle.
     *
     * <p>Highlight placement is inherited from the sprite rather than counted out here — see
     * {@link SpriteMask#isHighlight}.
     */
    public static BufferedImage armorIcon(SpriteMask mask, Palette palette, boolean shiny, boolean flat, int orientation) {
        return paint(mask, palette, flat ? 0.0F : ARMOR_TEXTURE_BLEND, false, false, shiny, orientation);
    }

    /** The worn-armour sheet; the UV layout and highlight positions are inherited from the mask. */
    public static BufferedImage armorSheet(SpriteMask mask, Palette palette, boolean shiny, boolean flat, int orientation) {
        return paint(mask, palette, flat ? 0.0F : ARMOR_TEXTURE_BLEND, false, false, shiny, orientation);
    }

    /**
     * Layer 1 of the worn-armour sheet, painted per body part.
     *
     * <p>That sheet carries the helmet, the chestplate <em>and</em> the boots in one 64x32 image,
     * so painting it with a single palette forces all three to share a face. On grass that came
     * out as green boots: the chestplate correctly took the grassy top, and the boots were dragged
     * along with it even though their icon was properly earth-coloured.
     *
     * <p>The standard biped unwrap is fixed, so the regions can simply be addressed: the head box
     * occupies the top half, and along the bottom half the leg box sits at x 0-15, the body at
     * x 16-39 and the arms from x 40. Boots are drawn from the leg box on this layer, which is why
     * that region takes the boot palette.
     */
    public static BufferedImage armorSheetLayer1(SpriteMask mask, Palette head, Palette body, Palette feet,
                                                 boolean shiny, boolean flat, int orientation) {
        float blend = flat ? 0.0F : ARMOR_TEXTURE_BLEND;
        return paint(mask, (x, y) -> {
            if (y < mask.height / 2) return head;
            return x < 16 ? feet : body;
        }, blend, false, false, shiny, orientation);
    }

    /** Picks the palette for a pixel, so one image can be painted from several faces. */
    public interface PalettePicker {
        Palette at(int x, int y);
    }

    private static BufferedImage paint(SpriteMask mask, Palette palette, float textureBlend,
                                       boolean keepShared, boolean outlineBody, boolean shiny,
                                       int orientation) {
        return paint(mask, (x, y) -> palette, textureBlend, keepShared, outlineBody, shiny, orientation);
    }

    /**
     * @param keepShared  true to copy shared pixels verbatim (a tool's stick), false to resolve
     *                    them into outline and highlight
     * @param outlineBody true to force the body's own darkest shade to flat shade colour
     */
    private static BufferedImage paint(SpriteMask mask, PalettePicker picker, float textureBlend,
                                       boolean keepShared, boolean outlineBody, boolean shiny,
                                       int orientation) {
        int w = mask.width, h = mask.height;
        BufferedImage out = Raster.create(w, h);
        int[] dst = Raster.pixels(out);

        int[] ref = mask.referencePixels;
        int inv = (int) ((1.0F - textureBlend) * 256);
        int amt = 256 - inv;
        float maxShading = shiny ? SHINY_MAX_SHADING : MATTE_MAX_SHADING;

        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                byte kind = mask.kindAt(x, y);

                if (kind == SpriteMask.NONE) {
                    dst[i] = 0;
                    continue;
                }

                Palette palette = picker.at(x, y);
                int[] src = palette.solidPixels;
                int sw = palette.solidWidth, sh = palette.solidHeight;
                int main = palette.main;
                int shade = palette.shade;
                int second = palette.secondary;
                int mr = (main >> 16) & 0xFF, mg = (main >> 8) & 0xFF, mb = main & 0xFF;
                int qr = (second >> 16) & 0xFF, qg = (second >> 8) & 0xFF, qb = second & 0xFF;

                boolean flatten = false;
                if (kind == SpriteMask.SHARED) {
                    if (keepShared) {
                        // A tool's stick: copy it through exactly as the game drew it.
                        dst[i] = ref[i];
                        continue;
                    }
                    if (!mask.isHighlight(x, y)) {
                        // The dark shared pixels: outline, and shadowed detail like the helmet's
                        // face opening. Both are the outline colour.
                        dst[i] = shade;
                        continue;
                    }
                    if (shiny) {
                        dst[i] = shineColour(shade);
                        continue;
                    }
                    // Matte: there is no highlight, so this pixel is just more of the material.
                    // Painting it the outline colour instead is what left dark lines across
                    // matte armour exactly where a shiny piece would have gleamed.
                    flatten = true;
                } else if (outlineBody && mask.isBodyEdge(x, y)) {
                    dst[i] = shade;
                    continue;
                }

                // Where the reference sprite is shadowed, use the material's OWN second tone
                // rather than a synthetically darkened main. Gold shades to its orange and brick
                // to its darker red, so a generated set keeps the two-tone character the source
                // has. The outline is untouched by this — it stays the flat shade colour, set
                // above and never mixed with either tone.
                boolean shadowed = !flatten && mask.shadingAt(x, y) < 0.97F;
                int br = shadowed ? qr : mr;
                int bg = shadowed ? qg : mg;
                int bb = shadowed ? qb : mb;

                int bp = src[sampleIndex(x, y, sw, sh, orientation)];
                int r = (br * inv + ((bp >> 16) & 0xFF) * amt) >> 8;
                int g = (bg * inv + ((bp >> 8) & 0xFF) * amt) >> 8;
                int b = (bb * inv + (bp & 0xFF) * amt) >> 8;

                // A gentle residual multiplier on top for depth. Much softer than before, because
                // the two-tone pick above already carries most of the shading; leaving it at full
                // strength would darken the shadowed tone twice over.
                float f = flatten ? 1.0F : 1.0F + (mask.shadingAt(x, y) - 1.0F) * 0.30F;
                if (f < 0.70F) f = 0.70F;
                else if (f > maxShading) f = maxShading;

                dst[i] = 0xFF000000
                        | (clamp((int) (r * f)) << 16)
                        | (clamp((int) (g * f)) << 8)
                        | clamp((int) (b * f));
            }
        }
        return out;
    }

    /**
     * Maps a destination pixel to a source pixel through one of the eight square symmetries.
     *
     * <p>Quarter turns and mirrors only. An arbitrary angle would need interpolation, and
     * interpolating a 16x16 texture turns crisp material into mush; the eight lossless
     * orientations are plenty to keep the pieces of a set looking distinct from one another.
     */
    private static int sampleIndex(int x, int y, int sw, int sh, int orientation) {
        int px = x % sw;
        int py = y % sh;
        int sx;
        int sy;
        switch (orientation & 3) {
            case 1:  sx = py;          sy = sw - 1 - px; break;
            case 2:  sx = sw - 1 - px; sy = sh - 1 - py; break;
            case 3:  sx = sh - 1 - py; sy = px;          break;
            default: sx = px;          sy = py;          break;
        }
        if ((orientation & 4) != 0) sx = sw - 1 - sx;
        if (sx < 0) sx = 0; else if (sx >= sw) sx = sw - 1;
        if (sy < 0) sy = 0; else if (sy >= sh) sy = sh - 1;
        return sy * sw + sx;
    }

    /**
     * "Much lighter, almost white, but tinted" — the outline colour taken most of the way to white
     * while keeping a clearly visible cast of its own hue.
     *
     * <p>The saturation has to be restored <em>before</em> the push to white, which is the part
     * that was wrong first time round. The outline is the main colour already darkened, so it is
     * comparatively desaturated; brightening that directly washes the hue out and the highlight
     * lands on plain white. Normalising the brightest channel back to full first means a gold
     * highlight reads as pale gold and a diamond one as pale cyan, while a genuinely grey material
     * still — correctly — comes out white.
     */
    static int shineColour(int shade) {
        int r = (shade >> 16) & 0xFF, g = (shade >> 8) & 0xFF, b = shade & 0xFF;

        int max = Math.max(r, Math.max(g, b));
        if (max > 0) {
            float boost = 255.0F / max;
            r = clamp((int) (r * boost));
            g = clamp((int) (g * boost));
            b = clamp((int) (b * boost));
        }

        float t = 0.72F;
        return 0xFF000000
                | (clamp((int) (r + (255 - r) * t)) << 16)
                | (clamp((int) (g + (255 - g) * t)) << 8)
                | clamp((int) (b + (255 - b) * t));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
