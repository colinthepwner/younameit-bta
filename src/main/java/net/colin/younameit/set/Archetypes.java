package net.colin.younameit.set;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemFood;

import java.util.Locale;

public final class Archetypes {
    private Archetypes() {}

    private static final String[] SOFT_WORDS = {
            "wool", "cloth", "fabric", "feather", "down", "string", "silk", "web", "leather",
            "hide", "pelt", "fur", "paper", "cotton", "linen", "cushion", "carpet", "yarn", "fluff"
    };
    private static final String[] PLANT_WORDS = {
            "wheat", "reed", "cactus", "vine", "leaf", "leaves", "sapling", "flower", "grass",
            "seed", "wart", "kelp", "algae", "moss", "straw", "hay", "bamboo", "fiber", "fibre", "root"
    };
    private static final String[] FOOD_WORDS = {
            "egg", "meat", "beef", "pork", "chicken", "fish", "apple", "bread", "cake", "cookie",
            "melon", "berry", "stew", "soup", "cheese", "mushroom"
    };
    private static final String[] BONE_WORDS = {
            "bone", "shell", "horn", "tooth", "fang", "claw", "skull", "chitin", "carapace", "ivory", "antler"
    };
    private static final String[] GLASS_WORDS = {"glass", "ice", "crystal_pane", "pane"};
    private static final String[] METAL_WORDS = {
            "ingot", "iron", "steel", "copper", "bronze", "silver", "gold", "tin", "lead", "zinc",
            "nickel", "platinum", "titanium", "tungsten", "cobalt", "brass", "alloy", "metal",
            "mythril", "mithril", "adamant", "electrum", "aluminum", "aluminium"
    };
    private static final String[] GEM_WORDS = {
            "diamond", "emerald", "ruby", "sapphire", "topaz", "amethyst", "peridot", "tanzanite",
            "malachite", "olivine", "opal", "jade", "garnet", "quartz", "crystal", "gem", "lapis"
    };
    private static final String[] WOOD_WORDS = {"wood", "plank", "log", "stick", "timber", "bark"};
    private static final String[] STONE_WORDS = {
            "stone", "rock", "cobble", "granite", "basalt", "marble", "slate", "limestone",
            "obsidian", "netherrack", "brick", "concrete", "gravel", "sand", "dirt", "clay", "ore"
    };

    public static Archetype of(Block<?> block, Item ingredient) {
        Archetype byMaterial = fromBlockMaterial(block);
        if (byMaterial != null) return byMaterial;
        return ofItem(ingredient);
    }

    public static Archetype ofItem(Item item) {
        String key = keyOf(item);

        if (isOre(key)) return Archetype.STONE;

        if (matches(key, BONE_WORDS)) return Archetype.BONE;
        if (matches(key, GLASS_WORDS)) return Archetype.GLASS;
        if (matches(key, SOFT_WORDS)) return Archetype.SOFT;
        if (matches(key, GEM_WORDS)) return Archetype.GEM;
        if (matches(key, METAL_WORDS)) return Archetype.METAL;
        if (matches(key, PLANT_WORDS)) return Archetype.PLANT;
        if (matches(key, FOOD_WORDS)) return Archetype.FOOD;
        if (matches(key, WOOD_WORDS)) return Archetype.WOOD;
        if (matches(key, STONE_WORDS)) return Archetype.STONE;

        if (item instanceof ItemFood) return Archetype.FOOD;
        return Archetype.UNKNOWN;
    }

    private static Archetype fromBlockMaterial(Block<?> block) {
        if (block == null) return null;
        try {
            Material m = block.getMaterial();
            if (m == null) return null;
            if (m == Materials.CLOTH || m == Materials.WEB) return Archetype.SOFT;
            if (m == Materials.PLANT || m == Materials.VEGETABLE || m == Materials.LEAVES
                    || m == Materials.CACTUS || m == Materials.CORAL) return Archetype.PLANT;
            if (m == Materials.GLASS || m == Materials.ICE) return Archetype.GLASS;
            if (m == Materials.DIAMOND || m == Materials.LAPIS
                    || m == Materials.OLIVINE || m == Materials.QUARTZ) return Archetype.GEM;
            if (m == Materials.METAL || m == Materials.IRON || m == Materials.GOLD
                    || m == Materials.STEEL) return Archetype.METAL;
            if (m == Materials.WOOD) return Archetype.WOOD;
            if (m == Materials.CAKE) return Archetype.FOOD;
            if (m.isStone()) return Archetype.STONE;
            if (m.isMetal()) return Archetype.METAL;
        } catch (Throwable ignored) {

        }
        return null;
    }

    private static String keyOf(Item item) {
        if (item == null) return "";
        try {
            String key = item.getKey();
            if (key != null) return key.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {

        }
        return "";
    }

    private static boolean isOre(String key) {
        return key.equals("ore") || key.endsWith("_ore") || key.contains("_ore_") || key.startsWith("ore_")
                || key.endsWith(".ore") || key.contains(".ore.") || key.startsWith("ore.");
    }

    private static boolean matches(String key, String[] words) {
        for (String w : words) {
            if (key.contains(w)) return true;
        }
        return false;
    }
}
