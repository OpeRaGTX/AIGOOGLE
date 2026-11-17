package io.github.operagtx.hearthholm.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/**
 * Finds the enclosed room around a block by flood-filling the walkable air next to it.
 * A room is valid when the fill stays inside size limits, never reaches open sky and its
 * boundary contains at least one door. Doors, fences, glass and slabs all count as walls.
 */
public final class RoomScanner {
    public static final int MAX_CELLS = 1600;
    public static final int MAX_SPAN = 20;
    public static final int MAX_HEIGHT = 12;

    private RoomScanner() {}

    public record Room(boolean enclosed, String problem, Set<BlockPos> interior, Set<BlockPos> boundary,
                       List<BlockPos> beds, List<BlockPos> containers, int doors, int comfort) {
        public boolean contains(BlockPos pos) {
            return interior.contains(pos) || boundary.contains(pos);
        }
    }

    public static Room scan(Level level, BlockPos anchor) {
        BlockPos start = null;
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos p = anchor.relative(d);
            if (passable(level, p)) { start = p; break; }
        }
        if (start == null) return fail("blocked");

        Set<BlockPos> interior = new HashSet<>();
        Set<BlockPos> boundary = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        interior.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (interior.size() > MAX_CELLS) return fail("too_big");
            if (Math.abs(p.getX() - anchor.getX()) > MAX_SPAN || Math.abs(p.getZ() - anchor.getZ()) > MAX_SPAN
                    || Math.abs(p.getY() - anchor.getY()) > MAX_HEIGHT) return fail("open");
            if (p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ())) return fail("roofless");
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (interior.contains(n) || boundary.contains(n)) continue;
                if (!level.isLoaded(n)) return fail("open");
                if (passable(level, n)) {
                    interior.add(n);
                    queue.add(n);
                } else {
                    boundary.add(n);
                }
            }
        }

        List<BlockPos> beds = new ArrayList<>();
        List<BlockPos> containers = new ArrayList<>();
        int doors = 0;
        Set<Block> furniture = new HashSet<>();
        boolean light = false;
        for (BlockPos p : boundary) {
            BlockState s = level.getBlockState(p);
            Block b = s.getBlock();
            if (b instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) beds.add(p.immutable());
            if (b instanceof DoorBlock && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) doors++;
            if (level.getBlockEntity(p) instanceof Container && !(b instanceof BedBlock)) containers.add(p.immutable());
            if (s.getLightEmission() > 0) light = true;
            if (isFurniture(b)) furniture.add(b);
        }
        for (BlockPos p : interior) {
            BlockState s = level.getBlockState(p);
            if (s.getLightEmission() > 0) light = true;
            if (s.getBlock() instanceof CarpetBlock || s.getBlock() instanceof FlowerPotBlock) furniture.add(s.getBlock());
        }
        int comfort = (light ? 2 : 0) + Math.min(6, furniture.size());
        if (!beds.isEmpty() && interior.size() / beds.size() >= 12) comfort += 2;
        comfort = Math.min(10, comfort);

        String problem = doors == 0 ? "no_door" : "";
        return new Room(problem.isEmpty(), problem, interior, boundary, beds, containers, doors, comfort);
    }

    private static boolean isFurniture(Block b) {
        return b instanceof CraftingTableBlock || b instanceof AbstractFurnaceBlock || b == Blocks.BOOKSHELF
                || b instanceof LanternBlock || b instanceof BarrelBlock || b instanceof ChestBlock
                || b instanceof LecternBlock || b instanceof CampfireBlock || b instanceof BannerBlock
                || b instanceof CarpetBlock || b instanceof FlowerPotBlock || b instanceof StairBlock;
    }

    /** Air-like for room purposes: no collision, or only a thin floor layer (carpet, plate). Doors are walls. */
    public static boolean passable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapDoorBlock) return false;
        VoxelShape shape = s.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Axis.Y) <= 0.1875;
    }

    private static Room fail(String problem) {
        return new Room(false, problem, Set.of(), Set.of(), List.of(), List.of(), 0, 0);
    }
}
