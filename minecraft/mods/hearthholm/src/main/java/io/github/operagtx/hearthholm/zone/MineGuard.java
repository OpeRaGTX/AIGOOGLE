package io.github.operagtx.hearthholm.zone;

import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wave J3: places the miner never digs. A guard is a list of column boxes; a block is protected when its column is inside
 * a box and it is not above the box's top (so everything below the structure is safe too): the marker and its chest,
 * the town hall desk, the warehouse ledgers and containers, and every detected house, workshop or hall.
 */
public final class MineGuard {
    /** A protected column box; blocks at or below {@code topY} inside it are protected. */
    public record Box(int x0, int z0, int x1, int z1, int topY) {
        boolean has(int x, int y, int z) { return y <= topY && x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    private final List<Box> boxes;

    private MineGuard(List<Box> boxes) { this.boxes = boxes; }

    public static MineGuard none() { return new MineGuard(List.of()); }

    public static MineGuard of(ServerLevel level, WorkSiteBlockEntity w, @Nullable Settlement s) {
        List<Box> out = new ArrayList<>();
        BlockPos m = w.getBlockPos();
        out.add(new Box(m.getX(), m.getZ(), m.getX(), m.getZ(), m.getY() + 1));
        BlockPos chest = ZoneMapService.chestNear(level, m);
        if (chest != null) out.add(new Box(chest.getX(), chest.getZ(), chest.getX(), chest.getZ(), chest.getY() + 1));
        if (s != null) {
            out.add(around(s.center, 2, 6));
            for (BlockPos p : s.ledgers) out.add(around(p, 1, 3));
            for (BlockPos p : s.workshops) out.add(around(p, 1, 3));
            for (BuildingRecord b : s.buildings) {
                int x0 = b.anchor().getX() - 3, x1 = b.anchor().getX() + 3, z0 = b.anchor().getZ() - 3, z1 = b.anchor().getZ() + 3, top = b.anchor().getY() + 6;
                List<BlockPos> all = new ArrayList<>(b.beds());
                all.addAll(b.containers());
                all.addAll(b.stations());
                for (BlockPos p : all) {
                    x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX());
                    z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ());
                    top = Math.max(top, p.getY() + 4);
                }
                out.add(new Box(x0 - 2, z0 - 2, x1 + 2, z1 + 2, top));
            }
        }
        return new MineGuard(out);
    }

    private static Box around(BlockPos p, int r, int up) {
        return new Box(p.getX() - r, p.getZ() - r, p.getX() + r, p.getZ() + r, p.getY() + up);
    }

    public boolean blocked(BlockPos p) { return blocked(p.getX(), p.getY(), p.getZ()); }

    public boolean blocked(int x, int y, int z) {
        for (Box b : boxes) if (b.has(x, y, z)) return true;
        return false;
    }

    /** Whether any block of the 2 x 2 cell footprint at the given layer span is protected (the map paints it red). */
    public boolean blockedCell(int x, int z, int cell, int y, int h) {
        for (int dx = 0; dx < cell; dx++) for (int dz = 0; dz < cell; dz++) for (int dy = 0; dy < h; dy++) {
            if (blocked(x + dx, y + dy, z + dz)) return true;
        }
        return false;
    }
}
