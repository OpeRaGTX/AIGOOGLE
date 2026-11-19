package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Walks to a point. Re-paths regularly; if the settler makes no progress for a while it hops
 * to a free spot next to the target instead of standing stuck (the most common complaint about
 * colony NPCs).
 */
public final class MoveTo implements Task {
    private final Vec3 target;
    private final double rangeSqr;
    private final double speed;
    private int repath;
    private int ticks;
    private int stuck;
    private Vec3 last;
    private boolean hop = true;

    public MoveTo(Vec3 target, double range, double speed) {
        this.target = target;
        this.rangeSqr = range * range;
        this.speed = speed;
    }

    /** Gives up instead of hopping when stuck (idle strolls). */
    public MoveTo noHop() {
        hop = false;
        return this;
    }

    public static MoveTo block(BlockPos pos, double range) {
        return new MoveTo(Vec3.atBottomCenterOf(pos), range, 0.6);
    }

    public static MoveTo run(BlockPos pos, double range) {
        return new MoveTo(Vec3.atBottomCenterOf(pos), range, 0.9);
    }

    @Override
    public Status tick(SettlerEntity s) {
        if (s.position().distanceToSqr(target) <= rangeSqr) {
            s.getNavigation().stop();
            return Status.DONE;
        }
        ticks++;
        if (--repath <= 0) {
            repath = 30;
            s.getNavigation().moveTo(target.x, target.y, target.z, speed * s.moveFactor());
        }
        if (ticks % 40 == 0) {
            if (last != null && last.distanceToSqr(s.position()) < 0.25) stuck += 40; else stuck = 0;
            last = s.position();
        }
        if (stuck >= 160 || ticks > 1600) {
            return hop && s.hopNear(BlockPos.containing(target)) ? Status.DONE : Status.FAILED;
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(SettlerEntity s) {
        s.getNavigation().stop();
    }
}
