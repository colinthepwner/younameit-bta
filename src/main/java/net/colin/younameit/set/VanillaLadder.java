package net.colin.younameit.set;

/**
 * BTA's own tool and armour numbers, and the floor that sits below them.
 *
 * <p>Read directly out of {@code ToolMaterial} and {@code ArmorMaterial} in 8.0.1 rather than
 * remembered from modern Minecraft, because almost none of them match. Wooden tools mine at
 * <b>2.0</b>, not 4.0; iron lasts 384, not 250; and only diamond carries a melee bonus at all,
 * so a stone sword hits exactly as hard as a wooden one.
 *
 * <p>That mattered more than any single balance figure. The old derivation started every material
 * at efficiency 2.0 and added from there, which meant the weakest imaginable material mined at
 * exactly wooden speed and everything above it beat wood outright. No amount of adjusting the
 * multipliers on top could fix that, because the floor itself was in the wrong place.
 */
public final class VanillaLadder {
    private VanillaLadder() {}

    /** Index is mining tier: 0 wood, 1 stone, 2 iron, 3 diamond. */
    public static final int[] TOOL_DURABILITY = {64, 128, 384, 1536};
    public static final float[] TOOL_EFFICIENCY = {2.0F, 4.0F, 6.0F, 14.0F};
    /** Only diamond has one. wood, stone, iron and steel are all 0. */
    public static final int[] TOOL_DAMAGE = {0, 0, 0, 4};

    /**
     * Armour durability by tier. Vanilla's own figures are not monotonic (leather 180, gold 120,
     * iron 200, chainmail 240, diamond 800, steel 1200), so this is a monotonic ladder drawn
     * through them rather than a copy of any one row.
     */
    public static final int[] ARMOR_DURABILITY = {150, 220, 380, 800};
    /** Percentages, matching the scale ArmorMaterial uses (leather 20, iron 45, diamond 66). */
    public static final float[] ARMOR_COMBAT = {20.0F, 32.0F, 45.0F, 66.0F};
    public static final float[] ARMOR_BLAST = {20.0F, 32.0F, 45.0F, 66.0F};
    public static final float[] ARMOR_FALL = {28.0F, 34.0F, 45.0F, 66.0F};

    // ---- the floor, below wood -------------------------------------------------------------
    // Where an infinitely abundant material lands. Bare hands mine at 1.0, so 1.15 is "barely
    // better than nothing", and 3 uses is a handful of blocks before it breaks. Dirt and gravel
    // have to sit here or there is never a reason to cut a plank.
    public static final int FLOOR_TOOL_DURABILITY = 3;
    public static final float FLOOR_TOOL_EFFICIENCY = 1.15F;
    public static final int FLOOR_TOOL_DAMAGE = -1;
    public static final int FLOOR_ARMOR_DURABILITY = 30;
    public static final float FLOOR_ARMOR_PROTECTION = 6.0F;

    /**
     * The toughness score at which a block is considered a full wooden-grade material. Oak planks
     * sit at roughly this value, which is what makes plank-equivalent blocks land exactly on the
     * vanilla wood row.
     */
    public static final double WOOD_SCORE = 2.75;

    /**
     * How a sub-wood material approaches wood as its score rises.
     *
     * <p>Deliberately steep. A linear ramp puts dirt, at a fifth of wood's score, a fifth of the
     * way to wooden gear, which is far too generous for something you can dig up forever.
     */
    public static double floorFraction(double score) {
        double f = Math.max(0.0, Math.min(1.0, score / WOOD_SCORE));
        return Math.pow(f, 2.8);
    }

    public static float lerp(float from, float to, double t) {
        return (float) (from + (to - from) * Math.max(0.0, Math.min(1.0, t)));
    }

    public static int lerp(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * Math.max(0.0, Math.min(1.0, t)));
    }
}
