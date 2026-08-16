package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.lang.I18n;

import java.util.Locale;

public interface YniGear {

    MaterialSet yniSet();

    void yniSetDurability(int durability);

    String yniPieceName();

    static String displayName(MaterialSet set, String piece) {
        return sourceName(set) + " " + piece;
    }

    static String sourceName(MaterialSet set) {
        try {
            Item ingredient = set.ingredient;
            if (ingredient != null) {

                String name = ingredient.getTranslatedName(set.ingredientStack());
                if (name != null && !name.isEmpty() && !name.endsWith(".name")) {
                    if (set.variantName == null || set.siblingMetadata < 0) return name;

                    ItemStack sibling = new ItemStack(ingredient, 1);
                    sibling.setMetadata(set.siblingMetadata);
                    String other = ingredient.getTranslatedName(sibling);
                    return name.equals(other) ? set.variantName + " " + name : name;
                }
            }
        } catch (Throwable ignored) {

        }
        try {
            String key = set.block.getKey();
            if (key != null) {
                String translated = I18n.getInstance().translateKey("tile." + key + ".name");
                if (translated != null && !translated.isEmpty() && !translated.endsWith(".name")) {
                    return translated;
                }
            }
        } catch (Throwable ignored) {

        }
        return prettify(set.id);
    }

    static String prettify(String id) {
        String s = id;
        int underscore = s.indexOf('_');
        if (underscore > 0) {

            s = s.substring(underscore + 1);
        }
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
