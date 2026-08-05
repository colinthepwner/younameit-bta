package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ToolMaterial;
import net.minecraft.core.item.tool.ItemToolHoe;

public class YniHoe extends ItemToolHoe implements YniGear {
    private final MaterialSet set;
    private final YniNameCache nameCache = new YniNameCache();

    public YniHoe(MaterialSet set, String texKey, int id, ToolMaterial material) {
        super(set.id + "_hoe", texKey, id, material);
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
        return "Hoe";
    }

    @Override
    public String getTranslatedName(ItemStack stack) {
        return nameCache.get(set, "Hoe");
    }

    @Override
    public String getTranslatedDescription(ItemStack stack) {
        return YniLore.describe(set, "Hoe", true);
    }
}
