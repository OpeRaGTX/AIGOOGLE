package io.github.operagtx.hearthholm.zone;

import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wave J2: server side of the map screen shared by every zone profession - samples the area around a work site into a
 * one-byte-per-cell snapshot, throttles requests and validates zone edits (owner, distance, bounds).
 */
public final class ZoneMapService {
    /** A player may ask for a snapshot at most every this many ticks. */
    public static final int REQUEST_GAP_TICKS = 10;
    /** How far from the block a player may edit its zone (blocks). */
    public static final double EDIT_REACH = 12.0;

    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    /** Counters for tests and the log. */
    public static int served, throttled, rejected, applied;

    private ZoneMapService() {}

    // ---- open / refresh -----------------------------------------------------------------------

    /** Opens the map screen of a work site for a player (permission checked by the caller); false when throttled. */
    public static boolean open(ServerPlayer player, WorkSiteBlockEntity site, @Nullable Settlement settlement) {
        return send(player, site, settlement, WorkZone.SURFACE);
    }

    public static void handleRequest(ServerPlayer player, Payloads.ZoneMapRequest r) {
        ServerLevel level = player.level();
        if (!(level.getBlockEntity(r.pos()) instanceof WorkSiteBlockEntity w) || WorkZone.specOf(w.type().id()) == null) return;
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(r.pos())) > EDIT_REACH * EDIT_REACH * 4) return;
        Settlement s = SettlementService.manager(level).byId(w.settlementId());
        if (s != null && !SettlementService.canManage(player, s)) return;
        send(player, w, s, r.layerY());
    }

    private static boolean send(ServerPlayer player, WorkSiteBlockEntity w, @Nullable Settlement s, int layerY) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < REQUEST_GAP_TICKS && now >= last) { throttled++; return false; }
        LAST_REQUEST.put(player.getUUID(), now);
        WorkZone.Spec spec = w.zoneSpec();
        if (!spec.layered()) layerY = WorkZone.SURFACE;
        ServerPlayNetworking.send(player, snapshot(level, w, s, layerY));
        served++;
        return true;
    }

    // ---- snapshot -----------------------------------------------------------------------------

    /** Samples the grid around the site: the surface (heightmap top block) or, for a layered zone, the block at {@code layerY}. */
    public static Payloads.ZoneMapData snapshot(ServerLevel level, WorkSiteBlockEntity w, @Nullable Settlement s, int layerY) {
        WorkZone.Spec spec = w.zoneSpec();
        WorkZone zone = w.zone() != null ? w.zone() : WorkZone.centred(spec, w.getBlockPos());
        int n = zone.size();
        byte[] colors = new byte[n * n];
        byte[] flags = new byte[(n * n + 3) / 4];
        boolean surface = layerY == WorkZone.SURFACE;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int[] prevH = new int[n];
        java.util.Arrays.fill(prevH, Integer.MIN_VALUE);
        int minY = level.getMinY();
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                int x = zone.blockX(ix) + zone.cell() / 2, z = zone.blockZ(iz) + zone.cell() / 2;
                int i = iz * n + ix;
                if (!level.hasChunkAt(mp.set(x, level.getMinY(), z))) { prevH[ix] = Integer.MIN_VALUE; continue; }
                if (surface) {
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    BlockState st = level.getBlockState(mp.set(x, y, z));
                    while (y > minY && (st.isAir() || st.getMapColor(level, mp) == MapColor.NONE)) st = level.getBlockState(mp.set(x, --y, z));
                    int flag = 0;
                    if (st.is(BlockTags.LOGS)) flag = Payloads.ZoneMapData.LOG;
                    else if (st.is(BlockTags.LEAVES)) {
                        flag = Payloads.ZoneMapData.LEAF;
                        // a trunk under the canopy marks the column as a tree base
                        for (int dy = 1; dy <= 14; dy++) {
                            BlockState below = level.getBlockState(mp.set(x, y - dy, z));
                            if (below.is(BlockTags.LOGS)) { flag = Payloads.ZoneMapData.LOG; break; }
                            if (!below.is(BlockTags.LEAVES) && !below.isAir()) break;
                        }
                    }
                    flags[i >> 2] |= (byte) (flag << ((i & 3) * 2));
                    MapColor color = st.getMapColor(level, mp.set(x, y, z));
                    MapColor.Brightness b;
                    if (!st.getFluidState().isEmpty() && st.getFluidState().is(Fluids.WATER) || color == MapColor.WATER) {
                        int depth = 0;
                        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos(x, y, z);
                        while (depth < 12 && y - depth > minY && !level.getFluidState(q.set(x, y - depth - 1, z)).isEmpty()) depth++;
                        double d = depth * 0.1 + ((ix + iz) & 1) * 0.2;
                        b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                        prevH[ix] = y;
                    } else {
                        int north = prevH[ix] == Integer.MIN_VALUE ? y : prevH[ix];
                        double d = (y - north) * 4.0 / (zone.cell() + 4) + (((ix + iz) & 1) - 0.5) * 0.4;
                        b = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                        prevH[ix] = y;
                    }
                    colors[i] = color.getPackedId(b);
                } else {
                    BlockState st = level.getBlockState(mp.set(x, layerY, z));
                    MapColor color = st.isAir() ? MapColor.NONE : st.getMapColor(level, mp);
                    colors[i] = color == MapColor.NONE ? 0 : color.getPackedId(MapColor.Brightness.NORMAL);
                }
            }
        }
        List<Payloads.MapMarker> markers = new ArrayList<>();
        BlockPos c = w.getBlockPos();
        markers.add(new Payloads.MapMarker(Payloads.MapMarker.SITE, c.getX(), c.getZ()));
        BlockPos chest = chestNear(level, c);
        if (chest != null) markers.add(new Payloads.MapMarker(Payloads.MapMarker.CHEST, chest.getX(), chest.getZ()));
        if (s != null) {
            int count = 0;
            for (BlockPos p : s.storagePositions()) {
                int ix = zone.cellX(p.getX()), iz = zone.cellZ(p.getZ());
                if (zone.inGrid(ix, iz) && count++ < 6) markers.add(new Payloads.MapMarker(Payloads.MapMarker.WAREHOUSE, p.getX(), p.getZ()));
            }
        }
        return new Payloads.ZoneMapData(SettlementService.siteView(level, w, s), layerY, n, zone.cell(), zone.originX(), zone.originZ(),
                colors, flags, zone.bits(layerY), markers);
    }

    /** The container next to the block (same rule as {@code BaseJob.chestsNear}), or null. */
    public static @Nullable BlockPos chestNear(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!p.equals(pos) && level.getBlockEntity(p) instanceof Container) return p.immutable();
        }
        return null;
    }

    // ---- edits --------------------------------------------------------------------------------

    /** Applies a zone edit after checking owner, distance, layer and bounds; returns whether it was applied. */
    public static boolean handleEdit(ServerPlayer player, Payloads.ZoneEdit e) {
        ServerLevel level = player.level();
        if (!(level.getBlockEntity(e.pos()) instanceof WorkSiteBlockEntity w) || WorkZone.specOf(w.type().id()) == null) return reject("no zone site");
        Settlement s = SettlementService.manager(level).byId(w.settlementId());
        if (s != null && !SettlementService.canManage(player, s)) {
            SettlementService.say(player, "not_owner", s.ownerName);
            return reject("not the owner");
        }
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(e.pos())) > EDIT_REACH * EDIT_REACH) return reject("too far");
        WorkZone.Spec spec = w.zoneSpec();
        if (!spec.layered() && e.layerY() != WorkZone.SURFACE) return reject("layer");
        if (e.op() < Payloads.ZoneEdit.SET || e.op() > Payloads.ZoneEdit.CLEAR_ALL) return reject("op");
        if (e.rects().size() > Payloads.ZoneEdit.MAX_RECTS) return reject("too many rects");
        WorkZone zone = w.zoneForEdit();
        for (Payloads.Rect r : e.rects()) {
            if (r.x0() > r.x1() || r.z0() > r.z1() || r.x0() < 0 || r.z0() < 0 || r.x1() >= zone.size() || r.z1() >= zone.size()) return reject("bounds");
        }
        if (e.op() == Payloads.ZoneEdit.CLEAR_ALL) zone.clear(e.layerY());
        else for (Payloads.Rect r : e.rects()) zone.setRect(e.layerY(), r.x0(), r.z0(), r.x1(), r.z1(), e.op() == Payloads.ZoneEdit.SET);
        w.zoneChanged();
        applied++;
        return true;
    }

    private static boolean reject(String why) {
        rejected++;
        io.github.operagtx.hearthholm.Hearthholm.LOG.debug("zone edit rejected: {}", why);
        return false;
    }
}
