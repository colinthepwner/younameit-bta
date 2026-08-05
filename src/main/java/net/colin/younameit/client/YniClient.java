package net.colin.younameit.client;

import net.colin.younameit.YniConfig;
import net.colin.younameit.YouNameIt;
import net.colin.younameit.client.tex.BlockTexture;
import net.colin.younameit.client.tex.Composer;
import net.colin.younameit.client.tex.FaceSelection;
import net.colin.younameit.client.tex.GeneratedPack;
import net.colin.younameit.client.tex.Palette;
import net.colin.younameit.client.tex.SpriteMask;
import net.colin.younameit.client.tex.VanillaArt;
import net.colin.younameit.set.MaterialSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.model.ItemModelDispatcher;
import net.minecraft.client.render.item.model.ItemModelStandard;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.core.item.Item;
import turniplabs.halplibe.util.ModelEntrypoint;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Generates every texture and hooks up every item model.
 *
 * <p>Runs from {@code initItemModels}, which lands in exactly the right gap in BTA's startup:
 * block models are already registered (so a block's texture can be resolved, modded ones
 * included), the texture pack list exists, and {@code textureManager.refreshTextures()} — the
 * actual atlas stitch — has not happened yet. Asking {@code TextureRegistry.getTexture} for a key
 * creates the icon slot on demand, and the stitch that follows pulls the pixels out of our
 * generated pack.
 */
@Environment(EnvType.CLIENT)
public final class YniClient implements ModelEntrypoint {

    private static final String[] TOOL_TYPES = {"pickaxe", "axe", "shovel", "hoe", "sword"};
    private static final String[] ARMOR_PIECES = {"helmet", "chestplate", "leggings", "boots"};

    @Override
    public void initItemModels(ItemModelDispatcher dispatcher) {
        List<MaterialSet> sets = YouNameIt.getSets();
        if (sets.isEmpty()) return;

        long start = System.currentTimeMillis();
        VanillaArt.clearCache();
        GeneratedPack pack = GeneratedPack.get();
        pack.clear();
        if (!GeneratedPack.install()) {
            YouNameIt.LOGGER.error("No texture pack list available; generated art cannot be shown.");
            return;
        }

        // Warm the mask cache on this thread. The masks are shared by every set, and deriving
        // them lazily from several threads at once would race on the cache map.
        for (String t : TOOL_TYPES) VanillaArt.tool(t);
        for (String p : ARMOR_PIECES) VanillaArt.armorIcon(p);
        VanillaArt.armorSheet(1);
        VanillaArt.armorSheet(2);

        // Resolving each material's source texture has to happen here: it reads through the
        // texture pack list and the model dispatchers, neither of which is thread-safe.
        List<FacePalettes> palettes = new ArrayList<>(sets.size());
        List<MaterialSet> paintable = new ArrayList<>(sets.size());
        int skipped = 0;
        for (MaterialSet set : sets) {
            try {
                FaceSelection faces = BlockTexture.facesOf(set.block, set.ingredient, set.metadata);
                if (faces == null && YniConfig.skipUntexturedBlocks) {
                    skipped++;
                    continue;
                }
                paintable.add(set);
                // Seeded off the set id so the scatter is identical every launch and every
                // resource reload — gear that reshuffled its own texture would be maddening.
                palettes.add(new FacePalettes(faces, set.id.hashCode(), shouldScatter(set, faces)));
            } catch (Throwable t) {
                skipped++;
                YouNameIt.LOGGER.warn("Could not read the source texture for '{}'.", set.id, t);
            }
        }

        // Painting and PNG-encoding is pure CPU over private buffers, so it parallelises cleanly.
        // This is the bulk of the work — eleven images per set — and on a large modpack it is the
        // difference between a noticeable stall and an imperceptible one.
        final List<MaterialSet> finalSets = paintable;
        final List<FacePalettes> finalPalettes = palettes;
        IntStream.range(0, finalSets.size()).parallel().forEach(i -> {
            MaterialSet set = finalSets.get(i);
            try {
                paint(set, finalPalettes.get(i), pack);
            } catch (Throwable t) {
                YouNameIt.LOGGER.warn("Could not generate art for '{}'.", set.id, t);
            }
        });

        // Model dispatch is a shared map, so bind on one thread once the images exist.
        for (MaterialSet set : finalSets) {
            bindAll(set, dispatcher);
        }

        YouNameIt.LOGGER.info("Generated {} texture files for {} sets ({} skipped) in {} ms.",
                pack.size(), finalSets.size(), skipped, System.currentTimeMillis() - start);
    }

    /**
     * Whether this material's texture should be scattered rather than tiled.
     *
     * <p>Loose items always scatter. Blocks scatter when they are not full cubes — a lever, a
     * button, a torch, a rail, a flower. Their textures are drawn as a small object on an empty
     * field exactly like an item sprite, so tiling one produces a lonely lever floating on a
     * smear, whereas piling it up reads as a material.
     *
     * <p>Cube-shaped is the right test rather than counting transparent pixels: glass is 27%
     * opaque and a bone is 17%, so no coverage threshold can separate "a tile with holes in it"
     * from "an object on an empty background", but "is it a full block" answers it directly.
     * {@code isCubeShaped} also lives on BlockLogic, so it agrees between client and server.
     */
    private static boolean shouldScatter(MaterialSet set, FaceSelection faces) {
        if (set.block == null) return true;
        try {
            if (set.block.getLogic().isCubeShaped()) return false;
            // Not a cube is not enough on its own. A cactus is inset, so it fails the cube test,
            // but its texture is still a proper repeating tile — 87% opaque with a spine ridge
            // running down it — and scattering shreds the ridge into speckle. A lever or a torch
            // is a small object on a mostly empty field, and that is what actually wants piling
            // up. So the texture has to be genuinely sparse as well.
            return faces != null && opaqueFraction(faces.forTool()) < 0.60;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double opaqueFraction(BufferedImage img) {
        if (img == null) return 1.0;
        int w = img.getWidth(), h = img.getHeight();
        if (h > w && h % w == 0) h = w;   // animation strip: judge the first frame only
        int opaque = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) >= 16) opaque++;
            }
        }
        return opaque / (double) (w * h);
    }

    /** Pure image work — safe to run on any thread. */
    private void paint(MaterialSet set, FacePalettes palettes, GeneratedPack pack) {
        boolean shiny = set.stats.shiny;
        boolean flat = set.stats.flatColour;

        if (YniConfig.generateTools) {
            Item[] tools = {set.pickaxe, set.axe, set.shovel, set.hoe, set.sword};
            Palette toolPalette = palettes.forTool();
            for (int i = 0; i < TOOL_TYPES.length; i++) {
                if (tools[i] == null) continue;
                SpriteMask mask = VanillaArt.tool(TOOL_TYPES[i]);
                if (mask == null) continue;
                pack.put(itemPath(textureName(tools[i])), Composer.tool(mask, toolPalette, shiny, flat, i));
            }
        }

        if (YniConfig.generateArmor) {
            Item[] armor = set.armorItems();
            for (int i = 0; i < ARMOR_PIECES.length; i++) {
                if (armor[i] == null) continue;
                SpriteMask mask = VanillaArt.armorIcon(ARMOR_PIECES[i]);
                if (mask == null) continue;
                pack.put(itemPath(textureName(armor[i])),
                        Composer.armorIcon(mask, palettes.forArmor(i), shiny, flat, i + 1));
            }

            // The worn model. BTA binds /assets/<ns>/textures/armor/<material>_1.png for the
            // head, chest and boots, and _2.png for the legs — the material's own id is the
            // filename, which is why SetRegistrar names the ArmorMaterial after the set.
            //
            // Layer 1 carries the head, chest and boots on one sheet, so it cannot honour a
            // per-piece face; the chestplate's is the right one to show, being the largest and
            // the one the odd face was chosen for.
            for (int layer = 1; layer <= 2; layer++) {
                SpriteMask sheet = VanillaArt.armorSheet(layer);
                if (sheet == null) continue;
                if (layer == 1) {
                    // Head, chest and boots all live on this one sheet, so it is painted per
                    // region rather than from a single face.
                    pack.put(armorPath(set.id, layer), Composer.armorSheetLayer1(sheet,
                            palettes.forArmor(FaceSelection.HELMET),
                            palettes.forArmor(FaceSelection.CHESTPLATE),
                            palettes.forArmor(FaceSelection.BOOTS),
                            shiny, flat, layer));
                } else {
                    pack.put(armorPath(set.id, layer), Composer.armorSheet(sheet,
                            palettes.forArmor(FaceSelection.LEGGINGS), shiny, flat, layer));
                }
            }
        }
    }

    /** Touches the shared model dispatcher, so main thread only. */
    private void bindAll(MaterialSet set, ItemModelDispatcher dispatcher) {
        if (YniConfig.generateTools) {
            Item[] tools = {set.pickaxe, set.axe, set.shovel, set.hoe, set.sword};
            for (int i = 0; i < TOOL_TYPES.length; i++) {
                if (tools[i] != null) bindIcon(dispatcher, tools[i], true);
            }
        }
        if (YniConfig.generateArmor) {
            Item[] armor = set.armorItems();
            for (int i = 0; i < ARMOR_PIECES.length; i++) {
                if (armor[i] != null) bindIcon(dispatcher, armor[i], false);
            }
        }
    }

    /**
     * The atlas name an item's own model will ask for.
     *
     * <p>Not a name of our choosing. {@code ItemModelStandard(item, modId)} sets its icon in the
     * constructor, before we can touch it, deriving the name from the item's key — and asking
     * {@code TextureRegistry} for a name is what creates the atlas slot, so that slot exists
     * whatever we do next. Overwriting {@code model.icon} afterwards with a differently-named
     * texture therefore did not replace that slot, it added a second one, and the first stayed
     * empty forever: two slots per item, half of them blank, and a "could not be found" warning
     * for every one. Generating the art under the name the constructor already asked for collapses
     * the pair back into one.
     *
     * <p>Recomputed from the key by the same expression the constructor uses rather than
     * reassembled from the set id, so the two cannot drift apart.
     */
    private static String textureName(Item item) {
        String key = item.getKey();
        String stem = key.startsWith("item.") ? key.substring("item.".length()) : key;
        return stem.replace(".", "_");
    }

    /**
     * @param handheld true for tools. Vanilla gives every tool four HANDHELD display positions so
     *                 it is gripped at an angle in first and third person; an item model without
     *                 them falls back to the flat, generic item pose, which is why generated tools
     *                 looked wrong in hand while their icons were fine. Armour keeps the default
     *                 pose, exactly as vanilla armour does.
     */
    private void bindIcon(ItemModelDispatcher dispatcher, Item item, boolean handheld) {
        try {
            // This constructor already resolved the icon from the item's key. Asking for the same
            // name returns the same slot rather than making a new one, which is the whole point of
            // painting under that name — see textureName. The stitch that follows this entrypoint
            // is what pulls the pixels out of the generated pack.
            ItemModelStandard model = new ItemModelStandard(item, YouNameIt.MOD_ID);
            model.icon = TextureRegistry.getTexture(YouNameIt.MOD_ID + ":item/" + textureName(item));
            if (handheld) {
                model.setDisplayPos("firstperson_righthand", ItemModelDispatcher.HANDHELD_FIRST_PERSON_RIGHT_HAND)
                        .setDisplayPos("firstperson_lefthand", ItemModelDispatcher.HANDHELD_FIRST_PERSON_LEFT_HAND)
                        .setDisplayPos("thirdperson_righthand", ItemModelDispatcher.HANDHELD_THIRD_PERSON_RIGHT_HAND)
                        .setDisplayPos("thirdperson_lefthand", ItemModelDispatcher.HANDHELD_THIRD_PERSON_LEFT_HAND);
            }
            dispatcher.addDispatch(model);
        } catch (Throwable t) {
            YouNameIt.LOGGER.warn("Could not bind the icon for {}", item.getKey(), t);
        }
    }

    private static String itemPath(String name) {
        return "/assets/" + YouNameIt.MOD_ID + "/textures/item/" + name + ".png";
    }

    private static String armorPath(String setId, int layer) {
        return "/assets/" + YouNameIt.MOD_ID + "/textures/armor/" + setId + "_" + layer + ".png";
    }

    @Override public void initBlockModels(net.minecraft.client.render.block.model.BlockModelDispatcher d) {}
    @Override public void initEntityModels(net.minecraft.client.render.EntityRendererDispatcher d) {}
    @Override public void initTileEntityModels(net.minecraft.client.render.TileEntityRenderDispatcher d) {}
    @Override public void initBlockColors(net.minecraft.client.render.block.color.BlockColorDispatcher d) {}
}
