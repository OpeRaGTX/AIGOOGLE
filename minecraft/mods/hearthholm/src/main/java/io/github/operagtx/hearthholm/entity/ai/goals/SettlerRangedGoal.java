package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import io.github.operagtx.hearthholm.core.Balance;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Bow attack for settlers (vanilla's {@code RangedBowAttackGoal} needs a Monster): keeps 7 to 14 blocks from the
 * target, draws the bow for a second, shoots and consumes one arrow from the pockets per shot.
 */
public final class SettlerRangedGoal extends Goal {
    private final SettlerEntity settler;
    private int cooldown, seeTime, repath;

    public SettlerRangedGoal(SettlerEntity settler) {
        this.settler = settler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = settler.getTarget();
        return t != null && t.isAlive() && settler.isCombatant() && !settler.isSleeping() && !settler.isRetreating() && settler.isRanged();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        settler.setAggressive(true);
        cooldown = 15;
        seeTime = 0;
    }

    @Override
    public void stop() {
        settler.setAggressive(false);
        settler.stopUsingItem();
        seeTime = 0;
        settler.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = settler.getTarget();
        if (t == null) return;
        double dist = Math.sqrt(settler.distanceToSqr(t));
        boolean sees = settler.getSensing().hasLineOfSight(t);
        seeTime = sees ? Math.max(1, seeTime + 1) : Math.min(-1, seeTime - 1);
        settler.getLookControl().setLookAt(t, 30f, 30f);
        if (--repath <= 0) {
            repath = 10;
            if (sees && dist < 7) {
                Vec3 away = settler.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(8);
                BlockPos p = BlockPos.containing(settler.getX() + away.x, settler.getY(), settler.getZ() + away.z);
                settler.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.15 * settler.moveFactor());
            } else if (!sees || dist > 14) {
                settler.getNavigation().moveTo(t, 1.0 * settler.moveFactor());
            } else {
                settler.getNavigation().stop();
            }
        }
        if (settler.isUsingItem()) {
            if (!sees && seeTime < -60) {
                settler.stopUsingItem();
            } else if (sees && settler.getTicksUsingItem() >= Balance.ARCHER_DRAW_TICKS) {
                int drawn = settler.getTicksUsingItem();
                settler.stopUsingItem();
                shoot(t, dist, drawn);
                cooldown = 22;
            }
        } else if (--cooldown <= 0 && seeTime >= -60 && sees && dist < 22) {
            settler.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private void shoot(LivingEntity target, double dist, int drawn) {
        if (!(settler.level() instanceof ServerLevel sl) || !settler.takeItem(Items.ARROW, 1)) return;
        ItemStack bow = settler.getMainHandItem();
        ItemStack ammo = new ItemStack(Items.ARROW);
        float power = BowItem.getPowerForTime(drawn);   // vanilla: 0..1 from the draw time, full at one second
        AbstractArrow arrow = ProjectileUtil.getMobArrow(settler, ammo, power, bow);
        // vanilla bow: damage = ceil(speed x base damage) = 3 x 2 = 6 at full charge; a small bonus per level
        arrow.setBaseDamage(2.0 + Balance.ARCHER_DMG_PER_LEVEL_PCT / 100.0 * (settler.level(settler.professionId()) - 1));
        arrow.setCritArrow(false);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        double dx = target.getX() - settler.getX(), dy = target.getY(0.3333) - arrow.getY(), dz = target.getZ() - settler.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(arrow, sl, ammo, dx, dy + d * 0.2, dz, power * 3.0f, 1.5f);
        if (bow.isDamageableItem()) bow.hurtAndBreak(1, settler, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        settler.playSound(SoundEvents.SKELETON_SHOOT, 1f, 1f / (settler.getRandom().nextFloat() * 0.4f + 0.8f));
        settler.gainXp(1);
        var st = settler.settlement();
        if (st != null) st.stats.merge("arrows_used", 1, Integer::sum);
    }
}
