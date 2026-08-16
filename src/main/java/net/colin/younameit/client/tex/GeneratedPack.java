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

public final class GeneratedPack extends TexturePack {

    private static GeneratedPack instance;

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    private GeneratedPack() {
        this.packId = YouNameIt.MOD_ID + ":generated";
        this.fileName = YouNameIt.MOD_ID + "-generated";

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

        return data != null ? new ByteArrayInputStream(data) : super.getResourceAsStream(path);
    }

    @Override
    public String toString() {
        return "GeneratedPack[" + files.size() + " files]";
    }
}
