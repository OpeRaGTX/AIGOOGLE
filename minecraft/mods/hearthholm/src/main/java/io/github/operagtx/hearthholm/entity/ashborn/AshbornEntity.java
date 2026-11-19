package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.threat.ThreatConfig;
import io.github.operagtx.hearthholm.threat.Threats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Ashborn ("Пепельники"): soot-grey marauders who raid wealthy settlements. The base class is the plain
 * raider (torch or axe); arsonists, archers, brutes and the chieftain add their own goals.
 * Raid members carry the settlement they attack and march towards its town hall when nobody is in reach.
 */
public class AshbornEntity extends Monster {
    private @Nullable BlockPos raidTarget;
    private @Nullable UUID raidSettlement;
    /** Targets given up on (unreachable) with the game time until which they are ignored. */
    private final java.util.Map<UUID, Long> ignored = new java.util.HashMap<>();
    private @Nullable BlockPos breakGoal;
    private int unreachable;

    public AshbornEntity(EntityType<? extends AshbornEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BreakBarrierGoal(this, breaksPalisades()));
        addCombatGoals();
        goalSelector.addGoal(4, new MarchGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, AshbornEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, SettlerEntity.class, 10, true, false, (e, lvl) -> !ignores(e)));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    /** Brutes and the chieftain also break palisade walls and gates (everyone breaks doors). */
    protected boolean breaksPalisades() { return false; }

    /** Melee by default; archers replace it with a bow goal. */
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
    }

    /** Weapons for this kind (called on spawn). */
    protected void equip(RandomSource random) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.TORCH : Items.IRON_AXE));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        equip(level.getRandom());
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0.04f);
        return out;
    }

    // ---- raid membership ---------------------------------------------------------------------

    public void joinRaid(UUID settlement, BlockPos hall) {
        raidSettlement = settlement;
        raidTarget = hall;
        setPersistenceRequired();
    }

    public @Nullable BlockPos raidTarget() { return raidTarget; }
    /** A door the raider wants to break down because every settler it could hunt is hiding behind it. */
    public @Nullable BlockPos breakGoal() { return breakGoal; }
    public void setBreakGoal(@Nullable BlockPos pos) { breakGoal = pos; }

    public boolean ignores(Entity e) {
        Long until = ignored.get(e.getUUID());
        return until != null && until > level().getGameTime();
    }

    /**
     * A target that stays out of reach (no path, not even a barrier to break) is dropped for half a minute so the raider
     * hunts someone else, goes for a door or roams, instead of standing at the wall.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 20 != 0 || raidTarget == null) return;
        LivingEntity t = getTarget();
        if (t instanceof SettlerEntity && distanceToSqr(t) > 6.25 && getNavigation().isDone()
                && !goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof BreakBarrierGoal)) {
            if (++unreachable >= 4) {
                ignored.put(t.getUUID(), level.getGameTime() + 600);
                setTarget(null);
                unreachable = 0;
            }
        } else {
            unreachable = 0;
        }
        if (ignored.size() > 16) ignored.values().removeIf(v -> v <= level.getGameTime());
    }
    public @Nullable UUID raidSettlement() { return raidSettlement; }

    // ---- combat --------------------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && ThreatConfig.raidFire && getMainHandItem().is(Items.TORCH) && random.nextInt(3) == 0) target.igniteForSeconds(2);
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && !isRemoved()) Threats.onRaiderDied(sl, this);
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean playerKill) {
        super.dropCustomDeathLoot(level, source, playerKill);
        RandomSource r = getRandom();
        if (r.nextFloat() < 0.5f) spawnAtLocation(level, new ItemStack(Items.CHARCOAL, 1 + r.nextInt(2)));
        if (r.nextFloat() < 0.25f) spawnAtLocation(level, new ItemStack(Items.FLINT));
        if (r.nextFloat() < 0.2f) spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 1 + r.nextInt(3)));
        if (r.nextFloat() < 0.15f) spawnAtLocation(level, new ItemStack(ModItems.HEARTH_MARK));
        extraLoot(level, r);
    }

    protected void extraLoot(ServerLevel level, RandomSource r) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return raidTarget == null && super.removeWhenFarAway(distance);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.PILLAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PILLAGER_DEATH; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("RaidTarget", BlockPos.CODEC, raidTarget);
        out.storeNullable("RaidSettlement", UUIDUtil.CODEC, raidSettlement);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        raidTarget = in.read("RaidTarget", BlockPos.CODEC).orElse(null);
        raidSettlement = in.read("RaidSettlement", UUIDUtil.CODEC).orElse(null);
    }
}
