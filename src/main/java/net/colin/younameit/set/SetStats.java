package net.colin.younameit.set;

import net.colin.younameit.YniConfig;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.block.tag.BlockTags;
import net.minecraft.core.crafting.LookupFuelFurnace;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.tag.ItemTags;
import net.minecraft.core.item.tool.ItemToolPickaxe;

/**
 * Turns a material into tool and armour numbers.
 *
 * <p>Two routes in. A <b>block</b> has real physical properties, so hardness and blast resistance
 * drive it and the anchors are vanilla: cobblestone lands near stone tools, an iron block near
 * iron, obsidian near diamond. A plain <b>item</b> has no such properties — there is no "hardness
 * of a feather" — so its {@link Archetype} decides everything instead.
 *
 * <p>Either way nothing is allowed to exceed diamond, and anything not positively identified as a
 * hard material is capped at wood. That is the guard that stops a random mob drop, from this game
 * or any mod, coming out better than a stone pickaxe.
 */
public final class SetStats {

    private static final int DIAMOND_TOOL_DURABILITY = 1536;
    private static final int DIAMOND_ARMOR_DURABILITY = 800;
    private static final float DIAMOND_COMBAT_PROTECTION = 70.0F;

    public final Archetype archetype;
    /** 0 = wood, 1 = stone, 2 = iron, 3 = diamond. Never above 3. */
    public final int tier;
    public final int toolDurability;
    public final int armorDurability;
    public final float efficiency;
    public final int attackDamage;
    public final boolean silkTouch;

    /** Armour protection percentages, 0..100 — BTA divides these by 100 internally. */
    public final float combatProtection;
    public final float blastProtection;
    public final float fireProtection;
    public final float fallProtection;

    public final boolean fuel;
    public final boolean fireResistant;
    /** Whether gear made of this catches the light — see {@link #isShiny}. */
    public final boolean shiny;
    /**
     * Paint the gear in flat material colour with no source texture layered over it.
     *
     * <p>For a mineral that drops straight out of stone and is never smelted — a diamond, a lapis
     * lazuli, a ruby from some mod — scattering the gem sprite across a pickaxe head reads as a
     * pile of gravel rather than as a gem tool. Vanilla draws diamond gear as flat cyan with
     * shading, and that is what these should look like. Block-backed gems keep their texture,
     * because a block face is a real tile that tiles properly.
     */
    public final boolean flatColour;
    /**
     * Boots of this material freeze water underfoot, at a slow cost to their own durability.
     *
     * <p>Keyed on the block material being ICE rather than only on the name, so a modded glacier
     * block qualifies without being called anything in particular; the name test is a fallback for
     * loose items, which have no block material to consult.
     */
    public final boolean freezesWater;
    public final double score;

    private SetStats(Archetype archetype, int tier, int toolDurability, int armorDurability, float efficiency,
                     int attackDamage, boolean silkTouch, float combatProtection, float blastProtection,
                     float fireProtection, float fallProtection, boolean fuel, boolean fireResistant,
                     boolean shiny, boolean flatColour, boolean freezesWater, double score) {
        this.archetype = archetype;
        this.tier = tier;
        this.toolDurability = toolDurability;
        this.armorDurability = armorDurability;
        this.efficiency = efficiency;
        this.attackDamage = attackDamage;
        this.silkTouch = silkTouch;
        this.combatProtection = combatProtection;
        this.blastProtection = blastProtection;
        this.fireProtection = fireProtection;
        this.fallProtection = fallProtection;
        this.fuel = fuel;
        this.fireResistant = fireResistant;
        this.shiny = shiny;
        this.flatColour = flatColour;
        this.freezesWater = freezesWater;
        this.score = score;
    }

    /** @param block may be null, in which case the material is a plain item. */
    public static SetStats derive(Block<?> block, Item ingredient) {
        Archetype archetype = block != null ? Archetypes.of(block, ingredient) : Archetypes.ofItem(ingredient);

        double score;
        int hardnessTier;
        int cap;

        if (block != null) {
            float hardness = Math.max(0.0F, block.getHardness());
            // Obsidian's resistance runs to 6000; clamp so it informs the curve without dominating.
            float resistance = Math.min(Math.max(0.0F, block.blastResistance), 60.0F);
            score = Math.max(0.05, hardness * 0.75 + resistance * 0.25);

            hardnessTier = hardness < 1.0F ? 0
                    : hardness < 3.0F ? 1
                    : hardness < 10.0F ? 2
                    : 3;
            // Hardness proposes, the harvest requirement disposes: a block you can mine
            // bare-handed can still make stone-tier gear, but nothing that needs only a wooden
            // pickaxe reaches iron.
            cap = Math.min(3, requiredHarvestLevel(block) + 1);
            // A soft archetype overrides both. Wool has a hardness number; it is still wool.
            if (archetype.capsBlocks()) cap = Math.min(cap, archetype.tierCeiling);
        } else {
            // No physical properties to read. The archetype is the whole story, and its ceiling
            // is also its tier — an item cannot argue its way up the way a block can.
            cap = archetype.tierCeiling;
            hardnessTier = cap;
            score = itemScore(archetype, ingredient);
        }

        // For a block, `cap` already folds in the archetype ceiling where it should apply (the
        // soft families, via capsBlocks above). Applying the ceiling a second time here would
        // hold every stone-material block to tier 1 — obsidian included, despite hardness 50 and
        // needing a diamond pickaxe to mine — which is not what "hardness decides, harvest caps"
        // is supposed to mean. A loose item has no physical properties to appeal to, so there the
        // ceiling really is the last word.
        int tier = block != null
                ? Math.max(0, Math.min(3, Math.min(hardnessTier, cap)))
                : Math.max(0, Math.min(Math.min(hardnessTier, cap), archetype.tierCeiling));

        boolean fuel = isFuel(ingredient);
        boolean fireResistant = isFireResistant(block, ingredient, archetype);
        boolean silkTouch = isGoldLike(block, ingredient);

        // ---- place the material on BTA's own ladder -----------------------------------------
        // Everything below is interpolation between real vanilla rows rather than a fitted curve
        // with correction factors stacked on top. A material either sits on a vanilla row, sits
        // between two of them, or sits between the floor and wood; nothing can drift off the
        // ladder entirely, which is what kept happening before.
        double rung;
        if (block != null) {
            // Below wood, position is how close the block's toughness gets to plank-grade.
            // At or above it, the tier decides the row and score nudges within it.
            // rung 0 = the floor, 1 = wood, 2 = stone, 3 = iron, 4 = diamond. A tier-N material
            // must land ON row N, so tier 1 is rung 2 and not rung 1 — writing `tier + fraction`
            // put every material one row low and left obsidian, a tier-3 block, on iron's row.
            rung = tier == 0
                    ? VanillaLadder.floorFraction(score)
                    : 1 + tier + withinTier(score, tier);
        } else {
            // A loose item has no toughness to measure, so its archetype names the row directly.
            rung = itemRung(archetype, ingredient);
        }
        rung = Math.max(0.0, Math.min(4.0, rung));

        int toolDurability = interpTool(VanillaLadder.TOOL_DURABILITY, VanillaLadder.FLOOR_TOOL_DURABILITY, rung);
        float efficiency = interpToolF(VanillaLadder.TOOL_EFFICIENCY, VanillaLadder.FLOOR_TOOL_EFFICIENCY, rung);
        int attackDamage = interpTool(VanillaLadder.TOOL_DAMAGE, VanillaLadder.FLOOR_TOOL_DAMAGE, rung);
        int armorDurability = interpTool(VanillaLadder.ARMOR_DURABILITY, VanillaLadder.FLOOR_ARMOR_DURABILITY, rung);

        // Archetype flavour, applied narrowly so it colours the row instead of leaving it.
        toolDurability = (int) Math.round(toolDurability * archetype.durabilityMultiplier);
        efficiency = (float) (efficiency * archetype.efficiencyMultiplier);
        armorDurability = (int) Math.round(armorDurability * archetype.durabilityMultiplier);
        attackDamage += archetype.bonusDamage;

        if (silkTouch) {
            // Gold's bargain in BTA: silk touch and speed, bought with durability.
            efficiency += 3.0F;
            toolDurability = Math.max(VanillaLadder.FLOOR_TOOL_DURABILITY, toolDurability / 3);
        }

        // Bare hands mine at 1.0; a tool slower than no tool is merely confusing.
        efficiency = Math.max(VanillaLadder.FLOOR_TOOL_EFFICIENCY, efficiency);
        toolDurability = clamp(toolDurability, VanillaLadder.FLOOR_TOOL_DURABILITY, DIAMOND_TOOL_DURABILITY);
        armorDurability = clamp(armorDurability, VanillaLadder.FLOOR_ARMOR_DURABILITY, DIAMOND_ARMOR_DURABILITY);
        attackDamage = Math.max(VanillaLadder.FLOOR_TOOL_DAMAGE, Math.min(4, attackDamage));

        // Armour protection rides the same ladder, so a stone-grade block gives stone-grade
        // plating rather than whatever a separate curve happened to produce.
        float combat = interpToolF(VanillaLadder.ARMOR_COMBAT, VanillaLadder.FLOOR_ARMOR_PROTECTION, rung)
                * archetype.protectionMultiplier;
        float blast = interpToolF(VanillaLadder.ARMOR_BLAST, VanillaLadder.FLOOR_ARMOR_PROTECTION, rung)
                * archetype.protectionMultiplier;
        combat = clampF(combat, 2.0F, DIAMOND_COMBAT_PROTECTION);
        blast = clampF(blast, 1.0F, 70.0F);

        // Soft, springy materials cushion a landing. Vanilla leather carries 120% fall protection
        // against its 20% combat, so a big bonus here is in keeping rather than out of character.
        float fall = interpToolF(VanillaLadder.ARMOR_FALL, VanillaLadder.FLOOR_ARMOR_PROTECTION, rung)
                * archetype.protectionMultiplier + archetype.bonusFallProtection;
        fall = clampF(fall, 0.0F, 120.0F);

        float fire;
        if (fireResistant && YniConfig.fireResistantSetBonus) {
            // 100 across all four pieces sums to exactly 1.0 in getTotalProtectionAmount, i.e. a
            // complete set. BTA still floors damage at 1%, so PlayerFireSetMixin does the rest.
            fire = 100.0F;
        } else if (fuel && YniConfig.fuelReducesFireDamage) {
            fire = clampF(combat * 0.9F + 20.0F, 20.0F, 80.0F);
        } else {
            fire = clampF(combat * 0.6F, 0.0F, 45.0F);
        }

        return new SetStats(archetype, tier, toolDurability, armorDurability, efficiency, attackDamage,
                silkTouch, combat, blast, fire, fall, fuel, fireResistant, isShiny(block, archetype),
                block == null && archetype == Archetype.GEM, isIcy(block, ingredient), score);
    }

    /**
     * Where a material sits between its own tier's row and the next one up, 0 to just under 1.
     *
     * <p>Bounded well short of 1 so a material can be the best of its tier without quietly
     * becoming the next tier: a very hard stone block should beat cobblestone and still lose to
     * iron.
     */
    private static double withinTier(double score, int tier) {
        // Each band STARTS at the score of that tier's canonical material, so the canonical case
        // sits exactly on the vanilla row: cobblestone scores about 9 and must come out as stone
        // 128/4.0, not part-way to iron. Only a block notably tougher than the tier's namesake
        // climbs above it, and never by more than 0.4 of the way to the next row.
        double lo = tier == 1 ? 9.0 : tier == 2 ? 25.0 : 45.0;
        double hi = tier == 1 ? 25.0 : tier == 2 ? 45.0 : 60.0;
        double t = (score - lo) / (hi - lo);
        return Math.max(0.0, Math.min(0.4, t));
    }

    /** The ladder position an item occupies, named by its archetype rather than measured. */
    private static double itemRung(Archetype archetype, Item ingredient) {
        double rung;
        switch (archetype) {
            // Same scale as blocks: 1 = wood, 2 = stone, 3 = iron, 4 = diamond.
            case GEM:     rung = 4.0; break;
            case METAL:   rung = 3.0; break;
            case STONE:   rung = 2.0; break;
            case WOOD:    rung = 1.0; break;
            case BONE:    rung = 1.5; break;
            case GLASS:   rung = 1.1; break;
            case SOFT:    rung = 0.30; break;
            case PLANT:   rung = 0.22; break;
            case FOOD:    rung = 0.12; break;
            default:      rung = 0.50; break;  // UNKNOWN: below wood, deliberately.
        }
        // Generic signals nudge it, never enough to change which row it belongs to.
        return rung * itemSignalBonus(ingredient);
    }

    /** Interpolates the floor value up through the vanilla rows at the given ladder position. */
    private static int interpTool(int[] rows, int floor, double rung) {
        if (rung <= 1.0) return VanillaLadder.lerp(floor, rows[0], rung);
        int i = (int) Math.floor(rung) - 1;
        if (i >= rows.length - 1) return rows[rows.length - 1];
        return VanillaLadder.lerp(rows[i], rows[i + 1], rung - Math.floor(rung));
    }

    private static float interpToolF(float[] rows, float floor, double rung) {
        if (rung <= 1.0) return VanillaLadder.lerp(floor, rows[0], rung);
        int i = (int) Math.floor(rung) - 1;
        if (i >= rows.length - 1) return rows[rows.length - 1];
        return VanillaLadder.lerp(rows[i], rows[i + 1], rung - Math.floor(rung));
    }

    /**
     * A stand-in "toughness" for an item, built only from things the game can actually tell us.
     *
     * <p>The archetype ceiling sets the range; within it, an item that stacks small or carries its
     * own durability reads as more substantial than one that stacks to 64.
     */
    private static double itemScore(Archetype archetype, Item ingredient) {
        double base;
        switch (archetype) {
            case GEM:     base = 34.0; break;
            case METAL:   base = 14.0; break;
            case STONE:   base = 6.0;  break;
            case BONE:    base = 4.5;  break;
            case WOOD:    base = 3.0;  break;
            case GLASS:   base = 2.0;  break;
            case SOFT:    base = 1.2;  break;
            case PLANT:   base = 1.0;  break;
            case FOOD:    base = 0.6;  break;
            default:      base = 1.5;  break;  // UNKNOWN — the universal variant.
        }
        if (ingredient == null) return base;
        return base * itemSignalBonus(ingredient);
    }

    /**
     * A small multiplier from what the game can tell us generically: an item that stacks small or
     * carries its own durability reads as more substantial than one that stacks to 64. Kept
     * narrow (0.9x to 1.25x) so it colours the anchor rather than overriding it.
     */
    private static double itemSignalBonus(Item ingredient) {
        if (ingredient == null) return 1.0;
        double bonus = 1.0;
        try {
            int stack = ingredient.getItemStackLimit(new ItemStack(ingredient));
            if (stack > 0 && stack <= 1) bonus *= 1.25;
            else if (stack <= 16) bonus *= 1.10;
            else if (stack >= 64) bonus *= 0.95;
            if (ingredient.getMaxDamage() > 0) bonus *= 1.10;
        } catch (Throwable ignored) {
            // Generic signals are a bonus, not a requirement.
        }
        return Math.max(0.9, Math.min(1.25, bonus));
    }

    /**
     * What pickaxe level a block needs. BTA keeps these in a public map on ItemToolPickaxe and
     * falls back to the mineable tag, so absent means "no special tool required".
     */
    private static int requiredHarvestLevel(Block<?> block) {
        try {
            int level = ItemToolPickaxe.miningLevels.getOrDefault(block, -1);
            if (level >= 0) return level;
        } catch (Throwable ignored) {
            // Map shape could change between BTA versions; a missing level just means tier 0.
        }
        return 0;
    }

    private static boolean isFuel(Item ingredient) {
        if (ingredient == null) return false;
        try {
            return LookupFuelFurnace.instance.getFuelYield(new ItemStack(ingredient)) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFireResistant(Block<?> block, Item ingredient, Archetype archetype) {
        try {
            if (ingredient != null && ingredient.hasTag(ItemTags.IS_FIRE_PROOF)) return true;
        } catch (Throwable ignored) {
            // Tag lookups can fail on odd modded items; fall through to the material check.
        }
        if (block == null) {
            // Only materials that plainly do not burn qualify without a block to inspect.
            return archetype == Archetype.METAL || archetype == Archetype.STONE || archetype == Archetype.GEM;
        }
        try {
            Material m = block.getMaterial();
            if (m == null || m.isFlammable()) return false;
            if (block.hasTag(BlockTags.INFINITE_BURN)) return true;
            return m.isStone() || m.isMetal();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Whether gear made of this should carry a specular highlight.
     *
     * <p>For a block the break sound is the giveaway and costs nothing to read: BTA already
     * classifies every block as metal, glass, crystal, stone, wood, sand and so on, and a modded
     * block that picks a sensible sound is classified correctly without knowing this mod exists.
     * Gems shine by definition.
     *
     * <p>For a loose item there is no sound to consult, so the {@link Archetype} stands in —
     * which is the same judgement made from the item's identity rather than its physics. Anything
     * unrecognised comes back matte, so an unknown modded drop is never gratuitously blingy.
     */
    private static boolean isShiny(Block<?> block, Archetype archetype) {
        if (archetype == Archetype.GEM || archetype == Archetype.METAL || archetype == Archetype.GLASS) {
            return true;
        }
        if (block == null) return false;
        try {
            net.minecraft.core.sound.BlockSound sound = block.getSound();
            return sound == net.minecraft.core.sound.BlockSounds.METAL
                    || sound == net.minecraft.core.sound.BlockSounds.GLASS
                    || sound == net.minecraft.core.sound.BlockSounds.CRYSTAL;
        } catch (Throwable ignored) {
            // No sound to read means no reason to think it shines.
            return false;
        }
    }

    /**
     * Whether this material is cold enough to freeze water underfoot.
     *
     * <p>The block material is the reliable half: anything BTA built out of ICE qualifies, modded
     * glaciers included, with no naming convention required. The name test covers loose items,
     * which have no block material to ask, and is matched on word boundaries so "juice", "spice"
     * and "police" do not come out cryogenic.
     */
    private static boolean isIcy(Block<?> block, Item ingredient) {
        try {
            if (block != null && block.getMaterial() == net.minecraft.core.block.material.Materials.ICE) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to the name test.
        }
        String key = block != null && block.getKey() != null ? block.getKey().toLowerCase()
                : ingredient != null && ingredient.getKey() != null ? ingredient.getKey().toLowerCase() : "";
        return key.equals("ice") || key.endsWith("_ice") || key.contains("_ice_") || key.startsWith("ice_")
                || key.endsWith(".ice") || key.contains(".ice.") || key.contains("icicle")
                || key.contains("frost") || key.contains("glacier") || key.contains("permafrost");
    }

    private static boolean isGoldLike(Block<?> block, Item ingredient) {
        String key = block == null || block.getKey() == null ? "" : block.getKey().toLowerCase();
        String ing = ingredient == null || ingredient.getKey() == null ? "" : ingredient.getKey().toLowerCase();
        return key.contains("gold") || ing.contains("gold");
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
