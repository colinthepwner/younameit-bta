package net.colin.younameit.set;

import net.minecraft.core.block.Block;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ArmorMaterial;
import net.minecraft.core.item.material.ToolMaterial;

/**
 * One generated family: the source block, the derived numbers, and the nine items made from it.
 */
public final class MaterialSet {
    public static final int ITEMS_PER_SET = 9;

    /** The block this set is made of, or null when the material is a loose item like a feather. */
    public final Block<?> block;
    /** Sanitised, unique, lowercase id fragment, e.g. {@code minecraft_cobblestone}. */
    public final String id;
    /** The crafting ingredient — the item you actually hold in survival. */
    public final Item ingredient;
    /**
     * Which metadata variant of {@link #ingredient} this set is made of.
     *
     * <p>BTA packs colour and material variants into one block id: there is a single
     * {@code Blocks.WOOL} carrying sixteen colours as metadata, not sixteen blocks. Ignoring that
     * meant every colour of wool produced the same white gear, because both the texture lookup and
     * the recipe were pinned to metadata 0.
     */
    public final int metadata;
    /** True when the source block distinguishes variants by metadata at all. */
    public final boolean hasVariants;
    /**
     * Human name of this variant, or null when the material has only one.
     *
     * <p>Carried separately because a painted block reports the same translated name for every
     * colour it holds — BlockLogic.getLanguageKey ignores its metadata argument — so the colour
     * has to come from the block's own DyeColor mapping instead.
     */
    public final String variantName;
    /**
     * Metadata of another variant of the same material, or -1 when there is none.
     *
     * <p>Exists so a variant can tell whether its material names its own colours, by comparing its
     * resolved name against a sibling. Comparing against metadata 0 does not work: the meta-0
     * variant then compares with itself, always matches, and gets the colour prefixed twice.
     */
    public final int siblingMetadata;

    public final SetStats stats;

    // Filled in by SetRegistrar.
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

    /** The exact stack a player crafts with. */
    public ItemStack ingredientStack() {
        ItemStack stack = new ItemStack(ingredient, 1);
        stack.setMetadata(metadata);
        return stack;
    }

    /** All nine items, in a stable order; entries may be null when a category is disabled. */
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
