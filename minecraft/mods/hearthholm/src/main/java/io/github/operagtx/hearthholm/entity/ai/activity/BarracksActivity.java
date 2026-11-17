package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.Task;
import net.minecraft.core.BlockPos;

/** A wounded fighter (below 85 %) rests in the barracks until mended: healing is much faster there. */
public final class BarracksActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private int ticks;

    public BarracksActivity(SettlerEntity s) { this.s = s; }

    @Override
    public boolean wants() {
        return s.isCombatant() && !s.inParty() && !s.isLeaving() && s.hasArrived() && s.getTarget() == null
                && s.getHealth() < s.getMaxHealth() * 0.85f && s.barracksSpot() != null;
    }

    @Override
    public void start() {
        walk = null;
        ticks = 0;
    }

    @Override
    public boolean tick() {
        BlockPos rack = s.barracksSpot();
        if (rack == null || s.getHealth() >= s.getMaxHealth() * 0.97f || ++ticks > 2400) return false;
        s.setStatus("resting_barracks");
        if (s.inBarracks()) {
            s.getNavigation().stop();
            return true;
        }
        if (walk == null) walk = MoveTo.block(rack, 3.5);
        if (walk.tick(s) != Task.Status.RUNNING) walk = null;
        return true;
    }

    @Override
    public void stop() {
        if (walk != null) walk.stop(s);
        walk = null;
    }
}
