package io.github.operagtx.hearthholm.crafting;

import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wave M2: what a blueprint still lacks in storage (the check marks on the construction stake) and turning that lack into workshop orders (the
 * "add the missing to the orders" button). Server logic only: the screen draws {@link #stock}'s numbers and sends one action. Both parts are pure
 * functions of the world and the settlement, so the order rules can be changed here without touching the UI or the builder.
 */
public final class MaterialOrders {
    private MaterialOrders() {}

    /** Why an item cannot be ordered. */
    public enum Blocker { NO_WORKSHOP_KIND, NO_BUILDING, NO_CRAFTER, NO_RECIPE, NO_HEAT, LIST_FULL }

    public record Refused(Item item, Blocker why, @Nullable Workshop kind) {}

    /** {@code ordered}: orders created or raised; {@code unchanged}: already ordered with at least this amount; {@code refused}: items no present workshop can make. */
    public record Result(List<Item> ordered, List<Item> unchanged, List<Refused> refused) {}

    /** Items on hand per material of the blueprint (same order as {@code bp.materialList()}): warehouse plus the chests touching the stake. */
    public static int[] stock(ServerLevel level, Settlement s, Blueprint bp, BlockPos stake) {
        List<Blueprint.Material> mats = bp.materialList();
        List<Container> boxes = new ArrayList<>(Storage.resolve(level, s.storagePositions()));
        for (BlockPos p : BlockPos.betweenClosed(stake.offset(-1, -1, -1), stake.offset(1, 1, 1))) {
            if (!p.equals(stake) && level.getBlockEntity(p) instanceof Container c && !boxes.contains(c)) boxes.add(c);
        }
        int[] out = new int[mats.size()];
        for (int i = 0; i < out.length; i++) {
            Item need = mats.get(i).icon();
            out[i] = Storage.count(boxes, st -> Blueprint.accepts(need, st));
        }
        return out;
    }

    /**
     * Orders every missing material some present workshop can make: an order means "keep this many in the warehouse", so the amount is the total the
     * blueprint needs, and pressing the button again changes nothing. Existing larger orders are kept. Raw materials (logs, stone, ore) have no
     * workshop recipe and stay missing.
     */
    public static Result orderMissing(ServerLevel level, Settlement s, Blueprint bp, BlockPos stake) {
        List<Blueprint.Material> mats = bp.materialList();
        int[] have = stock(level, s, bp, stake);
        List<Item> ordered = new ArrayList<>(), unchanged = new ArrayList<>();
        List<Refused> refused = new ArrayList<>();
        for (int i = 0; i < mats.size(); i++) {
            Blueprint.Material m = mats.get(i);
            if (have[i] >= m.count()) continue;
            Item item = m.icon();
            Blocker why = blocker(level, s, item);
            if (why != null) { refused.add(new Refused(item, why, Workshop.forItem(item))); continue; }
            int existing = 0;
            for (Settlement.Order o : s.orders) if (o.item() == item) existing = o.amount();
            if (existing >= m.count()) { unchanged.add(item); continue; }
            SettlementService.setOrder(s, item, m.count());
            boolean placed = false;
            for (Settlement.Order o : s.orders) if (o.item() == item && o.amount() >= m.count()) placed = true;
            if (placed) ordered.add(item); else refused.add(new Refused(item, Blocker.LIST_FULL, Workshop.forItem(item)));
        }
        return new Result(ordered, unchanged, refused);
    }

    /** Null when a present workshop with a crafter can make {@code item}; otherwise the first missing thing. */
    public static @Nullable Blocker blocker(ServerLevel level, Settlement s, Item item) {
        Workshop kind = Workshop.forItem(item);
        if (kind == null || !kind.hasCatalog()) return Blocker.NO_WORKSHOP_KIND;
        BuildingRecord shop = CraftPlanner.workshop(s, kind);
        if (shop == null) return Blocker.NO_BUILDING;
        if (!CraftPlanner.hasCrafter(s, kind)) return Blocker.NO_CRAFTER;
        if (Recipes.usable(level, item, kind, true).isEmpty()) return Blocker.NO_RECIPE;
        if (Recipes.usable(level, item, kind, CraftPlanner.heat(kind, shop)).isEmpty()) return Blocker.NO_HEAT;
        return null;
    }
}
