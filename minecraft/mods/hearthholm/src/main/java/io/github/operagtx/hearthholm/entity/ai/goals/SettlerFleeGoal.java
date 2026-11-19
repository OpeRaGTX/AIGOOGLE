package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.core.Trait;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;

/** Civilians run from monsters (cowards from further away), the brave stand firm. */
public final class SettlerFleeGoal extends AvoidEntityGoal<Monster> {
    private final SettlerEntity settler;

    public SettlerFleeGoal(SettlerEntity settler) {
        super(settler, Monster.class, 8f, 0.7, 1.0);
        this.settler = settler;
    }

    @Override
    public boolean canUse() {
        if (settler.isCombatant() || settler.isSleeping()) return false;
        for (Trait t : settler.profile().traitList()) if (t.fearless()) return false;
        return super.canUse();
    }
}
