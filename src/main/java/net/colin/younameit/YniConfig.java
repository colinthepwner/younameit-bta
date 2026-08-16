package net.colin.younameit;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class YniConfig {
    private YniConfig() {}

    public static boolean enabled = true;

    public static int itemIdBase = 14000;

    public static int itemIdMax = 32767;

    public static int blockIdHeadroom = 1024;

    public static boolean generateTools = true;
    public static boolean generateArmor = true;

    public static boolean generateRecipes = true;

    public static double ownBlockBonus = 1.6;

    public static boolean fuelReducesFireDamage = true;

    public static boolean fireResistantSetBonus = true;

    public static boolean skipUntexturedBlocks = true;

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
