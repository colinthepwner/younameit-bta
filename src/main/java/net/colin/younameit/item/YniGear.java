package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.lang.I18n;

import java.util.Locale;

/**
 * Shared behaviour for every generated item.
 *
 * <p>Names are computed rather than translated. There is no way to ship a .lang file for items
 * whose very existence depends on which mods are installed, so each piece builds its display name
 * from the source block's own translated name — which means a modded block that is localised
 * stays localised, for free.
 */
public interface YniGear {

    MaterialSet yniSet();

    /**
     * Re-applies durability after construction.
     *
     * <p>Needed because BTA bakes maxDamage into the Item in its constructor rather than reading
     * it back off the ToolMaterial, so adjusting the material later changes mining speed and
     * harvest level (both read live) but leaves durability at whatever it was built with.
     * { setMaxDamage} is protected on Item, which a subclass can call and an interface
     * cannot — hence one implementation per class rather than a default here.
     */
    void yniSetDurability(int durability);

    /** "Pickaxe", "Chestplate", … */
    String yniPieceName();

    static String displayName(MaterialSet set, String piece) {
        return sourceName(set) + " " + piece;
    }

    /** The source material's display name, falling back to a de-slugged key. */
    static String sourceName(MaterialSet set) {
        try {
            Item ingredient = set.ingredient;
            if (ingredient != null) {
                // The variant's OWN stack, not a bare one. Building the name from metadata 0 is
                // why every colour of wool called itself white.
                String name = ingredient.getTranslatedName(set.ingredientStack());
                if (name != null && !name.isEmpty() && !name.endsWith(".name")) {
                    if (set.variantName == null || set.siblingMetadata < 0) return name;

                    // Add the colour only when the material does not already say it. A painted
                    // button resolves to "Gray Wooden Button" on its own, so prefixing gives
                    // "Gray Gray Wooden Button"; wool resolves to plain "Wool" for all sixteen
                    // colours and needs the prefix.
                    //
                    // Compared against a SIBLING variant, not against metadata 0. Using 0 as the
                    // baseline means the meta-0 variant compares with itself, always matches, and
                    // is the one case that stays doubled — which is exactly how it failed.
                    //
                    // Resolved names are also the only workable comparison. Language keys are
                    // identical across colours even where the names differ, and searching the name
                    // for the colour word fails because DyeColor.SILVER displays as "Light Gray".
                    // It happens here rather than in the scan because I18n is not up that early.
                    ItemStack sibling = new ItemStack(ingredient, 1);
                    sibling.setMetadata(set.siblingMetadata);
                    String other = ingredient.getTranslatedName(sibling);
                    return name.equals(other) ? set.variantName + " " + name : name;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the key-derived name below.
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
            // Fall through.
        }
        return prettify(set.id);
    }

    /** {@code minecraft_mossy_cobblestone} -> {@code Mossy Cobblestone}. */
    static String prettify(String id) {
        String s = id;
        int underscore = s.indexOf('_');
        if (underscore > 0) {
            // Drop the namespace prefix; "minecraft_stone" reads better as "Stone".
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
