package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FaceSelection {

    public static final int HELMET = 0;
    public static final int CHESTPLATE = 1;
    public static final int LEGGINGS = 2;
    public static final int BOOTS = 3;

    private final BufferedImage toolFace;
    private final BufferedImage[] armorFaces;

    private FaceSelection(BufferedImage toolFace, BufferedImage[] armorFaces) {
        this.toolFace = toolFace;
        this.armorFaces = armorFaces;
    }

    public BufferedImage forTool() {
        return toolFace;
    }

    public BufferedImage forArmor(int piece) {
        return armorFaces[Math.max(0, Math.min(armorFaces.length - 1, piece))];
    }

    public List<BufferedImage> distinctUsed() {
        List<BufferedImage> out = new ArrayList<>();
        out.add(toolFace);
        for (BufferedImage img : armorFaces) {
            if (!out.contains(img)) out.add(img);
        }
        return out;
    }

    public static FaceSelection single(BufferedImage only) {
        return new FaceSelection(only, new BufferedImage[]{only, only, only, only});
    }

    public static FaceSelection of(BufferedImage top, BufferedImage bottom, List<BufferedImage> sides) {
        List<BufferedImage> all = new ArrayList<>();
        if (top != null) all.add(top);
        if (bottom != null) all.add(bottom);
        all.addAll(sides);

        List<Variant> variants = collapseSimilar(group(all));
        if (variants.isEmpty()) return null;
        if (variants.size() == 1) return single(variants.get(0).image);

        BufferedImage topFace = representative(top, variants);
        List<Variant> sideVariants = group(sides);
        sideVariants = collapseSimilar(sideVariants);

        BufferedImage majoritySide = null;
        BufferedImage oddSide = null;
        if (!sideVariants.isEmpty()) {
            sideVariants.sort(Comparator.comparingInt(v -> -v.count));
            majoritySide = representative(sideVariants.get(0).image, variants);
            if (sideVariants.size() > 1) {
                oddSide = representative(sideVariants.get(1).image, variants);
            }
        }
        if (majoritySide == null) majoritySide = variants.get(0).image;
        if (topFace == null) topFace = majoritySide;

        BufferedImage[] armor = new BufferedImage[4];
        armor[HELMET] = topFace;
        armor[CHESTPLATE] = oddSide != null && topFace == majoritySide ? oddSide : topFace;
        armor[LEGGINGS] = majoritySide;
        armor[BOOTS] = majoritySide;
        return new FaceSelection(majoritySide, armor);
    }

    private static BufferedImage representative(BufferedImage face, List<Variant> variants) {
        if (face == null) return null;
        int[] px = Raster.read(face);
        Variant best = null;
        double bestGap = Double.MAX_VALUE;
        Variant probe = new Variant(face, px);
        for (Variant v : variants) {
            if (java.util.Arrays.equals(v.pixels, px)) return v.image;
            double gap = Math.sqrt(
                    Math.pow(probe.meanR - v.meanR, 2)
                            + Math.pow(probe.meanG - v.meanG, 2)
                            + Math.pow(probe.meanB - v.meanB, 2));
            if (gap < bestGap) {
                bestGap = gap;
                best = v;
            }
        }
        return best != null ? best.image : face;
    }

    private static List<Variant> collapseSimilar(List<Variant> variants) {
        if (variants.size() < 2) return variants;

        List<Variant> merged = new ArrayList<>();
        for (Variant v : variants) {
            Variant into = null;
            for (Variant m : merged) {
                double colourGap = Math.sqrt(
                        Math.pow(v.meanR - m.meanR, 2)
                                + Math.pow(v.meanG - m.meanG, 2)
                                + Math.pow(v.meanB - m.meanB, 2));
                if (colourGap < 28.0 && Math.abs(v.variation - m.variation) < 12.0) {
                    into = m;
                    break;
                }
            }
            if (into != null) {

                if (v.count > into.count) {
                    merged.set(merged.indexOf(into), v);
                    v.count += into.count;
                } else {
                    into.count += v.count;
                }
            } else {
                merged.add(v);
            }
        }
        return merged;
    }

    private static List<Variant> group(List<BufferedImage> faces) {
        List<Variant> variants = new ArrayList<>();
        for (BufferedImage face : faces) {
            if (face == null) continue;
            int[] px = Raster.read(face);
            Variant match = null;
            for (Variant v : variants) {
                if (java.util.Arrays.equals(v.pixels, px)) {
                    match = v;
                    break;
                }
            }
            if (match != null) {
                match.count++;
            } else {
                variants.add(new Variant(face, px));
            }
        }
        return variants;
    }

    private static double[] meanColour(int[] px) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < 16) continue;
            r += (p >> 16) & 0xFF;
            g += (p >> 8) & 0xFF;
            b += p & 0xFF;
            n++;
        }
        if (n == 0) return new double[]{0, 0, 0};
        return new double[]{r / (double) n, g / (double) n, b / (double) n};
    }

    private static double colourVariation(int[] px) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < 16) continue;
            r += (p >> 16) & 0xFF;
            g += (p >> 8) & 0xFF;
            b += p & 0xFF;
            n++;
        }
        if (n == 0) return 0.0;
        double ar = r / (double) n, ag = g / (double) n, ab = b / (double) n;

        double sum = 0.0;
        for (int p : px) {
            if (((p >>> 24) & 0xFF) < 16) continue;
            double dr = ((p >> 16) & 0xFF) - ar;
            double dg = ((p >> 8) & 0xFF) - ag;
            double db = (p & 0xFF) - ab;
            sum += Math.sqrt(dr * dr + dg * dg + db * db);
        }
        return sum / n;
    }

    private static final class Variant {
        final BufferedImage image;
        final int[] pixels;
        final double variation;
        final double meanR, meanG, meanB;
        int count = 1;

        Variant(BufferedImage image, int[] pixels) {
            this.image = image;
            this.pixels = pixels;
            this.variation = colourVariation(pixels);
            double[] mean = meanColour(pixels);
            this.meanR = mean[0];
            this.meanG = mean[1];
            this.meanB = mean[2];
        }
    }
}
