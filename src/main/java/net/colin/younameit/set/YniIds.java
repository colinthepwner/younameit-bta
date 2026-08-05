package net.colin.younameit.set;

import net.colin.younameit.YniConfig;
import net.colin.younameit.YouNameIt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.item.Item;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Allocates and persists set-id → first-item-id, so numeric item ids stay put across launches.
 *
 * <p>This matters more here than in a normal mod. Items are keyed by number in saves, so if a
 * modpack adds one block and every subsequent set shifts by nine, a player's chests quietly turn
 * into the wrong tools. Ids are therefore allocated once, written to disk, and never reused.
 */
public final class YniIds {
    private YniIds() {}

    private static final Map<String, Integer> assigned = new TreeMap<>();
    private static boolean dirty = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("younameit-ids.properties");
    }

    /** Loads the saved map, then allocates ids for any set that does not have one yet. */
    static int load(List<MaterialSet> sets) {
        assigned.clear();
        dirty = false;

        Properties p = new Properties();
        Path path = file();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            } catch (IOException e) {
                YouNameIt.LOGGER.warn("Could not read the id map; ids will be reallocated.", e);
            }
        }

        int stride = Math.max(1, MaterialSet.ITEMS_PER_SET);
        boolean[] taken = occupancy();

        // Saved ids are only honoured if their whole run is still free. If a game update or
        // another mod has moved into one, reallocating is the lesser evil — the alternative is
        // failing to register that set at all.
        int reused = 0, moved = 0;
        for (String key : p.stringPropertyNames()) {
            try {
                int value = Integer.parseInt(p.getProperty(key).trim());
                if (isRunFree(taken, value, stride)) {
                    assigned.put(key, value);
                    markRun(taken, value, stride);
                    reused++;
                } else {
                    moved++;
                    dirty = true;
                }
            } catch (NumberFormatException ignored) {
                dirty = true;
            }
        }

        // Built after the saved ids have claimed their slots, so the capacity it reports is the
        // space actually available to hand out rather than the space that existed before.
        Slots slots = new Slots(taken);

        int fresh = 0, denied = 0;
        for (MaterialSet set : sets) {
            if (assigned.containsKey(set.id)) continue;
            int base = slots.take(stride);
            if (base < 0) {
                denied++;
                continue;
            }
            assigned.put(set.id, base);
            fresh++;
            dirty = true;
        }

        YouNameIt.LOGGER.info(
                "Item ids: {} free slot(s) across the array ({} above the items, {} in unused block space); "
                        + "{} reused, {} reallocated, {} new, {} set(s) could not be served.",
                slots.capacity(), slots.highCapacity(), slots.lowCapacity(), reused, moved, fresh, denied);
        return assigned.size();
    }

    /**
     * Every index in {@code Item.itemsList} that is already spoken for.
     *
     * <p>The block list has to be consulted as well as the item list. BTA gives a block's item the
     * block's own index, so the two arrays overlap across 0..{@code blocksList.length}, and a block
     * that has no item of its own would otherwise look like a free slot right up until something
     * asked for it.
     */
    private static boolean[] occupancy() {
        Item[] items = Item.itemsList;
        boolean[] taken = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) taken[i] = true;
        }
        Block<?>[] blocks = Blocks.blocksList;
        for (int i = 0; i < blocks.length && i < taken.length; i++) {
            if (blocks[i] != null) taken[i] = true;
        }
        return taken;
    }

    /**
     * Hands out free runs from the item array, in the order we are willing to use them.
     *
     * <p>The array is one flat {@code Item[32768]}. Blocks take the low end of it — each block's
     * item sits at the block's own id — and loose items start at 16384 and climb. Walking upward
     * from the highest occupied slot, which is all this used to do, therefore only ever sees
     * whatever is left above the last mod's items: with Better O' Plenty installed that is about
     * 1,700 slots out of 32,768, enough for 188 sets of the 1,083 that exist. The other ~14,000
     * free slots are sitting in the block half, unused, because barely two thousand blocks exist.
     *
     * <p>So both halves are used. The item half first, ascending, since nothing else wants it.
     * Then the block half, descending from the top — mods number their blocks upward from the
     * low hundreds, so filling from 16383 down keeps our items as far as possible from where the
     * next mod will register, and {@code blockIdHeadroom} reserves a run above the highest block
     * that we will not touch at all.
     *
     * <p>{@code Item}'s constructor takes the index verbatim, applies no offset, and imposes no
     * low-id rule — it only refuses a slot that is already occupied — so an index in the block
     * half is a perfectly ordinary item id. Nothing infers "this is a block" from the number; the
     * block list is what answers that, and there is no block at the indices we take.
     */
    private static final class Slots {
        private final boolean[] taken;

        private int highCursor;
        private final int highLimit;
        private int lowCursor;
        private final int lowFloor;

        private final int highFree;
        private final int lowFree;

        Slots(boolean[] taken) {
            this.taken = taken;

            int highestItem = 0;
            for (int i = 0; i < taken.length; i++) {
                if (taken[i]) highestItem = i;
            }
            this.highCursor = Math.max(YniConfig.itemIdBase, highestItem + 1);
            this.highLimit = Math.min(YniConfig.itemIdMax, taken.length - 1);

            // Keep clear of the blocks that exist and of a headroom run above them, so a mod
            // added later still has somewhere to put its blocks without colliding with us.
            int blockTop = Math.min(Blocks.blocksList.length, taken.length) - 1;
            this.lowFloor = Math.max(0, Blocks.highestBlockId + Math.max(0, YniConfig.blockIdHeadroom));
            this.lowCursor = blockTop;

            this.highFree = count(highCursor, highLimit);
            this.lowFree = count(lowFloor, blockTop);
        }

        private int count(int from, int to) {
            int n = 0;
            for (int i = Math.max(0, from); i <= Math.min(to, taken.length - 1); i++) {
                if (!taken[i]) n++;
            }
            return n;
        }

        int capacity() { return highFree + lowFree; }
        int highCapacity() { return highFree; }
        int lowCapacity() { return lowFree; }

        /** The base of a free run of {@code stride} slots, or -1 when the array is full. */
        int take(int stride) {
            while (highCursor + stride - 1 <= highLimit) {
                if (isRunFree(taken, highCursor, stride)) {
                    int base = highCursor;
                    markRun(taken, base, stride);
                    highCursor += stride;
                    return base;
                }
                highCursor++;
            }
            while (lowCursor - stride + 1 >= lowFloor) {
                int base = lowCursor - stride + 1;
                if (isRunFree(taken, base, stride)) {
                    markRun(taken, base, stride);
                    lowCursor = base - 1;
                    return base;
                }
                lowCursor--;
            }
            return -1;
        }
    }

    /** True when every slot in [base, base+stride) exists and is unoccupied. */
    private static boolean isRunFree(boolean[] taken, int base, int stride) {
        if (base < 0 || base + stride > taken.length) return false;
        for (int i = base; i < base + stride; i++) {
            if (taken[i]) return false;
        }
        return true;
    }

    private static void markRun(boolean[] taken, int base, int stride) {
        for (int i = base; i < base + stride && i < taken.length; i++) {
            taken[i] = true;
        }
    }

    static Integer idFor(String setId) {
        return assigned.get(setId);
    }

    static void save() {
        if (!dirty) return;
        Properties p = new Properties();
        assigned.forEach((k, v) -> p.setProperty(k, String.valueOf(v)));
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "You Name It! - set id -> first item id. Deleting this reshuffles item ids.");
            }
            dirty = false;
        } catch (IOException e) {
            YouNameIt.LOGGER.warn("Could not write the id map; ids may move next launch.", e);
        }
    }

    /** Exposed for the client renderer's diagnostics. */
    public static Map<String, Integer> snapshot() {
        return new HashMap<>(assigned);
    }
}
