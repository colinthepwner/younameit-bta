package net.colin.younameit.set;

import net.colin.younameit.YniConfig;
import net.colin.younameit.YouNameIt;
import net.colin.younameit.item.YniArmorPiece;
import net.colin.younameit.item.YniAxe;
import net.colin.younameit.item.YniHoe;
import net.colin.younameit.item.YniPickaxe;
import net.colin.younameit.item.YniShovel;
import net.colin.younameit.item.YniSword;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.material.ArmorMaterial;
import net.minecraft.core.item.material.ToolMaterial;
import net.minecraft.core.util.collection.NamespaceID;
import turniplabs.halplibe.helper.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds and registers the actual items.
 *
 * <p>Runs from {@code afterItemInit}, which is the sweet spot in BTA's startup: every block and
 * item in the game exists, but {@code TextureRegistry.init()} has not run yet, so nothing has
 * been baked into the atlas. Textures themselves are produced later, on the client only — see
 * {@code net.colin.younameit.client.YniClient}.
 */
public final class SetRegistrar {
    private SetRegistrar() {}

    public static List<MaterialSet> registerAll(List<MaterialSet> candidates) {
        List<MaterialSet> registered = new ArrayList<>();
        YniIds.load(candidates);

        int denied = 0;
        for (MaterialSet set : candidates) {
            int needed = perSetItemCount();
            if (needed == 0) break;

            Integer base = YniIds.idFor(set.id);
            if (base == null || base + needed > Item.itemsList.length) {
                denied++;
                continue;
            }

            try {
                build(set, base);
                registered.add(set);
            } catch (Throwable t) {
                YouNameIt.LOGGER.warn("Could not build the set for '{}'; skipping it.", set.id, t);
            }
        }

        if (denied > 0) {
            // Not a failure to allocate well — a genuine ceiling. BTA's item array is 32,768 wide
            // and shared by every block and item in the game, so a pack past that simply cannot
            // have gear for everything. Say which materials lost out rather than only how many,
            // because "895 blocks got no gear" gives no clue whether the ones you wanted survived.
            YouNameIt.LOGGER.warn(
                    "The item array is full: {} of {} material(s) got no gear. Every mod took turns, "
                            + "so the shortfall is spread evenly rather than falling on one of them. "
                            + "Turn off generateArmor or generateTools in config/younameit.properties "
                            + "to halve the demand, or lower blockIdHeadroom to reclaim a little more.",
                    denied, candidates.size());
            logShortfall(candidates);
        }
        YniIds.save();
        return registered;
    }

    /** Which mods lost sets, and to what extent. Counted per namespace so the split is visible. */
    private static void logShortfall(List<MaterialSet> candidates) {
        Map<String, int[]> tally = new TreeMap<>();
        for (MaterialSet set : candidates) {
            String ns;
            try {
                ns = set.block != null ? set.block.namespaceId().namespace() : set.ingredient.namespaceID.namespace();
            } catch (Throwable ignored) {
                ns = "?";
            }
            int[] row = tally.computeIfAbsent(ns, k -> new int[2]);
            row[0]++;
            if (YniIds.idFor(set.id) == null) row[1]++;
        }
        StringBuilder sb = new StringBuilder();
        tally.forEach((ns, row) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ns).append(' ').append(row[0] - row[1]).append('/').append(row[0]);
        });
        YouNameIt.LOGGER.warn("Sets served per mod: {}.", sb);
    }

    private static int perSetItemCount() {
        return (YniConfig.generateTools ? 5 : 0) + (YniConfig.generateArmor ? 4 : 0);
    }

    private static void build(MaterialSet set, int baseId) {
        SetStats s = set.stats;
        int id = baseId;

        if (YniConfig.generateTools) {
            ToolMaterial tm = new ToolMaterial()
                    .setMiningLevel(s.tier)
                    .setDurability(s.toolDurability)
                    .setEfficiency(s.efficiency, s.efficiency * 1.5F)
                    .setDamage(s.attackDamage)
                    .setSilkTouch(s.silkTouch);
            set.toolMaterial = tm;

            ItemBuilder b = new ItemBuilder(YouNameIt.MOD_ID);
            set.pickaxe = b.build(new YniPickaxe(set, texKey(set, "pickaxe"), id++, tm));
            set.axe = b.build(new YniAxe(set, texKey(set, "axe"), id++, tm));
            set.shovel = b.build(new YniShovel(set, texKey(set, "shovel"), id++, tm));
            set.hoe = b.build(new YniHoe(set, texKey(set, "hoe"), id++, tm));
            set.sword = b.build(new YniSword(set, texKey(set, "sword"), id++, tm));
        }

        if (YniConfig.generateArmor) {
            // The material's NamespaceID doubles as the armour sheet filename: BTA binds
            // /assets/<namespace>/textures/armor/<value>_1.png and _2.png off exactly this.
            ArmorMaterial am = new ArmorMaterial(NamespaceID.fromPool(YouNameIt.MOD_ID, set.id), s.armorDurability)
                    .withProtectionPercentage(net.minecraft.core.util.helper.DamageType.COMBAT, s.combatProtection)
                    .withProtectionPercentage(net.minecraft.core.util.helper.DamageType.BLAST, s.blastProtection)
                    .withProtectionPercentage(net.minecraft.core.util.helper.DamageType.FIRE, s.fireProtection)
                    .withProtectionPercentage(net.minecraft.core.util.helper.DamageType.FALL, s.fallProtection);
            set.armorMaterial = am;

            ItemBuilder b = new ItemBuilder(YouNameIt.MOD_ID);
            set.helmet = b.build(new YniArmorPiece(set, texKey(set, "helmet"), id++, am, HumanArmorShape.HEAD));
            set.chestplate = b.build(new YniArmorPiece(set, texKey(set, "chestplate"), id++, am, HumanArmorShape.CHEST));
            set.leggings = b.build(new YniArmorPiece(set, texKey(set, "leggings"), id++, am, HumanArmorShape.LEGS));
            set.boots = b.build(new YniArmorPiece(set, texKey(set, "boots"), id++, am, HumanArmorShape.BOOTS));
        }
    }

    /** Texture key of the form {@code younameit:item/<set>_<piece>}. */
    public static String texKey(MaterialSet set, String piece) {
        return YouNameIt.MOD_ID + ":item/" + set.id + "_" + piece;
    }

    /** The plain item name used as the translation key stem. */
    public static String itemName(MaterialSet set, String piece) {
        return set.id + "_" + piece;
    }

    @SuppressWarnings("unused")
    private static Item unusedKeepImport() {
        return null;
    }
}
