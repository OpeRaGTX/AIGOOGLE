package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Opens the settlement's order board: "keep N of item X in stock" quotas for the crafters. */
public class OrderBoardBlock extends HorizontalBlock {
    public OrderBoardBlock(Properties properties) {
        super(properties, box(2, 0, 2, 14, 16, 14));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) SettlementService.openOrders(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
