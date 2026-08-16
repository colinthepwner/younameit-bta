package net.colin.younameit.set;

public enum Archetype {

    SOFT(0, 0.30, 0.85, 0, 55.0F, 0.55F, 20),

    PLANT(0, 0.28, 0.90, 0, 8.0F, 0.60F, 15),

    FOOD(0, 0.20, 0.80, 0, 5.0F, 0.45F, 8),

    BONE(1, 0.75, 1.05, 1, 6.0F, 0.85F, 78),

    GLASS(1, 0.22, 1.80, 0, 4.0F, 0.70F, 26),

    WOOD(0, 1.00, 1.00, 0, 4.0F, 1.00F, 59),
    STONE(1, 1.00, 1.00, 0, 3.0F, 1.00F, 131),

    METAL(2, 1.30, 1.15, 0, 3.0F, 1.15F, 250),

    GEM(3, 1.50, 1.30, 1, 3.0F, 1.30F, 1561),

    UNKNOWN(0, 0.50, 0.95, 0, 4.0F, 0.75F, 28);

    public final int tierCeiling;

    public final double durabilityMultiplier;

    public final double efficiencyMultiplier;

    public final int bonusDamage;

    public final float bonusFallProtection;

    public final float protectionMultiplier;

    public final int itemToolDurability;

    Archetype(int tierCeiling, double durabilityMultiplier, double efficiencyMultiplier,
              int bonusDamage, float bonusFallProtection, float protectionMultiplier,
              int itemToolDurability) {
        this.tierCeiling = tierCeiling;
        this.durabilityMultiplier = durabilityMultiplier;
        this.efficiencyMultiplier = efficiencyMultiplier;
        this.bonusDamage = bonusDamage;
        this.bonusFallProtection = bonusFallProtection;
        this.protectionMultiplier = protectionMultiplier;
        this.itemToolDurability = itemToolDurability;
    }

    public boolean capsBlocks() {

        return this == SOFT || this == PLANT || this == FOOD || this == WOOD;
    }

    public boolean cushionsFalls() {
        return this == SOFT || this == PLANT;
    }
}
