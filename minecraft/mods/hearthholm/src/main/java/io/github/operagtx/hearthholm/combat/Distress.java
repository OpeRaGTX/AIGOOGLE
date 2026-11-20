package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Wave L1: "a resident is attacked". Every combat settler that is awake, not retreating and free hears the call within the radius of his role
 * ({@link CombatRoles.Role#distressRadius}: the guard's covers the whole settlement, the others hear only what happens close by), takes on the attacker
 * and runs to him. Called by {@code SettlerEntity.hurtServer} when a hostile mob hurts any settler; players are never "called" on (strangers who hit
 * a resident are handled by the victim's own defence, owners are never fought, see wave M1).
 */
public final class Distress {
    private Distress() {}

    /** The widest radius any role listens to (the search box). */
    private static int widest() {
        return Math.max(io.github.operagtx.hearthholm.core.Balance.DISTRESS_RADIUS_GUARD, io.github.operagtx.hearthholm.core.Balance.DISTRESS_RADIUS_OTHER);
    }

    /** Alerts the combat settlers that hear {@code victim}; returns who answered (for the test and the log). */
    public static List<SettlerEntity> raise(SettlerEntity victim, LivingEntity attacker) {
        List<SettlerEntity> out = victim.level().getEntitiesOfClass(SettlerEntity.class, victim.getBoundingBox().inflate(widest()),
                g -> g != victim && g.isCombatant() && !g.isSleeping() && !g.isRetreating() && (g.getTarget() == null || !g.getTarget().isAlive())
                        && g.distanceToSqr(victim) <= sq(CombatRoles.of(g.professionId()).distressRadius()));
        for (SettlerEntity g : out) g.setTarget(attacker);
        return out;
    }

    private static double sq(int r) { return (double) r * r; }
}
