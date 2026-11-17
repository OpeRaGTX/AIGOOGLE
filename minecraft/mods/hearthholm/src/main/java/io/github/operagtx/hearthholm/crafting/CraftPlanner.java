package io.github.operagtx.hearthholm.crafting;

import io.github.operagtx.hearthholm.crafting.Recipes.Option;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.ResidentRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Decides what a crafter makes: the first unmet order of its workshop whose ingredients are in
 * the warehouse (or the default meal stock for cooks), and explains why an order is stuck.
 * Ingredients are matched greedily, picking the most plentiful fitting item for each slot.
 */
public final class CraftPlanner {
    public static final int MAX_BATCHES = 12;

    private CraftPlanner() {}

    /** A crafting job: what to make, how many times, and which items to take for that. */
    public record Plan(Item order, Option option, int batches, Map<Item, Integer> take) {}

    /** Result of evaluating one order. {@code plan} is set when work is possible now. */
    public record Verdict(Item item, int target, int stock, @Nullable Plan plan, Component status, int color) {}

    public static final int OK = 0xFF8FD46B, WARN = 0xFFE8B84A, BAD = 0xFFE36B5B, DIM = 0xFFA6977E;

    public static Map<Item, Integer> stock(List<Container> containers) {
        Map<Item, Integer> out = new HashMap<>();
        for (Container c : containers)
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && !s.isDamaged()) out.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        return out;
    }

    public static Map<Item, Integer> stock(Iterable<ItemStack> stacks) {
        Map<Item, Integer> out = new HashMap<>();
        for (ItemStack s : stacks) if (!s.isEmpty()) out.merge(s.getItem(), s.getCount(), Integer::sum);
        return out;
    }

    /** Takes one batch of inputs out of {@code stock} (mutated); returns what was used or null. */
    public static @Nullable Map<Item, Integer> allocate(Option o, Map<Item, Integer> stock) {
        Map<Item, Integer> used = new HashMap<>();
        for (Ingredient ing : o.inputs()) {
            Item best = null;
            int bestCount = 0;
            for (Map.Entry<Item, Integer> e : stock.entrySet()) {
                if (e.getValue() > bestCount && ing.acceptsItem(e.getKey().builtInRegistryHolder())) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            if (best == null) {
                used.forEach((it, n) -> stock.merge(it, n, Integer::sum));
                return null;
            }
            stock.merge(best, -1, Integer::sum);
            used.merge(best, 1, Integer::sum);
        }
        return used;
    }

    /** How many batches the stock allows (up to {@code max}); fills {@code take}. */
    public static int batches(Option o, Map<Item, Integer> stock, int max, Map<Item, Integer> take) {
        Map<Item, Integer> s = new HashMap<>(stock);
        int n = 0;
        int itemsPerBatch = o.inputs().size();
        while (n < max && (n + 1) * itemsPerBatch <= 64 * 10) {
            Map<Item, Integer> used = allocate(o, s);
            if (used == null) break;
            used.forEach((it, k) -> take.merge(it, k, Integer::sum));
            n++;
        }
        return n;
    }

    /** The first ingredient the stock cannot cover for one batch, with how many are missing. */
    public static Component lacking(Option o, Map<Item, Integer> stock) {
        Map<Ingredient, Integer> need = new LinkedHashMap<>();
        for (Ingredient i : o.inputs()) need.merge(i, 1, Integer::sum);
        for (Map.Entry<Ingredient, Integer> e : need.entrySet()) {
            int have = 0;
            for (Map.Entry<Item, Integer> s : stock.entrySet())
                if (s.getValue() > 0 && e.getKey().acceptsItem(s.getKey().builtInRegistryHolder())) have += s.getValue();
            if (have < e.getValue()) {
                ItemStack shown = e.getKey().items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
                return Component.translatable("order.hearthholm.needs", e.getValue() - have, shown.getHoverName());
            }
        }
        return Component.translatable("order.hearthholm.needs_more");
    }

    public static @Nullable BuildingRecord workshop(Settlement st, Workshop kind) {
        for (BuildingRecord b : st.buildings) if (b.is(kind.role()) && b.valid() && !b.stations().isEmpty()) return b;
        return null;
    }

    public static boolean heat(Workshop kind, @Nullable BuildingRecord shop) {
        return kind.ownHeat() || (shop != null && shop.has(BuildingRecord.FURNACE));
    }

    /** Orders of this workshop kind, plus the default meal order for kitchens without orders. */
    public static List<Settlement.Order> ordersFor(Settlement st, Workshop kind) {
        List<Settlement.Order> out = new ArrayList<>();
        for (Settlement.Order o : st.orders) if (kind.catalog().contains(o.item())) out.add(o);
        return out;
    }

    public static Verdict evaluate(ServerLevel level, Settlement st, Workshop kind, Settlement.Order order,
                                   Map<Item, Integer> stock, @Nullable BuildingRecord shop, boolean crafterPresent) {
        Item item = order.item();
        int have = stock.getOrDefault(item, 0);
        if (have >= order.amount()) return new Verdict(item, order.amount(), have, null, Component.translatable("order.hearthholm.stocked"), OK);
        if (shop == null) return new Verdict(item, order.amount(), have, null,
                Component.translatable("order.hearthholm.no_workshop", kind.label()), BAD);
        if (!crafterPresent) return new Verdict(item, order.amount(), have, null,
                Component.translatable("order.hearthholm.no_crafter", Component.translatable("profession.hearthholm." + kind.profession().getPath())), BAD);
        boolean heat = heat(kind, shop);
        List<Option> opts = Recipes.usable(level, item, kind, heat);
        if (opts.isEmpty()) {
            boolean needsFurnace = !Recipes.usable(level, item, kind, true).isEmpty();
            return new Verdict(item, order.amount(), have, null,
                    Component.translatable(needsFurnace ? "order.hearthholm.needs_furnace" : "order.hearthholm.no_recipe"), BAD);
        }
        Component lack = null;
        for (Option o : opts) {
            int perBatch = Math.max(1, o.output().getCount());
            int wantBatches = Math.min(MAX_BATCHES, (order.amount() - have + perBatch - 1) / perBatch);
            Map<Item, Integer> take = new HashMap<>();
            int n = batches(o, stock, wantBatches, take);
            if (n > 0) {
                String who = st.crafting.get(item);
                Component status = who != null ? Component.translatable("order.hearthholm.crafting", who)
                        : Component.translatable("order.hearthholm.queued");
                return new Verdict(item, order.amount(), have, new Plan(item, o, n, take), status, WARN);
            }
            if (lack == null) lack = lacking(o, stock);
        }
        return new Verdict(item, order.amount(), have, null, lack, BAD);
    }

    public static boolean hasCrafter(Settlement st, Workshop kind) {
        for (ResidentRecord r : st.residents) if (r.profession.equals(kind.profession())) return true;
        return false;
    }

    /**
     * What a crafter of this kind should do now. Returns a verdict with a plan, or the verdict
     * that best explains why nothing can be made (null when there is simply nothing to do).
     */
    public static @Nullable Verdict choose(ServerLevel level, Settlement st, Workshop kind, @Nullable BuildingRecord shop) {
        List<Container> storage = Storage.resolve(level, st.storagePositions());
        Map<Item, Integer> stock = stock(storage);
        List<Settlement.Order> orders = ordersFor(st, kind);
        if (orders.isEmpty() && kind == Workshop.KITCHEN) {
            int meals = 0;
            for (Item it : kind.catalog()) meals += stock.getOrDefault(it, 0);
            if (meals >= Workshop.DEFAULT_MEALS) return null;
            for (Item it : kind.catalog()) orders.add(new Settlement.Order(it, stock.getOrDefault(it, 0) + Workshop.DEFAULT_MEALS - meals));
        }
        Verdict firstProblem = null;
        for (Settlement.Order o : orders) {
            Verdict v = evaluate(level, st, kind, o, stock, shop, true);
            if (v.plan() != null) return v;
            if (firstProblem == null && v.stock() < v.target()) firstProblem = v;
        }
        return firstProblem;
    }
}
