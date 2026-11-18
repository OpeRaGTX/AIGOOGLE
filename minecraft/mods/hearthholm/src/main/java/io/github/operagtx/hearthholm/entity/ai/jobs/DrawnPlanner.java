package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner.Kind;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner.Step;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.function.IntBinaryOperator;

/**
 * Wave J3: turns the cells painted on the miner's map into an ordered step list. A cell is 2 x 2 blocks and {@code height}
 * blocks tall from its layer Y. Cells touching each other (same layer, or neighbouring layers) form a component. The
 * first component is reached by an access staircase from the marker (2 wide, 1:1 slope, lit with torches, then a flat
 * tunnel to the nearest cell); then the component is dug layer by layer starting at the arrival layer, nearest cell first
 * (a flood over the painted cells). The next component is the nearest one to anything already dug, with its own stair
 * from the nearest dug cell. A component with a cell that is open to the sky (its top block is not under the surface)
 * needs no stair: the miner walks to it, which is how hills and building pads are cleared.
 * The geometry depends on the zone, the marker and the terrain surface only, never on what is already dug, so
 * rebuilding the plan later gives the same order.
 */
final class DrawnPlanner {
    /** Hard cap on dig steps (a full 50 x 50 zone of several layers would be millions); the rest is logged and dropped. */
    static final int MAX_DIGS = 120_000;

    record Input(WorkZone zone, BlockPos marker, Direction facing, boolean torches, IntBinaryOperator surface, int minY, int maxY) {}

    private static final class Cell {
        final int y, ix, iz, h;
        Cell(int y, int ix, int iz, int h) { this.y = y; this.ix = ix; this.iz = iz; this.h = h; }
        int top() { return y + h - 1; }
    }

    private record Origin(int x, int z, int feetY) {}

    private final Input in;
    private final WorkZone zone;
    private final int cs;
    private final List<Step> steps = new ArrayList<>();
    private final Set<BlockPos> dug = new LinkedHashSet<>();
    private final Set<BlockPos> floors = new LinkedHashSet<>();
    private final Map<Integer, List<Cell>> col = new HashMap<>();
    private int digCount, cellsDone, torchCounter;
    boolean truncated;

    private DrawnPlanner(Input in) {
        this.in = in;
        this.zone = in.zone();
        this.cs = zone.cell();
    }

    /** Builds the steps of the drawn zone; {@code dugOut} receives every position that will be dug. */
    static List<Step> build(Input in, Set<BlockPos> dugOut) {
        DrawnPlanner p = new DrawnPlanner(in);
        p.run();
        dugOut.addAll(p.dug);
        List<Step> out = new ArrayList<>(p.steps.size());
        for (Step s : p.steps) {
            if (s.kind() == Kind.FLOOR && p.dug.contains(s.pos())) continue;   // a floor that is dug later is no floor
            out.add(s);
        }
        return out;
    }

    private int key(int ix, int iz) { return iz * zone.size() + ix; }

    // ---- components ---------------------------------------------------------------------------

    private static boolean touches(Cell a, Cell b) { return b.y <= a.y + a.h && a.y <= b.y + b.h; }

    private List<Cell> neighbours(Cell c) {
        List<Cell> out = new ArrayList<>();
        int[][] d = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            List<Cell> l = col.get(key(c.ix + o[0], c.iz + o[1]));
            if (l == null || c.ix + o[0] < 0 || c.iz + o[1] < 0 || c.ix + o[0] >= zone.size() || c.iz + o[1] >= zone.size()) continue;
            for (Cell n : l) if (n != c && touches(c, n)) out.add(n);
        }
        return out;
    }

    private boolean open(Cell c) {
        int worst = Integer.MIN_VALUE;
        for (int dx = 0; dx < cs; dx++) for (int dz = 0; dz < cs; dz++) {
            worst = Math.max(worst, in.surface().applyAsInt(zone.blockX(c.ix) + dx, zone.blockZ(c.iz) + dz));
        }
        return worst != Integer.MAX_VALUE && c.top() >= worst;
    }

    private int cx(Cell c) { return zone.blockX(c.ix) + cs / 2; }
    private int cz(Cell c) { return zone.blockZ(c.iz) + cs / 2; }

    private static int dist(Origin o, int x, int z, int y) { return Math.abs(o.x - x) + Math.abs(o.z - z) + Math.abs(o.feetY - y); }

    // ---- main ---------------------------------------------------------------------------------

    private void run() {
        List<Cell> cells = new ArrayList<>();
        for (int layer : zone.layerKeys()) {
            if (layer == WorkZone.SURFACE) continue;
            zone.forEachCell(layer, (ix, iz) -> {
                Cell c = new Cell(layer, ix, iz, zone.height(layer, ix, iz));
                cells.add(c);
                col.computeIfAbsent(key(ix, iz), k -> new ArrayList<>()).add(c);
            });
        }
        if (cells.isEmpty()) return;
        Map<Cell, Integer> comp = new HashMap<>();
        List<List<Cell>> comps = new ArrayList<>();
        for (Cell c : cells) {
            if (comp.containsKey(c)) continue;
            List<Cell> members = new ArrayList<>();
            ArrayDeque<Cell> q = new ArrayDeque<>();
            q.add(c);
            comp.put(c, comps.size());
            while (!q.isEmpty()) {
                Cell a = q.poll();
                members.add(a);
                for (Cell n : neighbours(a)) if (comp.putIfAbsent(n, comps.size()) == null) q.add(n);
            }
            comps.add(members);
        }
        List<Origin> origins = new ArrayList<>();
        BlockPos m = in.marker();
        origins.add(new Origin(m.getX(), m.getZ(), m.getY()));
        List<List<Cell>> remaining = new ArrayList<>(comps);
        while (!remaining.isEmpty() && digCount < MAX_DIGS) {
            // the component with a cell nearest to any origin (the marker, then everything dug so far)
            List<Cell> bestComp = null;
            Origin bestOrigin = null;
            int bestD = Integer.MAX_VALUE;
            for (List<Cell> cc : remaining) {
                for (Cell c : cc) {
                    for (Origin o : origins) {
                        int d = dist(o, cx(c), cz(c), c.y);
                        if (d < bestD) { bestD = d; bestComp = cc; bestOrigin = o; }
                    }
                }
            }
            remaining.remove(bestComp);
            boolean anyOpen = false;
            for (Cell c : bestComp) if (open(c)) { anyOpen = true; break; }
            Cell arrival = null;
            int ad = Integer.MAX_VALUE;
            for (Cell c : bestComp) {
                if (anyOpen && !open(c)) continue;
                int d = dist(bestOrigin, cx(c), cz(c), c.y);
                if (d < ad) { ad = d; arrival = c; }
            }
            if (!anyOpen) stair(bestOrigin, arrival);
            List<Cell> order = order(bestComp, arrival);
            for (Cell c : order) {
                if (digCount >= MAX_DIGS) { truncated = true; break; }
                emitCell(c, !anyOpen && !open(c));
            }
            // everything of this component is an origin for the next islands (a thinned sample keeps the search cheap)
            int stride = Math.max(1, order.size() / 40);
            for (int i = 0; i < order.size(); i += stride) origins.add(new Origin(cx(order.get(i)), cz(order.get(i)), order.get(i).y));
        }
    }

    /** Layer by layer from the arrival layer outward, a flood from the cells that touch what is already ordered. */
    private List<Cell> order(List<Cell> comp, Cell arrival) {
        TreeMap<Integer, List<Cell>> byLayer = new TreeMap<>();
        for (Cell c : comp) byLayer.computeIfAbsent(c.y, k -> new ArrayList<>()).add(c);
        List<Integer> layers = new ArrayList<>(byLayer.keySet());
        layers.sort(Comparator.<Integer>comparingInt(y -> Math.abs(y - arrival.y)).thenComparing(Comparator.reverseOrder()));
        List<Cell> out = new ArrayList<>();
        Set<Cell> ordered = new HashSet<>();
        for (int y : layers) {
            List<Cell> layer = byLayer.get(y);
            Set<Cell> todo = new LinkedHashSet<>(layer);
            while (!todo.isEmpty()) {
                ArrayDeque<Cell> q = new ArrayDeque<>();
                // seeds: cells of this layer touching an ordered cell; else the arrival itself, else the cell nearest to the arrival
                List<Cell> seeds = new ArrayList<>();
                for (Cell c : todo) for (Cell n : neighbours(c)) if (ordered.contains(n)) { seeds.add(c); break; }
                if (seeds.isEmpty()) {
                    Cell near = todo.contains(arrival) ? arrival : null;
                    if (near == null) {
                        int best = Integer.MAX_VALUE;
                        for (Cell c : todo) {
                            int d = Math.abs(c.ix - arrival.ix) + Math.abs(c.iz - arrival.iz);
                            if (d < best) { best = d; near = c; }
                        }
                    }
                    seeds.add(near);
                }
                seeds.sort(Comparator.comparingInt(c -> Math.abs(c.ix - arrival.ix) + Math.abs(c.iz - arrival.iz)));
                for (Cell s : seeds) if (todo.remove(s)) { q.add(s); }
                while (!q.isEmpty()) {
                    Cell c = q.poll();
                    out.add(c);
                    ordered.add(c);
                    int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] o : d) {
                        List<Cell> l = col.get(key(c.ix + o[0], c.iz + o[1]));
                        if (l == null) continue;
                        for (Cell n : l) if (n.y == y && todo.remove(n)) q.add(n);
                    }
                }
            }
        }
        return out;
    }

    // ---- cells --------------------------------------------------------------------------------

    private BlockPos last;

    private void emitCell(Cell c, boolean lit) {
        int x0 = zone.blockX(c.ix), z0 = zone.blockZ(c.iz);
        List<int[]> cols = new ArrayList<>();
        for (int dx = 0; dx < cs; dx++) for (int dz = 0; dz < cs; dz++) cols.add(new int[]{x0 + dx, z0 + dz});
        if (last != null) {
            BlockPos ref = last;
            cols.sort(Comparator.comparingInt(a -> Math.abs(a[0] - ref.getX()) + Math.abs(a[1] - ref.getZ())));
        }
        for (int[] a : cols) {
            for (int yy = c.top(); yy >= c.y; yy--) dig(new BlockPos(a[0], yy, a[1]));
            floor(new BlockPos(a[0], c.y - 1, a[1]));
            last = new BlockPos(a[0], c.y, a[1]);
        }
        cellsDone++;
        if (lit && in.torches() && c.h >= 2 && cellsDone % 4 == 0) {
            steps.add(new Step(Kind.TORCH, new BlockPos(x0, c.y + 1, z0), Direction.UP));
        }
    }

    private void dig(BlockPos p) {
        if (p.getY() < in.minY() || p.getY() > in.maxY()) return;
        if (dug.add(p)) {
            steps.add(new Step(Kind.DIG, p, Direction.UP));
            digCount++;
        }
    }

    private void floor(BlockPos p) {
        if (p.getY() < in.minY()) return;
        if (floors.add(p)) steps.add(new Step(Kind.FLOOR, p, Direction.UP));
    }

    // ---- access staircase ---------------------------------------------------------------------

    private static Direction dirOf(int dx, int dz) {
        return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** L-shaped block path from (sx, sz) to (tx, tz), the longer leg first; excludes the start. */
    private static List<int[]> lpath(int sx, int sz, int tx, int tz) {
        List<int[]> p = new ArrayList<>();
        int x = sx, z = sz;
        boolean xFirst = Math.abs(tx - sx) >= Math.abs(tz - sz);
        for (int leg = 0; leg < 2; leg++) {
            boolean doX = (leg == 0) == xFirst;
            if (doX) while (x != tx) { x += Integer.signum(tx - x); p.add(new int[]{x, z}); }
            else while (z != tz) { z += Integer.signum(tz - z); p.add(new int[]{x, z}); }
        }
        return p;
    }

    /** A 2-wide stair at 1:1 from the origin's level to the target layer, then a flat tunnel to the target cell. */
    private void stair(Origin o, Cell target) {
        int bx = zone.blockX(target.ix), bz = zone.blockZ(target.iz);
        int tx = Math.max(bx, Math.min(bx + cs - 1, o.x())), tz = Math.max(bz, Math.min(bz + cs - 1, o.z()));
        int dy = target.y - o.feetY();
        int n = Math.abs(dy), sgn = Integer.signum(dy);
        List<int[]> path = lpath(o.x(), o.z(), tx, tz);
        if (path.isEmpty() && n == 0) return;
        Direction lastDir = path.isEmpty() ? in.facing() : dirOfLast(path, o);
        List<int[]> slope = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            if (j < path.size()) slope.add(path.get(j));
            else {
                int[] prev = slope.isEmpty() ? new int[]{o.x(), o.z()} : slope.get(slope.size() - 1);
                slope.add(new int[]{prev[0] + lastDir.getStepX(), prev[1] + lastDir.getStepZ()});
            }
        }
        int px = o.x(), pz = o.z();
        int torchEvery = Math.max(2, Balance.MINE_TORCH_EVERY_SPIRAL);
        for (int j = 0; j < slope.size(); j++) {
            int[] c = slope.get(j);
            Direction d = dirOf(Integer.signum(c[0] - px), Integer.signum(c[1] - pz));
            int feet = o.feetY() + sgn * (j + 1);
            carve(c[0], c[1], d, feet, 3);
            if (in.torches() && j % torchEvery == torchEvery - 1) steps.add(new Step(Kind.TORCH, lane(c[0], c[1], d).atY(feet + 1), Direction.UP));
            px = c[0]; pz = c[1];
        }
        List<int[]> flat = lpath(px, pz, tx, tz);
        int k = 0;
        for (int[] c : flat) {
            Direction d = dirOf(Integer.signum(c[0] - px), Integer.signum(c[1] - pz));
            carve(c[0], c[1], d, target.y, 2);
            if (in.torches() && ++k % Math.max(2, Balance.MINE_TORCH_EVERY_TUNNEL) == 0) steps.add(new Step(Kind.TORCH, lane(c[0], c[1], d).atY(target.y + 1), Direction.UP));
            px = c[0]; pz = c[1];
        }
    }

    private static Direction dirOfLast(List<int[]> path, Origin o) {
        int[] l = path.get(path.size() - 1);
        int[] p = path.size() > 1 ? path.get(path.size() - 2) : new int[]{o.x(), o.z()};
        return dirOf(Integer.signum(l[0] - p[0]), Integer.signum(l[1] - p[1]));
    }

    private static BlockPos lane(int x, int z, Direction d) {
        Direction lat = d.getClockWise();
        return new BlockPos(x + lat.getStepX(), 0, z + lat.getStepZ());
    }

    /** Digs the 2-wide footprint of a stair cell: {@code high} blocks from {@code feet}, floor below. */
    private void carve(int x, int z, Direction d, int feet, int high) {
        BlockPos[] cols = {new BlockPos(x, feet, z), lane(x, z, d).atY(feet)};
        for (BlockPos c : cols) {
            for (int yy = feet + high - 1; yy >= feet; yy--) dig(c.atY(yy));
            floor(c.atY(feet - 1));
        }
    }
}
