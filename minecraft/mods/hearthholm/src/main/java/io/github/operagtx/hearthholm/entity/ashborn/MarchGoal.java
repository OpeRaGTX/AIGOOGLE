package io.github.operagtx.hearthholm.entity.ashborn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** With nobody to fight, raid members march on the town hall in 16-block legs (paths stay short and cheap). */
public final class MarchGoal extends Goal {
    private final AshbornEntity mob;
    private int recalc;

    public MarchGoal(AshbornEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos t = mob.raidTarget();
        return t != null && mob.getTarget() == null && mob.blockPosition().distSqr(t) > 36;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        recalc = 0;
    }

    @Override
    public void tick() {
        if (--recalc > 0 && !mob.getNavigation().isDone()) return;
        recalc = 40;
        BlockPos t = mob.raidTarget();
        if (t == null) return;
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
        mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
