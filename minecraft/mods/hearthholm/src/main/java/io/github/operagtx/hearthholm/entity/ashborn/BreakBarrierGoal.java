package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.block.PalisadeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Brutes batter through palisades, palisade gates and doors (nothing else) when one blocks their way:
 * they stand still and hack at it for several seconds, showing the usual crack animation.
 */
public final class BreakBarrierGoal extends Goal {
    private final AshbornEntity mob;
    private @Nullable BlockPos barrier;
    private int progress, needed, check;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks;

    public BreakBarrierGoal(AshbornEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean isBarrier(BlockState s) {
        return s.getBlock() instanceof PalisadeBlock || s.getBlock() instanceof DoorBlock;
    }

    @Override
    public boolean canUse() {
        if (++check % 10 != 0) return false;
        if (mob.position().distanceToSqr(lastPos) < 0.04) stuckTicks += 10; else stuckTicks = 0;
        lastPos = mob.position();
        Vec3 goal = goal();
        if (goal == null || stuckTicks < 20) return false;
        barrier = findBarrier(goal);
        return barrier != null;
    }

    private @Nullable Vec3 goal() {
        LivingEntity t = mob.getTarget();
        if (t != null) return t.position();
        BlockPos r = mob.raidTarget();
        return r == null ? null : Vec3.atBottomCenterOf(r);
    }

    private @Nullable BlockPos findBarrier(Vec3 goal) {
        BlockPos feet = mob.blockPosition();
        Direction toward = Direction.getApproximateNearest(goal.x - mob.getX(), 0, goal.z - mob.getZ());
        Direction[] order = {toward, toward.getClockWise(), toward.getCounterClockWise()};
        for (Direction d : order)
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = feet.relative(d).above(dy);
                if (isBarrier(mob.level().getBlockState(p))) return p;
            }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return barrier != null && isBarrier(mob.level().getBlockState(barrier)) && mob.distanceToSqr(Vec3.atCenterOf(barrier)) < 9;
    }

    @Override
    public void start() {
        progress = 0;
        BlockState s = mob.level().getBlockState(barrier);
        needed = s.getBlock() instanceof PalisadeBlock ? 120 : 80;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(Vec3.atCenterOf(barrier));
        progress++;
        if (progress % 15 == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level().playSound(null, barrier, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.9f, 0.8f);
        }
        mob.level().destroyBlockProgress(mob.getId(), barrier, (int) (progress / (float) needed * 10f));
        if (progress >= needed) {
            mob.level().destroyBlock(barrier, true, mob, 512);
            mob.level().destroyBlockProgress(mob.getId(), barrier, -1);
            barrier = null;
            stuckTicks = 0;
        }
    }

    @Override
    public void stop() {
        if (barrier != null) mob.level().destroyBlockProgress(mob.getId(), barrier, -1);
        barrier = null;
    }
}
