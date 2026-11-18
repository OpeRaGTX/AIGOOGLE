package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One of the outer blocks of the well multiblock; breaking any of them removes the whole well. */
public class WellPartBlock extends HorizontalBlock {
    public enum Part implements StringRepresentable {
        RING_EDGE, RING_CORNER, POST, ROPE, ROOF_CORNER, ROOF_EDGE, ROOF_POST, ROOF_CENTER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape RING = Block.box(0, 0, 0, 16, 10, 16), POST = Block.box(5, 0, 5, 11, 16, 11), ROOF = Block.box(0, 6, 0, 16, 16, 16),
            ROPE = Block.box(5, 0, 5, 11, 16, 11);

    public WellPartBlock(Properties properties) {
        super(properties, RING);
        registerDefaultState(defaultBlockState().setValue(PART, Part.RING_EDGE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
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
