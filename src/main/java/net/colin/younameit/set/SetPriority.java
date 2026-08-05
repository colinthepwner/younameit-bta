package net.colin.younameit.set;

import net.colin.younameit.YouNameIt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Decides the order sets are offered ids in, which decides who survives when there are not enough.
 *
 * <p>There is a hard ceiling on how many sets can exist: BTA's item array is 32,768 wide and every
 * block, every item and every mod shares it, so a large pack will always want more gear than the
 * game has room for. That is not avoidable. What is avoidable is <em>choosing badly</em>.
 *
 * <p>The list used to be sorted by set id, which reads {@code <namespace>_<path>}, so the order was
 * alphabetical by mod name. With Better O' Plenty installed and room for 188 of 1,083 sets, every
 * surviving set was {@code betteroplenty_*} — "b" sorts before "m" — and the base game got nothing
 * at all. Every vanilla material silently lost its recipes, which is exactly the sort of failure
 * that produces a clean log and a broken game.
 *
 * <p>So the order is fair by construction rather than incidental:
 *
 * <ul>
 *   <li><b>Round-robin across namespaces.</b> Each mod contributes its first set, then its second,
 *       and so on. A mod that registers two thousand blocks can no longer starve one that
 *       registers ten, and no mod's position in the alphabet means anything. The base game goes
 *       first within each round, being the one material set every player is guaranteed to have.</li>
 *   <li><b>Round-robin across materials within a namespace.</b> A block split into sixteen colours
 *       offers its first colour in the first round and the rest later, so a tight budget buys one
 *       of everything before it buys a second shade of anything.</li>
 *   <li><b>Quality decides the order inside a round</b>, so if the budget runs out mid-round it is
 *       a flower rather than an ore that misses out.</li>
 * </ul>
 *
 * <p>Order is a pure function of what is installed, so ids stay reproducible launch to launch; the
 * persisted id map in {@link YniIds} then keeps them stable even when the order does change.
 */
final class SetPriority {
    private SetPriority() {}

    /** The base game first, then every mod alphabetically, so rounds are deterministic. */
    private static final String BASE_NAMESPACE = "minecraft";

    static List<MaterialSet> rank(List<MaterialSet> sets) {
        // namespace -> material -> that material's variants, best first
        Map<String, Map<String, List<MaterialSet>>> byNamespace = new TreeMap<>();
        for (MaterialSet set : sets) {
            byNamespace
                    .computeIfAbsent(namespaceOf(set), k -> new TreeMap<>())
                    .computeIfAbsent(materialOf(set), k -> new ArrayList<>())
                    .add(set);
        }

        // Flatten each namespace into one list whose Nth entry is "the Nth-best thing this mod has
        // to offer", by interleaving its materials. Sorting the variants of a material by metadata
        // keeps a block's base form ahead of its recolours.
        Map<String, List<MaterialSet>> queues = new LinkedHashMap<>();
        List<String> order = new ArrayList<>(byNamespace.keySet());
        order.sort(Comparator.comparing((String ns) -> ns.equals(BASE_NAMESPACE) ? 0 : 1)
                .thenComparing(Comparator.naturalOrder()));
        for (String ns : order) {
            queues.put(ns, interleave(byNamespace.get(ns)));
        }

        List<MaterialSet> out = new ArrayList<>(sets.size());
        for (int round = 0; out.size() < sets.size(); round++) {
            boolean any = false;
            for (List<MaterialSet> queue : queues.values()) {
                if (round < queue.size()) {
                    out.add(queue.get(round));
                    any = true;
                }
            }
            if (!any) break;   // cannot happen while out.size() < sets.size(), but do not spin
        }

        if (queues.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<MaterialSet>> e : queues.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append(' ').append(e.getValue().size());
            }
            YouNameIt.LOGGER.info("Set priority: {} namespace(s) taking turns - {}.", queues.size(), sb);
        }
        return out;
    }

    /** One material's best variant, then the next material's, and so on round by round. */
    private static List<MaterialSet> interleave(Map<String, List<MaterialSet>> materials) {
        List<List<MaterialSet>> lists = new ArrayList<>(materials.values());
        int total = 0;
        for (List<MaterialSet> variants : lists) {
            variants.sort(Comparator.comparingInt((MaterialSet s) -> s.metadata).thenComparing(s -> s.id));
            total += variants.size();
        }
        // Best material first inside each round, judged on its base variant.
        lists.sort(Comparator.comparingInt((List<MaterialSet> l) -> -score(l.get(0)))
                .thenComparing(l -> l.get(0).id));

        List<MaterialSet> out = new ArrayList<>(total);
        for (int round = 0; out.size() < total; round++) {
            for (List<MaterialSet> variants : lists) {
                if (round < variants.size()) out.add(variants.get(round));
            }
        }
        return out;
    }

    /**
     * How much a material deserves gear, when not everything can have it.
     *
     * <p>Every signal here is server-safe — archetype, shape and hardness are all decided from the
     * block itself — because item registration has to produce identical ids on a dedicated server
     * or every id desyncs.
     */
    private static int score(MaterialSet set) {
        int v;
        switch (set.stats.archetype) {
            case GEM:     v = 100; break;
            case METAL:   v = 95;  break;
            case STONE:   v = 80;  break;
            case WOOD:    v = 75;  break;
            case BONE:    v = 60;  break;
            case GLASS:   v = 50;  break;
            case SOFT:    v = 40;  break;
            case FOOD:    v = 25;  break;
            case PLANT:   v = 20;  break;
            default:      v = 10;  break;
        }
        // A full cube is a building material you stockpile; a lever, a torch or a flower is not.
        if (set.block != null && cubeShaped(set.block)) v += 30;
        // A loose item is something you already hold by definition, so it needs no shape test.
        if (set.block == null) v += 20;
        // Harder things read as more tool-worthy, but only as a tie-break.
        v += Math.min(10, (int) Math.round(set.stats.score / 5.0));
        return v;
    }

    private static boolean cubeShaped(net.minecraft.core.block.Block<?> block) {
        try {
            return block.getLogic().isCubeShaped();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The mod a set came from. */
    private static String namespaceOf(MaterialSet set) {
        try {
            if (set.block != null) return set.block.namespaceId().namespace();
        } catch (Throwable ignored) {
            // Fall through to the ingredient.
        }
        try {
            if (set.ingredient != null) return set.ingredient.namespaceID.namespace();
        } catch (Throwable ignored) {
            // Fall through to the catch-all.
        }
        return "";
    }

    /** What a set is made of, so a block's colour variants queue up behind each other. */
    private static String materialOf(MaterialSet set) {
        try {
            if (set.block != null) return "b" + set.block.id();
        } catch (Throwable ignored) {
            // Fall through to the ingredient.
        }
        return "i" + (set.ingredient == null ? 0 : set.ingredient.id);
    }
}
