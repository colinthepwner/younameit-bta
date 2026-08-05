package net.colin.younameit.client;

import net.colin.younameit.client.tex.FaceSelection;
import net.colin.younameit.client.tex.Palette;

import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One {@link Palette} per distinct face a set actually uses.
 *
 * <p>Built eagerly on the main thread, because {@code Palette.of} is pure but the face images it
 * works from were resolved through the model dispatchers, and the painting pass that consumes
 * these runs in parallel. Palettes are keyed by image identity so a block whose six sides collapse
 * to one texture still only computes one palette, which is the overwhelming majority of blocks.
 */
final class FacePalettes {

    private final Map<BufferedImage, Palette> byFace = new IdentityHashMap<>();
    private final FaceSelection selection;
    private final Palette fallback;

    FacePalettes(FaceSelection selection, long seed, boolean allowScatter) {
        this.selection = selection;
        if (selection == null) {
            this.fallback = Palette.of(null, seed, allowScatter);
            return;
        }
        for (BufferedImage face : selection.distinctUsed()) {
            byFace.computeIfAbsent(face, img -> Palette.of(img, seed, allowScatter));
        }
        this.fallback = byFace.isEmpty() ? Palette.of(null, seed, allowScatter) : null;
    }

    Palette forTool() {
        if (selection == null) return fallback;
        Palette p = byFace.get(selection.forTool());
        return p != null ? p : fallback;
    }

    Palette forArmor(int piece) {
        if (selection == null) return fallback;
        Palette p = byFace.get(selection.forArmor(piece));
        return p != null ? p : forTool();
    }
}
