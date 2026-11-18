package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.minecraft.core.BlockPos;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Turns mine-marker options into an ordered list of steps. Three layouts:
 * <ul>
 *   <li>spiral — a two-lane spiral staircase around a stone pillar (walkable both ways), then a tunnel;</li>
 *   <li>ladder — a 2x1 shaft (ladders in one column), then a tunnel;</li>
 *   <li>tunnel — a straight 2-wide, 2-high tunnel from the marker into a hillside.</li>
 * </ul>
 * Wave I1: everything is two blocks wide. Tunnels get side branches every 4 blocks (strip mining) and wall torches when enabled.
 * The plan is deterministic, so progress is a single index saved in the marker.
 */
public final class MinePlanner {
    public enum Kind { DIG, FLOOR, TORCH, LADDER }

    public record Step(Kind kind, BlockPos pos, Direction dir) {}

    /**
     * {@code autoFrom} >= 0: wave J3, the steps from that index on are the automatic layout of {@code autoMode} (a drawn plan that
     * goes back to auto when done); {@code mode} is then {@link #DRAWN}.
     */
    public record Plan(List<Step> steps, Set<BlockPos> dug, int mode, BlockPos shaft, int topY, int bottomY, int autoFrom, int autoMode) {
        public Plan(List<Step> steps, Set<BlockPos> dug, int mode, BlockPos shaft, int topY, int bottomY) {
            this(steps, dug, mode, shaft, topY, bottomY, -1, mode);
        }

        /** Whether the step at {@code index} belongs to a ladder shaft (climbing needed). */
        public boolean ladderAt(int index) {
            return mode == LADDER || (autoFrom >= 0 && autoMode == LADDER && index >= autoFrom);
        }
    }

    public static final int SPIRAL = 0, LADDER = 1, TUNNEL = 2, DRAWN = 3;
    /** Rough ticks the miner needs per block (walking included), for the map's time estimate only. */
    public static final int EST_TICKS_PER_BLOCK = 16;
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    private static final int BRANCH_EVERY = 4, BRANCH_LENGTH = 6;

    private static final Map<String, Plan> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Plan> e) { return size() > 32; }
    };

    private MinePlanner() {}

    /** Whether the site digs its drawn zone (plan option set and cells painted). */
    public static boolean drawn(WorkSiteBlockEntity be) {
        return be.flag("plan") && be.hasZone();
    }

    public static Plan plan(WorkSiteBlockEntity be) {
        int depth = be.option("depth");
        if (be.getLevel() != null) {
            int floor = be.getLevel().getMinY() + 6;
            depth = Math.max(2, Math.min(depth, be.getBlockPos().getY() - 1 - floor));
        }
        if (drawn(be) && be.getLevel() instanceof ServerLevel level) return drawnPlan(be, level, depth, be.flag("after"));
        return plan(be.getBlockPos(), be.facing(), be.option("mode"), depth, be.option("length"),
                be.flag("branches"), be.flag("torches"));
    }

    /** The plan of the painted cells alone (what the map's summary counts), whatever the plan option says. */
    public static Plan drawnOnly(WorkSiteBlockEntity be, ServerLevel level) {
        return drawnPlan(be, level, be.option("depth"), false);
    }

    private static synchronized Plan drawnPlan(WorkSiteBlockEntity be, ServerLevel level, int depth, boolean after) {
        WorkZone zone = be.zone();
        String key = "D/" + be.getBlockPos().asLong() + "/" + be.facing() + "/" + zone.contentHash() + "/" + be.flag("torches") + "/" + after
                + (after ? "/" + be.option("mode") + "/" + depth + "/" + be.option("length") + "/" + be.flag("branches") : "");
        return CACHE.computeIfAbsent(key, k -> {
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            DrawnPlanner.Input in = new DrawnPlanner.Input(zone.copy(), be.getBlockPos(), be.facing(), be.flag("torches"),
                    (x, z) -> level.hasChunkAt(mp.set(x, level.getMinY(), z)) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1 : Integer.MAX_VALUE,
                    level.getMinY(), level.getMaxY());
            Set<BlockPos> dug = new HashSet<>();
            List<Step> steps = new ArrayList<>(DrawnPlanner.build(in, dug));
            int top = be.getBlockPos().getY(), bottom = top;
            for (BlockPos p : dug) bottom = Math.min(bottom, p.getY());
            if (!after) return new Plan(List.copyOf(steps), dug, DRAWN, be.getBlockPos(), top, bottom);
            Plan auto = build(be.getBlockPos(), be.facing(), be.option("mode"), depth, be.option("length"), be.flag("branches"), be.flag("torches"));
            int from = steps.size();
            steps.addAll(auto.steps());
            dug.addAll(auto.dug());
            return new Plan(List.copyOf(steps), dug, DRAWN, auto.shaft(), auto.topY(), auto.bottomY(), from, be.option("mode"));
        });
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
            int lastTorch = -100;
            while (true) {
                int[] rf = RING[i % 8];
                int feet = y0 - 1 - i;
                cell = c.relative(right, rf[0]).relative(f, rf[1]).atY(feet);
                dig(steps, cell.above(2));
                dig(steps, cell.above());
                dig(steps, cell);
                steps.add(new Step(Kind.FLOOR, cell.below(), Direction.UP));
                // second lane: the cells just outside the ring at the same level
                int sx = Integer.signum(rf[0]), sz = Integer.signum(rf[1]);
                List<int[]> outer = new ArrayList<>();
                if (sx != 0) outer.add(new int[]{rf[0] + sx, rf[1]});
                if (sz != 0) outer.add(new int[]{rf[0], rf[1] + sz});
                if (sx != 0 && sz != 0) outer.add(new int[]{rf[0] + sx, rf[1] + sz});
                for (int[] o : outer) {
                    BlockPos oc = c.relative(right, o[0]).relative(f, o[1]).atY(feet);
                    dig(steps, oc.above(2));
                    dig(steps, oc.above());
                    dig(steps, oc);
                    steps.add(new Step(Kind.FLOOR, oc.below(), Direction.UP));
                }
                boolean edge = rf[0] == 0 || rf[1] == 0;
                if (torches && edge && i - lastTorch >= Balance.MINE_TORCH_EVERY_SPIRAL && i >= 2) {
                    Direction out = rf[0] == 0 ? (rf[1] > 0 ? f : f.getOpposite()) : (rf[0] > 0 ? right : right.getOpposite());
                    steps.add(new Step(Kind.TORCH, cell.above(), out));
                    lastTorch = i;
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
                dig(steps, cell.relative(right));
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
            for (BlockPos q : new BlockPos[]{p, p.relative(right)}) {
                dig(steps, q.above());
                dig(steps, q);
                steps.add(new Step(Kind.FLOOR, q.below(), Direction.UP));
            }
            // torch on the wall of the right-hand lane
            if (torches && k % Balance.MINE_TORCH_EVERY_TUNNEL == 2) steps.add(new Step(Kind.TORCH, p.relative(right).above(), right.getOpposite()));
            if (branches && k % BRANCH_EVERY == 0) {
                for (Direction side : new Direction[]{right.getOpposite(), right}) {
                    for (int j = (side == right ? 2 : 1); j <= BRANCH_LENGTH + (side == right ? 1 : 0); j++) {
                        BlockPos q0 = p.relative(side, j);
                        for (BlockPos q : new BlockPos[]{q0, q0.relative(f)}) {
                            dig(steps, q.above());
                            dig(steps, q);
                            steps.add(new Step(Kind.FLOOR, q.below(), Direction.UP));
                        }
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
        if (drawn(be)) {   // the outline of the drawn zone (the plan itself needs the server world)
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int y : be.zone().layerKeys()) { lo = Math.min(lo, y); hi = Math.max(hi, y + WorkZone.MAX_HEIGHT); }
            AABB zb = be.zone().bounds(lo, hi);
            if (zb != null) return zb;
        }
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
