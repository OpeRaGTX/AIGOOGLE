package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public final class SettlerMeleeGoal extends MeleeAttackGoal {
    private final SettlerEntity settler;

    public SettlerMeleeGoal(SettlerEntity settler) {
        super(settler, 1.0, true);
        this.settler = settler;
    }

    private long nextBlow;

    /** Vanilla-like cooldown of the held weapon: 20 ticks / attack speed (sword 1.6 -> 13 ticks, bare hand 4 -> 5), never below the balance floor. */
    private int interval() {
        double speed = Math.max(0.5, settler.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
        return Math.max(io.github.operagtx.hearthholm.core.Balance.ATTACK_INTERVAL_MIN_TICKS, (int) Math.ceil(20.0 / speed));
    }

    @Override protected boolean isTimeToAttack() { return settler.level().getGameTime() >= nextBlow; }
    @Override protected void resetAttackCooldown() { nextBlow = settler.level().getGameTime() + interval(); }

    @Override public boolean canUse() { return settler.isCombatant() && !settler.isSleeping() && !settler.isRetreating() && !settler.isRanged() && !settler.isCavalryMounted() && super.canUse(); }
    @Override public boolean canContinueToUse() { return settler.isCombatant() && !settler.isRetreating() && !settler.isRanged() && !settler.isCavalryMounted() && super.canContinueToUse(); }
}
