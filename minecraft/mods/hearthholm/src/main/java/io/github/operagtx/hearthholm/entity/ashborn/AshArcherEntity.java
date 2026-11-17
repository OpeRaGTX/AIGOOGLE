package io.github.operagtx.hearthholm.entity.ashborn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Keeps its distance and shoots arrows. */
public class AshArcherEntity extends AshbornEntity implements RangedAttackMob {
    public AshArcherEntity(EntityType<? extends AshArcherEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AshbornEntity.createAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.29);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, 40, 15f));
    }

    @Override
    protected void equip(RandomSource random) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel sl)) return;
        ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack ammo = getProjectile(bow);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power, bow);
        double dx = target.getX() - getX(), dy = target.getY(0.3333) - arrow.getY(), dz = target.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(arrow, sl, ammo, dx, dy + d * 0.2, dz, 1.6f, 14 - sl.getDifficulty().getId() * 4);
        playSound(SoundEvents.SKELETON_SHOOT, 1f, 1f / (getRandom().nextFloat() * 0.4f + 0.8f));
    }

    @Override
    protected void extraLoot(ServerLevel level, RandomSource r) {
        if (r.nextFloat() < 0.6f) spawnAtLocation(level, new ItemStack(Items.ARROW, 2 + r.nextInt(4)));
    }
}
