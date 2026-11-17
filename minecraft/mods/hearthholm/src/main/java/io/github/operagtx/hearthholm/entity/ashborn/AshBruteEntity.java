package io.github.operagtx.hearthholm.entity.ashborn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Big, slow and shielded (arrows mostly glance off); hits hard, knocks back and breaks palisades and doors. */
public class AshBruteEntity extends AshbornEntity {
    public AshBruteEntity(EntityType<? extends AshBruteEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AshbornEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 70)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ATTACK_KNOCKBACK, 1.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.SCALE, 1.2);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(1, new BreakBarrierGoal(this));
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, true));
    }

    @Override
    protected void equip(RandomSource random) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.STONE_AXE : Items.IRON_SHOVEL));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) amount *= 0.35f;
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void extraLoot(ServerLevel level, RandomSource r) {
        if (r.nextFloat() < 0.5f) spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(2)));
        if (r.nextFloat() < 0.5f) spawnAtLocation(level, new ItemStack(Items.LEATHER, 1 + r.nextInt(2)));
    }
}
