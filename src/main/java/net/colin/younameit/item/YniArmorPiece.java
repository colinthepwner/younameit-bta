package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.material.ArmorMaterial;

import java.util.Locale;

public class YniArmorPiece extends ItemArmor<HumanArmorShape> implements YniGear {
    private final MaterialSet set;
    private final String pieceName;
    private final YniNameCache nameCache = new YniNameCache();

    public YniArmorPiece(MaterialSet set, String texKey, int id, ArmorMaterial material, HumanArmorShape shape) {
        super(set.id + "_" + pieceOf(shape).toLowerCase(Locale.ROOT), texKey, id, material, shape);
        this.set = set;
        this.pieceName = pieceOf(shape);
    }

    public static String pieceOf(HumanArmorShape shape) {
        switch (shape) {
            case HEAD:  return "Helmet";
            case CHEST: return "Chestplate";
            case LEGS:  return "Leggings";
            default:    return "Boots";
        }
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
        return pieceName;
    }

    @Override
    public String getTranslatedName(ItemStack stack) {
        return nameCache.get(set, pieceName);
    }

    @Override
    public String getTranslatedDescription(ItemStack stack) {
        return YniLore.describe(set, pieceName, false);
    }
}
