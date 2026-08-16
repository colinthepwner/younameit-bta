package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public final class Raster {
    private Raster() {}

    public static BufferedImage create(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

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

    public static int[] read(BufferedImage img) {
        int[] direct = pixels(img);
        if (direct != null && img.getType() == BufferedImage.TYPE_INT_ARGB) return direct;
        int w = img.getWidth(), h = img.getHeight();
        int[] out = new int[w * h];
        img.getRGB(0, 0, w, h, out, 0, w);
        return out;
    }
}
