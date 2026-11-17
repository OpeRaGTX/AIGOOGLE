package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.town.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Bulletin board: settlers post small requests (bring items, clear a pest) for marks, renown or gifts. */
public class BulletinBoardBlock extends DecorBlock {
    public BulletinBoardBlock(Properties properties) {
        super(properties, Block.box(1, 0, 6, 15, 16, 10));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel sl && !oldState.is(this)) Quests.registerBoard(sl, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) Quests.open(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
