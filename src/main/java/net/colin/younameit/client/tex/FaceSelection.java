package net.colin.younameit.client.tex;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which of a block's faces each generated piece is drawn from.
 *
 * <p>Most blocks are the same on all six sides and this collapses to "use the one texture". The
 * interesting cases are the blocks that are not:
 *
 * <ul>
 *   <li><b>One odd face out</b>, like a pumpkin's carved front or a furnace's opening. That face is
 *       the block's identity, not its substance, so no tool uses it and only the chestplate does —
 *       a pickaxe made of pumpkin should look like pumpkin, not like a face grinning off the
 *       head. Everything else takes the majority face.</li>
 *   <li><b>Several genuinely different faces</b>, like TNT's top, bottom and sides. Tools take the
 *       calmest face, because a busy texture squeezed into a pickaxe head turns to noise; armour
 *       spreads the faces across its four pieces, with the chestplate — the largest canvas — given
 *       the busiest one.</li>
 * </ul>
 */
public final class FaceSelection {

    /** Indices into {@link #armorFaces}, matching the piece order used by the client. */
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

    /** Every distinct image this selection can hand out, for palette caching. */
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

    /**
     * Assigns faces to pieces by where they sit on the block, not by how busy they are.
     *
     * <p>Position is the rule that actually matches what a player expects to see. Grass is the
     * case that proves it: green on top, earth down the sides. Worn as a set that should read as
     * grass over the head and shoulders and soil down the legs, which is anatomy, not colour
     * statistics. Sorting by busyness put the green on the chest and scattered earth and the
     * underside across the other three, so the suit did not resemble the block from any angle.
     *
     * <p>So the top face clothes the head and chest, the sides clothe the legs and feet, and tools
     * take a side, because the sides are what the block is made of rather than what grows on it.
     * A block whose sides disagree with each other — a pumpkin's carved front, a furnace's opening
     * — hands that odd side to the chestplate, which is the one piece with room to show it.
     *
     * @param top    the up face, may be null
     * @param bottom the down face, may be null
     * @param sides  the four horizontal faces, nulls allowed and ignored
     */
    public static FaceSelection of(BufferedImage top, BufferedImage bottom, List<BufferedImage> sides) {
        List<BufferedImage> all = new ArrayList<>();
        if (top != null) all.add(top);
        if (bottom != null) all.add(bottom);
        all.addAll(sides);

        List<Variant> variants = collapseSimilar(group(all));
        if (variants.isEmpty()) return null;
        if (variants.size() == 1) return single(variants.get(0).image);

        // Resolve each position to whichever surviving variant represents it, so a face that was
        // merged away (a cactus top folded into its sides) still resolves to its stand-in.
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

    /** The surviving variant that a given face collapsed into, or the face itself. */
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

    /**
     * Merges faces that are different images but plainly the same material.
     *
     * <p>A cactus is the case this exists for: its top and its spined sides are separate textures,
     * so counting distinct images alone treats it exactly like TNT and starts handing a different
     * face to each armour piece. But cactus is green all the way round with the same busyness on
     * every side, whereas TNT is red sides against a pale top. Comparing average colour <em>and</em>
     * busyness separates the two without needing to know what either block is: faces that agree on
     * both are one material seen twice, faces that disagree on either are genuinely different
     * sides. A pumpkin's carved front survives this because the carving makes it far busier than
     * the plain sides, even though both are orange.
     */
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
                // Keep whichever covers more sides as the representative image.
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

    /** Collapses identical faces, counting how many sides each distinct image covers. */
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

    /**
     * How busy a texture is: the mean distance of its pixels from their own average colour.
     *
     * <p>Plain deviation rather than a count of distinct colours, because a texture can use many
     * near-identical shades and still read as flat, which is exactly the sort of face that shrinks
     * down well onto a tool head.
     */
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
