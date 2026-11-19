package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.threat.ThreatConfig;
import io.github.operagtx.hearthholm.threat.Threats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Arsonists look for wooden buildings near them (a few random samples per check, no area scans), walk up
 * and light a fire beside the wood. At most {@link #MAX_FIRES} fires each; every fire is tracked by
 * {@link Threats} and put out after a short while together with whatever spread from it.
 */
public final class IgniteGoal extends Goal {
    public static final int MAX_FIRES = 3;
    private final AshArsonistEntity mob;
    private @Nullable BlockPos fireAt;
    private int cooldown, timeout;

    public IgniteGoal(AshArsonistEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ThreatConfig.raidFire || mob.firesLit() >= MAX_FIRES || mob.raidTarget() == null) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 10;
        if (mob.getTarget() != null && mob.distanceToSqr(mob.getTarget()) < 36) return false;
        fireAt = findSpot();
        return fireAt != null;
    }

    private @Nullable BlockPos findSpot() {
        var r = mob.getRandom();
        BlockPos base = mob.blockPosition();
        for (int i = 0; i < 24; i++) {
            BlockPos p = base.offset(r.nextInt(21) - 10, r.nextInt(5) - 1, r.nextInt(21) - 10);
            BlockState s = mob.level().getBlockState(p);
            if (!intendedTarget(s)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos air = p.relative(d);
                if (mob.level().getBlockState(air).isAir() && openToSky(mob.level(), air)
                        && BaseFireBlock.canBePlacedAt(mob.level(), air, d.getOpposite())) return air.immutable();
            }
        }
        return null;
    }

    /** Wave E: only palisades, gates, doors and fences are fair game; planks and logs (walls, roofs) are never targets. */
    public static boolean intendedTarget(BlockState s) {
        return s.is(io.github.operagtx.hearthholm.registry.ModBlocks.PALISADE) || s.is(io.github.operagtx.hearthholm.registry.ModBlocks.PALISADE_GATE)
                || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.FENCE_GATES);
    }

    /** The fire spot has no ceiling above it, so the flames cannot lick a roof or an eave. */
    public static boolean openToSky(net.minecraft.world.level.Level level, BlockPos p) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) <= p.getY() + 1;
    }

    @Override
    public boolean canContinueToUse() {
        return fireAt != null && timeout > 0 && mob.level().getBlockState(fireAt).isAir();
    }

    @Override
    public void start() {
        timeout = 200;
        mob.getNavigation().moveTo(fireAt.getX() + 0.5, fireAt.getY(), fireAt.getZ() + 0.5, 1.15);
    }

    @Override
    public void tick() {
        timeout--;
        mob.getLookControl().setLookAt(fireAt.getX() + 0.5, fireAt.getY() + 0.5, fireAt.getZ() + 0.5);
        if (mob.distanceToSqr(fireAt.getX() + 0.5, fireAt.getY(), fireAt.getZ() + 0.5) > 2.6 * 2.6) {
            if (timeout % 20 == 0) mob.getNavigation().moveTo(fireAt.getX() + 0.5, fireAt.getY(), fireAt.getZ() + 0.5, 1.15);
            return;
        }
        if (mob.level() instanceof ServerLevel sl && Threats.ignite(sl, fireAt)) {
            mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
            sl.playSound(null, fireAt, SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1f, 0.9f);
            mob.addFire();
        }
        fireAt = null;
        cooldown = 160;
    }

    @Override
    public void stop() {
        fireAt = null;
        mob.getNavigation().stop();
    }
}
