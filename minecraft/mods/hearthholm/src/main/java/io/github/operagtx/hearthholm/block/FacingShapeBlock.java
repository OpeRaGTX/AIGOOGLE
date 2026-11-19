package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** A facing block whose hitbox is a union of boxes written for "facing north" and rotated to the real facing. */
public class FacingShapeBlock extends HorizontalBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /** Each box is {x0, y0, z0, x1, y1, z1} in 1/16 block units, authored facing north. */
    public FacingShapeBlock(Properties properties, double[]... boxes) {
        super(properties, rotate(Direction.NORTH, boxes));
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) shapes.put(d, rotate(d, boxes));
    }

    public static VoxelShape rotate(Direction facing, double[]... boxes) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int i = 0; i < turns; i++) {
                double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
                x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
            }
            out = Shapes.or(out, net.minecraft.world.level.block.Block.box(x0, b[1], z0, x1, b[4], z1));
        }
        return out;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }
}
