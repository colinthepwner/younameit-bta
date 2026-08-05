package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Direct access to a BufferedImage's backing int array.
 *
 * <p>{@code getRGB}/{@code setRGB} go through the colour model on every single call. That is
 * irrelevant for one sprite and very relevant here: a big modpack can produce several thousand
 * sets, each of which is nine 16x16 icons plus two 64x32 armour sheets, so the pixel count runs
 * into the millions on every texture reload. Working on the raw array instead keeps the whole
 * generation pass well under a second.
 */
public final class Raster {
    private Raster() {}

    /** A new TYPE_INT_ARGB image, guaranteed to be backed by an int array. */
    public static BufferedImage create(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    /** The backing array of an INT_ARGB image, or null if the image is some other type. */
    public static int[] pixels(BufferedImage img) {
        if (img == null) return null;
        int type = img.getType();
        if (type != BufferedImage.TYPE_INT_ARGB && type != BufferedImage.TYPE_INT_RGB) return null;
        try {
            return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The pixels of an image in ARGB order, converting to a known layout first when the source
     * is something exotic — a paletted or greyscale PNG out of a resource pack, typically.
     */
    public static int[] read(BufferedImage img) {
        int[] direct = pixels(img);
        if (direct != null && img.getType() == BufferedImage.TYPE_INT_ARGB) return direct;
        int w = img.getWidth(), h = img.getHeight();
        int[] out = new int[w * h];
        img.getRGB(0, 0, w, h, out, 0, w);
        return out;
    }
}
