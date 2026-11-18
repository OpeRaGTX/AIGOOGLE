package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A chair or stool: right-click to sit (sneak to get up, or break the block). */
public class SeatBlock extends FacingShapeBlock {
    private final double surface;

    /** @param surfacePx height of the seat surface in 1/16 blocks */
    public SeatBlock(Properties properties, double surfacePx, double[]... boxes) {
        super(properties, boxes);
        this.surface = surfacePx / 16.0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return SeatEntity.sit(level, pos, player, surface) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
