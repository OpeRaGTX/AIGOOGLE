package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One of the outer blocks of the well multiblock; breaking any of them removes the whole well. */
public class WellPartBlock extends HorizontalBlock implements SimpleWaterloggedBlock {
    public enum Part implements StringRepresentable {
        RING_EDGE, RING_CORNER, POST, ROPE, ROOF_CORNER, ROOF_EDGE, ROOF_POST, ROOF_CENTER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Wave H3: the ring blocks hold the real water of the well (default true so ring blocks of old saves get their water). */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape RING = Block.box(0, 0, 0, 16, 16, 16), POST = Block.box(5, 0, 5, 11, 16, 11), ROOF = Block.box(0, 6, 0, 16, 16, 16),
            ROPE = Block.box(5, 0, 5, 11, 16, 11);

    public WellPartBlock(Properties properties) {
        super(properties, RING);
        registerDefaultState(defaultBlockState().setValue(PART, Part.RING_EDGE).setValue(WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, WATERLOGGED);
    }

    private static boolean holdsWater(BlockState state) {
        Part p = state.getValue(PART);
        return (p == Part.RING_EDGE || p == Part.RING_CORNER) && state.getValue(WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return holdsWater(state) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public boolean canPlaceLiquid(LivingEntity placer, BlockGetter level, BlockPos pos, BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    /** A bucket used on the water or the rim takes water; the well stays full. */
    @Override
    public ItemStack pickupBlock(LivingEntity picker, LevelAccessor level, BlockPos pos, BlockState state) {
        return holdsWater(state) ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return null;   // only placed by the well
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case RING_EDGE, RING_CORNER -> RING;
            case POST -> POST;
            case ROPE -> ROPE;
            default -> ROOF;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == Part.ROPE ? Shapes.empty() : getShape(state, level, pos, context);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        WellStructure.willBreak(player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        WellStructure.collapse(level, WellStructure.masterOf(state, pos));
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(io.github.operagtx.hearthholm.registry.ModBlocks.WELL);
    }
}
