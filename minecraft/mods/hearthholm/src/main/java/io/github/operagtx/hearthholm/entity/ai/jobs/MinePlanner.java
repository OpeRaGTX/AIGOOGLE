package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Turns mine-marker options into an ordered list of steps. Three layouts:
 * <ul>
 *   <li>spiral — a 3x3 spiral staircase around a stone pillar (walkable both ways), then a tunnel;</li>
 *   <li>ladder — a 1x1 shaft with ladders on the wall, then a tunnel;</li>
 *   <li>tunnel — a straight 1x2 tunnel from the marker into a hillside.</li>
 * </ul>
 * Tunnels get side branches every 4 blocks (strip mining) and wall torches when enabled.
 * The plan is deterministic, so progress is a single index saved in the marker.
 */
public final class MinePlanner {
    public enum Kind { DIG, FLOOR, TORCH, LADDER }

    public record Step(Kind kind, BlockPos pos, Direction dir) {}

    public record Plan(List<Step> steps, Set<BlockPos> dug, int mode, BlockPos shaft, int topY, int bottomY) {}

    public static final int SPIRAL = 0, LADDER = 1, TUNNEL = 2;
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    private static final int BRANCH_EVERY = 4, BRANCH_LENGTH = 6;

    private static final Map<String, Plan> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Plan> e) { return size() > 32; }
    };

    private MinePlanner() {}

    public static Plan plan(WorkSiteBlockEntity be) {
        int depth = be.option("depth");
        if (be.getLevel() != null) {
            int floor = be.getLevel().getMinY() + 6;
            depth = Math.max(2, Math.min(depth, be.getBlockPos().getY() - 1 - floor));
        }
        return plan(be.getBlockPos(), be.facing(), be.option("mode"), depth, be.option("length"),
                be.flag("branches"), be.flag("torches"));
    }

    public static synchronized Plan plan(BlockPos marker, Direction facing, int mode, int depth, int length,
                                         boolean branches, boolean torches) {
        String key = marker.asLong() + "/" + facing + "/" + mode + "/" + depth + "/" + length + "/" + branches + "/" + torches;
        return CACHE.computeIfAbsent(key, k -> build(marker, facing, mode, depth, length, branches, torches));
    }

    private static Plan build(BlockPos marker, Direction f, int mode, int depth, int length, boolean branches, boolean torches) {
        List<Step> steps = new ArrayList<>();
        Direction right = f.getClockWise();
        int y0 = marker.getY();
        BlockPos tunnelStart;
        BlockPos shaft;
        int bottom;
        if (mode == SPIRAL) {
            BlockPos c = marker.relative(f, 3);
            shaft = c;
            int i = 0;
            BlockPos cell;
            while (true) {
                int[] rf = RING[i % 8];
                int feet = y0 - 1 - i;
                cell = c.relative(right, rf[0]).relative(f, rf[1]).atY(feet);
                dig(steps, cell.above(2));
                dig(steps, cell.above());
                dig(steps, cell);
                steps.add(new Step(Kind.FLOOR, cell.below(), Direction.UP));
                if (torches && i % 16 == 8 && (rf[0] == 0 || rf[1] == 0)) {
                    Direction out = rf[0] == 0 ? (rf[1] > 0 ? f : f.getOpposite()) : (rf[0] > 0 ? right : right.getOpposite());
                    steps.add(new Step(Kind.TORCH, cell.above(), out));
                }
                if (feet <= y0 - depth && i % 8 == 4) break;
                if (i > 400) break;
                i++;
            }
            tunnelStart = cell;
            bottom = cell.getY();
        } else if (mode == LADDER) {
            shaft = marker.relative(f, 2);
            BlockPos cell = shaft;
            for (int d = 0; d < depth; d++) {
                cell = shaft.atY(y0 - 1 - d);
                dig(steps, cell);
                steps.add(new Step(Kind.LADDER, cell, f));
            }
            tunnelStart = cell;
            bottom = cell.getY();
        } else {
            shaft = marker;
            tunnelStart = marker;
            bottom = y0;
        }

        for (int k = 1; k <= length; k++) {
            BlockPos p = tunnelStart.relative(f, k);
            dig(steps, p.above());
            dig(steps, p);
            steps.add(new Step(Kind.FLOOR, p.below(), Direction.UP));
            if (torches && k % 8 == 2) steps.add(new Step(Kind.TORCH, p.above(), right.getOpposite()));
            if (branches && k % BRANCH_EVERY == 0) {
                for (Direction side : new Direction[]{right.getOpposite(), right}) {
                    for (int j = 1; j <= BRANCH_LENGTH; j++) {
                        BlockPos q = p.relative(side, j);
                        dig(steps, q.above());
                        dig(steps, q);
                        steps.add(new Step(Kind.FLOOR, q.below(), Direction.UP));
                    }
                }
            }
        }
        Set<BlockPos> dug = new HashSet<>();
        for (Step s : steps) if (s.kind == Kind.DIG) dug.add(s.pos);
        return new Plan(List.copyOf(steps), dug, mode, shaft, y0, bottom);
    }

    private static void dig(List<Step> steps, BlockPos pos) {
        steps.add(new Step(Kind.DIG, pos.immutable(), Direction.UP));
    }

    public static AABB previewBox(WorkSiteBlockEntity be) {
        Plan plan = plan(be);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : plan.dug()) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        if (minX == Integer.MAX_VALUE) return new AABB(be.getBlockPos());
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
