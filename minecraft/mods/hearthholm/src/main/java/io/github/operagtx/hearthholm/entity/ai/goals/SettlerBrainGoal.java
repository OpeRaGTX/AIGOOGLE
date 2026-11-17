package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Hosts the settler brain inside vanilla's goal system so combat/flee goals can pre-empt it. */
public final class SettlerBrainGoal extends Goal {
    private final SettlerEntity settler;

    public SettlerBrainGoal(SettlerEntity settler) {
        this.settler = settler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() { return settler.isAlive() && !settler.isPassenger(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { settler.brain().tick(); }
    @Override public void stop() { settler.brain().pause(); }
}
