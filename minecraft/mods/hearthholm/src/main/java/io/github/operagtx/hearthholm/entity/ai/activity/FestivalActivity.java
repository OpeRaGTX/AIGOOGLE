package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.settlement.Festival;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.social.Social;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** During the harvest festival everybody gathers on the square by the town hall and dances. */
public final class FestivalActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private int dance, spin = 1;
    private boolean cheered;

    public FestivalActivity(SettlerEntity s) { this.s = s; }

    private Settlement active() {
        Settlement st = s.settlement();
        return st != null && s.level() instanceof ServerLevel sl && Festival.active(sl, st) ? st : null;
    }

    @Override
    public boolean wants() {
        return !s.isLeaving() && s.hasArrived() && !s.isSleeping() && active() != null;
    }

    @Override
    public void start() {
        walk = null;
        cheered = false;
        dance = 0;
    }

    @Override
    public boolean tick() {
        Settlement st = active();
        if (st == null || !(s.level() instanceof ServerLevel sl)) return false;
        s.setStatus("festival");
        BlockPos sq = Festival.square(sl, st);
        if (walk != null) {
            if (walk.tick(s) != Task.Status.RUNNING) { walk = null; dance = 80 + s.getRandom().nextInt(80); }
            return true;
        }
        if (Math.sqrt(s.blockPosition().distSqr(sq)) > 6) {
            walk = new MoveTo(Vec3.atBottomCenterOf(sq).add(s.getRandom().nextInt(7) - 3, 0, s.getRandom().nextInt(7) - 3), 1.5, 0.55);
            return true;
        }
        if (!cheered) {
            cheered = true;
            if (s.festivalSeen() != st.festivalStart) {
                s.setFestivalSeen(st.festivalStart);
                s.addThought("festival", io.github.operagtx.hearthholm.core.Balance.MOOD_FESTIVAL, 72000);
            }
            Social.say(s, "festival", 4);
        }
        // dance: hop, spin, wander a little
        if (--dance <= 0) {
            walk = new MoveTo(Vec3.atBottomCenterOf(sq).add(s.getRandom().nextInt(7) - 3, 0, s.getRandom().nextInt(7) - 3), 1.0, 0.45).noHop();
            return true;
        }
        int t = s.tickCount;
        if (t % 24 == 0 && s.onGround()) s.jumpFromGround();
        if (t % 6 == 0) {
            if (t % 120 == 0) spin = -spin;
            float yaw = s.getYRot() + 45f * spin;
            s.setYRot(yaw);
            s.yBodyRot = yaw;
            s.yHeadRot = yaw;
        }
        if (t % 20 == 0) sl.sendParticles(ParticleTypes.NOTE, s.getX(), s.getY() + 2.2, s.getZ(), 1, 0.3, 0.1, 0.3, 1.0);
        return true;
    }

    @Override
    public void stop() {
        if (walk != null) walk.stop(s);
        walk = null;
    }
}
