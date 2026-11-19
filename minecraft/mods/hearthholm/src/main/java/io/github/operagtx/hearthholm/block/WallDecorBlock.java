package io.github.operagtx.hearthholm.block;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Shelf / hanging herbs: the front faces away from the wall that was clicked (player-facing when placed on the floor or ceiling). */
public class WallDecorBlock extends FacingShapeBlock {
    public WallDecorBlock(Properties properties, double[]... boxes) {
        super(properties, boxes);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) return defaultBlockState().setValue(FACING, face);
        return super.getStateForPlacement(context);
    }
}
