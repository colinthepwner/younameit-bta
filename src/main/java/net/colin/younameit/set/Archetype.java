package net.colin.younameit.set;

/**
 * What kind of stuff a material is, and what that entitles it to.
 *
 * <p>Blocks can be judged on hardness and blast resistance. A feather cannot — it has no physical
 * properties to read, so the only honest way to rank it is to recognise what it is. That is what
 * these archetypes are for.
 *
 * <p>The important field is {@link #tierCeiling}. Every archetype that is not positively
 * identified as a hard material tops out at wood, so a mob drop from some mod nobody has ever
 * heard of cannot quietly out-perform a stone pickaxe. {@link #UNKNOWN} is the universal variant:
 * whatever the mod, an unrecognised item lands here and gets conservative numbers rather than a
 * guess that might be generous.
 */
public enum Archetype {

    /** Wool, cloth, feathers, string, leather, paper. Barely holds an edge; cushions a landing. */
    SOFT(0, 0.30, 0.85, 0, 55.0F, 0.55F, 20),

    /** Wheat, reeds, cactus, leaves, vines. */
    PLANT(0, 0.28, 0.90, 0, 8.0F, 0.60F, 15),

    /** Eggs, meat, fruit. Exactly as good a pickaxe as it sounds. */
    FOOD(0, 0.20, 0.80, 0, 5.0F, 0.45F, 8),

    /** Bone, shell, horn, tooth. Brittle but genuinely hard, and it hits harder than it should. */
    BONE(1, 0.75, 1.05, 1, 6.0F, 0.85F, 78),

    /** Glass, ice. Takes a wicked edge and shatters almost immediately. */
    GLASS(1, 0.22, 1.80, 0, 4.0F, 0.70F, 26),

    WOOD(0, 1.00, 1.00, 0, 4.0F, 1.00F, 59),
    STONE(1, 1.00, 1.00, 0, 3.0F, 1.00F, 131),

    /** Anything positively identified as a worked metal. */
    METAL(2, 1.30, 1.15, 0, 3.0F, 1.15F, 250),

    /** Diamonds, emeralds, and the endless modded variations on them. */
    GEM(3, 1.50, 1.30, 1, 3.0F, 1.30F, 1561),

    /**
     * The fallback. Deliberately no better than wood: an unrecognised item is an item we cannot
     * justify ranking, and under-rating it is the only failure mode that is not a balance bug.
     */
    UNKNOWN(0, 0.50, 0.95, 0, 4.0F, 0.75F, 28);

    /** Hard ceiling on mining level, 0 = wood .. 3 = diamond. */
    public final int tierCeiling;
    /** Multiplier applied to the durability the raw numbers would otherwise give. */
    public final double durabilityMultiplier;
    /** Multiplier on mining speed. */
    public final double efficiencyMultiplier;
    /** Flat bonus to melee damage. */
    public final int bonusDamage;
    /** Extra fall-damage protection, in percentage points, on top of the normal scaling. */
    public final float bonusFallProtection;
    /** Multiplier on armour protection generally. */
    public final float protectionMultiplier;
    /**
     * Tool durability for a material with no block behind it, anchored to vanilla.
     *
     * <p>A loose item has no hardness to measure, so its gear is pinned directly to the value the
     * equivalent vanilla tool has: metal to iron's 250, gem to diamond's 1561, stone to 131, wood
     * to 59. That keeps a smelted ingot's pickaxe exactly as good as an iron pickaxe rather than
     * whatever a synthetic toughness score happened to work out to — it was landing around 570
     * for metals, comfortably better than iron, which is not a thing a mod should quietly do.
     */
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

    /**
     * True for archetypes soft enough that they should cap a block's tier too. A wool block has a
     * hardness figure, but no amount of hardness should make wool shears better than stone.
     */
    public boolean capsBlocks() {
        // WOOD belongs here as much as the soft families do. Oak planks have hardness 2.0, the
        // same as cobblestone, so hardness alone cannot tell them apart and planks would come out
        // at stone tier. Wood is wood whatever it weighs.
        return this == SOFT || this == PLANT || this == FOOD || this == WOOD;
    }

    /** Soft, springy materials cushion a fall — the one effect asked for by name. */
    public boolean cushionsFalls() {
        return this == SOFT || this == PLANT;
    }
}
