package net.colin.younameit.client.tex;

import net.colin.younameit.YouNameIt;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.item.model.ItemModelDispatcher;
import net.minecraft.core.block.Block;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class BlockTexture {
    private BlockTexture() {}

    public static BufferedImage of(Block<?> block, Item ingredient) {
        BufferedImage img = block != null ? of(block) : null;
        return img != null ? img : ofItem(ingredient);
    }

    public static BufferedImage of(Block<?> block) {
        return imageFor(iconFor(block), block == null ? "?" : block.getKey());
    }

    public static FaceSelection facesOf(Block<?> block, Item ingredient, int metadata) {
        if (block != null) {

            BufferedImage top = faceOf(block, Side.TOP, metadata);
            BufferedImage bottom = faceOf(block, Side.BOTTOM, metadata);
            List<BufferedImage> sides = new ArrayList<>(4);
            for (Side side : new Side[]{Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST}) {
                BufferedImage img = faceOf(block, side, metadata);
                if (img != null) sides.add(img);
            }
            FaceSelection selection = FaceSelection.of(top, bottom, sides);
            if (selection != null) return selection;
        }
        BufferedImage fallback = of(block, ingredient);
        return fallback == null ? null : FaceSelection.single(fallback);
    }

    private static BufferedImage faceOf(Block<?> block, Side side, int metadata) {
        try {
            BlockModel<?> model = BlockModelDispatcher.getInstance().getDispatch(block);
            if (model == null) return null;
            IconCoordinate icon = model.getParticleTexture(side, metadata);
            return icon == null ? null : imageFor(icon, block.getKey());
        } catch (Throwable ignored) {

            return null;
        }
    }

    public static BufferedImage ofItem(Item item) {
        if (item == null) return null;
        try {
            ItemModel model = ItemModelDispatcher.getInstance().getDispatch(new ItemStack(item));
            if (model == null) return null;
            return imageFor(model.getIcon(null, new ItemStack(item)), item.getKey());
        } catch (Throwable t) {
            YouNameIt.LOGGER.debug("No item model for {}", item.getKey(), t);
            return null;
        }
    }

    private static BufferedImage imageFor(IconCoordinate icon, String what) {
        if (icon == null) return null;
        try {
            String path = icon.parentAtlas.getSourceImageId(icon.namespaceId) + ".png";
            BufferedImage img = VanillaArt.read(path);
            if (img != null) return img;
            YouNameIt.LOGGER.debug("No image at {} for {}", path, what);
        } catch (Throwable t) {
            YouNameIt.LOGGER.debug("Could not resolve a texture path for {}", what, t);
        }
        return null;
    }

    private static IconCoordinate iconFor(Block<?> block) {
        if (block == null) return null;
        try {
            BlockModel<?> model = BlockModelDispatcher.getInstance().getDispatch(block);
            if (model == null) return null;

            for (Side side : new Side[]{Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST, Side.TOP, Side.BOTTOM}) {
                try {
                    IconCoordinate icon = model.getParticleTexture(side, 0);
                    if (icon != null) return icon;
                } catch (Throwable ignored) {

                }
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.debug("No block model for {}", block.getKey(), t);
        }
        return null;
    }
}
