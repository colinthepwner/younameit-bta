package net.colin.younameit.set;

public final class VanillaLadder {
    private VanillaLadder() {}

    public static final int[] TOOL_DURABILITY = {64, 128, 384, 1536};
    public static final float[] TOOL_EFFICIENCY = {2.0F, 4.0F, 6.0F, 14.0F};

    public static final int[] TOOL_DAMAGE = {0, 0, 0, 4};

    public static final int[] ARMOR_DURABILITY = {150, 220, 380, 800};

    public static final float[] ARMOR_COMBAT = {20.0F, 32.0F, 45.0F, 66.0F};
    public static final float[] ARMOR_BLAST = {20.0F, 32.0F, 45.0F, 66.0F};
    public static final float[] ARMOR_FALL = {28.0F, 34.0F, 45.0F, 66.0F};

    public static final int FLOOR_TOOL_DURABILITY = 3;
    public static final float FLOOR_TOOL_EFFICIENCY = 1.15F;
    public static final int FLOOR_TOOL_DAMAGE = -1;
    public static final int FLOOR_ARMOR_DURABILITY = 30;
    public static final float FLOOR_ARMOR_PROTECTION = 6.0F;

    public static final double WOOD_SCORE = 2.75;

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
