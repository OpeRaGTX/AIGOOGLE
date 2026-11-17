package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class LeaveActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private int ticks;

    public LeaveActivity(SettlerEntity s) { this.s = s; }

    @Override public boolean wants() { return s.isLeaving(); }

    @Override
    public void start() {
        double a = s.getRandom().nextDouble() * Math.PI * 2;
        walk = new MoveTo(s.position().add(new Vec3(Math.cos(a) * 40, 0, Math.sin(a) * 40)), 3, 0.7);
        ticks = 0;
    }

    @Override
    public boolean tick() {
        s.setStatus("leaving");
        ticks++;
        if (walk.tick(s) != Task.Status.RUNNING || ticks > 900) {
            if (s.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.POOF, s.getX(), s.getY() + 1, s.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            }
            s.discard();
            return false;
        }
        return true;
    }

    @Override public void stop() { if (walk != null) walk.stop(s); }
}
