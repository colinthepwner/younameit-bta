package net.colin.younameit.client.tex;

import net.colin.younameit.YouNameIt;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class VanillaArt {
    private VanillaArt() {}

    private static final String ITEM_DIR = "/assets/minecraft/textures/item/";
    private static final String ARMOR_DIR = "/assets/minecraft/textures/armor/";

    private static final String MAT_A = "iron";
    private static final String MAT_B = "diamond";

    private static final Map<String, SpriteMask> cache = new HashMap<>();

    public static void clearCache() {
        cache.clear();
    }

    public static SpriteMask tool(String type) {
        return cached("tool:" + type,
                ITEM_DIR + "tool_" + type + "_" + MAT_A + ".png",
                ITEM_DIR + "tool_" + type + "_" + MAT_B + ".png");
    }

    public static SpriteMask armorIcon(String piece) {
        return cached("armor:" + piece,
                ITEM_DIR + "armor_" + piece + "_" + MAT_A + ".png",
                ITEM_DIR + "armor_" + piece + "_" + MAT_B + ".png");
    }

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

        }
        try (InputStream in = VanillaArt.class.getResourceAsStream(path)) {
            if (in != null) return ImageIO.read(in);
        } catch (Throwable ignored) {

        }
        return null;
    }
}
