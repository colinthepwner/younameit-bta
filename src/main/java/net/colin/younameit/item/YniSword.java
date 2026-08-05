package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.block.Block;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ToolMaterial;
import net.minecraft.core.item.tool.ItemToolSword;

/** In BTA {@code ItemToolSword} extends {@code Item} directly, not {@code ItemTool}. */
public class YniSword extends ItemToolSword implements YniGear {
    private final MaterialSet set;
    private final YniNameCache nameCache = new YniNameCache();

    public YniSword(MaterialSet set, String texKey, int id, ToolMaterial material) {
        super(set.id + "_sword", texKey, id, material);
        this.set = set;
    }

    @Override
    public MaterialSet yniSet() {
        return set;
    }

    @Override
    public void yniSetDurability(int durability) {
        setMaxDamage(durability);
    }

    @Override
    public String yniPieceName() {
        return "Sword";
    }

    // Deliberately no getStrVsBlock override. A sword is not a digging tool: BTA returns a flat
    // 1.5 for every block, and applying the own-material affinity on top turned every generated
    // sword into a 2.4 shovel for its own block, which is faster at digging than any vanilla
    // sword and creeps up on a wooden shovel. The affinity belongs on tools that dig.

    @Override
    public String getTranslatedName(ItemStack stack) {
        return nameCache.get(set, "Sword");
    }

    @Override
    public String getTranslatedDescription(ItemStack stack) {
        return YniLore.describe(set, "Sword", true);
    }
}
