package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.town.Tavern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Evening: off-duty settlers meet at the tavern; the innkeeper pours. Gives the "Evening at the tavern" thought. */
public final class TavernActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private int wander, serve;

    public TavernActivity(SettlerEntity s) { this.s = s; }

    private @org.jetbrains.annotations.Nullable BlockPos keg() {
        return Tavern.keg(s.settlement());
    }

    @Override
    public boolean wants() {
        return !s.isLeaving() && s.hasArrived() && !s.isSleeping() && !s.inParty() && s.getTarget() == null
                && Tavern.offDuty(s) && keg() != null;
    }

    @Override
    public void start() {
        walk = null;
        wander = 40;
        serve = 60;
    }

    @Override
    public boolean tick() {
        BlockPos keg = keg();
        Settlement st = s.settlement();
        if (keg == null || st == null || !Tavern.offDuty(s) || s.isSleeping() || !(s.level() instanceof ServerLevel sl)) return false;
        boolean keeper = s.profession() == Professions.INNKEEPER;
        if (s.blockPosition().distSqr(keg) > 36 && walk == null) walk = MoveTo.block(keg, 3.2);
        if (walk != null) {
            s.setStatus("going_tavern");
            if (walk.tick(s) != Task.Status.RUNNING) walk = null;
            return true;
        }
        s.setStatus(keeper ? "tavern_serving" : "at_tavern");
        Tavern.arrive(s);
        s.getLookControl().setLookAt(Vec3.atCenterOf(keg));
        if (keeper && --serve <= 0) {
            serve = 70;
            Tavern.serve(sl, st, s, keg);
        }
        if (!keeper && --wander <= 0) {
            wander = 100 + s.getRandom().nextInt(100);
            walk = new MoveTo(Vec3.atBottomCenterOf(keg).add(s.getRandom().nextInt(5) - 2, 0, s.getRandom().nextInt(5) - 2), 1.5, 0.4).noHop();
        }
        return true;
    }

    @Override
    public void stop() {
        if (walk != null) walk.stop(s);
        walk = null;
    }
}
