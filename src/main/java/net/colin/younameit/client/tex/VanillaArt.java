package net.colin.younameit.client.tex;

import net.colin.younameit.YouNameIt;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the vanilla sprites the masks are derived from, and caches the resulting masks.
 *
 * <p>Everything is read through the texture pack list rather than the classloader, so an active
 * resource pack's tools are what we trace.
 */
public final class VanillaArt {
    private VanillaArt() {}

    private static final String ITEM_DIR = "/assets/minecraft/textures/item/";
    private static final String ARMOR_DIR = "/assets/minecraft/textures/armor/";

    /** Two materials whose sprites differ everywhere the material shows. */
    private static final String MAT_A = "iron";
    private static final String MAT_B = "diamond";

    private static final Map<String, SpriteMask> cache = new HashMap<>();

    public static void clearCache() {
        cache.clear();
    }

    /** {@code pickaxe}, {@code axe}, {@code shovel}, {@code hoe}, {@code sword}. */
    public static SpriteMask tool(String type) {
        return cached("tool:" + type,
                ITEM_DIR + "tool_" + type + "_" + MAT_A + ".png",
                ITEM_DIR + "tool_" + type + "_" + MAT_B + ".png");
    }

    /** {@code helmet}, {@code chestplate}, {@code leggings}, {@code boots}. */
    public static SpriteMask armorIcon(String piece) {
        return cached("armor:" + piece,
                ITEM_DIR + "armor_" + piece + "_" + MAT_A + ".png",
                ITEM_DIR + "armor_" + piece + "_" + MAT_B + ".png");
    }

    /** The worn-armour sheets: layer 1 covers head/chest/boots, layer 2 the legs. */
    public static SpriteMask armorSheet(int layer) {
        return cached("sheet:" + layer,
                ARMOR_DIR + MAT_A + "_" + layer + ".png",
                ARMOR_DIR + MAT_B + "_" + layer + ".png");
    }

    private static SpriteMask cached(String key, String pathA, String pathB) {
        if (cache.containsKey(key)) return cache.get(key);
        SpriteMask mask = SpriteMask.derive(read(pathA), read(pathB));
        if (mask == null) {
            YouNameIt.LOGGER.warn("Could not build a mask from {} / {}; that piece will be skipped.", pathA, pathB);
        }
        cache.put(key, mask);
        return mask;
    }

    public static BufferedImage read(String path) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.texturePackList != null) {
                try (InputStream in = mc.texturePackList.getResourceAsStream(path)) {
                    if (in != null) {
                        BufferedImage img = ImageIO.read(in);
                        if (img != null) return img;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the classpath.
        }
        try (InputStream in = VanillaArt.class.getResourceAsStream(path)) {
            if (in != null) return ImageIO.read(in);
        } catch (Throwable ignored) {
            // Reported by the caller as a missing mask.
        }
        return null;
    }
}
