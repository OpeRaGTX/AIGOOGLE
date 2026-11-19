package io.github.operagtx.hearthholm.entity.ashborn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * With nobody to fight, raid members march on the town hall in 16-block legs (paths stay short and cheap).
 * Once there they hunt: the nearest settler out in the open (else anyone) becomes the target, sight or not.
 */
public final class MarchGoal extends Goal {
    private final AshbornEntity mob;
    private int recalc;

    public MarchGoal(AshbornEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private int huntCooldown, noProgress;

    @Override
    public boolean canUse() {
        BlockPos t = mob.raidTarget();
        if (t == null || mob.getTarget() != null) return false;
        BlockPos door = mob.breakGoal();
        if (door != null) {
            if (mob.level().getBlockState(door).getBlock() instanceof net.minecraft.world.level.block.DoorBlock && mob.blockPosition().distSqr(door) < 30 * 30) return true;
            mob.setBreakGoal(null);
        }
        if (--huntCooldown <= 0) {
            huntCooldown = 20;
            hunt();
            // nobody to hunt in the open and the hall is reached or walled off: go and break a door
            if (mob.getTarget() == null && mob.breakGoal() == null && (mob.blockPosition().distSqr(t) <= 36 || ++noProgress > 3)) pickDoor();
        }
        if (mob.getTarget() != null) return false;
        if (mob.breakGoal() != null) return true;
        return mob.blockPosition().distSqr(t) > 36;
    }

    /** Nobody left to hunt in the open: go and break down the nearest closed door (that is where they hide). */
    private void pickDoor() {
        var level = mob.level();
        BlockPos from = mob.blockPosition();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-18, -2, -18), from.offset(18, 3, 18))) {
            var st = level.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) || st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                    || st.getBlock() instanceof io.github.operagtx.hearthholm.block.PalisadeGateBlock) continue;
            if (st.getValue(net.minecraft.world.level.block.DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) continue;
            double d = p.distSqr(from);
            if (d < bd) { bd = d; best = p.immutable(); }
        }
        if (best != null) mob.setBreakGoal(best);
    }

    private void hunt() {
        var level = mob.level();
        io.github.operagtx.hearthholm.entity.SettlerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : level.getEntitiesOfClass(io.github.operagtx.hearthholm.entity.SettlerEntity.class, mob.getBoundingBox().inflate(48, 12, 48), x -> x.isAlive() && !mob.ignores(x))) {
            double d = mob.distanceToSqr(e);
            boolean open = level.getHeight(Heightmap.Types.MOTION_BLOCKING, e.getBlockX(), e.getBlockZ()) <= e.getBlockY() + 1;
            if (!open) d += 48 * 48;
            if (d < bd) { bd = d; best = e; }
        }
        if (best != null) mob.setTarget(best);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        recalc = 0;
    }

    private Vec3 lastPos = Vec3.ZERO;
    private int still;

    @Override
    public void tick() {
        if (mob.position().distanceToSqr(lastPos) < 1) {
            if (++still > 60) {
                // wedged against something: side-step a few blocks and try again
                still = 0;
                int x = mob.getBlockX() + mob.getRandom().nextInt(13) - 6, z = mob.getBlockZ() + mob.getRandom().nextInt(13) - 6;
                mob.getNavigation().moveTo(x + 0.5, mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 1.0);
                recalc = 40;
                return;
            }
        } else {
            still = 0;
            lastPos = mob.position();
        }
        if (--recalc > 0 && !mob.getNavigation().isDone()) return;
        recalc = 40;
        BlockPos t = mob.raidTarget();
        if (t == null) return;
        BlockPos door = mob.breakGoal();
        if (door != null) {
            if (!mob.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.0)) {
                mob.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.0);
            }
            return;
        }
        Vec3 from = mob.position();
        Vec3 to = Vec3.atBottomCenterOf(t);
        Vec3 d = to.subtract(from);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        if (len > 20) {
            Vec3 step = from.add(d.x / len * 16 + mob.getRandom().nextInt(5) - 2, 0, d.z / len * 16 + mob.getRandom().nextInt(5) - 2);
            int x = (int) Math.floor(step.x), z = (int) Math.floor(step.z);
            int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            to = new Vec3(x + 0.5, y, z + 0.5);
        }
        if (!mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0)) {
            // no path to the leg target (water, a wall): try a short random step towards the hall instead
            Vec3 near = from.add(d.x / Math.max(1, len) * 6 + mob.getRandom().nextInt(7) - 3, 0, d.z / Math.max(1, len) * 6 + mob.getRandom().nextInt(7) - 3);
            int x = (int) Math.floor(near.x), z = (int) Math.floor(near.z);
            mob.getNavigation().moveTo(x + 0.5, mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 1.0);
            recalc = 20;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
