package net.colin.younameit.recipe;

import net.colin.younameit.YouNameIt;
import net.colin.younameit.item.YniGear;
import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.data.registry.Registries;
import net.minecraft.core.data.registry.recipe.RecipeEntryBase;
import net.minecraft.core.data.registry.recipe.RecipeSymbol;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ToolMaterial;
import net.minecraft.core.item.tool.ItemTool;
import net.minecraft.core.item.tool.ItemToolSword;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BlockMaterialUpgrade {
    private BlockMaterialUpgrade() {}

    private static final double DURABILITY_BONUS = 2.5;
    private static final int DIAMOND_TOOL_DURABILITY = 1536;

    static int apply(List<MaterialSet> sets) {
        Map<Integer, ToolMaterial> gearMaterialByItem = new HashMap<>();
        Map<Integer, Integer> componentOfBlock = new HashMap<>();

        try {
            for (RecipeEntryBase<?, ?, ?> entry : Registries.RECIPES.getAllCraftingRecipes()) {
                Object output = entry.getOutput();
                if (!(output instanceof ItemStack result) || result.getItem() == null) continue;
                Item resultItem = result.getItem();

                if (resultItem instanceof YniGear) continue;

                ToolMaterial material = materialOf(resultItem);
                if (material != null) {
                    for (Item input : YniRecipes.inputsOfStatic(entry.getInput())) {
                        if (input != null) gearMaterialByItem.putIfAbsent(input.id, material);
                    }
                    continue;
                }

                Integer component = nineOfOne(entry.getInput());
                if (component != null && result.stackSize == 1 && component != resultItem.id) {
                    componentOfBlock.putIfAbsent(resultItem.id, component);
                }
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.warn("Could not scan recipes for storage-block materials.", t);
            return 0;
        }

        int upgraded = 0;
        for (MaterialSet set : sets) {
            if (set.ingredient == null || set.toolMaterial == null) continue;
            Integer component = componentOfBlock.get(set.ingredient.id);
            if (component == null) continue;
            ToolMaterial base = gearMaterialByItem.get(component);
            if (base == null) continue;

            try {
                int durability = (int) Math.min(DIAMOND_TOOL_DURABILITY,
                        Math.round(base.getDurability() * DURABILITY_BONUS));

                set.toolMaterial
                        .setMiningLevel(base.getMiningLevel())
                        .setEfficiency(base.getEfficiency(false), base.getEfficiency(true))
                        .setDamage(base.getDamage())
                        .setDurability(durability);

                for (Item item : set.allItems()) {
                    if (item instanceof YniGear gear) {
                        gear.yniSetDurability(item instanceof ItemArmor
                                ? Math.max(3, durability / 20)
                                : durability);
                    }
                }

                YouNameIt.LOGGER.info("  {} now mines like its component: level {}, speed {}, durability {}.",
                        set.id, base.getMiningLevel(),
                        String.format("%.2f", base.getEfficiency(false)), durability);
                upgraded++;
            } catch (Throwable t) {
                YouNameIt.LOGGER.debug("Could not upgrade '{}' to its component's material.", set.id, t);
            }
        }
        return upgraded;
    }

    private static ToolMaterial materialOf(Item item) {
        try {
            if (item instanceof ItemTool tool) return tool.getMaterial();
            if (item instanceof ItemToolSword) {

                return null;
            }
        } catch (Throwable ignored) {

        }
        return null;
    }

    private static Integer nineOfOne(Object input) {
        if (!(input instanceof RecipeSymbol[] symbols) || symbols.length != 9) return null;
        Integer id = null;
        for (RecipeSymbol symbol : symbols) {
            if (symbol == null) return null;
            ItemStack stack = symbol.getStack();
            if (stack == null || stack.getItem() == null) return null;
            if (id == null) id = stack.getItem().id;
            else if (id != stack.getItem().id) return null;
        }
        return id;
    }
}
