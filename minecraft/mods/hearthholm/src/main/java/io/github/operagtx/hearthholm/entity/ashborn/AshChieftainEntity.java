package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModEntities;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ash Chieftain ("Вождь Пепла"), the raid boss. Three phases by health: 1) a heavy axe; 2) enraged —
 * faster swings and steps; 3) calls raiders to his side and drops rings of fire: a ring of small flames
 * marks the spot for two seconds (the telegraph), then it burns for three.
 */
public class AshChieftainEntity extends AshbornEntity {
    public static final int TELEGRAPH = 40, BURN = 60;
    public static final double RING_RADIUS = 3.5;
    private static final AttributeModifier RAGE = new AttributeModifier(Hearthholm.id("chieftain_rage"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    /** A fire ring on the ground: armed (deals damage) from {@code armAt} until {@code endAt}. */
    public record Ring(Vec3 center, long armAt, long endAt) {}

    private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.hearthholm.ash_chieftain"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
    private final List<Ring> rings = new ArrayList<>();
    private int phase = 1;
    private int ringCooldown = 60, summonCooldown;

    public AshChieftainEntity(EntityType<? extends AshChieftainEntity> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        bossEvent.setDarkenScreen(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AshbornEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 220)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.SCALE, 1.6);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(1, new BreakBarrierGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            protected int getAttackInterval() {
                return phase >= 2 ? 10 : 20;
            }
        });
    }

    @Override
    protected void equip(RandomSource random) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    public int phase() { return phase; }
    public List<Ring> rings() { return rings; }
    public ServerBossEvent bossEvent() { return bossEvent; }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        float frac = getHealth() / getMaxHealth();
        bossEvent.setProgress(frac);
        int want = frac > 0.66f ? 1 : frac > 0.33f ? 2 : 3;
        if (want > phase) enterPhase(level, want);
        tickRings(level);
        if (phase >= 3 && getTarget() != null) {
            if (--ringCooldown <= 0) {
                ringCooldown = 140;
                castRing(getTarget().position());
            }
            if (--summonCooldown <= 0) {
                summonCooldown = 600;
                summon(level, 2);
            }
        }
    }

    private void enterPhase(ServerLevel level, int p) {
        phase = p;
        level.playSound(null, blockPosition(), ModSounds.BOSS_ROAR, SoundSource.HOSTILE, 3f, p == 2 ? 1f : 0.85f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 30, 0.8, 1, 0.8, 0.05);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 40, 1, 1, 1, 0.08);
        if (p >= 2) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.addOrUpdateTransientModifier(RAGE);
            bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
        }
        if (p >= 3) {
            bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
            summon(level, 3);
            summonCooldown = 600;
            ringCooldown = 30;
        }
        bossEvent.setName(Component.translatable("entity.hearthholm.ash_chieftain").append(" — ")
                .append(Component.translatable("boss.hearthholm.phase." + p)));
    }

    /** Places a fire ring centred on {@code at}: telegraphed first, then burning. */
    public void castRing(Vec3 at) {
        long now = level().getGameTime();
        rings.add(new Ring(at, now + TELEGRAPH, now + TELEGRAPH + BURN));
    }

    private void tickRings(ServerLevel level) {
        long now = level.getGameTime();
        rings.removeIf(r -> now > r.endAt());
        for (Ring r : rings) {
            boolean armed = now >= r.armAt();
            if (now % 4 == 0) {
                int n = 28;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + now * 0.02;
                    double x = r.center().x + Math.cos(a) * RING_RADIUS, z = r.center().z + Math.sin(a) * RING_RADIUS;
                    level.sendParticles(armed ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, x, r.center().y + 0.1, z, 1, 0.02, armed ? 0.25 : 0.02, 0.02, 0.01);
                }
                if (armed) level.sendParticles(ParticleTypes.LAVA, r.center().x, r.center().y + 0.2, r.center().z, 3, RING_RADIUS * 0.5, 0.1, RING_RADIUS * 0.5, 0);
            }
            if (armed && (now - r.armAt()) % 10 == 0) {
                AABB box = new AABB(r.center(), r.center()).inflate(RING_RADIUS, 1.5, RING_RADIUS).move(0, 1, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof AshbornEntity) && e.isAlive())) {
                    if (e.position().distanceToSqr(r.center().x, e.getY(), r.center().z) > RING_RADIUS * RING_RADIUS) continue;
                    e.hurtServer(level, level.damageSources().inFire(), 3f);
                    e.igniteForSeconds(2);
                }
            }
        }
    }

    private void summon(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos p = blockPosition().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            AshbornEntity e = (i % 2 == 0 ? ModEntities.ASH_RAIDER : ModEntities.ASH_ARCHER).spawn(level, p, EntitySpawnReason.MOB_SUMMONED);
            if (e == null) continue;
            if (raidSettlement() != null && raidTarget() != null) {
                e.joinRaid(raidSettlement(), raidTarget());
                io.github.operagtx.hearthholm.threat.Threats.adopt(level, e);
            }
            if (getTarget() != null) e.setTarget(getTarget());
            level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (name != null) bossEvent.setName(name);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean playerKill) {
        super.dropCustomDeathLoot(level, source, playerKill);
        spawnAtLocation(level, new ItemStack(ModBlocks.CHIEFTAIN_BANNER));
        spawnAtLocation(level, new ItemStack(ModItems.HEARTH_MARK, 20 + random.nextInt(13)));
        spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(4)));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Phase", phase);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        phase = in.getIntOr("Phase", 1);
        if (phase >= 2) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.addOrUpdateTransientModifier(RAGE);
        }
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
