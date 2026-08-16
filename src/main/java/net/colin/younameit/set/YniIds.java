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

    private static int floorId() {
        return Blocks.blocksList.length;
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
        int floor = floorId();

        int reused = 0, moved = 0, evacuated = 0;
        for (String key : p.stringPropertyNames()) {
            try {
                int value = Integer.parseInt(p.getProperty(key).trim());
                if (value < floor) {

                    evacuated++;
                    dirty = true;
                } else if (isRunFree(taken, value, stride)) {
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
        if (evacuated > 0) {
            YouNameIt.LOGGER.warn(
                    "Moved {} set(s) out of the block id range, where an earlier version had put them. "
                            + "Those items change id, so anything already in a world will follow its "
                            + "saved name rather than its number — keep Useless Numerical installed for "
                            + "that, which is what makes the remap by name possible.",
                    evacuated);
        }

        Slots slots = new Slots(taken, floor);

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
                "Item ids: {} free slot(s) in the item half (ids {}-{}); "
                        + "{} reused, {} reallocated, {} moved out of block space, {} new, "
                        + "{} set(s) could not be served.",
                slots.capacity(), floor, Math.min(YniConfig.itemIdMax, Item.itemsList.length - 1),
                reused, moved, evacuated, fresh, denied);
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

        private int cursor;
        private final int limit;
        private final int free;

        Slots(boolean[] taken, int floor) {
            this.taken = taken;
            this.cursor = Math.max(floor, Math.min(YniConfig.itemIdBase, taken.length - 1));
            this.limit = Math.min(YniConfig.itemIdMax, taken.length - 1);

            int n = 0;
            for (int i = Math.max(0, cursor); i <= limit; i++) {
                if (!taken[i]) n++;
            }
            this.free = n;
        }

        int capacity() { return free; }

        int take(int stride) {
            while (cursor + stride - 1 <= limit) {
                if (isRunFree(taken, cursor, stride)) {
                    int base = cursor;
                    markRun(taken, base, stride);
                    cursor += stride;
                    return base;
                }
                cursor++;
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
