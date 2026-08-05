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
import net.minecraft.core.item.tool.ItemTool;
import net.minecraft.core.item.tool.ItemToolSword;
import turniplabs.halplibe.helper.RecipeBuilder;
import turniplabs.halplibe.util.RecipeEntrypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The standard vanilla shapes, with the source block as the material.
 *
 * <p>Runs from {@code recipesReady}, which fires after BTA has loaded its own recipe files, so
 * these are additive and nothing vanilla is displaced.
 */
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

        Set<Integer> alreadyCraftable = materialsWithExistingGear();
        Set<Integer> mustBeSmelted = materialsThatMustBeSmelted();

        int made = 0, failed = 0, skipped = 0, unsmelted = 0;
        for (MaterialSet set : sets) {
            if (set.ingredient != null && alreadyCraftable.contains(set.ingredient.id)) {
                skipped++;
                continue;
            }
            if (set.ingredient != null && mustBeSmelted.contains(set.ingredient.id)) {
                unsmelted++;
                continue;
            }
            try {
                made += register(set);
            } catch (Throwable t) {
                failed++;
                YouNameIt.LOGGER.debug("Could not register recipes for '{}'.", set.id, t);
            }
        }
        // Without this nothing we registered is craftable, however correct the recipes are.
        // RecipeRegistry.getAllCraftingRecipes() memoises into a private recipeCache, and
        // findMatchingCraftingRecipe reads that cache rather than the registry — so any recipe
        // added after the cache is first built is invisible to the crafting grid forever. It
        // fails silently in the worst way: the recipes are genuinely in the registry, the counts
        // in the log are right, and the grid simply produces nothing.
        try {
            Registries.RECIPES.invalidateCaches();
            if (Registries.RECIPES_LOCAL_COPY != null && Registries.RECIPES_LOCAL_COPY != Registries.RECIPES) {
                Registries.RECIPES_LOCAL_COPY.invalidateCaches();
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.warn("Could not invalidate the recipe cache; recipes may not be craftable.", t);
        }

        YouNameIt.LOGGER.info(
                "Registered {} recipes; skipped {} material(s) whose gear is already craftable and "
                        + "{} that must be smelted first{}.",
                made, skipped, unsmelted, failed > 0 ? " (" + failed + " sets failed)" : "");

        int upgraded = BlockMaterialUpgrade.apply(sets);
        if (upgraded > 0) {
            YouNameIt.LOGGER.info("Matched {} storage block(s) to the gear power of what they are made of.", upgraded);
        }

        logVariantNames();
        verifyRegistration();
    }

    /**
     * Prints the display name of a few colour variants.
     *
     * <p>Logged here rather than during registration because {@code I18n.initialize} runs later in
     * startGame than the item pass does, so a name resolved at scan time would come back as a raw
     * translation key and prove nothing.
     */
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

    /**
     * Reads a couple of our recipes back out of the registry and logs exactly what landed.
     *
     * <p>A recipe that registers cleanly but never matches gives no error anywhere — the crafting
     * grid just stays empty — so the only way to tell a registration bug from a matching bug is to
     * inspect the stored entry. Cheap, runs once, and worth keeping.
     */
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

    /**
     * Items that refine into something better in a furnace, and so should not be craftable into
     * gear in their raw state.
     *
     * <p>Without this an ore could be turned straight into tools, skipping smelting entirely and
     * flattening the one progression step the furnace exists to provide. The test is whether the
     * smelted result belongs to a <em>higher</em> archetype tier than the input: iron ore is stone
     * that becomes metal, so it must be smelted, whereas cobblestone becomes stone, sand becomes
     * glass and a log becomes charcoal — all lateral or downward, so those stay optional and the
     * raw form remains usable exactly as it is in vanilla.
     *
     * <p>This runs at {@code recipesReady} rather than during the scan because BTA loads its
     * recipe files well after items are registered; at scan time the furnace registry is empty.
     */
    private Set<Integer> materialsThatMustBeSmelted() {
        Set<Integer> out = new HashSet<>();
        try {
            // Everything any crafting recipe consumes, ours excluded. This is the structural test
            // that replaces guessing from names: a material the game already treats as a building
            // block — cobblestone, sand, logs — appears here, whereas something whose only use is
            // to be smelted does not. It needs no naming convention, so a mod that calls its ore
            // "raw_chunk_of_whatever" is classified correctly regardless.
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

                    // Primary, naming-independent signal: this thing exists only to be smelted.
                    boolean onlyEverSmelted = !craftingIngredients.contains(input.id);
                    // Secondary: smelting demonstrably refines it into a better class of material.
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

    /** Pulls the concrete items out of whatever shape a recipe's input takes. */
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
            // An input shape we do not recognise simply contributes nothing.
        }
        return items;
    }

    /**
     * Item ids that some existing recipe — vanilla's or another mod's — already turns into gear.
     *
     * <p>Without this, a cobblestone set would register the exact shape that already makes a
     * vanilla stone pickaxe, and an iron-ingot set the one that makes an iron pickaxe. Two recipes
     * with identical inputs means whichever the lookup reaches first wins and the other silently
     * becomes uncraftable — so the honest thing is to leave the material alone and let the
     * existing recipe stand. The check is on the recipe table rather than a hardcoded list of
     * vanilla materials, so a mod that adds copper tools is deferred to just the same way.
     */
    private Set<Integer> materialsWithExistingGear() {
        Set<Integer> out = new HashSet<>();
        try {
            for (RecipeEntryBase<?, ?, ?> entry : Registries.RECIPES.getAllCraftingRecipes()) {
                Object output = entry.getOutput();
                if (!(output instanceof ItemStack result)) continue;
                Item resultItem = result.getItem();
                if (!isGear(resultItem)) continue;

                Object input = entry.getInput();
                if (input instanceof RecipeSymbol[] symbols) {
                    for (RecipeSymbol symbol : symbols) {
                        collect(symbol, out);
                    }
                } else if (input instanceof Iterable<?> many) {
                    for (Object o : many) {
                        if (o instanceof RecipeSymbol symbol) collect(symbol, out);
                    }
                }
            }
        } catch (Throwable t) {
            // If the recipe table cannot be read we would rather register everything than
            // nothing; a duplicate recipe is a smaller problem than no recipes at all.
            YouNameIt.LOGGER.warn("Could not scan existing recipes; skipping the duplicate check.", t);
        }
        return out;
    }

    private void collect(RecipeSymbol symbol, Set<Integer> out) {
        if (symbol == null) return;
        try {
            // resolve() expands item groups and tags, so a recipe written against a
            // "planks" group contributes every plank in it.
            for (ItemStack stack : symbol.resolve()) {
                if (stack == null) continue;
                Item item = stack.getItem();
                // Sticks appear in every tool recipe and would otherwise match everything.
                if (item != null && item != Items.STICK) out.add(item.id);
            }
        } catch (Throwable ignored) {
            ItemStack stack = symbol.getStack();
            if (stack != null && stack.getItem() != null && stack.getItem() != Items.STICK) {
                out.add(stack.getItem().id);
            }
        }
    }

    private static boolean isGear(Item item) {
        return item instanceof ItemTool || item instanceof ItemToolSword || item instanceof ItemArmor;
    }

    private int register(MaterialSet set) {
        Item mat = set.ingredient;
        if (mat == null) return 0;
        Item stick = Items.STICK;
        int n = 0;

        if (YniConfig.generateTools) {
            n += shaped(set, "pickaxe", set.pickaxe, mat, stick, "MMM", " S ", " S ");
            n += shaped(set, "axe", set.axe, mat, stick, "MM ", "MS ", " S ");
            n += shaped(set, "shovel", set.shovel, mat, stick, " M ", " S ", " S ");
            n += shaped(set, "hoe", set.hoe, mat, stick, "MM ", " S ", " S ");
            n += shaped(set, "sword", set.sword, mat, stick, " M ", " M ", " S ");
        }
        if (YniConfig.generateArmor) {
            n += shaped(set, "helmet", set.helmet, mat, stick, "MMM", "M M");
            n += shaped(set, "chestplate", set.chestplate, mat, stick, "M M", "MMM", "MMM");
            n += shaped(set, "leggings", set.leggings, mat, stick, "MMM", "M M", "M M");
            n += shaped(set, "boots", set.boots, mat, stick, "M M", "M M");
        }
        return n;
    }

    private int shaped(MaterialSet set, String piece, Item result, Item material, Item stick, String... shape) {
        // The ingredient carries its own metadata, so a red wool set is crafted from RED wool.
        // Using the -1 wildcard here made every colour of a variant block craft the meta-0 set,
        // which is why every wool colour produced white gear.
        if (result == null) return 0;
        try {
            RecipeBuilder.Shaped(YouNameIt.MOD_ID)
                    .setShape(shape)
                    // Metadata -1 is the wildcard. RecipeSymbol.matches compares metadata exactly
                    // unless the recipe asks for -1, and addInput(char, item) pins it to 0 — so a
                    // material whose obtained form carries any other metadata could never match
                    // its own recipe. BTA uses metadata for block variants extensively, which made
                    // this look like "recipes don't craft" for a large share of materials.
                    // Accepting any metadata is also the correct behaviour here: variants of one
                    // block share a single Block object, so they share a single set.
                    .addInput('M', set.ingredientStack())
                    .addInput('S', stick)
                    .create(set.id + "_" + piece, new ItemStack(result, 1));
            return 1;
        } catch (Throwable t) {
            // A duplicate shape against an existing recipe is the usual cause; not fatal.
            YouNameIt.LOGGER.debug("Recipe {}_{} rejected.", set.id, piece, t);
            return 0;
        }
    }
}
