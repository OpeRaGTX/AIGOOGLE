package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;

/** Guards and warriors hunt hostile mobs inside the area they protect (never creepers in melee). */
public final class SettlerTargetGoal extends NearestAttackableTargetGoal<Mob> {
    private final SettlerEntity settler;

    public SettlerTargetGoal(SettlerEntity settler) {
        super(settler, Mob.class, 10, true, false,
                (target, level) -> target instanceof Enemy && !(target instanceof Creeper) && settler.canDefend(target));
        this.settler = settler;
    }

    @Override
    public boolean canUse() {
        return settler.isCombatant() && !settler.isSleeping() && super.canUse();
    }
}
