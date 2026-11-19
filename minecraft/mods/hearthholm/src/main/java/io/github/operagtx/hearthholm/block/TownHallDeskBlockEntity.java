package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.registry.ModBlockEntities;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TownHallDeskBlockEntity extends BlockEntity {
    private @Nullable UUID settlementId;
    /** Set while the desk is moved by a builder so removing the old block does not orphan the settlement. */
    public boolean relocating;

    public TownHallDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL_DESK, pos, state);
    }

    public @Nullable UUID settlementId() {
        return settlementId;
    }

    public void setSettlementId(@Nullable UUID id) {
        this.settlementId = id;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("Settlement", UUIDUtil.CODEC, settlementId);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        settlementId = in.read("Settlement", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!relocating && level instanceof ServerLevel server && settlementId != null) {
            SettlementService.onDeskRemoved(server, pos, settlementId);
        }
    }
}
