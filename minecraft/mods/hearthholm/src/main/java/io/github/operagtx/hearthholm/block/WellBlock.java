package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The well (wave H3: the centre block holds real, still, waterlogged water). This block is the centre (master) of a 3x3x3 multiblock and keeps the old block id, so old saves and the
 * "Beautiful street" decor list still work. A well without {@link #COMPLETE} is the old single-block well.
 */
public class WellBlock extends DecorBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty COMPLETE = BooleanProperty.create("complete");
    /** Old saves have no such property: it defaults to true, so an old complete well simply gets its water. */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SINGLE = Block.box(0, 0, 0, 16, 14, 16), BASIN = Block.box(0, 0, 0, 16, 14, 16), FLOOR = Block.box(0, 0, 0, 16, 2, 16);

    public WellBlock(Properties properties) {
        super(properties, SINGLE);
        registerDefaultState(defaultBlockState().setValue(COMPLETE, false).setValue(WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COMPLETE, WATERLOGGED);
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
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(COMPLETE) ? FLOOR : SINGLE;
    }

    /** Still water only once the multiblock is complete (the single-block well has its painted water). */
    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(COMPLETE) && state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public boolean canPlaceLiquid(LivingEntity placer, BlockGetter level, BlockPos pos, BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    /** The well is a spring: a bucket gets water and the well stays full. The water never spreads (no fluid tick is ever scheduled). */
    @Override
    public ItemStack pickupBlock(LivingEntity picker, LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getValue(COMPLETE) && state.getValue(WATERLOGGED) ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY;
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
