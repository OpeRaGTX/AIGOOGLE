package io.github.operagtx.hearthholm.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wave J2: a hand-drawn work zone, a grid of square cells around the work-site block (100x100 by default). One bit per
 * cell in a {@code long[]} per layer; layers are keyed by Y (the lumberjack uses the single {@link #SURFACE} layer, the
 * miner of J3 one layer per level). A cell is {@code cell} x {@code cell} blocks, so a 100x100 grid covers 100x100 blocks
 * (cell 1) or 200x200 blocks (cell 2). Cell (0,0) is the north-west corner; the block sits in the middle cell.
 * The same class runs on the server (saved in the block entity) and on the client (the map screen edits a copy).
 */
public final class WorkZone {
    /** Layer key of the surface (ground-level) zone. */
    public static final int SURFACE = Integer.MIN_VALUE;
    public static final int DEFAULT_SIZE = 100;

    /** Size and cell size of the zone of one work-site type; {@code layered} zones draw one map per Y level. */
    public record Spec(int size, int cell, boolean layered) {
        public static final Spec LUMBERJACK = new Spec(DEFAULT_SIZE, 1, false);
        /** Wave J3: the miner draws 50 x 50 cells of 2 x 2 blocks (100 x 100 blocks), one layer per Y level. */
        public static final Spec MINER = new Spec(50, 2, true);
    }

    /** Zone grids by work-site type id; the profession module registers its own. */
    private static final Map<Identifier, Spec> SPECS = new HashMap<>();

    public static void register(Identifier siteType, Spec spec) { SPECS.put(siteType, spec); }

    public static @Nullable Spec specOf(Identifier siteType) { return SPECS.get(siteType); }

    private record LayerData(int y, List<Long> bits) {
        static final Codec<LayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(LayerData::y),
                Codec.LONG.listOf().fieldOf("bits").forGetter(LayerData::bits)).apply(i, LayerData::new));
    }

    /** Wave J3: a painted cell digs {@code height} blocks up from its layer; default 2, range 1..4 (stored as two bit planes, 0 = default). */
    public static final int DEFAULT_HEIGHT = 2, MAX_HEIGHT = 4;

    public static final Codec<WorkZone> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("size", DEFAULT_SIZE).forGetter(z -> z.size),
            Codec.INT.optionalFieldOf("cell", 1).forGetter(z -> z.cell),
            Codec.INT.optionalFieldOf("ox", 0).forGetter(z -> z.originX),
            Codec.INT.optionalFieldOf("oz", 0).forGetter(z -> z.originZ),
            LayerData.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(z -> {
                List<LayerData> out = new ArrayList<>();
                z.layers.forEach((y, bits) -> {
                    List<Long> l = new ArrayList<>(bits.length);
                    for (long b : bits) l.add(b);
                    out.add(new LayerData(y, l));
                });
                return out;
            }),
            LayerData.CODEC.listOf().optionalFieldOf("heights", List.of()).forGetter(z -> {
                List<LayerData> out = new ArrayList<>();
                z.heights.forEach((y, bits) -> {
                    List<Long> l = new ArrayList<>(bits.length);
                    for (long b : bits) l.add(b);
                    out.add(new LayerData(y, l));
                });
                return out;
            })).apply(i, WorkZone::fromSaved));

    private static WorkZone fromSaved(int size, int cell, int ox, int oz, List<LayerData> layers, List<LayerData> heights) {
        WorkZone z = new WorkZone(Math.max(1, Math.min(256, size)), Math.max(1, Math.min(16, cell)), ox, oz);
        int words = z.words();
        for (LayerData d : layers) {
            long[] bits = new long[words];
            for (int k = 0; k < Math.min(words, d.bits().size()); k++) bits[k] = d.bits().get(k);
            z.layers.put(d.y(), bits);
        }
        for (LayerData d : heights) {
            long[] bits = new long[2 * words];
            for (int k = 0; k < Math.min(bits.length, d.bits().size()); k++) bits[k] = d.bits().get(k);
            z.heights.put(d.y(), bits);
        }
        z.dropEmptyLayers();
        return z;
    }

    private final int size, cell;
    private final int originX, originZ;
    private final TreeMap<Integer, long[]> layers = new TreeMap<>();
    /** Wave J3: per layer two bit planes of the cell height code (plane k starts at k * words); absent = every cell has the default height. */
    private final TreeMap<Integer, long[]> heights = new TreeMap<>();

    /** A zone of {@code size} x {@code size} cells whose north-west corner block is ({@code originX}, {@code originZ}). */
    public WorkZone(int size, int cell, int originX, int originZ) {
        this.size = size;
        this.cell = cell;
        this.originX = originX;
        this.originZ = originZ;
    }

    /** An empty zone centred on {@code centre} (the work-site block sits in cell size/2). */
    public static WorkZone centred(Spec spec, BlockPos centre) {
        int half = spec.size() * spec.cell() / 2;
        return new WorkZone(spec.size(), spec.cell(), centre.getX() - half, centre.getZ() - half);
    }

    public WorkZone copy() {
        WorkZone z = new WorkZone(size, cell, originX, originZ);
        layers.forEach((y, b) -> z.layers.put(y, b.clone()));
        heights.forEach((y, b) -> z.heights.put(y, b.clone()));
        return z;
    }

    public int size() { return size; }
    public int cell() { return cell; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }

    private int words() { return (size * size + 63) >>> 6; }

    // ---- cell <-> block coordinates ---------------------------------------------------------

    public int cellX(int blockX) { return Math.floorDiv(blockX - originX, cell); }
    public int cellZ(int blockZ) { return Math.floorDiv(blockZ - originZ, cell); }
    /** The west-most block of cell column {@code ix}. */
    public int blockX(int ix) { return originX + ix * cell; }
    public int blockZ(int iz) { return originZ + iz * cell; }
    public boolean inGrid(int ix, int iz) { return ix >= 0 && iz >= 0 && ix < size && iz < size; }

    // ---- cells ------------------------------------------------------------------------------

    public boolean get(int layer, int ix, int iz) {
        if (!inGrid(ix, iz)) return false;
        long[] b = layers.get(layer);
        if (b == null) return false;
        int i = iz * size + ix;
        return (b[i >>> 6] & (1L << (i & 63))) != 0;
    }

    /** Sets or clears one cell; returns whether it changed. */
    public boolean set(int layer, int ix, int iz, boolean on) {
        if (!inGrid(ix, iz)) return false;
        long[] b = layers.get(layer);
        if (b == null) {
            if (!on) return false;
            b = new long[words()];
            layers.put(layer, b);
        }
        int i = iz * size + ix;
        long m = 1L << (i & 63);
        boolean was = (b[i >>> 6] & m) != 0;
        if (was == on) return false;
        b[i >>> 6] ^= m;
        if (!on) putHeight(layer, i, DEFAULT_HEIGHT);
        return true;
    }

    // ---- wave J3: cell heights ----------------------------------------------------------------

    private static int encodeHeight(int h) { return (Math.max(1, Math.min(MAX_HEIGHT, h)) - 2) & 3; }   // 2->0, 3->1, 4->2, 1->3
    private static int decodeHeight(int code) { return ((code + 1) & 3) + 1; }

    private void putHeight(int layer, int i, int h) {
        int code = encodeHeight(h);
        long[] p = heights.get(layer);
        if (p == null) {
            if (code == 0) return;
            p = new long[2 * words()];
            heights.put(layer, p);
        }
        int w = words();
        long m = 1L << (i & 63);
        for (int k = 0; k < 2; k++) {
            if (((code >> k) & 1) != 0) p[k * w + (i >>> 6)] |= m; else p[k * w + (i >>> 6)] &= ~m;
        }
    }

    /** The dig height (1..4) of a painted cell; 2 unless painted otherwise. */
    public int height(int layer, int ix, int iz) {
        long[] p = heights.get(layer);
        if (p == null || !inGrid(ix, iz)) return DEFAULT_HEIGHT;
        int i = iz * size + ix, w = words();
        long m = 1L << (i & 63);
        int code = ((p[i >>> 6] & m) != 0 ? 1 : 0) | ((p[w + (i >>> 6)] & m) != 0 ? 2 : 0);
        return decodeHeight(code);
    }

    /** Paints the inclusive rectangle with the given dig height (cells already painted get the new height too); returns the cells newly painted. */
    public int paintRect(int layer, int x0, int z0, int x1, int z1, int height) {
        int ax = Math.max(0, Math.min(x0, x1)), bx = Math.min(size - 1, Math.max(x0, x1));
        int az = Math.max(0, Math.min(z0, z1)), bz = Math.min(size - 1, Math.max(z0, z1));
        int n = 0;
        for (int z = az; z <= bz; z++) for (int x = ax; x <= bx; x++) {
            if (set(layer, x, z, true)) n++;
            putHeight(layer, z * size + x, height);
        }
        return n;
    }

    /** A hash of every painted cell and height: plans are cached by it. */
    public int contentHash() {
        int h = 17;
        for (var e : layers.entrySet()) h = h * 31 + e.getKey() * 7 + java.util.Arrays.hashCode(e.getValue());
        for (var e : heights.entrySet()) h = h * 31 + e.getKey() * 13 + java.util.Arrays.hashCode(e.getValue());
        return h;
    }


    /** Sets or clears the inclusive cell rectangle (corners in any order, clipped to the grid); returns the cells that changed. */
    public int setRect(int layer, int x0, int z0, int x1, int z1, boolean on) {
        int ax = Math.max(0, Math.min(x0, x1)), bx = Math.min(size - 1, Math.max(x0, x1));
        int az = Math.max(0, Math.min(z0, z1)), bz = Math.min(size - 1, Math.max(z0, z1));
        int n = 0;
        for (int z = az; z <= bz; z++) for (int x = ax; x <= bx; x++) if (set(layer, x, z, on)) n++;
        if (!on) dropEmptyLayers();
        return n;
    }

    public void clear(int layer) { layers.remove(layer); heights.remove(layer); }

    public void clearAll() { layers.clear(); heights.clear(); }

    private void dropEmptyLayers() {
        layers.values().removeIf(b -> {
            for (long v : b) if (v != 0) return false;
            return true;
        });
        heights.keySet().removeIf(y -> !layers.containsKey(y));
        heights.values().removeIf(b -> {
            for (long v : b) if (v != 0) return false;
            return true;
        });
    }

    public boolean isEmpty() {
        for (long[] b : layers.values()) for (long v : b) if (v != 0) return false;
        return true;
    }

    public int cellCount() {
        int n = 0;
        for (long[] b : layers.values()) for (long v : b) n += Long.bitCount(v);
        return n;
    }

    public int cellCount(int layer) {
        long[] b = layers.get(layer);
        int n = 0;
        if (b != null) for (long v : b) n += Long.bitCount(v);
        return n;
    }

    /** Layer keys that hold at least one cell, ascending. */
    public List<Integer> layerKeys() { return new ArrayList<>(layers.keySet()); }

    @FunctionalInterface
    public interface CellVisitor { void visit(int ix, int iz); }

    /** Visits every set cell of a layer in row order. */
    public void forEachCell(int layer, CellVisitor v) {
        long[] b = layers.get(layer);
        if (b == null) return;
        for (int w = 0; w < b.length; w++) {
            long bits = b[w];
            while (bits != 0) {
                int t = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int i = (w << 6) + t;
                if (i < size * size) v.visit(i % size, i / size);
            }
        }
    }

    /** The layer bits as a copy (for the network); empty layers give an all-zero array. */
    public long[] bits(int layer) {
        long[] b = layers.get(layer);
        return b == null ? new long[words()] : b.clone();
    }

    public void setBits(int layer, long[] bits) {
        long[] b = new long[words()];
        System.arraycopy(bits, 0, b, 0, Math.min(bits.length, b.length));
        layers.put(layer, b);
        dropEmptyLayers();
    }

    // ---- block queries ----------------------------------------------------------------------

    /** Whether the block is in a set cell of the surface layer or of its own Y layer. */
    public boolean contains(BlockPos p) { return contains(p.getX(), p.getY(), p.getZ()); }

    public boolean contains(int x, int y, int z) {
        int ix = cellX(x), iz = cellZ(z);
        return get(SURFACE, ix, iz) || get(y, ix, iz);
    }

    /** Whether the column (x, z) is in a set cell of any layer. */
    public boolean containsColumn(int x, int z) {
        int ix = cellX(x), iz = cellZ(z);
        if (!inGrid(ix, iz)) return false;
        for (long[] b : layers.values()) {
            int i = iz * size + ix;
            if ((b[i >>> 6] & (1L << (i & 63))) != 0) return true;
        }
        return false;
    }

    /** Bounding box in blocks of every set cell (y from {@code minY} to {@code maxY}), or null when empty. */
    public @Nullable AABB bounds(double minY, double maxY) {
        int[] r = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int layer : layers.keySet()) {
            forEachCell(layer, (ix, iz) -> {
                r[0] = Math.min(r[0], ix); r[1] = Math.min(r[1], iz);
                r[2] = Math.max(r[2], ix); r[3] = Math.max(r[3], iz);
            });
        }
        if (r[0] == Integer.MAX_VALUE) return null;
        return new AABB(blockX(r[0]), minY, blockZ(r[1]), blockX(r[2]) + cell, maxY, blockZ(r[3]) + cell);
    }

    /** A random block column inside a random set cell of the layer, or null when the layer is empty. */
    public @Nullable BlockPos randomColumn(int layer, net.minecraft.util.RandomSource rnd, int y) {
        int count = cellCount(layer);
        if (count == 0) return null;
        int pick = rnd.nextInt(count);
        int[] at = {-1, -1};
        int[] n = {0};
        forEachCell(layer, (ix, iz) -> { if (n[0]++ == pick) { at[0] = ix; at[1] = iz; } });
        if (at[0] < 0) return null;
        return new BlockPos(blockX(at[0]) + rnd.nextInt(cell), y, blockZ(at[1]) + rnd.nextInt(cell));
    }
}
