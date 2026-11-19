package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small tabletop things (bottles, mugs, plates, candles): 1..3 per block like candles, and they sit on tables.
 * On a 14/16 high surface the model is lowered by 2/16 ({@link #ON_TABLE}) so it stands right on the tabletop.
 */
public class SmallStackBlock extends HorizontalBlock {
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 3);
    public static final BooleanProperty ON_TABLE = BooleanProperty.create("on_table");
    private final int max;
    private final VoxelShape[] shapes = new VoxelShape[2];

    /** @param heightPx height of the tallest item in 1/16 blocks */
    public SmallStackBlock(Properties properties, int max, double heightPx) {
        super(properties, Block.box(2, 0, 2, 14, heightPx, 14));
        this.max = max;
        shapes[0] = Block.box(2, 0, 2, 14, heightPx, 14);
        shapes[1] = Block.box(2, 0, 2, 14, Math.max(2, heightPx - 2), 14);
        registerDefaultState(defaultBlockState().setValue(COUNT, 1).setValue(ON_TABLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COUNT, ON_TABLE);
    }

    /** A table-like surface: its collision top is 14/16 high. */
    public static boolean isTableTop(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return false;
        double top = s.getCollisionShape(level, pos).max(Direction.Axis.Y);
        return Math.abs(top - 14.0 / 16.0) < 0.01;
    }

    private static boolean supported(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || isTableTop(level, below);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) return existing.setValue(COUNT, Math.min(max, existing.getValue(COUNT) + 1));
        BlockState st = super.getStateForPlacement(context);
        if (st == null) return null;
        BlockPos pos = context.getClickedPos();
        if (!supported(context.getLevel(), pos)) return null;
        return st.setValue(ON_TABLE, isTableTop(context.getLevel(), pos.below()));
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (!context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && state.getValue(COUNT) < max) return true;
        return super.canBeReplaced(state, context);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return supported(level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos nPos,
                                     BlockState nState, RandomSource random) {
        if (dir == Direction.DOWN) {
            if (!supported(level, pos)) return Blocks.AIR.defaultBlockState();
            return state.setValue(ON_TABLE, isTableTop(level, pos.below()));
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(ON_TABLE) ? 1 : 0];
    }
}
