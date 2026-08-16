package net.colin.younameit.set;

import net.colin.younameit.YouNameIt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class SetPriority {
    private SetPriority() {}

    private static final String BASE_NAMESPACE = "minecraft";

    static List<MaterialSet> rank(List<MaterialSet> sets) {

        Map<String, Map<String, List<MaterialSet>>> byNamespace = new TreeMap<>();
        for (MaterialSet set : sets) {
            byNamespace
                    .computeIfAbsent(namespaceOf(set), k -> new TreeMap<>())
                    .computeIfAbsent(materialOf(set), k -> new ArrayList<>())
                    .add(set);
        }

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
            if (!any) break;
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

    private static List<MaterialSet> interleave(Map<String, List<MaterialSet>> materials) {
        List<List<MaterialSet>> lists = new ArrayList<>(materials.values());
        int total = 0;
        for (List<MaterialSet> variants : lists) {
            variants.sort(Comparator.comparingInt((MaterialSet s) -> s.metadata).thenComparing(s -> s.id));
            total += variants.size();
        }

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

        if (set.block != null && cubeShaped(set.block)) v += 30;

        if (set.block == null) v += 20;

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

    private static String namespaceOf(MaterialSet set) {
        try {
            if (set.block != null) return set.block.namespaceId().namespace();
        } catch (Throwable ignored) {

        }
        try {
            if (set.ingredient != null) return set.ingredient.namespaceID.namespace();
        } catch (Throwable ignored) {

        }
        return "";
    }

    private static String materialOf(MaterialSet set) {
        try {
            if (set.block != null) return "b" + set.block.id();
        } catch (Throwable ignored) {

        }
        return "i" + (set.ingredient == null ? 0 : set.ingredient.id);
    }
}
