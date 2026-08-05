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

/**
 * Finds the image a block is drawn with.
 *
 * <p>Asking the block's own model for its particle texture is what makes this work for modded
 * blocks as well as vanilla ones: every {@code BlockModel} must answer
 * {@code getParticleTexture}, whatever shape the block actually is, so there is no per-mod
 * special casing. The resulting icon is turned back into a file path and read through the texture
 * pack list, which means resource packs are honoured.
 */
public final class BlockTexture {
    private BlockTexture() {}

    /**
     * The image for a material: the block's face when there is a block, otherwise the item's own
     * inventory icon. Loose items like feathers and bone only have the latter.
     */
    public static BufferedImage of(Block<?> block, Item ingredient) {
        BufferedImage img = block != null ? of(block) : null;
        return img != null ? img : ofItem(ingredient);
    }

    public static BufferedImage of(Block<?> block) {
        return imageFor(iconFor(block), block == null ? "?" : block.getKey());
    }

    /**
     * Which face each generated piece should be drawn from.
     *
     * <p>All six sides are read rather than the first one that resolves, because "the first face
     * that happens to answer" is arbitrary: for a pumpkin it might be the carved front, which then
     * ends up stamped across an entire tool set.
     */
    public static FaceSelection facesOf(Block<?> block, Item ingredient, int metadata) {
        if (block != null) {
            // Faces are kept apart by position, because which side a texture sits on is what
            // decides where it belongs on a suit of armour — see FaceSelection.of.
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

    /**
     * One face of a block at a given metadata.
     *
     * <p>Metadata matters: a single WOOL block holds all sixteen colours, so asking for metadata 0
     * every time is what made every colour of wool produce white gear.
     */
    private static BufferedImage faceOf(Block<?> block, Side side, int metadata) {
        try {
            BlockModel<?> model = BlockModelDispatcher.getInstance().getDispatch(block);
            if (model == null) return null;
            IconCoordinate icon = model.getParticleTexture(side, metadata);
            return icon == null ? null : imageFor(icon, block.getKey());
        } catch (Throwable ignored) {
            // A model that refuses a side simply contributes nothing.
            return null;
        }
    }

    /** Reads a plain item's inventory icon straight off its registered model. */
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
            // The sides are more representative than the top for most blocks; fall back around
            // the cube for models that only define some faces.
            for (Side side : new Side[]{Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST, Side.TOP, Side.BOTTOM}) {
                try {
                    IconCoordinate icon = model.getParticleTexture(side, 0);
                    if (icon != null) return icon;
                } catch (Throwable ignored) {
                    // Some models throw for sides they do not use; try the next one.
                }
            }
        } catch (Throwable t) {
            YouNameIt.LOGGER.debug("No block model for {}", block.getKey(), t);
        }
        return null;
    }
}
