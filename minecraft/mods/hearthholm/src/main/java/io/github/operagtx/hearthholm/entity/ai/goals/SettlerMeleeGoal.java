package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public final class SettlerMeleeGoal extends MeleeAttackGoal {
    private final SettlerEntity settler;

    public SettlerMeleeGoal(SettlerEntity settler) {
        super(settler, 1.0, true);
        this.settler = settler;
    }

    @Override public boolean canUse() { return settler.isCombatant() && !settler.isSleeping() && super.canUse(); }
    @Override public boolean canContinueToUse() { return settler.isCombatant() && super.canContinueToUse(); }
}
