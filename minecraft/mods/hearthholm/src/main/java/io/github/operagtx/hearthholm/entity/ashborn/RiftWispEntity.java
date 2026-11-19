package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rift Wisp: a small, fast, hovering spark that pours out of rifts. It ignores gravity and steers straight
 * at its target (no pathfinding), bumping into it for light damage; without a target it circles its home.
 */
public class RiftWispEntity extends Monster {
    private @Nullable BlockPos home;
    private int attackCooldown;

    public RiftWispEntity(EntityType<? extends RiftWispEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.xpReward = 4;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, SettlerEntity.class, false));
    }

    public void setHome(BlockPos pos) {
        home = pos;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (home == null) home = blockPosition();
        LivingEntity t = getTarget();
        Vec3 goal;
        if (t != null && t.isAlive()) {
            goal = t.position().add(0, t.getBbHeight() * 0.6, 0);
        } else {
            double a = (tickCount + getId() * 37) * 0.05;
            goal = Vec3.atCenterOf(home).add(Math.cos(a) * 4, 2.5 + Math.sin(a * 1.7), Math.sin(a) * 4);
        }
        Vec3 d = goal.subtract(position());
        double len = d.length();
        Vec3 v = getDeltaMovement().scale(0.8);
        if (len > 0.01) v = v.add(d.scale(0.09 / len));
        if (horizontalCollision) v = v.add(0, 0.08, 0);
        if (v.length() > 0.45) v = v.normalize().scale(0.45);
        setDeltaMovement(v);
        if (v.horizontalDistanceSqr() > 1e-4) {
            float yaw = (float) (Math.atan2(v.z, v.x) * 180 / Math.PI) - 90f;
            setYRot(yaw);
            yBodyRot = yaw;
        }
        if (attackCooldown > 0) attackCooldown--;
        if (t != null && attackCooldown == 0 && getBoundingBox().inflate(0.35).intersects(t.getBoundingBox())) {
            doHurtTarget(level, t);
            attackCooldown = 20;
        }
        if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 0.25, getZ(), 1, 0.1, 0.1, 0.1, 0);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean playerKill) {
        super.dropCustomDeathLoot(level, source, playerKill);
        if (getRandom().nextFloat() < 0.2f) spawnAtLocation(level, new ItemStack(ModItems.RIFT_SHARD));
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.VEX_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VEX_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VEX_DEATH; }
    @Override public float getVoicePitch() { return 1.6f; }
}
