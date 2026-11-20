package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wave L1: the skeleton's way of aiming. While the target is in range the shooter does not stand still: he sidesteps (left/right changes now and then)
 * and backs off when the target comes close, so he is harder to hit and to catch. Mirrors vanilla's {@code RangedBowAttackGoal} strafing; the state
 * (direction, timer) lives here, the goal only calls {@link #tick} while it aims and {@link #reset} otherwise.
 */
public final class Strafe {
    private int time = -1;
    private boolean clockwise = true, backwards;

    public void reset() { time = -1; }

    /** Whether strafing is running now (the goal must not give walking orders meanwhile). */
    public boolean active() { return time > -1; }

    /**
     * One tick of aiming at {@code target} from {@code dist} blocks. Every 20 ticks each of the two choices flips with 30% chance; backing off is forced
     * inside a quarter of {@code range} and cancelled outside three quarters.
     */
    public void tick(SettlerEntity s, LivingEntity target, double dist, double range) {
        s.getLookControl().setLookAt(target, 30f, 30f);
        if (edgeNear(s)) {   // a drop, water or lava within a step in any direction: no sidestepping here, shoot standing
            time = -1;
            s.getNavigation().stop();
            return;
        }
        time++;
        if (time >= 20) {
            if (s.getRandom().nextFloat() < 0.3f) clockwise = !clockwise;
            if (s.getRandom().nextFloat() < 0.3f) backwards = !backwards;
            time = 0;
        }
        if (dist > range * 0.75) backwards = false;
        else if (dist < range * 0.25) backwards = true;
        s.getMoveControl().strafe(backwards ? -0.5f : 0.5f, clockwise ? 0.5f : -0.5f);
    }

    /**
     * Whether the ground is missing or not safe within 1.5 blocks in any of eight directions (checked from the settler's feet: solid within two blocks
     * below, no fluid). A strafing shooter must never sidestep off a ledge, a shore or a pit; the test that found this lost an archer from a sky platform.
     */
    private static boolean edgeNear(SettlerEntity s) {
        var level = s.level();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            net.minecraft.core.BlockPos q = net.minecraft.core.BlockPos.containing(s.getX() + Math.cos(a) * 1.5, s.getY() - 0.5, s.getZ() + Math.sin(a) * 1.5);
            boolean solid = !level.getBlockState(q).isAir() || !level.getBlockState(q.below()).isAir();
            boolean fluid = !level.getFluidState(q).isEmpty() || !level.getFluidState(q.above()).isEmpty();
            if (!solid || fluid) return true;
        }
        return false;
    }
}
