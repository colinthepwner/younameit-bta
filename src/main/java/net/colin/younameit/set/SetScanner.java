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

public final class SetScanner {
    private SetScanner() {}

    public static List<MaterialSet> scan() {
        List<MaterialSet> out = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Set<Integer> seenIngredients = new HashSet<>();

        int skippedUnbreakable = 0, skippedNoItem = 0, skippedHidden = 0;
        int skippedGear = 0, skippedDuplicate = 0, splitBlocks = 0;

        for (Block<?> block : Blocks.blocksList) {
            if (block == null || block.id() == 0) continue;

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

    private static int siblingOf(int[] metas, int meta) {
        if (metas.length < 2) return -1;
        for (int candidate : metas) {
            if (candidate != meta) return candidate;
        }
        return -1;
    }

    private static String variantNameOf(Block<?> block, int meta) {
        if (block == null) return null;
        try {
            if (!(block.getLogic() instanceof IPainted painted)) return null;

            DyeColor colour = painted.fromMetadata(meta);
            if (colour != null) return prettify(colour.name());
        } catch (Throwable ignored) {

        }
        return null;
    }

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

    private static int[] variantsOf(Block<?> block, Item ingredient) {
        if (block == null) return new int[]{0};
        try {

            if (block.getLogic() instanceof IPainted painted) {
                Set<Integer> metas = new TreeSet<>();
                for (DyeColor colour : DyeColor.values()) {

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

        }
        return new int[]{0};
    }

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

    private static boolean isGear(Item item) {
        return item instanceof ItemTool || item instanceof ItemToolSword || item instanceof ItemArmor;
    }

    private static Item itemOf(Block<?> block) {
        try {
            return block.asItem();
        } catch (Throwable ignored) {

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

    private static String uniqueId(Block<?> block, Item ingredient, int meta, Set<String> used) {
        String base = null;
        try {
            if (block != null) {
                base = block.namespaceId().namespace() + "_" + block.namespaceId().value();
            } else if (ingredient != null) {
                base = ingredient.namespaceID.namespace() + "_" + ingredient.namespaceID.value();
            }
        } catch (Throwable ignored) {

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
