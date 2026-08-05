package net.colin.younameit.item;

import net.colin.younameit.YniConfig;
import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.block.Block;

/**
 * "Most efficient mining blocks of its own type."
 *
 * <p>A tool gets its full bonus on the exact block it was made from, and a smaller one on any
 * block sharing that material — so a granite pickaxe is best on granite but still comfortable
 * on stone generally.
 */
final class YniAffinity {
    private YniAffinity() {}

    static float apply(float base, MaterialSet set, Block<?> block) {
        // set.block is null for materials that are loose items (a feather has no block), and
        // those simply have no block to be especially good at.
        if (block == null || set == null || set.block == null) return base;
        if (block == set.block) {
            return base * (float) YniConfig.ownBlockBonus;
        }
        try {
            if (set.block.getMaterial() != null && set.block.getMaterial() == block.getMaterial()) {
                // Half the bonus, so same-family blocks are noticeably better but not equal.
                return base * (1.0F + (float) (YniConfig.ownBlockBonus - 1.0) * 0.5F);
            }
        } catch (Throwable ignored) {
            // A modded block with an odd material simply gets no affinity bonus.
        }
        return base;
    }
}
