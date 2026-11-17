package io.github.operagtx.hearthholm.entity.ashborn;

import io.github.operagtx.hearthholm.threat.ThreatConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Fast and frail; runs to wooden buildings and sets them alight (only when {@code raidFire} is on). */
public class AshArsonistEntity extends AshbornEntity {
    private int fires;

    public AshArsonistEntity(EntityType<? extends AshArsonistEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AshbornEntity.createAttributes().add(Attributes.MAX_HEALTH, 18).add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.ATTACK_DAMAGE, 3);
    }

    @Override
    protected void addCombatGoals() {
        super.addCombatGoals();
        goalSelector.addGoal(3, new IgniteGoal(this));
    }

    @Override
    protected void equip(RandomSource random) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TORCH));
        if (ThreatConfig.raidFire) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FLINT_AND_STEEL));
    }

    public int firesLit() { return fires; }
    public void addFire() { fires++; }

    @Override
    protected void extraLoot(ServerLevel level, RandomSource r) {
        if (r.nextFloat() < 0.4f) spawnAtLocation(level, new ItemStack(Items.COAL, 1 + r.nextInt(2)));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Fires", fires);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        fires = in.getIntOr("Fires", 0);
    }
}
