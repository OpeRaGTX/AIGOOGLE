package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Strolls around home or the town hall. */
public final class IdleActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private int wait;

    public IdleActivity(SettlerEntity s) { this.s = s; }

    @Override public boolean wants() { return true; }

    @Override
    public boolean tick() {
        if (s.profession() == Professions.UNEMPLOYED) s.setStatus("unemployed");
        else if (!s.isWorkTime()) s.setStatus("evening");
        else s.setStatus("resting");
        if (walk != null) {
            if (walk.tick(s) != Task.Status.RUNNING) walk = null;
            return true;
        }
        if (--wait > 0) return true;
        wait = 120 + s.getRandom().nextInt(240);
        Settlement st = s.settlement();
        BlockPos home = s.bed() != null ? s.bed() : st != null ? st.center : s.blockPosition();
        Vec3 t = Vec3.atBottomCenterOf(home).add(s.getRandom().nextInt(13) - 6, 0, s.getRandom().nextInt(13) - 6);
        walk = new MoveTo(t, 2, 0.45).noHop();
        return true;
    }

    @Override
    public void stop() {
        if (walk != null) walk.stop(s);
        walk = null;
    }
}
