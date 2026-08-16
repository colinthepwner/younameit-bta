package net.colin.younameit;

import net.colin.younameit.recipe.YniRecipes;
import net.colin.younameit.set.MaterialSet;
import net.colin.younameit.set.SetRegistrar;
import net.colin.younameit.set.SetScanner;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.util.ItemInitEntrypoint;

import java.util.Collections;
import java.util.List;

public class YouNameIt implements ModInitializer, ItemInitEntrypoint {
    public static final String MOD_ID = "younameit";
    public static final Logger LOGGER = LoggerFactory.getLogger("You Name It!");

    private static List<MaterialSet> sets = Collections.emptyList();

    public static List<MaterialSet> getSets() {
        return sets;
    }

    @Override
    public void onInitialize() {
        YniConfig.load();
        LOGGER.info("You Name It! loading — every block becomes a tool set.");
    }

    @Override
    public void afterItemInit() {
        if (!YniConfig.enabled) {
            LOGGER.info("Disabled in config; generating nothing.");
            return;
        }
        long start = System.currentTimeMillis();

        List<MaterialSet> found = SetScanner.scan();
        sets = SetRegistrar.registerAll(found);
        YniRecipes.setSets(sets);

        LOGGER.info("Generated {} material sets ({} items) in {} ms.",
                sets.size(), sets.size() * MaterialSet.ITEMS_PER_SET, System.currentTimeMillis() - start);

        logBalanceSample();
    }

    private void logBalanceSample() {
        String[] watch = {
                "minecraft_block_dirt", "minecraft_block_gravel", "minecraft_block_sand",
                "minecraft_block_planks_oak", "minecraft_block_cobble_stone", "minecraft_block_cobblestone",
                "minecraft_block_block_iron", "minecraft_block_obsidian", "minecraft_block_block_diamond",
        };
        LOGGER.info("Balance sample (vanilla: wood 64/2.0/0, stone 128/4.0/0, iron 384/6.0/0, diamond 1536/14.0/4):");
        for (String id : watch) {
            for (MaterialSet set : sets) {
                if (!set.id.equals(id)) continue;
                LOGGER.info("  {} -> tier {}, durability {}, efficiency {}, damage {}, armour {} dur / {}% combat",
                        set.id, set.stats.tier, set.stats.toolDurability,
                        String.format("%.2f", set.stats.efficiency), set.stats.attackDamage,
                        set.stats.armorDurability, String.format("%.0f", set.stats.combatProtection));
                break;
            }
        }
    }
}
