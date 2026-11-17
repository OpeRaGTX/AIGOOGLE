package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.jobs.HealerJob;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import java.util.List;

/**
 * A badly hurt settler (below 60 %) with no healer nearby fetches healing salves from the warehouse; it drinks them
 * on its own once it is below half health (see {@code SettlerEntity#tickMilitary}).
 */
public final class MedicActivity implements Activity {
    private final SettlerEntity s;
    private Task trip;
    private long nextTry;

    public MedicActivity(SettlerEntity s) { this.s = s; }

    private BlockPos findSalve() {
        Settlement st = s.settlement();
        if (st == null || !(s.level() instanceof ServerLevel sl)) return null;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : st.storagePositions()) {
            if (!(sl.getBlockEntity(p) instanceof Container c) || Storage.count(List.of(c), HealerJob.SALVE) == 0) continue;
            double d = p.distSqr(s.blockPosition());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    @Override
    public boolean wants() {
        if (s.isLeaving() || !s.hasArrived() || s.inParty() || s.getHealth() >= s.getMaxHealth() * 0.6f) return false;
        long now = s.level().getGameTime();
        if (now < nextTry || s.count(HealerJob.SALVE) > 0) return false;
        if (HealerJob.healerNear(s, 24)) return false;
        if (findSalve() == null) {
            nextTry = now + 600;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        trip = null;
    }

    @Override
    public boolean tick() {
        if (trip == null) {
            BlockPos p = findSalve();
            if (p == null) return false;
            s.setStatus("fetching_salve");
            trip = new ContainerTrip(p, (who, c) -> who.store(Storage.take(List.of(c), HealerJob.SALVE, 2)));
        }
        Task.Status st = trip.tick(s);
        if (st == Task.Status.RUNNING) return true;
        nextTry = s.level().getGameTime() + 600;
        return false;
    }

    @Override
    public void stop() {
        if (trip != null) trip.stop(s);
        trip = null;
    }
}
