package net.colin.younameit.item;

import net.colin.younameit.YniConfig;
import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.block.Block;

final class YniAffinity {
    private YniAffinity() {}

    static float apply(float base, MaterialSet set, Block<?> block) {

        if (block == null || set == null || set.block == null) return base;
        if (block == set.block) {
            return base * (float) YniConfig.ownBlockBonus;
        }
        try {
            if (set.block.getMaterial() != null && set.block.getMaterial() == block.getMaterial()) {

                return base * (1.0F + (float) (YniConfig.ownBlockBonus - 1.0) * 0.5F);
            }
        } catch (Throwable ignored) {

        }
        return base;
    }
}
