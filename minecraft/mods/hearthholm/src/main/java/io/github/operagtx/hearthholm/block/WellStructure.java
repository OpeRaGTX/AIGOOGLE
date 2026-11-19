package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Geometry of the 3x3x3 well: stone ring and water at the bottom, two posts, a rope with a bucket and a winch under a roof. */
public final class WellStructure {
    public record Entry(BlockPos pos, WellPartBlock.Part part, Direction facing) {}

    private static boolean collapsing, noDrop;

    private WellStructure() {}

    static Direction outward(int dx, int dz) {
        return dz < 0 ? Direction.NORTH : dz > 0 ? Direction.SOUTH : dx > 0 ? Direction.EAST : Direction.WEST;
    }

    static Direction corner(int dx, int dz) {
        return dx < 0 && dz < 0 ? Direction.NORTH : dx > 0 && dz < 0 ? Direction.EAST : dx > 0 ? Direction.SOUTH : Direction.WEST;
    }

    /** Offset of the corner part (relative to the well centre) for its facing. */
    static int[] cornerOffset(Direction f) {
        return switch (f) { case NORTH -> new int[]{-1, -1}; case EAST -> new int[]{1, -1}; case SOUTH -> new int[]{1, 1}; default -> new int[]{-1, 1}; };
    }

    /** Every block of the well except the centre (the master) for a well facing {@code facing}. */
    public static List<Entry> parts(BlockPos master, Direction facing) {
        List<Entry> out = new ArrayList<>();
        boolean postsOnX = facing.getAxis() == Direction.Axis.Z;   // posts left and right of a well facing north/south
        for (int dy = 0; dy <= 2; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    boolean centre = dx == 0 && dz == 0, isCorner = dx != 0 && dz != 0;
                    boolean postEdge = !centre && !isCorner && (postsOnX ? dx != 0 : dz != 0);
                    BlockPos p = master.offset(dx, dy, dz);
                    if (dy == 0) {
                        if (centre) continue;
                        out.add(new Entry(p, isCorner ? WellPartBlock.Part.RING_CORNER : WellPartBlock.Part.RING_EDGE, isCorner ? corner(dx, dz) : outward(dx, dz)));
                    } else if (dy == 1) {
                        if (centre) out.add(new Entry(p, WellPartBlock.Part.ROPE, facing));
                        else if (postEdge) out.add(new Entry(p, WellPartBlock.Part.POST, outward(dx, dz)));
                    } else {
                        if (centre) out.add(new Entry(p, WellPartBlock.Part.ROOF_CENTER, facing));
                        else if (isCorner) out.add(new Entry(p, WellPartBlock.Part.ROOF_CORNER, corner(dx, dz)));
                        else out.add(new Entry(p, postEdge ? WellPartBlock.Part.ROOF_POST : WellPartBlock.Part.ROOF_EDGE, outward(dx, dz)));
                    }
                }
        return out;
    }

    /** Whether the 3x3x3 volume above/around {@code master} is free for a well. */
    public static boolean fits(LevelReader level, BlockPos master, Direction facing) {
        if (master.getY() + 2 >= level.getMaxY() || master.getY() < level.getMinY()) return false;
        for (Entry e : parts(master, facing)) {
            BlockState s = level.getBlockState(e.pos());
            if (!s.isAir() && !s.canBeReplaced()) return false;
        }
        return true;
    }

    /** Places every part around an already placed master and marks the master complete. */
    public static void build(Level level, BlockPos master, Direction facing) {
        for (Entry e : parts(master, facing)) {
            boolean ring = e.part() == WellPartBlock.Part.RING_EDGE || e.part() == WellPartBlock.Part.RING_CORNER;
            level.setBlock(e.pos(), ModBlocks.WELL_PART.defaultBlockState().setValue(WellPartBlock.PART, e.part()).setValue(HorizontalBlock.FACING, e.facing())
                    .setValue(WellPartBlock.WATERLOGGED, ring), 3);
        }
        BlockState m = level.getBlockState(master);
        if (m.is(ModBlocks.WELL)) level.setBlock(master, m.setValue(WellBlock.COMPLETE, true), 3);
    }

    /** The master position for any part block. */
    public static BlockPos masterOf(BlockState state, BlockPos pos) {
        Direction f = state.getValue(HorizontalBlock.FACING);
        return switch (state.getValue(WellPartBlock.PART)) {
            case RING_EDGE -> pos.relative(f.getOpposite());
            case RING_CORNER -> pos.offset(-cornerOffset(f)[0], 0, -cornerOffset(f)[1]);
            case POST -> pos.relative(f.getOpposite()).below();
            case ROPE -> pos.below();
            case ROOF_EDGE, ROOF_POST -> pos.relative(f.getOpposite()).below(2);
            case ROOF_CORNER -> pos.offset(-cornerOffset(f)[0], -2, -cornerOffset(f)[1]);
            case ROOF_CENTER -> pos.below(2);
        };
    }

    /** Called when any block of the well was removed: removes the rest and drops one well item. */
    public static void collapse(ServerLevel level, BlockPos master) {
        if (collapsing) return;
        collapsing = true;
        try {
            for (int dy = 0; dy <= 2; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = master.offset(dx, dy, dz);
                        BlockState s = level.getBlockState(p);
                        boolean mine = s.is(ModBlocks.WELL_PART) ? masterOf(s, p).equals(master) : (dy == 0 && dx == 0 && dz == 0 && s.is(ModBlocks.WELL));
                        if (mine) level.destroyBlock(p, false);
                    }
            // destroying a waterlogged block leaves its water behind as a source block: remove that too (the whole 3x3 floor of the well held water)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = master.offset(dx, 0, dz);
                    if (level.getBlockState(p).is(Blocks.WATER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            if (!noDrop) Block.popResource(level, master, new ItemStack(ModBlocks.WELL));
        } finally {
            collapsing = false;
            noDrop = false;
        }
    }

    /** Creative players break the well without getting the item back. */
    static void willBreak(Player player) {
        noDrop = player.isCreative();
    }
}
