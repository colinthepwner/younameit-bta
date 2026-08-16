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

        for (String t : TOOL_TYPES) VanillaArt.tool(t);
        for (String p : ARMOR_PIECES) VanillaArt.armorIcon(p);
        VanillaArt.armorSheet(1);
        VanillaArt.armorSheet(2);

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

                palettes.add(new FacePalettes(faces, set.id.hashCode(), shouldScatter(set, faces)));
            } catch (Throwable t) {
                skipped++;
                YouNameIt.LOGGER.warn("Could not read the source texture for '{}'.", set.id, t);
            }
        }

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

        for (MaterialSet set : finalSets) {
            bindAll(set, dispatcher);
        }

        YouNameIt.LOGGER.info("Generated {} texture files for {} sets ({} skipped) in {} ms.",
                pack.size(), finalSets.size(), skipped, System.currentTimeMillis() - start);
    }

    private static boolean shouldScatter(MaterialSet set, FaceSelection faces) {
        if (set.block == null) return true;
        try {
            if (set.block.getLogic().isCubeShaped()) return false;

            return faces != null && opaqueFraction(faces.forTool()) < 0.60;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double opaqueFraction(BufferedImage img) {
        if (img == null) return 1.0;
        int w = img.getWidth(), h = img.getHeight();
        if (h > w && h % w == 0) h = w;
        int opaque = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) >= 16) opaque++;
            }
        }
        return opaque / (double) (w * h);
    }

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

            for (int layer = 1; layer <= 2; layer++) {
                SpriteMask sheet = VanillaArt.armorSheet(layer);
                if (sheet == null) continue;
                if (layer == 1) {

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

    private static String textureName(Item item) {
        String key = item.getKey();
        String stem = key.startsWith("item.") ? key.substring("item.".length()) : key;
        return stem.replace(".", "_");
    }

    private void bindIcon(ItemModelDispatcher dispatcher, Item item, boolean handheld) {
        try {

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
