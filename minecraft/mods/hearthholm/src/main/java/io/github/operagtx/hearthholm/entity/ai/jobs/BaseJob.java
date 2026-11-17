package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Shared helpers for jobs: trips to chests and the warehouse, tools, deposits. */
public abstract class BaseJob implements Job {
    protected final SettlerEntity settler;
    private long nextToolTry;

    protected BaseJob(SettlerEntity settler) {
        this.settler = settler;
    }

    // ---- containers -----------------------------------------------------------------------

    protected List<BlockPos> chestsNear(BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (settler.level().getBlockEntity(p) instanceof Container) out.add(p.immutable());
        }
        return out;
    }

    protected List<BlockPos> storage() {
        Settlement st = settler.settlement();
        return st == null ? List.of() : st.storagePositions();
    }

    /** Where results go and supplies come from; site jobs put their marker chest first. */
    protected List<BlockPos> dropOffs() {
        return storage();
    }

    protected @Nullable BlockPos findWith(List<BlockPos> positions, Predicate<ItemStack> filter) {
        if (!(settler.level() instanceof ServerLevel sl)) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            if (!(sl.getBlockEntity(p) instanceof Container c) || Storage.count(List.of(c), filter) == 0) continue;
            double d = p.distSqr(settler.blockPosition());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    protected @Nullable BlockPos findSpace(List<BlockPos> positions) {
        if (!(settler.level() instanceof ServerLevel sl)) return null;
        for (BlockPos p : positions) {
            if (!(sl.getBlockEntity(p) instanceof Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return p;
        }
        return null;
    }

    protected boolean pocketsFull() {
        return settler.freeSlots() <= 2;
    }

    /** Unloads everything except what {@code keep} wants to carry. */
    protected @Nullable Task depositTrip(Predicate<ItemStack> keep) {
        BlockPos target = findSpace(dropOffs());
        if (target == null) {
            settler.setStatus("storage_full");
            return null;
        }
        settler.setStatus("depositing");
        return new ContainerTrip(target, (s, c) -> {
            for (int i = 0; i < s.inventory().getContainerSize(); i++) {
                ItemStack st = s.inventory().getItem(i);
                if (st.isEmpty() || keep.test(st)) continue;
                s.inventory().setItem(i, Storage.insert(c, st));
            }
            s.inventory().setChanged();
        });
    }

    /** Goes to fetch a fitting tool when the hands are empty (at most every 2 minutes). */
    protected @Nullable Task toolTrip() {
        if (settler.profession().isTool(settler.getMainHandItem())) return null;
        long now = settler.level().getGameTime();
        if (now < nextToolTry) return null;
        nextToolTry = now + 2400;
        BlockPos p = findWith(dropOffs(), settler.profession()::isTool);
        if (p == null) return null;
        settler.setStatus("fetching_tool");
        return new ContainerTrip(p, (s, c) -> s.equipToolFrom(c));
    }

    protected @Nullable Task fetch(Predicate<ItemStack> filter, int count, String statusKey) {
        BlockPos p = findWith(dropOffs(), filter);
        if (p == null) return null;
        settler.setStatus(statusKey);
        return new ContainerTrip(p, (s, c) -> s.store(Storage.take(List.of(c), filter, count)));
    }
}
