package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The well. This block is the centre (master) of a 3x3x3 multiblock and keeps the old block id, so old saves and the
 * "Beautiful street" decor list still work. A well without {@link #COMPLETE} is the old single-block well.
 */
public class WellBlock extends DecorBlock {
    public static final BooleanProperty COMPLETE = BooleanProperty.create("complete");
    private static final VoxelShape SINGLE = Block.box(0, 0, 0, 16, 14, 16), BASIN = Block.box(0, 0, 0, 16, 6, 16);

    public WellBlock(Properties properties) {
        super(properties, SINGLE);
        registerDefaultState(defaultBlockState().setValue(COMPLETE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COMPLETE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState st = super.getStateForPlacement(context);
        if (st == null) return null;
        if (!WellStructure.fits(context.getLevel(), context.getClickedPos(), st.getValue(FACING))) {
            if (!context.getLevel().isClientSide() && context.getPlayer() != null)
                context.getPlayer().sendOverlayMessage(Component.translatable("message.hearthholm.well_no_room"));
            return null;
        }
        return st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) WellStructure.build(level, pos, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(COMPLETE) ? BASIN : SINGLE;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        WellStructure.willBreak(player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        WellStructure.collapse(level, pos);
    }
}
