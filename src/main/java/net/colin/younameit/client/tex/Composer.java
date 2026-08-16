package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;

public final class Composer {
    private Composer() {}

    private static final float TOOL_TEXTURE_BLEND = 0.28F;

    private static final float ARMOR_TEXTURE_BLEND = 0.62F;

    private static final float MATTE_MAX_SHADING = 1.0F;
    private static final float SHINY_MAX_SHADING = 1.55F;

    public static BufferedImage tool(SpriteMask mask, Palette palette, boolean shiny, boolean flat, int orientation) {
        return paint(mask, palette, flat ? 0.0F : TOOL_TEXTURE_BLEND, true, true, shiny, orientation);
    }

    public static BufferedImage armorIcon(SpriteMask mask, Palette palette, boolean shiny, boolean flat, int orientation) {
        return paint(mask, palette, flat ? 0.0F : ARMOR_TEXTURE_BLEND, false, false, shiny, orientation);
    }

    public static BufferedImage armorSheet(SpriteMask mask, Palette palette, boolean shiny, boolean flat, int orientation) {
        return paint(mask, palette, flat ? 0.0F : ARMOR_TEXTURE_BLEND, false, false, shiny, orientation);
    }

    public static BufferedImage armorSheetLayer1(SpriteMask mask, Palette head, Palette body, Palette feet,
                                                 boolean shiny, boolean flat, int orientation) {
        float blend = flat ? 0.0F : ARMOR_TEXTURE_BLEND;
        return paint(mask, (x, y) -> {
            if (y < mask.height / 2) return head;
            return x < 16 ? feet : body;
        }, blend, false, false, shiny, orientation);
    }

    public interface PalettePicker {
        Palette at(int x, int y);
    }

    private static BufferedImage paint(SpriteMask mask, Palette palette, float textureBlend,
                                       boolean keepShared, boolean outlineBody, boolean shiny,
                                       int orientation) {
        return paint(mask, (x, y) -> palette, textureBlend, keepShared, outlineBody, shiny, orientation);
    }

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

                        dst[i] = ref[i];
                        continue;
                    }
                    if (!mask.isHighlight(x, y)) {

                        dst[i] = shade;
                        continue;
                    }
                    if (shiny) {
                        dst[i] = shineColour(shade);
                        continue;
                    }

                    flatten = true;
                } else if (outlineBody && mask.isBodyEdge(x, y)) {
                    dst[i] = shade;
                    continue;
                }

                boolean shadowed = !flatten && mask.shadingAt(x, y) < 0.97F;
                int br = shadowed ? qr : mr;
                int bg = shadowed ? qg : mg;
                int bb = shadowed ? qb : mb;

                int bp = src[sampleIndex(x, y, sw, sh, orientation)];
                int r = (br * inv + ((bp >> 16) & 0xFF) * amt) >> 8;
                int g = (bg * inv + ((bp >> 8) & 0xFF) * amt) >> 8;
                int b = (bb * inv + (bp & 0xFF) * amt) >> 8;

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
