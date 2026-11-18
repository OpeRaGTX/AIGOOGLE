package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A combat settler below 30 % health withdraws to a barracks (or to a healer) and stays until it is back above 70 %.
 * Only when such a place exists; otherwise it fights on.
 */
public final class SettlerRetreatGoal extends Goal {
    private final SettlerEntity settler;
    private BlockPos spot;
    private int repath;
    /** Wave J1: archer without arrows withdrawing to the post / warehouse (a bounded retreat so it can go fetch arrows afterwards). */
    private boolean archer;
    private long archerUntil;

    public SettlerRetreatGoal(SettlerEntity settler) {
        this.settler = settler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return settler.isAlive() && (settler.wantsRetreat() || settler.wantsArcherRetreat());
    }

    @Override
    public boolean canContinueToUse() {
        if (archer) return settler.isAlive() && spot != null && settler.needsArrows() && settler.level().getGameTime() < archerUntil;
        return settler.isAlive() && settler.isCombatant() && !settler.inParty() && settler.getHealth() < settler.getMaxHealth() * 0.7f && spot != null;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        archer = !settler.wantsRetreat();
        archerUntil = settler.level().getGameTime() + 160;
        settler.setRetreating(true);
        settler.setTarget(null);
        settler.setAggressive(false);
        settler.stopUsingItem();
        settler.setStatus("retreating");
        settler.say("retreat");
        spot = archer ? settler.archerRetreatSpot() : settler.retreatSpot();
        repath = 0;
    }

    @Override
    public void stop() {
        settler.setRetreating(false);
        settler.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (settler.getTarget() != null && settler.tickCount % 10 == 0) settler.setTarget(null);
        if (--repath > 0) return;
        repath = 20;
        BlockPos fresh = archer ? settler.archerRetreatSpot() : settler.retreatSpot();
        if (fresh != null) spot = fresh;
        if (spot.distSqr(settler.blockPosition()) > 9) {
            settler.setStatus("retreating");
            settler.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.25 * settler.moveFactor());
        } else {
            settler.setStatus("resting_wounded");
            settler.getNavigation().stop();
        }
    }
}
