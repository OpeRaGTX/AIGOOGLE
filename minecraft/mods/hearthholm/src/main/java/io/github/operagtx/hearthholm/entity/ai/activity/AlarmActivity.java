package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.threat.Defense;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Alarm: civilians shelter by their bed (or in the town hall) and stay there; guards and warriors gather
 * at the nearest alarm bell. While a rift is open, combat settlers go and hack at its core instead.
 * Combat goals still pre-empt this whenever an enemy is in sight.
 */
public final class AlarmActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;
    private int recheck;

    public AlarmActivity(SettlerEntity s) { this.s = s; }

    private boolean riftDuty(Settlement st) {
        return s.isCombatant() && st.rift.open();
    }

    @Override
    public boolean wants() {
        Settlement st = s.settlement();
        return st != null && !s.isLeaving() && s.hasArrived() && (st.alarm || riftDuty(st));
    }

    @Override
    public void start() {
        walk = null;
        recheck = 0;
        if (s.isSleeping()) s.stopSleeping();
    }

    /** Where this settler should be right now. */
    public static BlockPos post(SettlerEntity s, Settlement st) {
        if (s.isCombatant()) return st.rift.open() && !st.alarm ? st.rift.core : Defense.rallyPoint(st, s.blockPosition());
        return s.bed() != null ? s.bed() : st.center;
    }

    @Override
    public boolean tick() {
        Settlement st = s.settlement();
        if (st == null || !(st.alarm || riftDuty(st))) return false;
        BlockPos post = post(s, st);
        boolean rift = s.isCombatant() && post.equals(st.rift.core);
        s.setStatus(rift ? "rift_duty" : s.isCombatant() ? "at_the_bell" : "sheltering");
        if (walk != null) {
            if (walk.tick(s) == Task.Status.RUNNING) return true;
            walk = null;
        }
        if (--recheck > 0) return true;
        recheck = 40;
        if (s.isCombatant() && st.raid.phase == io.github.operagtx.hearthholm.threat.RaidState.ACTIVE && s.getTarget() == null) {
            // the raid is on: go after the nearest raider inside the settlement
            var foes = s.level().getEntitiesOfClass(io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity.class,
                    new net.minecraft.world.phys.AABB(st.center).inflate(st.radius() + 8, 24, st.radius() + 8),
                    a -> a.isAlive() && st.contains(s.level().dimension(), a.blockPosition()));
            var nearest = foes.stream().min(java.util.Comparator.comparingDouble(s::distanceToSqr)).orElse(null);
            if (nearest != null) {
                s.setTarget(nearest);
                return true;
            }
        }
        double near = s.isCombatant() ? (rift ? 2.5 : 4) : 1.8;
        if (s.blockPosition().distSqr(post) > near * near) {
            walk = s.isCombatant() ? MoveTo.block(post, near) : MoveTo.block(post, near);
        } else {
            s.getNavigation().stop();
            if (rift || s.isCombatant()) s.getLookControl().setLookAt(Vec3.atCenterOf(post));
        }
        return true;
    }

    @Override
    public void stop() {
        if (walk != null) walk.stop(s);
        walk = null;
    }
}
