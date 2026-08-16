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

public final class SetStats {

    private static final int DIAMOND_TOOL_DURABILITY = 1536;
    private static final int DIAMOND_ARMOR_DURABILITY = 800;
    private static final float DIAMOND_COMBAT_PROTECTION = 70.0F;

    public final Archetype archetype;

    public final int tier;
    public final int toolDurability;
    public final int armorDurability;
    public final float efficiency;
    public final int attackDamage;
    public final boolean silkTouch;

    public final float combatProtection;
    public final float blastProtection;
    public final float fireProtection;
    public final float fallProtection;

    public final boolean fuel;
    public final boolean fireResistant;

    public final boolean shiny;

    public final boolean flatColour;

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

    public static SetStats derive(Block<?> block, Item ingredient) {
        Archetype archetype = block != null ? Archetypes.of(block, ingredient) : Archetypes.ofItem(ingredient);

        double score;
        int hardnessTier;
        int cap;

        if (block != null) {
            float hardness = Math.max(0.0F, block.getHardness());

            float resistance = Math.min(Math.max(0.0F, block.blastResistance), 60.0F);
            score = Math.max(0.05, hardness * 0.75 + resistance * 0.25);

            hardnessTier = hardness < 1.0F ? 0
                    : hardness < 3.0F ? 1
                    : hardness < 10.0F ? 2
                    : 3;

            cap = Math.min(3, requiredHarvestLevel(block) + 1);

            if (archetype.capsBlocks()) cap = Math.min(cap, archetype.tierCeiling);
        } else {

            cap = archetype.tierCeiling;
            hardnessTier = cap;
            score = itemScore(archetype, ingredient);
        }

        int tier = block != null
                ? Math.max(0, Math.min(3, Math.min(hardnessTier, cap)))
                : Math.max(0, Math.min(Math.min(hardnessTier, cap), archetype.tierCeiling));

        boolean fuel = isFuel(ingredient);
        boolean fireResistant = isFireResistant(block, ingredient, archetype);
        boolean silkTouch = isGoldLike(block, ingredient);

        double rung;
        if (block != null) {

            rung = tier == 0
                    ? VanillaLadder.floorFraction(score)
                    : 1 + tier + withinTier(score, tier);
        } else {

            rung = itemRung(archetype, ingredient);
        }
        rung = Math.max(0.0, Math.min(4.0, rung));

        int toolDurability = interpTool(VanillaLadder.TOOL_DURABILITY, VanillaLadder.FLOOR_TOOL_DURABILITY, rung);
        float efficiency = interpToolF(VanillaLadder.TOOL_EFFICIENCY, VanillaLadder.FLOOR_TOOL_EFFICIENCY, rung);
        int attackDamage = interpTool(VanillaLadder.TOOL_DAMAGE, VanillaLadder.FLOOR_TOOL_DAMAGE, rung);
        int armorDurability = interpTool(VanillaLadder.ARMOR_DURABILITY, VanillaLadder.FLOOR_ARMOR_DURABILITY, rung);

        toolDurability = (int) Math.round(toolDurability * archetype.durabilityMultiplier);
        efficiency = (float) (efficiency * archetype.efficiencyMultiplier);
        armorDurability = (int) Math.round(armorDurability * archetype.durabilityMultiplier);
        attackDamage += archetype.bonusDamage;

        if (silkTouch) {

            efficiency += 3.0F;
            toolDurability = Math.max(VanillaLadder.FLOOR_TOOL_DURABILITY, toolDurability / 3);
        }

        efficiency = Math.max(VanillaLadder.FLOOR_TOOL_EFFICIENCY, efficiency);
        toolDurability = clamp(toolDurability, VanillaLadder.FLOOR_TOOL_DURABILITY, DIAMOND_TOOL_DURABILITY);
        armorDurability = clamp(armorDurability, VanillaLadder.FLOOR_ARMOR_DURABILITY, DIAMOND_ARMOR_DURABILITY);
        attackDamage = Math.max(VanillaLadder.FLOOR_TOOL_DAMAGE, Math.min(4, attackDamage));

        float combat = interpToolF(VanillaLadder.ARMOR_COMBAT, VanillaLadder.FLOOR_ARMOR_PROTECTION, rung) *
                archetype.protectionMultiplier;
        float blast = interpToolF(VanillaLadder.ARMOR_BLAST, VanillaLadder.FLOOR_ARMOR_PROTECTION, rung) *
                archetype.protectionMultiplier;
        combat = clampF(combat, 2.0F, DIAMOND_COMBAT_PROTECTION);
        blast = clampF(blast, 1.0F, 70.0F);

        float fall = interpToolF(VanillaLadder.ARMOR_FALL, VanillaLadder.FLOOR_ARMOR_PROTECTION, rung) *
                archetype.protectionMultiplier + archetype.bonusFallProtection;
        fall = clampF(fall, 0.0F, 120.0F);

        float fire;
        if (fireResistant && YniConfig.fireResistantSetBonus) {

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

    private static double withinTier(double score, int tier) {

        double lo = tier == 1 ? 9.0 : tier == 2 ? 25.0 : 45.0;
        double hi = tier == 1 ? 25.0 : tier == 2 ? 45.0 : 60.0;
        double t = (score - lo) / (hi - lo);
        return Math.max(0.0, Math.min(0.4, t));
    }

    private static double itemRung(Archetype archetype, Item ingredient) {
        double rung;
        switch (archetype) {

            case GEM:     rung = 4.0; break;
            case METAL:   rung = 3.0; break;
            case STONE:   rung = 2.0; break;
            case WOOD:    rung = 1.0; break;
            case BONE:    rung = 1.5; break;
            case GLASS:   rung = 1.1; break;
            case SOFT:    rung = 0.30; break;
            case PLANT:   rung = 0.22; break;
            case FOOD:    rung = 0.12; break;
            default:      rung = 0.50; break;
        }

        return rung * itemSignalBonus(ingredient);
    }

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
            default:      base = 1.5;  break;
        }
        if (ingredient == null) return base;
        return base * itemSignalBonus(ingredient);
    }

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

        }
        return Math.max(0.9, Math.min(1.25, bonus));
    }

    private static int requiredHarvestLevel(Block<?> block) {
        try {
            int level = ItemToolPickaxe.miningLevels.getOrDefault(block, -1);
            if (level >= 0) return level;
        } catch (Throwable ignored) {

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

        }
        if (block == null) {

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

            return false;
        }
    }

    private static boolean isIcy(Block<?> block, Item ingredient) {
        try {
            if (block != null && block.getMaterial() == net.minecraft.core.block.material.Materials.ICE) {
                return true;
            }
        } catch (Throwable ignored) {

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
