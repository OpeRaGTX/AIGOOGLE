package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A long table: tables placed end to end along the same axis join up and lose the legs between them. */
public class LongTableBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Linked with another long table at the negative / positive end of the axis. */
    public static final BooleanProperty A = BooleanProperty.create("a"), B = BooleanProperty.create("b");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 14, 16), Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15));

    public LongTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(A, false).setValue(B, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, A, B);
    }

    private static boolean joins(LevelReader level, BlockPos pos, Direction.Axis axis, boolean positive) {
        BlockState n = level.getBlockState(pos.relative(Direction.get(positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis)));
        return n.getBlock() instanceof LongTableBlock && n.getValue(AXIS) == axis;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis).setValue(A, joins(context.getLevel(), context.getClickedPos(), axis, false))
                .setValue(B, joins(context.getLevel(), context.getClickedPos(), axis, true));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos nPos,
                                     BlockState nState, RandomSource random) {
        Direction.Axis axis = state.getValue(AXIS);
        if (dir.getAxis() != axis) return state;
        boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        boolean link = nState.getBlock() instanceof LongTableBlock && nState.getValue(AXIS) == axis;
        return state.setValue(positive ? B : A, link);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
