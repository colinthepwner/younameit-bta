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

public final class YniIds {
    private YniIds() {}

    private static final Map<String, Integer> assigned = new TreeMap<>();
    private static boolean dirty = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("younameit-ids.properties");
    }

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

    public static Map<String, Integer> snapshot() {
        return new HashMap<>(assigned);
    }
}
