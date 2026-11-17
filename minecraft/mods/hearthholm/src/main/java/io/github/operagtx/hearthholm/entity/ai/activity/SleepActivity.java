package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Goes to the assigned bed and sleeps until morning; without a bed it dozes near the hall. */
public final class SleepActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private boolean slept;
    private int retry;

    public SleepActivity(SettlerEntity s) { this.s = s; }

    @Override public boolean wants() { return s.isSleepTime() && !s.isLeaving() && s.hasArrived(); }

    @Override
    public void start() {
        walk = null;
        slept = false;
    }

    @Override
    public boolean tick() {
        if (!s.isSleepTime()) return false;
        if (s.isSleeping()) {
            slept = true;
            s.setStatus("sleeping");
            return true;
        }
        BlockPos bed = s.bed();
        BlockState state = bed == null ? null : s.level().getBlockState(bed);
        if (bed == null || !(state.getBlock() instanceof BedBlock)) {
            s.setStatus("no_bed");
            Settlement st = s.settlement();
            if (st != null && walk == null && s.blockPosition().distSqr(st.center) > 36) {
                walk = MoveTo.block(st.center, 4);
            }
            if (walk != null && walk.tick(s) != Task.Status.RUNNING) walk = null;
            return true;
        }
        if (retry > 0) { retry--; return true; }
        if (walk == null) walk = MoveTo.block(bed, 1.6);
        Task.Status r = walk.tick(s);
        if (r == Task.Status.RUNNING) {
            s.setStatus("going_to_bed");
            return true;
        }
        walk = null;
        if (r == Task.Status.DONE && !state.getValue(BedBlock.OCCUPIED)) {
            s.startSleeping(bed);
            slept = true;
        } else {
            s.setStatus("bed_taken");
            retry = 200;
        }
        return true;
    }

    @Override
    public void stop() {
        if (s.isSleeping()) s.stopSleeping();
        if (walk != null) walk.stop(s);
        walk = null;
        s.setSleptLastNight(slept);
    }
}
