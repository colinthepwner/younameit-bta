package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.block.Block;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ToolMaterial;
import net.minecraft.core.item.tool.ItemToolAxe;

public class YniAxe extends ItemToolAxe implements YniGear {
    private final MaterialSet set;
    private final YniNameCache nameCache = new YniNameCache();

    public YniAxe(MaterialSet set, String texKey, int id, ToolMaterial material) {
        super(set.id + "_axe", texKey, id, material);
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
        return "Axe";
    }

    @Override
    public float getStrVsBlock(ItemStack stack, Block<?> block) {
        return YniAffinity.apply(super.getStrVsBlock(stack, block), set, block);
    }

    @Override
    public String getTranslatedName(ItemStack stack) {
        return nameCache.get(set, "Axe");
    }

    @Override
    public String getTranslatedDescription(ItemStack stack) {
        return YniLore.describe(set, "Axe", true);
    }
}
