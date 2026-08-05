package net.colin.younameit.set;

import net.colin.younameit.YniConfig;
import net.colin.younameit.YouNameIt;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.tag.BlockTags;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.tag.ItemTags;
import net.minecraft.core.item.tool.ItemTool;
import net.minecraft.core.item.tool.ItemToolSword;
import net.minecraft.core.block.IPainted;
import net.minecraft.core.util.helper.DyeColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decides which materials earn a set.
 *
 * <p>Two passes. Blocks first, because a block carries real physical properties and should win
 * when the same item is reachable both ways; then every remaining item, so feathers, bone, wheat
 * and whatever a mod adds are covered too. Both are deduplicated against one shared set of
 * ingredient ids, so a material only ever produces one family of gear.
 *
 * <p>The bar for either is "could a survival player hold a stack of this?" — it must not be hidden
 * from the creative menu, which is how BTA and most mods flag technical content, and blocks must
 * additionally be breakable. Tools and armour are excluded outright so sets never compound.
 */
public final class SetScanner {
    private SetScanner() {}

    public static List<MaterialSet> scan() {
        List<MaterialSet> out = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Set<Integer> seenIngredients = new HashSet<>();

        int skippedUnbreakable = 0, skippedNoItem = 0, skippedHidden = 0;
        int skippedGear = 0, skippedDuplicate = 0, splitBlocks = 0;

        // ---- pass 1: blocks -------------------------------------------------------------
        for (Block<?> block : Blocks.blocksList) {
            if (block == null || block.id() == 0) continue;

            // Unbreakable blocks (bedrock and friends) report a negative hardness.
            if (block.getHardness() < 0.0F || Float.isInfinite(block.getHardness())) {
                skippedUnbreakable++;
                continue;
            }
            if (hasBlockTag(block, BlockTags.NOT_IN_CREATIVE_MENU)) {
                skippedHidden++;
                continue;
            }
            Item ingredient = itemOf(block);
            if (ingredient == null) {
                skippedNoItem++;
                continue;
            }
            if (isGear(ingredient)) {
                skippedGear++;
                continue;
            }
            // Several blocks share one drop (stone -> cobblestone). One set per ingredient.
            if (!seenIngredients.add(ingredient.id)) {
                skippedDuplicate++;
                continue;
            }
            int[] metas = variantsOf(block, ingredient);
            if (metas.length > 1) splitBlocks++;
            for (int meta : metas) {
                out.add(make(block, ingredient, meta, siblingOf(metas, meta), usedIds));
            }
        }

        int fromBlocks = out.size();

        // ---- pass 2: everything else ----------------------------------------------------
        for (Item item : Item.itemsList) {
            if (item == null) continue;
            if (seenIngredients.contains(item.id)) continue;
            if (isGear(item)) {
                skippedGear++;
                continue;
            }
            if (hasItemTag(item, ItemTags.NOT_IN_CREATIVE_MENU)) {
                skippedHidden++;
                continue;
            }
            seenIngredients.add(item.id);
            int[] itemMetas = itemVariantsOf(item);
            if (itemMetas.length > 1) splitBlocks++;
            for (int meta : itemMetas) {
                out.add(make(null, item, meta, siblingOf(itemMetas, meta), usedIds));
            }
        }

        // Sorted first so the ranking below has a deterministic list to work from, then ordered by
        // who most deserves an id. That second step is not cosmetic: there are never enough item
        // ids for a large pack, so this order is what decides which materials get gear at all.
        out.sort(Comparator.comparing(s -> s.id));
        out = SetPriority.rank(out);

        YouNameIt.LOGGER.info(
                "Scanned {} block slots and {} item slots: {} sets ({} from blocks, {} from loose items). "
                        + "Skipped {} unbreakable, {} without an item, {} hidden, {} tools/armour, {} duplicates. "
                        + "{} block(s) split into metadata variants.",
                Blocks.blocksList.length, Item.itemsList.length, out.size(), fromBlocks, out.size() - fromBlocks,
                skippedUnbreakable, skippedNoItem, skippedHidden, skippedGear, skippedDuplicate, splitBlocks);
        return out;
    }

    private static MaterialSet make(Block<?> block, Item ingredient, int meta, Set<String> usedIds) {
        return make(block, ingredient, meta, -1, usedIds);
    }

    private static MaterialSet make(Block<?> block, Item ingredient, int meta, int sibling, Set<String> usedIds) {
        String id = uniqueId(block, ingredient, meta, usedIds);
        boolean variants = meta != 0 || sibling >= 0;
        return new MaterialSet(block, id, ingredient, meta, variants,
                variantNameOf(block, meta), sibling, SetStats.derive(block, ingredient));
    }

    /** Any metadata in the group other than this one, for the self-naming comparison. */
    private static int siblingOf(int[] metas, int meta) {
        if (metas.length < 2) return -1;
        for (int candidate : metas) {
            if (candidate != meta) return candidate;
        }
        return -1;
    }

    /**
     * The colour name for a painted block's metadata, or null when there is nothing to add.
     *
     * <p>Taken from the block's own {@code fromMetadata} rather than from any name it reports,
     * because a painted block answers with the same translated name for every colour it holds.
     */
    private static String variantNameOf(Block<?> block, int meta) {
        if (block == null) return null;
        try {
            if (!(block.getLogic() instanceof IPainted painted)) return null;
            // Always report the colour here; whether it actually gets prefixed is decided at name
            // time in YniGear.sourceName, which is the only place the resolved names can be
            // compared (I18n is not up yet during the scan).
            DyeColor colour = painted.fromMetadata(meta);
            if (colour != null) return prettify(colour.name());
        } catch (Throwable ignored) {
            // No colour to report; the base name stands on its own.
        }
        return null;
    }

    /** {@code LIGHT_BLUE} -> {@code Light Blue}. */
    private static String prettify(String constant) {
        StringBuilder sb = new StringBuilder();
        for (String part : constant.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * The metadata values that are genuinely different variants of this block.
     *
     * <p>BTA keeps colour and material variants inside one block id — a single {@code Blocks.WOOL}
     * with sixteen colours as metadata — so treating a block as one material makes every colour
     * craft the same white gear.
     *
     * <p>Variants are found by asking the block for its <em>language key</em> at each metadata
     * value and keeping the ones that differ. That is deliberately not a texture comparison:
     * textures are client-side, and item registration has to produce identical ids on a dedicated
     * server or every id desyncs. A block that names its variants separately is telling us they
     * are separate things, and one that returns the same key for every value is telling us the
     * metadata means something else entirely — an axis, a fill level, a growth stage — which is
     * exactly the case we must not split on.
     */
    private static int[] variantsOf(Block<?> block, Item ingredient) {
        if (block == null) return new int[]{0};
        try {
            // IPainted is the block itself declaring "my metadata is a dye colour". Any block that
            // implements it — wool, painted planks, slabs, doors, lamps, and anything a mod adds
            // on the same interface — enumerates exactly through DyeColor, with no name matching
            // and no reliance on the client.
            //
            // Two earlier attempts were both wrong and both failed silently, which is why this is
            // measured rather than assumed: Item.getHasSubtypes() is false on BTA's wool despite
            // its sixteen colours, and Block.getLanguageKey(meta) returns the same key for every
            // one of them. Each produced "0 blocks split" and no error at all.
            if (block.getLogic() instanceof IPainted painted) {
                Set<Integer> metas = new TreeSet<>();
                for (DyeColor colour : DyeColor.values()) {
                    // No masking. A painted chest keeps its facing in the low bits and shifts the
                    // colour up by its own colorOffset, so an & 15 here erased the colour entirely
                    // and collapsed all sixteen chests back into one. The block already knows how
                    // to encode a colour; take whatever it returns.
                    metas.add(painted.toMetadata(colour));
                }
                if (metas.size() > 1) {
                    int[] out = new int[Math.min(metas.size(), YniConfig.maxVariantsPerBlock)];
                    int i = 0;
                    for (int meta : metas) {
                        if (i >= out.length) break;
                        out[i++] = meta;
                    }
                    return out;
                }
            }
        } catch (Throwable ignored) {
            // A modded logic class that misbehaves simply stays a single material.
        }
        return new int[]{0};
    }

    /**
     * The metadata variants of a loose item, such as the sixteen colours packed into one dye.
     *
     * <p>Items are probed by language key, which is the opposite of what works for blocks and is
     * worth stating plainly: {@code BlockLogic.getLanguageKey(int)} ignores its argument and
     * returns the block key, so every wool colour reports the same name — but {@code ItemDye}
     * overrides {@code getLanguageKey(ItemStack)} and genuinely names each colour. So the signal
     * that is useless on the block side is exactly the right one on the item side.
     *
     * <p>An item that returns one name for every metadata value is telling us the metadata means
     * damage, charge or nothing at all, and stays a single material.
     */
    private static int[] itemVariantsOf(Item item) {
        if (item == null) return new int[]{0};
        List<Integer> metas = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        try {
            for (int meta = 0; meta < YniConfig.maxVariantsPerBlock; meta++) {
                ItemStack probe = new ItemStack(item, 1);
                probe.setMetadata(meta);
                String key = item.getLanguageKey(probe);
                if (key == null) continue;
                if (seenKeys.add(key)) metas.add(meta);
            }
        } catch (Throwable ignored) {
            return new int[]{0};
        }
        if (metas.size() <= 1) return new int[]{0};

        int[] out = new int[metas.size()];
        for (int i = 0; i < out.length; i++) out[i] = metas.get(i);
        return out;
    }

    /** Never build a set out of gear — that is what would turn this into a recursion. */
    private static boolean isGear(Item item) {
        return item instanceof ItemTool || item instanceof ItemToolSword || item instanceof ItemArmor;
    }

    private static Item itemOf(Block<?> block) {
        try {
            return block.asItem();
        } catch (Throwable ignored) {
            // A malformed modded block should not take the whole scan down.
            return null;
        }
    }

    private static boolean hasBlockTag(Block<?> block, net.minecraft.core.data.tag.Tag<Block<?>> tag) {
        try {
            return block.hasTag(tag);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasItemTag(Item item, net.minecraft.core.data.tag.Tag<Item> tag) {
        try {
            return item.hasTag(tag);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** {@code namespace_path}, lowercased and stripped to [a-z0-9_], de-duplicated. */
    private static String uniqueId(Block<?> block, Item ingredient, int meta, Set<String> used) {
        String base = null;
        try {
            if (block != null) {
                base = block.namespaceId().namespace() + "_" + block.namespaceId().value();
            } else if (ingredient != null) {
                base = ingredient.namespaceID.namespace() + "_" + ingredient.namespaceID.value();
            }
        } catch (Throwable ignored) {
            // Fall through to the id-based name.
        }
        if (base == null) {
            base = block != null ? "block_" + block.id() : "item_" + (ingredient == null ? 0 : ingredient.id);
        }
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        if (base.isEmpty()) base = "material_" + (ingredient == null ? 0 : ingredient.id);
        if (meta != 0) base = base + "_v" + meta;

        String candidate = base;
        int n = 2;
        while (!used.add(candidate)) {
            candidate = base + "_" + n++;
        }
        return candidate;
    }
}
