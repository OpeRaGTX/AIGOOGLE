package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

public final class SettlerDefendGoal extends HurtByTargetGoal {
    private final SettlerEntity settler;

    public SettlerDefendGoal(SettlerEntity settler) {
        super(settler, SettlerEntity.class);
        this.settler = settler;
    }

    @Override
    public boolean canUse() {
        return settler.isCombatant() && super.canUse();
    }
}
