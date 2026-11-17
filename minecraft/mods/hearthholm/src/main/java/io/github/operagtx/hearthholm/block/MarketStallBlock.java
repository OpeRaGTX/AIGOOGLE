package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.town.Caravan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Market stall: where the trader caravan sets up. Right-click opens the trade screen while the caravan is in town. */
public class MarketStallBlock extends DecorBlock {
    public MarketStallBlock(Properties properties) {
        super(properties, Block.box(0, 0, 2, 16, 14, 14));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel sl && !oldState.is(this)) Caravan.registerMarket(sl, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) Caravan.openFromBlock(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
