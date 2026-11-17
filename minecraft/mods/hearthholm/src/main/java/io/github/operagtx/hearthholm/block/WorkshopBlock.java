package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Workstation that makes the room it stands in a workshop of its kind (like the warehouse ledger). */
public class WorkshopBlock extends HorizontalBlock {
    private final Workshop kind;

    public WorkshopBlock(Properties properties, Workshop kind, VoxelShape shape) {
        super(properties, shape);
        this.kind = kind;
    }

    public Workshop kind() { return kind; }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) SettlementService.onWorkshopPlaced(server, pos, placer instanceof ServerPlayer p ? p : null);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) SettlementService.describeWorkshop(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
