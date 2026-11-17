package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.block.PalisadeBlock;
import io.github.operagtx.hearthholm.block.PalisadeGateBlock;
import net.minecraft.core.BlockPos;
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
 * Raiders hack through whatever walls them off from their goal. Every Ashborn breaks wooden doors (not palisade gates);
 * brutes and the chieftain also break palisade walls and gates. Reliable by design: whenever the mob wants to reach
 * something, has no path (or has not moved for a moment) and a barrier lies within two blocks towards the goal, it
 * stops and hacks at it with the usual crack animation; a broken palisade/door column is cleared up to the top so the
 * gap is passable.
 */
public final class BreakBarrierGoal extends Goal {
    private final AshbornEntity mob;
    private final boolean palisades;
    private @Nullable BlockPos barrier;
    private int progress, needed, check;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks;

    public BreakBarrierGoal(AshbornEntity mob, boolean palisades) {
        this.mob = mob;
        this.palisades = palisades;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean isBarrier(BlockState s, boolean palisades) {
        if (s.getBlock() instanceof PalisadeGateBlock) return palisades;
        if (s.getBlock() instanceof PalisadeBlock) return palisades;
        return s.getBlock() instanceof DoorBlock;
    }

    public boolean breaking() { return barrier != null; }

    @Override
    public boolean canUse() {
        if (++check % 5 != 0) return false;
        if (mob.position().distanceToSqr(lastPos) < 0.03) stuckTicks += 5; else stuckTicks = 0;
        lastPos = mob.position();
        Vec3 goal = goal();
        if (goal == null) return false;
        boolean noPath = mob.getNavigation().isDone() || mob.getNavigation().getPath() == null;
        if (!noPath && stuckTicks < 20) return false;
        if (stuckTicks < 10 && !noPath) return false;
        barrier = findBarrier(goal);
        return barrier != null;
    }

    private @Nullable Vec3 goal() {
        LivingEntity t = mob.getTarget();
        if (t != null && mob.distanceToSqr(t) > 2.5) return t.position();
        BlockPos b = mob.breakGoal();
        if (b != null) return Vec3.atBottomCenterOf(b);
        BlockPos r = mob.raidTarget();
        return r == null || mob.blockPosition().distSqr(r) < 9 ? null : Vec3.atBottomCenterOf(r);
    }

    /** A barrier within two blocks in the direction of the goal (and the two neighbouring directions). */
    private @Nullable BlockPos findBarrier(Vec3 goal) {
        BlockPos feet = mob.blockPosition();
        double dx = goal.x - mob.getX(), dz = goal.z - mob.getZ();
        int sx = (int) Math.signum(Math.abs(dx) > 0.7 ? dx : 0), sz = (int) Math.signum(Math.abs(dz) > 0.7 ? dz : 0);
        int[][] dirs = {{sx, sz}, {sx, 0}, {0, sz}, {sz, -sx}, {-sz, sx}};
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int[] d : dirs) {
            if (d[0] == 0 && d[1] == 0) continue;
            for (int step = 1; step <= 2; step++)
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos p = feet.offset(d[0] * step, dy, d[1] * step);
                    if (!isBarrier(mob.level().getBlockState(p), palisades)) continue;
                    double dist = p.distSqr(feet) + (step - 1) * 0.5;
                    if (dist < bd) { bd = dist; best = p; }
                }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return barrier != null && isBarrier(mob.level().getBlockState(barrier), palisades) && mob.distanceToSqr(Vec3.atCenterOf(barrier)) < 16;
    }

    @Override
    public void start() {
        progress = 0;
        BlockState s = mob.level().getBlockState(barrier);
        needed = s.getBlock() instanceof PalisadeBlock ? 100 : s.getBlock() instanceof PalisadeGateBlock ? 120 : 70;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(Vec3.atCenterOf(barrier));
        if (mob.distanceToSqr(Vec3.atCenterOf(barrier)) > 6.25) mob.getNavigation().moveTo(barrier.getX() + 0.5, barrier.getY(), barrier.getZ() + 0.5, 1.0);
        else mob.getNavigation().stop();
        progress++;
        if (progress % 15 == 0) {
            mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
            mob.level().playSound(null, barrier, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.9f, 0.8f);
        }
        mob.level().destroyBlockProgress(mob.getId(), barrier, (int) (progress / (float) needed * 10f));
        if (progress >= needed) {
            mob.level().destroyBlockProgress(mob.getId(), barrier, -1);
            clear(barrier);
            barrier = null;
            stuckTicks = 0;
        }
    }

    /** Breaks the block and the rest of its column of barrier blocks (two-high walls, doors) so the gap is passable. */
    private void clear(BlockPos pos) {
        mob.level().destroyBlock(pos, true, mob, 512);
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos p = pos.above(dy);
            if (!p.equals(pos) && isBarrier(mob.level().getBlockState(p), palisades)
                    && (mob.level().getBlockState(p).getBlock() instanceof PalisadeBlock || mob.level().getBlockState(p).getBlock() instanceof PalisadeGateBlock || mob.level().getBlockState(p).getBlock() instanceof DoorBlock))
                mob.level().destroyBlock(p, true, mob, 512);
        }
    }

    @Override
    public void stop() {
        if (barrier != null) mob.level().destroyBlockProgress(mob.getId(), barrier, -1);
        barrier = null;
    }
}
