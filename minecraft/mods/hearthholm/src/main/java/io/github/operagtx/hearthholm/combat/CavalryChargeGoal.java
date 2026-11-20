package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Wave L1: the cavalryman's attack. He rides at the enemy and rams him with the spear, does not stop but rides on past him, turns when he is far enough away and
 * rams him again. Two phases: CHARGE (ride at the target, strike when close and the weapon is ready) and RUN_PAST (ride to a point beyond the target
 * until {@code Balance.CAVALRY_TURN_DISTANCE} blocks away, or the time is up). All movement goes through the horse's navigation (see {@link Cavalry}).
 */
public final class CavalryChargeGoal extends Goal {
    private enum Phase { CHARGE, RUN_PAST }

    private final SettlerEntity settler;
    private Phase phase = Phase.CHARGE;
    private Vec3 pastPoint = Vec3.ZERO;
    private long passUntil, nextBlow;
    private int repath;
    /** Blows struck and times the rider turned around, for the test and the log. */
    public int strikes, turns;

    public CavalryChargeGoal(SettlerEntity settler) {
        this.settler = settler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = settler.getTarget();
        return t != null && t.isAlive() && settler.isCombatant() && !settler.isRetreating() && Cavalry.mountOf(settler) != null;
    }

    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        phase = Phase.CHARGE;
        repath = 0;
        settler.setAggressive(true);
    }

    @Override
    public void stop() {
        settler.setAggressive(false);
        Horse h = Cavalry.mountOf(settler);
        if (h != null) h.getNavigation().stop();
    }

    @Override
    public void tick() {
        Horse horse = Cavalry.mountOf(settler);
        LivingEntity t = settler.getTarget();
        if (horse == null || t == null || !(settler.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        double dx = t.getX() - horse.getX(), dz = t.getZ() - horse.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        settler.getLookControl().setLookAt(t, 30f, 30f);
        if (phase == Phase.CHARGE) {
            if (--repath <= 0) {
                repath = 4;
                horse.getNavigation().moveTo(t, Balance.CAVALRY_CHARGE_SPEED_X10 / 10.0);
            }
            if (flat < Balance.CAVALRY_REACH_X10 / 10.0 && now >= nextBlow) {
                strike(level, t, horse);
                Vec3 away = flat < 0.01 ? horse.getLookAngle().multiply(1, 0, 1) : new Vec3(dx, 0, dz).normalize();
                pastPoint = new Vec3(t.getX() + away.x * (Balance.CAVALRY_TURN_DISTANCE + 4), t.getY(), t.getZ() + away.z * (Balance.CAVALRY_TURN_DISTANCE + 4));
                passUntil = now + Balance.CAVALRY_PASS_TICKS;
                phase = Phase.RUN_PAST;
                repath = 0;
            }
        } else {
            if (--repath <= 0) {
                repath = 6;
                horse.getNavigation().moveTo(pastPoint.x, pastPoint.y, pastPoint.z, Balance.CAVALRY_CHARGE_SPEED_X10 / 10.0);
            }
            if (flat >= Balance.CAVALRY_TURN_DISTANCE || now >= passUntil) {   // far enough (or boxed in): turn around
                phase = Phase.CHARGE;
                turns++;
                repath = 0;
            }
        }
    }

    /** One ram: the spear's own damage plus the charge bonus, a push away from the horse, a swing and a sound. */
    private void strike(ServerLevel level, LivingEntity target, Horse horse) {
        strikes++;
        nextBlow = level.getGameTime() + Balance.CAVALRY_BLOW_PAUSE_TICKS;
        settler.swingArm(InteractionHand.MAIN_HAND);
        settler.doHurtTarget(level, target);
        target.hurtServer(level, level.damageSources().mobAttack(settler), Balance.CAVALRY_CHARGE_BONUS_X10 / 10f);
        Vec3 push = horse.getDeltaMovement().multiply(1, 0, 1);   // the horse's momentum pushes the target on
        target.push(push.x * 0.8, 0.25, push.z * 0.8);
    }
}
