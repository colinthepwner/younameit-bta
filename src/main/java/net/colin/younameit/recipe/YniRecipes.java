package net.colin.younameit.recipe;

import net.colin.younameit.YniConfig;
import net.colin.younameit.YouNameIt;
import net.colin.younameit.set.Archetype;
import net.colin.younameit.set.Archetypes;
import net.colin.younameit.item.YniGear;
import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.data.registry.Registries;
import net.minecraft.core.data.registry.recipe.RecipeEntryBase;
import net.minecraft.core.data.registry.recipe.RecipeSymbol;
import net.minecraft.core.data.registry.recipe.entry.RecipeEntryCraftingShaped;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.tool.ItemToolAxe;
import net.minecraft.core.item.tool.ItemToolHoe;
import net.minecraft.core.item.tool.ItemToolPickaxe;
import net.minecraft.core.item.tool.ItemToolShovel;
import net.minecraft.core.item.tool.ItemToolSword;
import turniplabs.halplibe.helper.RecipeBuilder;
import turniplabs.halplibe.util.RecipeEntrypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YniRecipes implements RecipeEntrypoint {

    private static List<MaterialSet> sets = Collections.emptyList();

    public static void setSets(List<MaterialSet> value) {
        sets = value;
    }

    @Override
    public void initNamespaces() {
        RecipeBuilder.initNameSpace(YouNameIt.MOD_ID);
    }

    @Override
    public void onRecipesReady() {
        if (!YniConfig.generateRecipes || sets.isEmpty()) return;

        Map<Integer, Set<String>> alreadyCraftable = piecesWithExistingGear();
        Set<Integer> mustBeSmelted = materialsThatMustBeSmelted();

        int made = 0, failed = 0, skipped = 0, unsmelted = 0;
        for (MaterialSet set : sets) {
            if (set.ingredient != null && mustBeSmelted.contains(set.ingredient.id)) {
                unsmelted++;
                continue;
            }
            Set<String> taken = set.ingredient == null
                    ? Collections.emptySet()
                    : alreadyCraftable.getOrDefault(set.ingredient.id, Collections.emptySet());
            try {
                int before = made;
                made += register(set, taken);

                skipped += Math.max(0, expectedPieceCount() - (made - before));
            } catch (Throwable t) {
                failed++;
                YouNameIt.LOGGER.debug("Could not register recipes for '{}'.", set.id, t);
            }
        }

        try {
            Registries.RECIPES.invalidateCaches();
            if (Registries.RECIPES_LOCAL_COPY != null && Registries.RECIPES_LOCAL_COPY != Registries.RECIPES) {
                Registries.RECIPES_LOCAL_COPY.invalidateCaches();
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.warn("Could not invalidate the recipe cache; recipes may not be craftable.", t);
        }

        YouNameIt.LOGGER.info(
                "Registered {} recipes; deferred {} piece(s) to a recipe that already makes them and "
                        + "skipped {} material(s) that must be smelted first{}.",
                made, skipped, unsmelted, failed > 0 ? " (" + failed + " sets failed)" : "");

        int upgraded = BlockMaterialUpgrade.apply(sets);
        if (upgraded > 0) {
            YouNameIt.LOGGER.info("Matched {} storage block(s) to the gear power of what they are made of.", upgraded);
        }

        logVariantNames();
        verifyRegistration();
    }

    private void logVariantNames() {
        try {
            int shown = 0;
            for (MaterialSet set : sets) {
                if (set.variantName == null || set.pickaxe == null) continue;
                YouNameIt.LOGGER.info("Variant name check: {} -> \"{}\"",
                        set.id, set.pickaxe.getTranslatedName(new ItemStack(set.pickaxe)));
                if (++shown >= 4) break;
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.debug("Variant name check failed.", t);
        }
    }

    private void verifyRegistration() {
        try {
            int ours = 0;
            RecipeEntryCraftingShaped sample = null;
            for (RecipeEntryBase<?, ?, ?> entry : Registries.RECIPES.getAllCraftingRecipes()) {
                Object out = entry.getOutput();
                if (!(out instanceof ItemStack stack) || !(stack.getItem() instanceof YniGear)) continue;
                ours++;
                if (sample == null && entry instanceof RecipeEntryCraftingShaped shaped) sample = shaped;
            }
            YouNameIt.LOGGER.info("Recipe check: {} of our recipes are in the crafting registry.", ours);

            if (sample != null) {
                StringBuilder sb = new StringBuilder();
                Object in = sample.getInput();
                if (in instanceof RecipeSymbol[] symbols) {
                    for (RecipeSymbol s : symbols) {
                        if (s == null) {
                            sb.append("[empty] ");
                            continue;
                        }
                        ItemStack st = s.getStack();
                        sb.append(st == null
                                ? "[group " + s.getItemGroup() + "] "
                                : "[id=" + st.itemID + " meta=" + st.getMetadata() + "] ");
                    }
                }
                ItemStack out = (ItemStack) sample.getOutput();
                YouNameIt.LOGGER.info("Recipe check: sample '{}' {}x{} -> {} ; inputs: {}",
                        out.getItem().getKey(), sample.recipeWidth, sample.recipeHeight,
                        out.itemID, sb.toString().trim());
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.warn("Recipe check failed.", t);
        }
    }

    private Set<Integer> materialsThatMustBeSmelted() {
        Set<Integer> out = new HashSet<>();
        try {

            Set<Integer> craftingIngredients = new HashSet<>();
            for (RecipeEntryBase<?, ?, ?> entry : Registries.RECIPES.getAllCraftingRecipes()) {
                Object output = entry.getOutput();
                if (output instanceof ItemStack os && os.getItem() instanceof YniGear) continue;
                for (Item input : inputsOf(entry.getInput())) {
                    if (input != null) craftingIngredients.add(input.id);
                }
            }

            for (RecipeEntryBase<?, ?, ?> entry : Registries.RECIPES.getAllFurnaceRecipes()) {
                Object output = entry.getOutput();
                if (!(output instanceof ItemStack result) || result.getItem() == null) continue;
                Archetype outArchetype = Archetypes.ofItem(result.getItem());

                for (Item input : inputsOf(entry.getInput())) {
                    if (input == null || input == result.getItem()) continue;

                    boolean onlyEverSmelted = !craftingIngredients.contains(input.id);

                    boolean refinesUpward = outArchetype.tierCeiling > Archetypes.ofItem(input).tierCeiling;

                    if (onlyEverSmelted || refinesUpward) {
                        out.add(input.id);
                    }
                }
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.warn("Could not scan furnace recipes; smelting gate disabled.", t);
        }
        return out;
    }

    static List<Item> inputsOfStatic(Object input) {
        return new YniRecipes().inputsOf(input);
    }

    private List<Item> inputsOf(Object input) {
        List<Item> items = new ArrayList<>();
        try {
            if (input instanceof RecipeSymbol symbol) {
                for (ItemStack s : symbol.resolve()) {
                    if (s != null && s.getItem() != null) items.add(s.getItem());
                }
            } else if (input instanceof RecipeSymbol[] symbols) {
                for (RecipeSymbol symbol : symbols) items.addAll(inputsOf(symbol));
            } else if (input instanceof ItemStack stack) {
                if (stack.getItem() != null) items.add(stack.getItem());
            } else if (input instanceof Iterable<?> many) {
                for (Object o : many) items.addAll(inputsOf(o));
            }
        } catch (Throwable ignored) {

        }
        return items;
    }

    private Map<Integer, Set<String>> piecesWithExistingGear() {
        Map<Integer, Set<String>> out = new HashMap<>();
        try {
            for (RecipeEntryBase<?, ?, ?> entry : Registries.RECIPES.getAllCraftingRecipes()) {
                Object output = entry.getOutput();
                if (!(output instanceof ItemStack result)) continue;
                Item resultItem = result.getItem();

                if (resultItem instanceof YniGear) continue;
                String piece = pieceOf(resultItem);

                if (piece == null) continue;

                Set<Integer> materials = new HashSet<>();
                Object input = entry.getInput();
                if (input instanceof RecipeSymbol[] symbols) {
                    for (RecipeSymbol symbol : symbols) {
                        collect(symbol, materials);
                    }
                } else if (input instanceof Iterable<?> many) {
                    for (Object o : many) {
                        if (o instanceof RecipeSymbol symbol) collect(symbol, materials);
                    }
                }
                for (Integer id : materials) {
                    out.computeIfAbsent(id, k -> new HashSet<>()).add(piece);
                }
            }
        } catch (Throwable t) {

            YouNameIt.LOGGER.warn("Could not scan existing recipes; skipping the duplicate check.", t);
        }
        return out;
    }

    private void collect(RecipeSymbol symbol, Set<Integer> out) {
        if (symbol == null) return;
        try {

            for (ItemStack stack : symbol.resolve()) {
                if (stack == null) continue;
                Item item = stack.getItem();

                if (item != null && item != Items.STICK) out.add(item.id);
            }
        } catch (Throwable ignored) {
            ItemStack stack = symbol.getStack();
            if (stack != null && stack.getItem() != null && stack.getItem() != Items.STICK) {
                out.add(stack.getItem().id);
            }
        }
    }

    private static String pieceOf(Item item) {
        if (item instanceof ItemArmor<?> armor) {
            Object shape = armor.getArmorShape();
            if (shape == HumanArmorShape.HEAD) return "helmet";
            if (shape == HumanArmorShape.CHEST) return "chestplate";
            if (shape == HumanArmorShape.LEGS) return "leggings";
            if (shape == HumanArmorShape.BOOTS) return "boots";

            return null;
        }

        if (item instanceof ItemToolSword) return "sword";
        if (item instanceof ItemToolPickaxe) return "pickaxe";
        if (item instanceof ItemToolAxe) return "axe";
        if (item instanceof ItemToolShovel) return "shovel";
        if (item instanceof ItemToolHoe) return "hoe";
        return null;
    }

    private static int expectedPieceCount() {
        return (YniConfig.generateTools ? 5 : 0) + (YniConfig.generateArmor ? 4 : 0);
    }

    private int register(MaterialSet set, Set<String> taken) {
        Item mat = set.ingredient;
        if (mat == null) return 0;
        Item stick = Items.STICK;
        int n = 0;

        if (YniConfig.generateTools) {
            n += shaped(set, "pickaxe", set.pickaxe, mat, stick, taken, "MMM", " S ", " S ");
            n += shaped(set, "axe", set.axe, mat, stick, taken, "MM ", "MS ", " S ");
            n += shaped(set, "shovel", set.shovel, mat, stick, taken, " M ", " S ", " S ");
            n += shaped(set, "hoe", set.hoe, mat, stick, taken, "MM ", " S ", " S ");
            n += shaped(set, "sword", set.sword, mat, stick, taken, " M ", " M ", " S ");
        }
        if (YniConfig.generateArmor) {
            n += shaped(set, "helmet", set.helmet, mat, stick, taken, "MMM", "M M");
            n += shaped(set, "chestplate", set.chestplate, mat, stick, taken, "M M", "MMM", "MMM");
            n += shaped(set, "leggings", set.leggings, mat, stick, taken, "MMM", "M M", "M M");
            n += shaped(set, "boots", set.boots, mat, stick, taken, "M M", "M M");
        }
        return n;
    }

    private int shaped(MaterialSet set, String piece, Item result, Item material, Item stick,
                       Set<String> taken, String... shape) {

        if (taken.contains(piece)) return 0;

        if (result == null) return 0;
        try {
            RecipeBuilder.Shaped(YouNameIt.MOD_ID)
                    .setShape(shape)

                    .addInput('M', set.ingredientStack())
                    .addInput('S', stick)
                    .create(set.id + "_" + piece, new ItemStack(result, 1));
            return 1;
        } catch (Throwable t) {

            YouNameIt.LOGGER.debug("Recipe {}_{} rejected.", set.id, piece, t);
            return 0;
        }
    }
}
