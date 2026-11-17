package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.town.Decor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Street furniture (lamp post, bench, well...): registers itself with the settlement it stands in; it makes streets pretty. */
public class DecorBlock extends HorizontalBlock {
    public DecorBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel sl && !oldState.is(this)) Decor.register(sl, pos);
    }
}
