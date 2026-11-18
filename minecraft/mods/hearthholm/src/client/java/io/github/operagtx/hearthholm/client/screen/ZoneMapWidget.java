package io.github.operagtx.hearthholm.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wave J2: reusable zone map. Draws a {@link Payloads.ZoneMapData} snapshot as a dynamic texture, the zone as a second
 * semi-transparent texture, grid lines when zoomed, markers and trees; edits the zone with the brush, rectangle, eraser and
 * rectangle-eraser tools (diffs go out through {@link Sink}); zoom with the wheel (fit / 2x / 4x), pan with the right button.
 */
public final class ZoneMapWidget {
    public enum Tool { BRUSH, RECT, ERASER, ERASE_RECT }

    /** Receives the diffs of the player's edits. */
    public interface Sink { void send(int op, List<Payloads.Rect> rects); }

    public static final int VIEW = 200;   // viewport edge in GUI pixels
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final int BLOCKED_RGB = 0xE03030, ORE_DOT = 0xFFE066;
    private static final int ZONE_RGB = 0xFFC832, TREE_TRUNK = 0xFF7A4B24, SITE = 0xFFFFFFFF, CHEST = 0xFFB5733A, WAREHOUSE = 0xFF4F9BE0;

    public final int x, y;
    private final Sink sink;
    private Payloads.ZoneMapData data;
    private WorkZone zone;
    private final DynamicTexture mapTex, zoneTex, ghostTex;
    private final Identifier mapId, zoneId, ghostId;
    private final int n;

    private Tool tool = Tool.BRUSH;
    private int brush = 3;
    private int height = WorkZone.DEFAULT_HEIGHT;   // wave J3: dig height of the cells painted next (1..4)
    private int zoomLevel;      // 0 fit, 1 = 2x, 2 = 4x
    private int viewX, viewZ;   // top-left visible cell
    private int hoverX = -1, hoverZ = -1;
    // drag state
    private boolean painting, panning;
    private int lastX, lastZ, startX, startZ;
    private double panRemX, panRemZ;
    private final List<Payloads.Rect> pending = new ArrayList<>();
    private boolean pendingSet;

    public ZoneMapWidget(int x, int y, Payloads.ZoneMapData data, Sink sink) {
        this.x = x;
        this.y = y;
        this.sink = sink;
        this.n = data.size();
        int id = IDS.incrementAndGet();
        this.mapTex = new DynamicTexture(() -> "hearthholm zone map", n, n, false);
        this.zoneTex = new DynamicTexture(() -> "hearthholm zone overlay", n, n, false);
        this.ghostTex = new DynamicTexture(() -> "hearthholm zone ghost", n, n, false);
        this.ghostId = Hearthholm.id("zone_map/ghost" + id);
        Minecraft.getInstance().getTextureManager().register(ghostId, ghostTex);
        this.mapId = Hearthholm.id("zone_map/map" + id);
        this.zoneId = Hearthholm.id("zone_map/zone" + id);
        Minecraft.getInstance().getTextureManager().register(mapId, mapTex);
        Minecraft.getInstance().getTextureManager().register(zoneId, zoneTex);
        setData(data, true);
        centreView();
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(mapId);
        Minecraft.getInstance().getTextureManager().release(zoneId);
        Minecraft.getInstance().getTextureManager().release(ghostId);
    }

    // ---- data -------------------------------------------------------------------------------

    /** Replaces the snapshot; {@code withZone} false keeps the local zone (an edit is still on its way to the server). */
    public void setData(Payloads.ZoneMapData d, boolean withZone) {
        this.data = d;
        boolean newLayer = this.data != null && layerShown != d.layerY();
        if (withZone || zone == null || newLayer) {
            zone = new WorkZone(d.size(), d.cell(), d.originX(), d.originZ());
            zone.setBits(d.layerY(), d.zoneBits());
        }
        layerShown = d.layerY();
        paintMap();
        paintZone();
        paintGhost();
    }

    private int layerShown = Integer.MIN_VALUE + 1;

    /** Whether the snapshot is a layer view (the miner) and not the surface view. */
    private boolean layered() { return data.layerY() != WorkZone.SURFACE; }

    public Payloads.ZoneMapData data() { return data; }
    public WorkZone zone() { return zone; }
    public int layer() { return data.layerY(); }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }

    private void paintMap() {
        NativeImage img = mapTex.getPixels();
        int unknown = abgr(0xFF1E1711);
        for (int i = 0; i < n * n; i++) {
            int b = data.colors()[i] & 0xFF;
            int c = b == 0 ? unknown : abgr(MapColor.getColorFromPackedId(b));   // the vanilla helper returns ARGB
            int flag = data.flag(i);
            if (layered()) {
                if (flag == Payloads.ZoneMapData.ORE) c = lighten(c, 0.45f);
            } else if (flag == Payloads.ZoneMapData.LEAF) c = darken(c, 0.82f);
            else if (flag == Payloads.ZoneMapData.LOG) c = abgr(0xFF000000 | TREE_TRUNK);
            img.setPixelABGR(i % n, i / n, c);
        }
        mapTex.upload();
    }

    private static int lighten(int abgr, float k) {
        int r = (abgr & 0xFF), g = ((abgr >> 8) & 0xFF), b = ((abgr >> 16) & 0xFF);
        r += (int) ((255 - r) * k); g += (int) ((255 - g) * k); b += (int) ((255 - b) * k);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int darken(int abgr, float k) {
        int r = (int) ((abgr & 0xFF) * k), g = (int) (((abgr >> 8) & 0xFF) * k), b = (int) (((abgr >> 16) & 0xFF) * k);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private void paintZone() {
        NativeImage img = zoneTex.getPixels();
        int clear = 0;
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) img.setPixelABGR(x, z, clear);
        int layer = data.layerY();
        zone.forEachCell(layer, (ix, iz) -> img.setPixelABGR(ix, iz, zoneColor(ix, iz)));
        zoneTex.upload();
    }

    /** Wave J3: cells painted one layer above (blue) and below (orange) as a faint ghost; the current layer's cells cover them. */
    private void paintGhost() {
        NativeImage img = ghostTex.getPixels();
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) img.setPixelABGR(x, z, 0);
        if (layered()) {
            ghostBits(img, data.ghostDown(), 0x5AF09A3C);
            ghostBits(img, data.ghostUp(), 0x5A4C9CFF);
        }
        ghostTex.upload();
    }

    private void ghostBits(NativeImage img, long[] bits, int argb) {
        for (int i = 0; i < n * n && (i >> 6) < bits.length; i++) {
            if ((bits[i >> 6] & (1L << (i & 63))) != 0) img.setPixelABGR(i % n, i / n, abgr(argb));
        }
    }

    /** Zone cells are tinted, the cells on its border more strongly so the outline reads at any zoom. */
    private int zoneColor(int ix, int iz) {
        int layer = data.layerY();
        boolean edge = !zone.get(layer, ix - 1, iz) || !zone.get(layer, ix + 1, iz) || !zone.get(layer, ix, iz - 1) || !zone.get(layer, ix, iz + 1);
        if (layered() && data.flag(iz * n + ix) == Payloads.ZoneMapData.BLOCKED) return abgr((edge ? 0xD0000000 : 0xA0000000) | BLOCKED_RGB);   // red: the miner will not dig here
        return abgr((edge ? 0xB0000000 : 0x6A000000) | ZONE_RGB);
    }

    private void paintCell(int ix, int iz) {
        if (!zone.inGrid(ix, iz)) return;
        zoneTex.getPixels().setPixelABGR(ix, iz, zone.get(data.layerY(), ix, iz) ? zoneColor(ix, iz) : 0);
    }

    // ---- view -------------------------------------------------------------------------------

    public int ppc() {
        int fit = Math.max(1, VIEW / n);
        return fit << zoomLevel;
    }

    public int visible() { return Math.min(n, VIEW / ppc()); }

    public int zoomLevel() { return zoomLevel; }

    public void setTool(Tool t) { tool = t; }
    public Tool tool() { return tool; }
    public int brush() { return brush; }
    public void setBrush(int b) { brush = Math.max(1, Math.min(5, b)); }
    public int height() { return height; }
    private boolean editable = true;
    /** Wave J3: the miner's map is view-only while the automatic layout is chosen. */
    public void setEditable(boolean e) { editable = e; if (!e) { flush(); painting = false; } }
    public void setHeight(int h) { flush(); height = Math.max(1, Math.min(WorkZone.MAX_HEIGHT, h)); }

    private void centreView() {
        viewX = n / 2 - visible() / 2;
        viewZ = n / 2 - visible() / 2;
        clampView();
    }

    private void clampView() {
        int v = visible();
        viewX = Math.max(0, Math.min(n - v, viewX));
        viewZ = Math.max(0, Math.min(n - v, viewZ));
    }

    /** Sets the zoom (0 fit, 1 = 2x, 2 = 4x) keeping the cell under ({@code px}, {@code pz}) (GUI pixels) where it is. */
    public void zoomTo(int level, double px, double pz) {
        level = Math.max(0, Math.min(2, level));
        if (level == zoomLevel) return;
        double cx = viewX + (px - x) / ppc(), cz = viewZ + (pz - y) / ppc();
        zoomLevel = level;
        if (level == 0) { viewX = 0; viewZ = 0; }
        else {
            viewX = (int) Math.round(cx - (px - x) / ppc());
            viewZ = (int) Math.round(cz - (pz - y) / ppc());
        }
        clampView();
    }

    /** Zoom button: fit shows the whole grid, the others centre on the work-site block. */
    public void setZoom(int level) {
        zoomLevel = Math.max(0, Math.min(2, level));
        if (zoomLevel == 0) { viewX = 0; viewZ = 0; } else centreView();
    }

    public boolean contains(double mx, double my) {
        int s = visible() * ppc();
        return mx >= x && my >= y && mx < x + s && my < y + s;
    }

    private int cellAtX(double mx) { return viewX + (int) Math.floor((mx - x) / ppc()); }
    private int cellAtZ(double my) { return viewZ + (int) Math.floor((my - y) / ppc()); }

    public int hoverX() { return hoverX; }
    public int hoverZ() { return hoverZ; }

    // ---- input ------------------------------------------------------------------------------

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        int cx = cellAtX(mx), cz = cellAtZ(my);
        if (button == 1) { panning = true; panRemX = panRemZ = 0; return true; }
        if (button != 0 || !editable) return false;
        painting = true;
        startX = lastX = cx;
        startZ = lastZ = cz;
        if (tool == Tool.BRUSH || tool == Tool.ERASER) stamp(cx, cz, tool == Tool.BRUSH);
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        hoverX = cellAtX(mx); hoverZ = cellAtZ(my);
        if (panning && button == 1) {
            panRemX -= dx / ppc(); panRemZ -= dy / ppc();
            int sx = (int) panRemX, sz = (int) panRemZ;
            if (sx != 0 || sz != 0) {
                viewX += sx; viewZ += sz;
                panRemX -= sx; panRemZ -= sz;
                clampView();
            }
            return true;
        }
        if (painting && button == 0) {
            if (tool == Tool.BRUSH || tool == Tool.ERASER) {
                int cx = Math.max(0, Math.min(n - 1, cellAtX(mx))), cz = Math.max(0, Math.min(n - 1, cellAtZ(my)));
                boolean on = tool == Tool.BRUSH;
                int steps = Math.max(Math.abs(cx - lastX), Math.abs(cz - lastZ));
                for (int s = 1; s <= steps; s++) stamp(lastX + Math.round((cx - lastX) * s / (float) steps), lastZ + Math.round((cz - lastZ) * s / (float) steps), on);
                lastX = cx; lastZ = cz;
                if (pending.size() >= 128) flush();
            } else {
                lastX = Math.max(0, Math.min(n - 1, cellAtX(mx)));
                lastZ = Math.max(0, Math.min(n - 1, cellAtZ(my)));
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 && panning) { panning = false; return true; }
        if (button != 0 || !painting) return false;
        painting = false;
        if (tool == Tool.RECT || tool == Tool.ERASE_RECT) {
            int x0 = Math.max(0, Math.min(n - 1, startX)), z0 = Math.max(0, Math.min(n - 1, startZ));
            int x1 = Math.max(0, Math.min(n - 1, lastX)), z1 = Math.max(0, Math.min(n - 1, lastZ));
            applyRect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), tool == Tool.RECT);
            flush();
        } else flush();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double sy) {
        if (!contains(mx, my) || sy == 0) return false;
        zoomTo(zoomLevel + (sy > 0 ? 1 : -1), mx, my);
        return true;
    }

    public void hover(double mx, double my) {
        if (contains(mx, my)) { hoverX = cellAtX(mx); hoverZ = cellAtZ(my); } else { hoverX = hoverZ = -1; }
    }

    public boolean busy() { return painting || !pending.isEmpty(); }

    /** Applies a brush square at a cell centre and queues its diff (adjacent squares of a row merge into one run). */
    private void stamp(int cx, int cz, boolean on) {
        int half = brush / 2;
        int x0 = cx - half, z0 = cz - half, x1 = x0 + brush - 1, z1 = z0 + brush - 1;
        x0 = Math.max(0, x0); z0 = Math.max(0, z0); x1 = Math.min(n - 1, x1); z1 = Math.min(n - 1, z1);
        if (x0 > x1 || z0 > z1) return;
        applyRect(x0, z0, x1, z1, on);
    }

    private void applyRect(int x0, int z0, int x1, int z1, boolean on) {
        if (!pending.isEmpty() && pendingSet != on) flush();
        pendingSet = on;
        int layer = data.layerY();
        zone.setRect(layer, x0, z0, x1, z1, on);
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) paintCell(x, z);
        // neighbours of the changed block may change their border tint
        for (int z = z0 - 1; z <= z1 + 1; z++) for (int x = x0 - 1; x <= x1 + 1; x++) if (zone.get(layer, x, z)) paintCell(x, z);
        zoneTex.upload();
        if (!pending.isEmpty()) {
            Payloads.Rect last = pending.get(pending.size() - 1);
            if (last.z0() == z0 && last.z1() == z1 && x0 >= last.x0() && x0 <= last.x1() + 1) {
                pending.set(pending.size() - 1, new Payloads.Rect(last.x0(), z0, Math.max(last.x1(), x1), z1));
                return;
            }
            if (last.x0() == x0 && last.x1() == x1 && z0 >= last.z0() && z0 <= last.z1() + 1) {
                pending.set(pending.size() - 1, new Payloads.Rect(x0, last.z0(), x1, Math.max(last.z1(), z1)));
                return;
            }
        }
        pending.add(new Payloads.Rect(x0, z0, x1, z1));
    }

    /** Sends the queued diff to the server. */
    public void flush() {
        if (pending.isEmpty()) return;
        List<Payloads.Rect> out = new ArrayList<>(pending);
        pending.clear();
        sink.send(pendingSet ? Payloads.ZoneEdit.SET : Payloads.ZoneEdit.CLEAR, out);
    }

    /** "Clear all" - empties the layer locally and on the server. */
    public void clearAll() {
        flush();
        zone.clear(data.layerY());
        paintZone();
        sink.send(Payloads.ZoneEdit.CLEAR_ALL, List.of());
    }

    /** Wave J3: empties every layer of the zone (the server drops all of them). */
    public void clearEverything() {
        flush();
        zone.clearAll();
        paintZone();
        sink.send(Payloads.ZoneEdit.CLEAR_EVERYTHING, List.of());
    }

    // ---- drawing ----------------------------------------------------------------------------

    public void render(GuiGraphicsExtractor g, int mx, int my) {
        hover(mx, my);
        int ppc = ppc(), vis = visible(), size = vis * ppc;
        g.fill(x - 1, y - 1, x + VIEW + 1, y + VIEW + 1, 0xFF0C0805);
        g.blit(RenderPipelines.GUI_TEXTURED, mapId, x, y, viewX, viewZ, size, size, vis, vis, n, n);
        g.blit(RenderPipelines.GUI_TEXTURED, ghostId, x, y, viewX, viewZ, size, size, vis, vis, n, n);
        g.blit(RenderPipelines.GUI_TEXTURED, zoneId, x, y, viewX, viewZ, size, size, vis, vis, n, n);
        g.enableScissor(x, y, x + size, y + size);
        if (ppc >= 4) {
            for (int i = 0; i <= vis; i++) {
                boolean major = (viewX + i) % 10 == 0, majorZ = (viewZ + i) % 10 == 0;
                g.fill(x + i * ppc, y, x + i * ppc + 1, y + size, major ? 0x66000000 : 0x2A000000);
                g.fill(x, y + i * ppc, x + size, y + i * ppc + 1, majorZ ? 0x66000000 : 0x2A000000);
            }
            int t = Math.max(2, ppc / 2), off = (ppc - t) / 2;
            for (int cz = viewZ; cz < viewZ + vis && !layered(); cz++) {
                for (int cx = viewX; cx < viewX + vis; cx++) {
                    if (data.flag(cz * n + cx) != Payloads.ZoneMapData.LOG) continue;
                    int px = x + (cx - viewX) * ppc + off, pz = y + (cz - viewZ) * ppc + off;
                    g.fill(px - 1, pz - 1, px + t + 1, pz + t + 1, 0xFF1A0F06);
                    g.fill(px, pz, px + t, pz + t, 0xFF000000 | TREE_TRUNK);
                }
            }
        }
        if (layered()) {   // ore cells get a bright dot so veins stand out at any zoom
            int t = Math.max(2, ppc / 2), off = (ppc - t) / 2;
            for (int cz = viewZ; cz < viewZ + vis; cz++) {
                for (int cx = viewX; cx < viewX + vis; cx++) {
                    if (data.flag(cz * n + cx) != Payloads.ZoneMapData.ORE) continue;
                    int px = x + (cx - viewX) * ppc + off, pz = y + (cz - viewZ) * ppc + off;
                    g.fill(px - 1, pz - 1, px + t + 1, pz + t + 1, 0xFF1A1206);
                    g.fill(px, pz, px + t, pz + t, 0xFF000000 | ORE_DOT);
                }
            }
        }
        if (painting && (tool == Tool.RECT || tool == Tool.ERASE_RECT)) {
            int x0 = Math.min(startX, lastX), x1 = Math.max(startX, lastX), z0 = Math.min(startZ, lastZ), z1 = Math.max(startZ, lastZ);
            int px0 = x + (x0 - viewX) * ppc, pz0 = y + (z0 - viewZ) * ppc, px1 = x + (x1 + 1 - viewX) * ppc, pz1 = y + (z1 + 1 - viewZ) * ppc;
            g.fill(px0, pz0, px1, pz1, tool == Tool.RECT ? 0x4400FF66 : 0x44FF3B30);
            g.outline(px0, pz0, px1 - px0, pz1 - pz0, tool == Tool.RECT ? 0xFF8BFFB0 : 0xFFFF8A80);
        } else if (hoverX >= 0 && contains(mx, my) && (tool == Tool.BRUSH || tool == Tool.ERASER)) {
            int half = brush / 2;
            int px = x + (hoverX - half - viewX) * ppc, pz = y + (hoverZ - half - viewZ) * ppc;
            g.outline(px, pz, brush * ppc, brush * ppc, tool == Tool.BRUSH ? 0xFFFFFFFF : 0xFFFF8A80);
        }
        for (Payloads.MapMarker m : data.markers()) {
            int ix = zone.cellX(m.x()), iz = zone.cellZ(m.z());
            if (ix < viewX - 1 || iz < viewZ - 1 || ix > viewX + vis || iz > viewZ + vis) continue;
            int cx = x + (ix - viewX) * ppc + ppc / 2, cz = y + (iz - viewZ) * ppc + ppc / 2;
            int r = Math.max(2, Math.min(4, ppc));
            int col = m.kind() == Payloads.MapMarker.SITE ? SITE : m.kind() == Payloads.MapMarker.CHEST ? CHEST : WAREHOUSE;
            g.fill(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, 0xFF000000);
            g.fill(cx - r, cz - r, cx + r, cz + r, 0xFF000000 | col);
            if (m.kind() == Payloads.MapMarker.SITE) g.fill(cx - 1, cz - 1, cx + 1, cz + 1, 0xFF8B3A1A);
        }
        g.disableScissor();
        g.outline(x - 1, y - 1, VIEW + 2, VIEW + 2, HhScreen.TRIM_DARK);
    }

    public static int legendColor(int kind) {
        return 0xFF000000 | (kind == 0 ? SITE : kind == 1 ? CHEST : WAREHOUSE);
    }

    public static int zoneRgb() { return 0xFF000000 | ZONE_RGB; }

    public static int blockedRgb() { return 0xFF000000 | BLOCKED_RGB; }

    public static int oreRgb() { return 0xFF000000 | ORE_DOT; }

    public static int trunkRgb() { return 0xFF000000 | TREE_TRUNK; }
}
