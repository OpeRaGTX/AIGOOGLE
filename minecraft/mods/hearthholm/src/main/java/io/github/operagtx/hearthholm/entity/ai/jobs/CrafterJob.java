package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.crafting.CraftPlanner;
import io.github.operagtx.hearthholm.crafting.CraftPlanner.Plan;
import io.github.operagtx.hearthholm.crafting.CraftPlanner.Verdict;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.entity.ai.tasks.CraftAtStation;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Carpenter, mason, smith and cook. Works in a valid workshop of its kind: picks the first
 * unmet order whose ingredients are in the warehouse, fetches them, crafts at the workstation
 * over time and brings the results back. The plan is kept in memory only; after a reload the
 * crafter simply returns what it carries and plans again.
 */
public class CrafterJob extends BaseJob {
    protected final Workshop kind;
    private @Nullable Plan plan;
    private boolean fetched;
    private long nextPlan;
    private @Nullable Item busyWith;

    public CrafterJob(SettlerEntity settler, Workshop kind) {
        super(settler);
        this.kind = kind;
    }

    protected @Nullable BlockPos station(@Nullable BuildingRecord shop) {
        if (shop == null) return null;
        BlockPos best = null;
        for (BlockPos p : shop.stations()) {
            if (!(settler.level().getBlockState(p).getBlock() instanceof io.github.operagtx.hearthholm.block.WorkshopBlock wb) || wb.kind() != kind) continue;
            if (best == null || p.distSqr(settler.blockPosition()) < best.distSqr(settler.blockPosition())) best = p;
        }
        return best;
    }

    /** Extra duties before orders (the smith repairs tools). */
    protected @Nullable Task duty(Settlement st) {
        return null;
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st == null || !(s.level() instanceof ServerLevel level)) return new Wait(100);
        if (busyWith != null) st.crafting.remove(busyWith);
        busyWith = null;
        BuildingRecord shop = CraftPlanner.workshop(st, kind);
        BlockPos station = station(shop);
        if (station == null) {
            clearPlan(st);
            s.setStatus("need_workshop", kind.label());
            return cargo() ? deposit() : new Wait(200);
        }
        if (plan != null && fetched) {
            int ready = CraftPlanner.batches(plan.option(), CraftPlanner.stock(s.inventory().getItems()), plan.batches(), new java.util.HashMap<>());
            Plan p = plan;
            clearPlan(st);
            if (ready <= 0) return new Wait(20);
            busy(st, p.order());
            s.setStatus("crafting", p.option().output().getHoverName(), 0, ready);
            return new CraftAtStation(station, kind, p.option(), ready);
        }
        clearPlan(st);
        Task duty = duty(st);
        if (duty != null) return duty;
        if (cargo()) return deposit();
        long now = level.getGameTime();
        if (now < nextPlan) return new Wait(40);
        nextPlan = now + 100;
        Verdict v = CraftPlanner.choose(level, st, kind, shop);
        if (v == null) {
            s.setStatus("no_orders");
            return new Wait(200, net.minecraft.world.phys.Vec3.atCenterOf(station));
        }
        if (v.plan() == null) {
            s.setStatus("lacks", new ItemStack(v.item()).getHoverName(), v.status());
            return new Wait(200, net.minecraft.world.phys.Vec3.atCenterOf(station));
        }
        Plan p = v.plan();
        Item first = p.take().keySet().iterator().next();
        BlockPos chest = findWith(storage(), stack -> stack.is(first));
        if (chest == null) return new Wait(40);
        plan = p;
        busy(st, p.order());
        s.setStatus("fetching_ingredients", p.option().output().getHoverName());
        return new ContainerTrip(chest, (who, c) -> {
            takeAll(level, st, p.take());
            fetched = true;
        });
    }

    private void takeAll(ServerLevel level, Settlement st, Map<Item, Integer> take) {
        List<Container> storage = Storage.resolve(level, st.storagePositions());
        for (Map.Entry<Item, Integer> e : take.entrySet()) {
            int left = e.getValue();
            while (left > 0 && settler.freeSlots() > 0) {
                ItemStack got = Storage.take(storage, st2 -> st2.is(e.getKey()) && !st2.isDamaged(),
                        Math.min(left, e.getKey().getDefaultMaxStackSize()));
                if (got.isEmpty()) break;
                left -= got.getCount();
                settler.store(got);
            }
        }
    }

    private void busy(Settlement st, Item item) {
        busyWith = item;
        st.crafting.put(item, settler.getName().getString());
    }

    private void clearPlan(Settlement st) {
        plan = null;
        fetched = false;
    }

    protected boolean cargo() {
        return settler.freeSlots() < settler.inventory().getContainerSize();
    }

    protected Task deposit() {
        Task t = depositTrip(stack -> false);
        if (t == null) return new Wait(200);
        settler.setStatus("delivering_goods");
        return t;
    }

    @Override
    public void pause(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st == null) return;
        if (busyWith != null) st.crafting.remove(busyWith);
        busyWith = null;
        if (!fetched) clearPlan(st);
    }

    @Override
    public void release(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st == null) return;
        if (busyWith != null) st.crafting.remove(busyWith);
        busyWith = null;
        clearPlan(st);
    }
}
