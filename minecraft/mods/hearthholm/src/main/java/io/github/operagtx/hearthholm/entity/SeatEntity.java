package io.github.operagtx.hearthholm.entity;

import io.github.operagtx.hearthholm.block.HorizontalBlock;
import io.github.operagtx.hearthholm.block.SeatBlock;
import io.github.operagtx.hearthholm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Invisible, unsaved seat: whoever rides it sits on the chair or stool. It vanishes when empty or when its block is gone. */
public class SeatEntity extends Entity {
    /** Added to the seat surface height so that a rider's hips end up on the cushion. */
    public static final double RIDE_OFFSET = 0.0;
    private BlockPos anchor = BlockPos.ZERO;

    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public BlockPos anchor() {
        return anchor;
    }

    /** Seats {@code rider} on the block at {@code pos}; false if somebody sits there already. */
    public static boolean sit(Level level, BlockPos pos, LivingEntity rider, double surface) {
        if (level.isClientSide()) return true;
        List<SeatEntity> there = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(0.4));
        for (SeatEntity s : there) if (s.anchor.equals(pos) && !s.getPassengers().isEmpty()) return false;
        for (SeatEntity s : there) s.discard();
        SeatEntity seat = new SeatEntity(ModEntities.SEAT, level);
        seat.anchor = pos.immutable();
        seat.setPos(pos.getX() + 0.5, pos.getY() + surface + RIDE_OFFSET, pos.getZ() + 0.5);
        BlockState st = level.getBlockState(pos);
        if (st.hasProperty(HorizontalBlock.FACING)) rider.setYRot(st.getValue(HorizontalBlock.FACING).toYRot());
        level.addFreshEntity(seat);
        if (!rider.startRiding(seat)) {
            seat.discard();
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && (getPassengers().isEmpty() || !(level().getBlockState(anchor).getBlock() instanceof SeatBlock))) discard();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockState st = level().getBlockState(anchor);
        if (st.getBlock() instanceof SeatBlock && st.hasProperty(HorizontalBlock.FACING)) {
            Direction f = st.getValue(HorizontalBlock.FACING);
            BlockPos fp = anchor.relative(f);
            if (level().getBlockState(fp).getCollisionShape(level(), fp).isEmpty()) return Vec3.atBottomCenterOf(fp);
        }
        return Vec3.atBottomCenterOf(anchor.above());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
