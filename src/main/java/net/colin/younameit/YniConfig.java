package net.colin.younameit;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Plain .properties config. Deliberately not tied to any config library so the mod keeps
 * working against whatever HalpLibe version happens to be installed.
 */
public final class YniConfig {
    private YniConfig() {}

    public static boolean enabled = true;

    /** First item id handed out. BTA's item array is 32768 wide; vanilla uses the low few hundred. */
    public static int itemIdBase = 14000;
    /** Hard ceiling so a huge modpack degrades gracefully instead of overrunning the item array. */
    public static int itemIdMax = 32767;
    /**
     * Block ids kept clear above the highest block that exists.
     *
     * <p>The item array and the block array overlap: a block's item lives at the block's own id, so
     * the unused part of the block range is unused item ids too, and that is where most of the free
     * space in a modded game actually is. Those slots get used, but filling downward from the top
     * and stopping this far above the last block leaves room for a mod added later to register its
     * blocks without landing on gear.
     */
    public static int blockIdHeadroom = 1024;

    /** Generate the 5 tools / the 4 armour pieces. */
    public static boolean generateTools = true;
    public static boolean generateArmor = true;
    /** Register crafting recipes for everything generated. */
    public static boolean generateRecipes = true;

    /** How much faster a tool is on its own block, as a multiplier on its normal speed. */
    public static double ownBlockBonus = 1.6;

    /**
     * Literal reading of the brief: sets made from furnace fuel take <em>less</em> fire damage.
     * Flip this to false if you would rather flammable materials be worse in fire, not better.
     */
    public static boolean fuelReducesFireDamage = true;
    /** Full-set fire + lava immunity for sets made from non-flammable, fireproof blocks. */
    public static boolean fireResistantSetBonus = true;

    /** Skip blocks whose texture could not be resolved rather than emitting a blank set. */
    public static boolean skipUntexturedBlocks = true;

    /** Upper bound when splitting a block into its metadata variants (wool has 16). */
    public static int maxVariantsPerBlock = 16;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("younameit.properties");
    }

    public static void load() {
        Properties p = new Properties();
        Path path = file();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            } catch (IOException e) {
                YouNameIt.LOGGER.warn("Could not read config, using defaults.", e);
            }
        }
        enabled = bool(p, "enabled", enabled);
        itemIdBase = integer(p, "itemIdBase", itemIdBase);
        itemIdMax = integer(p, "itemIdMax", itemIdMax);
        blockIdHeadroom = integer(p, "blockIdHeadroom", blockIdHeadroom);
        generateTools = bool(p, "generateTools", generateTools);
        generateArmor = bool(p, "generateArmor", generateArmor);
        generateRecipes = bool(p, "generateRecipes", generateRecipes);
        ownBlockBonus = dbl(p, "ownBlockBonus", ownBlockBonus);
        fuelReducesFireDamage = bool(p, "fuelReducesFireDamage", fuelReducesFireDamage);
        fireResistantSetBonus = bool(p, "fireResistantSetBonus", fireResistantSetBonus);
        skipUntexturedBlocks = bool(p, "skipUntexturedBlocks", skipUntexturedBlocks);
        maxVariantsPerBlock = integer(p, "maxVariantsPerBlock", maxVariantsPerBlock);
        save();
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("itemIdBase", String.valueOf(itemIdBase));
        p.setProperty("itemIdMax", String.valueOf(itemIdMax));
        p.setProperty("blockIdHeadroom", String.valueOf(blockIdHeadroom));
        p.setProperty("generateTools", String.valueOf(generateTools));
        p.setProperty("generateArmor", String.valueOf(generateArmor));
        p.setProperty("generateRecipes", String.valueOf(generateRecipes));
        p.setProperty("ownBlockBonus", String.valueOf(ownBlockBonus));
        p.setProperty("fuelReducesFireDamage", String.valueOf(fuelReducesFireDamage));
        p.setProperty("fireResistantSetBonus", String.valueOf(fireResistantSetBonus));
        p.setProperty("skipUntexturedBlocks", String.valueOf(skipUntexturedBlocks));
        p.setProperty("maxVariantsPerBlock", String.valueOf(maxVariantsPerBlock));
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "You Name It! — tool and armour sets from every block");
            }
        } catch (IOException e) {
            YouNameIt.LOGGER.warn("Could not write config.", e);
        }
    }

    private static boolean bool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int integer(Properties p, String k, int def) {
        try {
            String v = p.getProperty(k);
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double dbl(Properties p, String k, double def) {
        try {
            String v = p.getProperty(k);
            return v == null ? def : Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
