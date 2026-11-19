package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Carries goods around: empties the chests next to work markers into the warehouse (leaving
 * what the worker needs there: tools, torches, ladders, some seeds and saplings) and keeps one
 * stack of the next needed materials in the chest next to every active construction stake.
 */
public final class HaulerJob extends BaseJob {
    private static final int SUPPLY_STACK = 64;
    private @Nullable BlockPos deliverTo;
    private @Nullable Item deliverItem;
    private long nextScan;

    public HaulerJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st == null || !(s.level() instanceof ServerLevel level)) return new Wait(100);
        if (deliverTo != null && deliverItem != null && s.has(deliverItem)) {
            BlockPos to = deliverTo;
            Item item = deliverItem;
            deliverTo = null;
            s.setStatus("supplying", new ItemStack(item).getHoverName());
            return new ContainerTrip(to, (who, c) -> {
                for (int i = 0; i < who.inventory().getContainerSize(); i++) {
                    ItemStack it = who.inventory().getItem(i);
                    if (it.is(item)) who.inventory().setItem(i, Storage.insert(c, it));
                }
                who.inventory().setChanged();
            });
        }
        deliverTo = null;
        if (settler.freeSlots() < settler.inventory().getContainerSize()) {
            Task t = depositTrip(x -> false);
            if (t != null) return t;
            return new Wait(200);
        }
        long now = level.getGameTime();
        if (now < nextScan) return new Wait(40);
        nextScan = now + 160;
        Set<BlockPos> warehouse = new HashSet<>(st.storagePositions());
        Task t = supply(level, st, warehouse);
        if (t == null) t = collect(level, st, warehouse);
        if (t != null) return t;
        s.setStatus("hauler_idle");
        return new Wait(100);
    }

    /** One stack of the next needed material into the chest beside an active construction stake. */
    private @Nullable Task supply(ServerLevel level, Settlement st, Set<BlockPos> warehouse) {
        for (Settlement.SiteRecord rec : st.sitesOf(WorkSiteTypes.CONSTRUCTION_ID)) {
            if (!level.isLoaded(rec.pos()) || !(level.getBlockEntity(rec.pos()) instanceof WorkSiteBlockEntity w) || !w.flag("build")) continue;
            Blueprint bp = BlueprintLibrary.byIndex(w.option("blueprint"));
            if (bp == null) continue;
            List<BlockPos> chests = chestsNear(rec.pos());
            chests.removeAll(warehouse);
            if (chests.isEmpty()) continue;
            List<Container> site = Storage.resolve(level, chests);
            for (Map.Entry<Item, Integer> e : BuilderJob.upcoming(level, bp.ops(rec.pos(), w.facing()), w.progress()).entrySet()) {
                Item item = e.getKey();
                int want = Math.min(SUPPLY_STACK, Math.min(item.getDefaultMaxStackSize(), e.getValue()));
                int have = Storage.count(site, x -> x.is(item));
                if (have >= want) continue;
                BlockPos from = findWith(storage(), x -> x.is(item));
                if (from == null) continue;
                BlockPos target = findSpace(chests);
                if (target == null) break;
                int amount = want - have;
                deliverTo = target;
                deliverItem = item;
                settler.setStatus("supplying", new ItemStack(item).getHoverName());
                return new ContainerTrip(from, (who, c) -> {
                    List<Container> all = Storage.resolve(level, st.storagePositions());
                    who.store(Storage.take(all, x -> x.is(item), amount));
                });
            }
        }
        return null;
    }

    /** Empties the produce out of chests next to work markers. */
    private @Nullable Task collect(ServerLevel level, Settlement st, Set<BlockPos> warehouse) {
        if (findSpace(storage()) == null) {
            settler.setStatus("storage_full");
            return null;
        }
        for (Settlement.SiteRecord rec : st.sites) {
            if (!level.isLoaded(rec.pos()) || !(level.getBlockEntity(rec.pos()) instanceof WorkSiteBlockEntity w)) continue;
            Set<Item> blueprint = Set.of();
            if (rec.type().equals(WorkSiteTypes.CONSTRUCTION_ID)) {
                Blueprint bp = BlueprintLibrary.byIndex(w.option("blueprint"));
                blueprint = bp == null ? Set.of() : bp.materials().keySet();
            }
            for (BlockPos p : chestsNear(rec.pos())) {
                if (warehouse.contains(p) || !(level.getBlockEntity(p) instanceof Container c)) continue;
                Map<Item, Integer> spare = spare(c, rec.type(), blueprint);
                if (spare.isEmpty()) continue;
                settler.setStatus("hauling", w.type().label());
                return new ContainerTrip(p, (who, box) -> {
                    int moved = 0;
                    for (int i = 0; i < box.getContainerSize() && who.freeSlots() > 1; i++) {
                        ItemStack it = box.getItem(i);
                        Integer left = spare.get(it.getItem());
                        if (it.isEmpty() || left == null || left <= 0) continue;
                        ItemStack part = it.split(Math.min(left, it.getCount()));
                        spare.put(it.getItem(), left - part.getCount());
                        moved += part.getCount();
                        who.store(part);
                    }
                    box.setChanged();
                    Settlement set = who.settlement();
                    if (set != null && moved > 0) {
                        set.count("hauled", moved);
                        who.gainXp(1);
                        SettlementService.markDirty(who);
                    }
                });
            }
        }
        return null;
    }

    /** How many of each item may be taken from a work-site chest. */
    private static Map<Item, Integer> spare(Container c, net.minecraft.resources.Identifier siteType, Set<Item> blueprint) {
        Map<Item, Integer> have = new HashMap<>();
        Set<Item> tools = new HashSet<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            have.merge(s.getItem(), s.getCount(), Integer::sum);
            for (Profession p : Professions.REGISTRY.values()) if (p.isTool(s)) tools.add(s.getItem());
            if (s.isDamageableItem()) tools.add(s.getItem());
        }
        Map<Item, Integer> out = new HashMap<>();
        for (Map.Entry<Item, Integer> e : have.entrySet()) {
            Item it = e.getKey();
            if (tools.contains(it) || blueprint.contains(it)) continue;
            int keep = 0;
            if (it == Items.TORCH || it == Items.LADDER) keep = Integer.MAX_VALUE;
            else if (siteType.equals(WorkSiteTypes.FARM_ID) && (it == Items.WHEAT_SEEDS || it == Items.BEETROOT_SEEDS
                    || it == Items.CARROT || it == Items.POTATO)) keep = 32;
            else if (siteType.equals(WorkSiteTypes.FOREST_ID) && it.getDefaultInstance().is(ItemTags.SAPLINGS)) keep = 16;
            int n = e.getValue() - keep;
            if (n > 0) out.put(it, n);
        }
        return out;
    }
}
