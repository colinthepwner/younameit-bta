package net.colin.younameit.client.tex;

import net.colin.younameit.YouNameIt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texturepack.ManifestFallback;
import net.minecraft.client.render.texturepack.TexturePack;
import net.minecraft.client.render.texturepack.TexturePackList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory texture pack holding everything we generate.
 *
 * <p>This is the whole reason the mod needs no mixin to get its art on screen. Every path BTA
 * uses to turn a texture id into pixels — the atlas stitcher's sizing pass, its pixel pass, and
 * {@code bindTexture} for armour world models — ends at
 * {@code texturePackList.getHighestPriorityTexturePackWithFile(path)} followed by
 * {@code pack.getResourceAsStream(path)}. Serving both from one pack covers all three.
 *
 * <p>It is inserted at index 0, the <em>lowest</em> priority, because both lookups walk
 * {@code selectedPacks} from the end. A real resource pack that happens to define one of our
 * paths therefore still wins, which is what a user would expect.
 */
public final class GeneratedPack extends TexturePack {

    private static GeneratedPack instance;

    /** Absolute asset path -> encoded PNG bytes. */
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    private GeneratedPack() {
        this.packId = YouNameIt.MOD_ID + ":generated";
        this.fileName = YouNameIt.MOD_ID + "-generated";
        // Required, not cosmetic. Anything sitting in selectedPacks gets walked by
        // CustomAtlasHandler.beforeRefreshTextures, which calls texturePack.manifest.getName()
        // with no null check — leaving the manifest unset crashes the game during the very
        // texture refresh this pack exists to serve.
        this.manifest = new ManifestFallback(
                "You Name It! (generated)",
                "Procedurally generated tool and armour art.",
                "Created at load time; not a file on disk.",
                1);
    }

    public static GeneratedPack get() {
        if (instance == null) {
            instance = new GeneratedPack();
        }
        return instance;
    }

    /**
     * Adds the pack to the live pack list if it is not already there. Safe to call repeatedly;
     * {@code refresh()} and {@code updateAvailableTexturePacks()} never clear {@code selectedPacks},
     * so in practice one successful call is enough for the whole session.
     *
     * <p>Deliberately not {@code setTexturePack()} — that would persist us into
     * {@code GameSettings.SKIN} and leave a phantom pack in the user's settings file.
     */
    public static boolean install() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return false;
            TexturePackList list = mc.texturePackList;
            if (list == null) return false;
            GeneratedPack pack = get();
            if (!list.selectedPacks.contains(pack)) {
                list.selectedPacks.add(0, pack);
                YouNameIt.LOGGER.info("Installed the generated texture pack.");
            }
            return true;
        } catch (Throwable t) {
            YouNameIt.LOGGER.error("Could not install the generated texture pack; art will be missing.", t);
            return false;
        }
    }

    /** Stores an image under an absolute asset path such as {@code /assets/younameit/textures/item/x.png}. */
    public void put(String path, BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            ImageIO.write(image, "png", out);
            files.put(path, out.toByteArray());
        } catch (IOException e) {
            YouNameIt.LOGGER.warn("Could not encode generated texture {}", path, e);
        }
    }

    public int size() {
        return files.size();
    }

    public void clear() {
        files.clear();
    }

    @Override
    public boolean hasFile(String path) {
        return path != null && files.containsKey(path);
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        byte[] data = path == null ? null : files.get(path);
        // Falling through to super keeps ordinary classpath lookups working for anything
        // that is not ours, which matters because this pack sits in the shared list.
        return data != null ? new ByteArrayInputStream(data) : super.getResourceAsStream(path);
    }

    @Override
    public String toString() {
        return "GeneratedPack[" + files.size() + " files]";
    }
}
