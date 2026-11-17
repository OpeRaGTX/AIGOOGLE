package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.Blueprint.BuildOp;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.*;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Builds the blueprint chosen on a construction stake: clears the plot, then places blocks
 * layer by layer with materials fetched from the warehouse. Missing materials show up as a
 * request in the settler's status and the town hall.
 */
public final class BuilderJob extends SiteJob {
    private static final int LOOKAHEAD = 160;

    public BuilderJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.CONSTRUCTION_ID);
    }

    @Override
    protected boolean available(Settlement st, WorkSiteBlockEntity w) {
        return w.flag("build") && BlueprintLibrary.byIndex(w.option("blueprint")) != null && super.available(st, w);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        WorkSiteBlockEntity w = claim();
        if (w == null) return new Wait(100);
        Blueprint bp = BlueprintLibrary.byIndex(w.option("blueprint"));
        if (!w.flag("build") || bp == null) {
            s.setStatus("waiting_start");
            release(s);
            return new Wait(100);
        }
        Task tool = toolTrip();
        if (tool != null) return tool;
        List<BuildOp> ops = bp.ops(w.getBlockPos(), w.facing());
        ServerLevel level = (ServerLevel) s.level();

        int i = w.progress();
        for (int guard = 0; guard < 200 && i < ops.size(); guard++) {
            if (!done(level, ops.get(i))) break;
            i++;
        }
        w.setProgress(i);
        if (i >= ops.size()) {
            SettlementService.finishConstruction(level, w, bp, s);
            return new Wait(40);
        }
        if (pocketsFull()) {
            Set<Item> needed = needed(ops, i).keySet();
            Task dep = depositTrip(st -> needed.contains(st.getItem()) || Storage.isFood(st));
            if (dep != null) return dep;
        }
        BuildOp op = ops.get(i);
        int percent = i * 100 / ops.size();
        if (op.clear()) {
            s.setStatus("clearing", percent);
            if (level.getBlockState(op.pos()).getDestroySpeed(level, op.pos()) < 0) {
                w.setProgress(i + 1);
                return new Wait(1);
            }
            return new BreakBlock(op.pos(), null);
        }
        BlockState current = level.getBlockState(op.pos());
        if (!current.isAir() && !current.canBeReplaced()) return new BreakBlock(op.pos(), null);
        if (op.desk()) {
            SettlementService.relocateDesk(level, s, op.pos(), op.state());
            w.setProgress(i + 1);
            return new Wait(5);
        }
        Item cost = Blueprint.cost(op.state());
        if (cost != null && !s.has(cost)) {
            Task fetch = fetchMaterials(ops, i);
            if (fetch != null) return fetch;
            ItemStack shown = new ItemStack(cost);
            int count = needed(ops, i).getOrDefault(cost, 1);
            s.setStatus("need_material", count, shown.getHoverName());
            return new Wait(200);
        }
        s.setStatus("building", percent);
        return new PlaceBlock(op.pos(), op.state(), cost, st -> {
            if (op.companionPos() != null) level.setBlock(op.companionPos(), op.companionState(), 3);
            st.gainXp(1);
            Settlement set = st.settlement();
            if (set != null) {
                set.count("placed", 1);
                if (set.stats.getOrDefault("placed", 0) % 20 == 0) set.addRenown(1);
                SettlementService.markDirty(st);
            }
        });
    }

    private static boolean done(ServerLevel level, BuildOp op) {
        BlockState current = level.getBlockState(op.pos());
        if (op.clear()) {
            if (current.getBlock() == op.state().getBlock()) return true;
            return current.getFluidState().isEmpty() && (current.isAir() || current.canBeReplaced());
        }
        return current.getBlock() == op.state().getBlock();
    }

    private Map<Item, Integer> needed(List<BuildOp> ops, int from) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (int i = from; i < ops.size() && i < from + LOOKAHEAD; i++) {
            BuildOp op = ops.get(i);
            if (op.clear()) continue;
            Item it = Blueprint.cost(op.state());
            if (it != null && settler.level().getBlockState(op.pos()).getBlock() != op.state().getBlock()) need.merge(it, 1, Integer::sum);
        }
        return need;
    }

    /** One warehouse trip that grabs as many of the upcoming materials as one chest offers. */
    private @Nullable Task fetchMaterials(List<BuildOp> ops, int from) {
        Map<Item, Integer> need = needed(ops, from);
        need.replaceAll((item, n) -> Math.max(0, n - settler.count(st -> st.is(item))));
        need.values().removeIf(n -> n <= 0);
        if (need.isEmpty()) return null;
        for (Item first : need.keySet()) {
            BlockPos chest = findWith(storage(), st -> st.is(first));
            if (chest == null) continue;
            settler.setStatus("fetching_materials");
            return new ContainerTrip(chest, (s, c) -> takeAll(s, c, need));
        }
        return null;
    }

    private static void takeAll(SettlerEntity s, Container c, Map<Item, Integer> need) {
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int left = e.getValue();
            while (left > 0 && s.freeSlots() > 1) {
                ItemStack got = Storage.take(List.of(c), st -> st.is(e.getKey()), Math.min(64, left));
                if (got.isEmpty()) break;
                left -= got.getCount();
                s.store(got);
            }
        }
    }
}
