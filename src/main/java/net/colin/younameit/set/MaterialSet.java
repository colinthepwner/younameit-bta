package net.colin.younameit.set;

import net.minecraft.core.block.Block;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ArmorMaterial;
import net.minecraft.core.item.material.ToolMaterial;

public final class MaterialSet {
    public static final int ITEMS_PER_SET = 9;

    public final Block<?> block;

    public final String id;

    public final Item ingredient;

    public final int metadata;

    public final boolean hasVariants;

    public final String variantName;

    public final int siblingMetadata;

    public final SetStats stats;

    public ToolMaterial toolMaterial;
    public ArmorMaterial armorMaterial;
    public Item pickaxe, axe, shovel, hoe, sword;
    public ItemArmor<?> helmet, chestplate, leggings, boots;

    public MaterialSet(Block<?> block, String id, Item ingredient, int metadata, boolean hasVariants,
                       String variantName, int siblingMetadata, SetStats stats) {
        this.block = block;
        this.id = id;
        this.ingredient = ingredient;
        this.metadata = metadata;
        this.hasVariants = hasVariants;
        this.variantName = variantName;
        this.siblingMetadata = siblingMetadata;
        this.stats = stats;
    }

    public ItemStack ingredientStack() {
        ItemStack stack = new ItemStack(ingredient, 1);
        stack.setMetadata(metadata);
        return stack;
    }

    public Item[] allItems() {
        return new Item[]{pickaxe, axe, shovel, hoe, sword, helmet, chestplate, leggings, boots};
    }

    public Item[] armorItems() {
        return new Item[]{helmet, chestplate, leggings, boots};
    }

    @Override
    public String toString() {
        return "MaterialSet[" + id + " tier=" + stats.tier + " dur=" + stats.toolDurability + "]";
    }
}
