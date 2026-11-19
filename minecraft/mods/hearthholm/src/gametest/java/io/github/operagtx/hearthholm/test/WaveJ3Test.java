package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.client.screen.ZoneMapScreen;
import io.github.operagtx.hearthholm.client.screen.ZoneMapWidget;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import io.github.operagtx.hearthholm.zone.MineGuard;
import io.github.operagtx.hearthholm.zone.WorkZone;
import io.github.operagtx.hearthholm.zone.ZoneMapService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave J3: the miner's stone and its drawn 3D dig plan. Layer snapshot, painting through the real payload, the access stair and an
 * L-shaped tunnel dug in full, a vein crossing it, a protected cell, falling gravel, clearing a stone hill above ground, the empty-zone
 * fallback to the automatic spiral, persistence, a stranger's edit, and the map screen (RU/EN, two window sizes). Run alone (plus the
 * base) with HH_ONLY_J3=1.
 */
final class WaveJ3Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos M, LEDGER;
    private static UUID MINER;
    private static int PROT_SOLID;
    private static final Set<BlockPos> PROT_AIR = new HashSet<>();
    private static final int CELL0 = 25;   // the marker sits in cell (25, 25) of the 50 x 50 grid

    private WaveJ3Test() {}

    private static void fail(String m) { FAILS.add(m); log("J3 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static SettlerEntity miner(MinecraftServer s) { return (SettlerEntity) s.overworld().getEntity(MINER); }

    private static WorkSiteBlockEntity site(MinecraftServer s) { return (WorkSiteBlockEntity) s.overworld().getBlockEntity(M); }

    private static Payloads.Rect rect(int x0, int z0, int x1, int z1) { return new Payloads.Rect(x0, z0, x1, z1); }

    private static Payloads.ZoneEdit paint(int layer, int h, Payloads.Rect... r) { return new Payloads.ZoneEdit(M, layer, Payloads.ZoneEdit.SET, List.of(r), h); }

    private static void setOwner(Settlement st, UUID id) {
        try {
            var f = Settlement.class.getField("owner");
            f.setAccessible(true);
            f.set(st, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setLang(ClientGameTestContext ctx, String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
        });
        var fut = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
        ctx.waitFor(mc -> fut.isDone(), 2400);
        ctx.waitTicks(120);
    }

    private static void setWindow(ClientGameTestContext ctx, int w, int h) {
        ctx.getInput().resizeWindow(w, h);
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.waitTicks(8);
    }

    private static int await(ClientGameTestContext ctx, TestSingleplayerContext sp, int maxTicks, Predicate<MinecraftServer> cond) {
        for (int t = 0; t <= maxTicks; t += 20) {
            AtomicBoolean ok = new AtomicBoolean();
            sp.getServer().runOnServer(s -> ok.set(cond.test(s)));
            if (ok.get()) return t;
            ctx.waitTicks(20);
        }
        return -1;
    }

    private static boolean solid(ServerLevel l, BlockPos p) {
        var st = l.getBlockState(p);
        return !st.getCollisionShape(l, p).isEmpty() || !st.getFluidState().isEmpty();
    }

    /** [dug positions of the plan, of those air, of those protected, protected ones still solid, ore left in the plan's area]. */
    private static int[] audit(MinecraftServer s) {
        ServerLevel l = s.overworld();
        WorkSiteBlockEntity w = site(s);
        MinePlanner.Plan plan = MinePlanner.plan(w);
        MineGuard g = MineGuard.of(l, w, town(s));
        int air = 0, prot = 0, protSolid = 0, open = 0, unprot = 0;
        for (BlockPos p : plan.dug()) {
            boolean b = g.blocked(p);
            if (b) { prot++; if (solid(l, p)) protSolid++; }
            else { unprot++; if (!solid(l, p)) air++; }
        }
        return new int[]{plan.dug().size(), air, prot, protSolid, unprot};
    }


    /** Paints with REAL simulated mouse input (cursor moves, button held and released) over the open map screen; returns the server's cell count of the site. */
    private static int realPaint(ClientGameTestContext ctx, TestSingleplayerContext sp, BlockPos site, String shot) {
        ctx.waitTicks(14);
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), site));
        ctx.waitForScreen(ZoneMapScreen.class);
        ctx.waitTicks(10);
        double[] g = ctx.computeOnClient(mc -> {
            ZoneMapWidget wd = ((ZoneMapScreen) mc.gui.screen()).widget();
            return new double[]{wd.x, wd.y, wd.ppc(), mc.getWindow().getGuiScale()};
        });
        double sc = g[3], ppc = g[2];
        var in = ctx.getInput();
        // 1. rectangle tool: drag a 6 x 4 cell rectangle
        ctx.runOnClient(mc -> ((ZoneMapScreen) mc.gui.screen()).widget().setTool(ZoneMapWidget.Tool.RECT));
        in.setCursorPos((g[0] + 8 * ppc + 1) * sc, (g[1] + 8 * ppc + 1) * sc);
        ctx.waitTicks(3);
        in.holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        for (int i = 0; i < 5; i++) { in.moveCursor(ppc * sc * 1.2, ppc * sc * 0.8); ctx.waitTicks(2); }
        in.releaseMouse(0);
        ctx.waitTicks(12);
        AtomicInteger afterRect = new AtomicInteger();
        sp.getServer().runOnServer(s -> afterRect.set(((WorkSiteBlockEntity) s.overworld().getBlockEntity(site)).zone() == null ? 0 : ((WorkSiteBlockEntity) s.overworld().getBlockEntity(site)).zone().cellCount()));
        // 2. brush stroke
        ctx.runOnClient(mc -> { var w = ((ZoneMapScreen) mc.gui.screen()).widget(); w.setTool(ZoneMapWidget.Tool.BRUSH); w.setBrush(3); });
        in.setCursorPos((g[0] + 20 * ppc + 1) * sc, (g[1] + 20 * ppc + 1) * sc);
        ctx.waitTicks(3);
        in.holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        for (int i = 0; i < 8; i++) { in.moveCursor(ppc * sc * 1.5, ppc * sc * 0.5); ctx.waitTicks(2); }
        in.releaseMouse(0);
        ctx.waitTicks(12);
        AtomicInteger afterBrush = new AtomicInteger();
        sp.getServer().runOnServer(s -> afterBrush.set(((WorkSiteBlockEntity) s.overworld().getBlockEntity(site)).zone() == null ? 0 : ((WorkSiteBlockEntity) s.overworld().getBlockEntity(site)).zone().cellCount()));
        log("J3 real mouse painting at " + site.toShortString() + ": scale=" + sc + " ppc=" + ppc + " server cells after rectangle=" + afterRect.get() + " after brush=" + afterBrush.get());
        check(afterRect.get() > 0, "real mouse: the rectangle drag painted nothing on the server (" + site.toShortString() + ")");
        check(afterBrush.get() > afterRect.get(), "real mouse: the brush stroke painted nothing more on the server (" + site.toShortString() + ")");
        if (shot != null) ctx.takeScreenshot(shot);
        ctx.setScreen(() -> null);
        ctx.waitTicks(6);
        return afterBrush.get();
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");
        sp.getServer().runCommand("gamerule doDaylightCycle false");
        M = O.offset(30, 0, 30).atY(O.getY() + 70);
        LEDGER = M.offset(9, 0, 3);
        AtomicReference<Payloads.ZoneMapData> snap = new AtomicReference<>();
        Balance.BREAK_PACE_PERCENT = 25;   // test-only acceleration (4 ticks per block at the floor); the I1 speed rules themselves are unchanged

        // ---- 0. set-up: a stone block in the sky with a grass top, the miner's stone + chest, ore, gravel, a stone hill, a warehouse ledger
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, M.getX() + 0.5, M.getY() + 1, M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            topUp(s, 7);
            Settlement st = town(s);
            for (Settlement.SiteRecord r : new ArrayList<>(st.sitesOf(WorkSiteTypes.MINE_ID))) {
                if (!r.pos().equals(M) && l.isLoaded(r.pos())) l.removeBlock(r.pos(), false);   // the earlier waves' mines: this wave owns the only one
            }
            for (int dx = -26; dx <= 26; dx++) for (int dz = -4; dz <= 30; dz++) {
                for (int dy = -14; dy <= -2; dy++) l.setBlock(M.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                l.setBlock(M.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= 12; dy++) l.setBlock(M.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            l.setBlock(M, ModBlocks.MINE_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, M, p);
            l.setBlock(M.east(), Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(M.east()) instanceof Container c) {
                c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                c.setItem(1, new ItemStack(Items.TORCH, 48));
                c.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
            }
            // a warehouse ledger on the ground east of the first leg: the cells below it must stay untouched
            l.setBlock(LEDGER, ModBlocks.WAREHOUSE_LEDGER.defaultBlockState(), 3);
            st.ledgers.add(LEDGER);
            // ore: a vein across the second leg, single ores elsewhere (for the map)
            int ly = M.getY() - 3;
            for (int x = 9; x <= 20; x++) l.setBlock(new BlockPos(M.getX() + x, ly, M.getZ() + 15), Blocks.IRON_ORE.defaultBlockState(), 2);
            l.setBlock(new BlockPos(M.getX() - 8, ly, M.getZ() + 8), Blocks.COAL_ORE.defaultBlockState(), 2);
            l.setBlock(new BlockPos(M.getX() - 8, ly, M.getZ() + 9), Blocks.COAL_ORE.defaultBlockState(), 2);
            l.setBlock(new BlockPos(M.getX() - 6, ly, M.getZ() + 14), Blocks.GOLD_ORE.defaultBlockState(), 2);
            l.setBlock(new BlockPos(M.getX() - 12, ly, M.getZ() + 4), Blocks.DIAMOND_ORE.defaultBlockState(), 2);
            // gravel and sand in the roof layer above leg 2: they fall into the tunnel when the block under them is dug
            l.setBlock(new BlockPos(M.getX() + 14, M.getY() - 1, M.getZ() + 20), Blocks.GRAVEL.defaultBlockState(), 2);
            l.setBlock(new BlockPos(M.getX() + 15, M.getY() - 1, M.getZ() + 22), Blocks.SAND.defaultBlockState(), 2);
            // the hill: a 6 x 6 x 3 pile of stone on the grass, west of the marker, aligned to the 2 x 2 cell grid
            for (int dx = -16; dx <= -11; dx++) for (int dz = 4; dz <= 9; dz++) for (int dy = 0; dy <= 2; dy++)
                l.setBlock(M.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
            List<SettlerEntity> all = settlers(s);
            SettlerEntity j = all.get(0);
            for (SettlerEntity e : all) if (e != j) e.setProfession(Professions.UNEMPLOYED);
            MINER = j.getUUID();
            j.setProfession(Professions.MINER);
            j.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            j.inventory().clearContent();
            j.markArrived();
            j.getNavigation().stop();
            j.teleportTo(M.getX() + 0.5, M.getY(), M.getZ() + 2.5);
            WorkSiteBlockEntity w = site(s);
            check(w != null && st.sitesOf(WorkSiteTypes.MINE_ID).size() == 1, "set-up: the miner's stone is not the settlement's only mine site");
            check(!w.hasZone() && w.option("plan") == 0 && w.option("mode") == 0, "fresh stone: zone, plan or mode not default");
            var m = io.github.operagtx.hearthholm.api.ProfessionModules.of(Professions.MINER);
            check(m.zone() != null && m.zone().kind() == io.github.operagtx.hearthholm.api.ProfessionModule.ZoneData.Kind.MAP
                    && m.screenOpener() != io.github.operagtx.hearthholm.api.ProfessionModule.SiteScreenOpener.GENERIC, "miner module is not a map module");
            WorkZone.Spec spec = WorkZone.specOf(WorkSiteTypes.MINE_ID);
            check(spec != null && spec.size() == 50 && spec.cell() == 2 && spec.layered(), "miner zone spec is not 50x50 cells of 2 blocks, layered");
            log("J3 set-up: marker " + M.toShortString() + " ledger " + LEDGER.toShortString() + " miner " + j.getName().getString());
        });
        ctx.waitTicks(10);
        sp.getServer().runCommand("gamemode survival @a");

        // ---- 1. snapshot at a given Y: colours, ore flags, dark air, ghosts, markers, summary, wire
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            WorkSiteBlockEntity w = site(s);
            int ly = M.getY() - 3;
            int n = 50;
            Payloads.ZoneMapData d = ZoneMapService.snapshot(l, w, st, ly);
            snap.set(d);
            // cell (ix, iz) holds blocks origin + 2*ix .. +1; origin = M - 50
            int coalCell = ((M.getZ() + 8 - (M.getZ() - 50)) / 2) * n + ((M.getX() - 8 - (M.getX() - 50)) / 2);
            int stoneCell = (CELL0 + 12) * n + (CELL0 - 8);
            int airCell = (CELL0 - 4) * n + (CELL0 + 3);   // north of the platform: nothing there
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), l.registryAccess());
            Payloads.ZoneMapData.CODEC.encode(buf, d);
            int wire = buf.readableBytes();
            Payloads.ZoneMapData back = Payloads.ZoneMapData.CODEC.decode(buf);
            log("J3 snapshot: layer=" + d.layerY() + " size=" + d.size() + " cell=" + d.cell() + " stone=" + (d.colors()[stoneCell] & 0xFF) + " coal=" + (d.colors()[coalCell] & 0xFF) + "/flag " + d.flag(coalCell)
                    + " air=" + (d.colors()[airCell] & 0xFF) + " info=" + d.info() + " wire=" + wire);
            check(d.layerY() == ly && d.size() == 50 && d.cell() == 2 && d.colors().length == 2500 && d.flags().length == 625, "layer snapshot dimensions");
            check((d.colors()[stoneCell] & 0xFF) >> 2 == MapColor.STONE.id, "stone expected in the cell at Y-3 (got id " + ((d.colors()[stoneCell] & 0xFF) >> 2) + ")");
            check(d.flag(coalCell) == Payloads.ZoneMapData.ORE, "coal ore cell is not flagged as ore");
            check(d.flag(stoneCell) == 0 && (d.colors()[airCell] & 0xFF) == 0, "stone cell flagged or air cell not dark");
            check(d.info().markerY() == M.getY() && d.info().minY() == l.getMinY() && d.info().maxY() == l.getMaxY() && d.info().cells() == 0 && d.info().blocks() == 0, "snapshot info wrong: " + d.info());
            check(wire < 12000 && wire > 3000, "layer snapshot is not a few KB: " + wire);
            check(java.util.Arrays.equals(back.colors(), d.colors()) && java.util.Arrays.equals(back.flags(), d.flags()) && back.info().equals(d.info()) && back.layerY() == ly, "layer snapshot did not survive the wire codec");
            // the marker's own layer on open: the screen opens at the marker's Y
            ZoneMapService.served = 0;
            boolean opened = ZoneMapService.open(p, w, st);
            check(opened, "opening the map was refused");
            // the map above the ground shows the grass top one layer lower
            Payloads.ZoneMapData top = ZoneMapService.snapshot(l, w, st, M.getY() - 1);
            check((top.colors()[stoneCell] & 0xFF) >> 2 == MapColor.GRASS.id, "grass expected at Y-1");
        });
        ctx.waitForScreen(ZoneMapScreen.class);
        int openedLayer = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.widget().layer() : -999);
        log("J3 map opens at layer " + openedLayer + " (marker Y " + M.getY() + ")");
        check(openedLayer == M.getY(), "the map did not open at the marker's Y");
        ctx.setScreen(() -> null);

        // ---- 2. paint an L-shaped tunnel two layers down through the real payload; validation; persistence
        int LY = M.getY() - 3;
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            WorkSiteBlockEntity w = site(s);
            // leg 1 east along row 28, leg 2 south along column 32, a 1-cell spur to the ledger
            boolean a = ZoneMapService.handleEdit(p, paint(LY, 2, rect(25, 28, 32, 28)));
            boolean b = ZoneMapService.handleEdit(p, paint(LY, 2, rect(32, 29, 32, 36)));
            boolean c = ZoneMapService.handleEdit(p, paint(LY, 2, rect(29, 26, 29, 27)));   // under the ledger: protected
            WorkZone z = w.zone();
            log("J3 paint: edits " + a + b + c + " cells=" + z.cellCount() + " layers=" + z.layerKeys() + " plan option=" + w.option("plan") + " height at (30,28)=" + (z != null ? z.height(LY, 30, 28) : -1));
            check(a && b && c, "owner paint edits were rejected");
            check(z.cellCount() == 8 + 8 + 2 && z.layerKeys().equals(List.of(LY)), "L tunnel cell count wrong: " + z.cellCount());
            check(w.option("plan") == 1, "the first painted cells did not switch the miner to the drawn plan");
            check(z.contains(M.getX() + 14, LY, M.getZ() + 10) && !z.contains(M.getX() + 14, LY + 3, M.getZ() + 10), "zone membership wrong");
            // heights: a 4-high and a 1-high cell and back to the default
            check(ZoneMapService.handleEdit(p, paint(LY, 4, rect(10, 10, 10, 10))) && z.height(LY, 10, 10) == 4, "height 4 not stored");
            check(ZoneMapService.handleEdit(p, paint(LY, 1, rect(11, 10, 11, 10))) && z.height(LY, 11, 10) == 1, "height 1 not stored");
            check(ZoneMapService.handleEdit(p, paint(LY, 0, rect(12, 10, 12, 10))) && z.height(LY, 12, 10) == 2, "height 0 should mean the default 2");
            check(ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, LY, Payloads.ZoneEdit.CLEAR, List.of(rect(10, 10, 12, 10)))), "erase edit rejected");
            check(z.cellCount() == 18 && z.height(LY, 10, 10) == 2, "erased cell kept a height or the count is wrong");
            // validation: layer out of the world, bad height, bad op, stranger
            int rej0 = ZoneMapService.rejected;
            boolean lowY = ZoneMapService.handleEdit(p, paint(l.getMinY() - 5, 2, rect(1, 1, 2, 2)));
            boolean highY = ZoneMapService.handleEdit(p, paint(l.getMaxY() + 5, 2, rect(1, 1, 2, 2)));
            boolean badH = ZoneMapService.handleEdit(p, paint(LY, 7, rect(1, 1, 2, 2)));
            boolean badOp = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, LY, 9, List.of(rect(1, 1, 2, 2))));
            boolean surfaceLayer = ZoneMapService.handleEdit(p, paint(WorkZone.SURFACE, 2, rect(1, 1, 2, 2)));
            Settlement st = town(s);
            UUID real = st.owner;
            setOwner(st, UUID.randomUUID());
            boolean stranger = ZoneMapService.handleEdit(p, paint(LY, 2, rect(1, 1, 5, 5)));
            boolean strangerClear = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, LY, Payloads.ZoneEdit.CLEAR_EVERYTHING, List.of()));
            setOwner(st, real);
            log("J3 validation: lowY=" + lowY + " highY=" + highY + " height=" + badH + " op=" + badOp + " surfaceLayer=" + surfaceLayer + " stranger=" + stranger + "/" + strangerClear
                    + " rejected+" + (ZoneMapService.rejected - rej0) + " cells=" + z.cellCount());
            check(!lowY && !highY && !badH && !badOp && !stranger && !strangerClear, "an invalid edit was accepted");
            check(z.cellCount() == 18 && ZoneMapService.rejected - rej0 >= 6, "rejected edits changed the zone or were not counted");
            // persistence: heights and the plan option survive save and reload
            check(ZoneMapService.handleEdit(p, paint(LY - 1, 3, rect(20, 20, 21, 21))), "paint on a second layer failed");
            var tag = w.saveCustomOnly(l.registryAccess());
            WorkSiteBlockEntity w2 = new WorkSiteBlockEntity(M, l.getBlockState(M));
            w2.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess(), tag));
            WorkZone z2 = w2.zone();
            log("J3 persisted: tag " + tag.toString().length() + " chars, reloaded cells=" + (z2 == null ? -1 : z2.cellCount()) + " layers=" + (z2 == null ? null : z2.layerKeys())
                    + " height(LY-1,20,20)=" + (z2 == null ? -1 : z2.height(LY - 1, 20, 20)) + " plan=" + w2.option("plan"));
            check(z2 != null && z2.cellCount() == 22 && z2.layerKeys().equals(List.of(LY - 1, LY)) && z2.height(LY - 1, 20, 20) == 3 && z2.height(LY, 28, 28) == 2 && w2.option("plan") == 1,
                    "the zone, its heights or the plan option were lost on save/reload");
            check(z2 != null && z2.contentHash() == z.contentHash(), "content hash differs after reload");
            // the second layer was only for the persistence check
            ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, LY - 1, Payloads.ZoneEdit.CLEAR_ALL, List.of()));
            check(z.cellCount() == 18 && z.layerKeys().equals(List.of(LY)), "clear layer did not remove the second layer");
            // snapshot with the painted layer: summary counts, ghost bits one layer up, the blocked flag under the ledger
            Payloads.ZoneMapData d = ZoneMapService.snapshot(l, w, st, LY);
            Payloads.ZoneMapData above = ZoneMapService.snapshot(l, w, st, LY + 1);
            int spurCell = 26 * 50 + 29, legCell = 28 * 50 + 30;
            log("J3 painted snapshot: info=" + d.info() + " flag(spur)=" + d.flag(spurCell) + " flag(leg)=" + d.flag(legCell) + " ghostDown(above layer) has leg=" + ((above.ghostDown()[legCell >> 6] >> (legCell & 63) & 1) != 0));
            check(d.info().cells() == 18 && d.info().blocks() > 18 * 8 && d.info().seconds() > 0, "summary wrong: " + d.info());
            check(d.flag(spurCell) == Payloads.ZoneMapData.BLOCKED && d.flag(legCell) != Payloads.ZoneMapData.BLOCKED, "the cell under the ledger is not marked red");
            check(java.util.Arrays.stream(above.ghostDown()).anyMatch(v -> v != 0), "layer above the painted one has no ghost-down bits");
            // the guard on an injected house record (no rescan in this window)
            st.buildings.add(new BuildingRecord(BuildingRecord.HOUSING, M.offset(-20, 0, 20), List.of(M.offset(-20, 0, 21)), List.of(), 1, 20, "", List.of(), 0));
            MineGuard g = MineGuard.of(l, w, st);
            check(g.blocked(M.offset(-20, -5, 20)) && g.blocked(M.offset(-20, 1, 21)) && !g.blocked(M.offset(-20, 12, 20)) && g.blocked(M.getX(), M.getY() - 4, M.getZ()) && !g.blocked(M.offset(3, -4, 3)),
                    "mine guard boxes wrong (house below/above, marker column, free cell)");
            st.buildings.remove(st.buildings.size() - 1);
            MinePlanner.Plan plan = MinePlanner.plan(w);
            int torches = 0, floors = 0, digs = 0;
            for (var stp : plan.steps()) { if (stp.kind() == MinePlanner.Kind.TORCH) torches++; else if (stp.kind() == MinePlanner.Kind.FLOOR) floors++; else if (stp.kind() == MinePlanner.Kind.DIG) digs++; }
            log("J3 plan: mode=" + plan.mode() + " steps=" + plan.steps().size() + " digs=" + digs + " torches=" + torches + " floors=" + floors + " dug=" + plan.dug().size());
            check(plan.mode() == MinePlanner.DRAWN && digs == plan.dug().size() && torches >= 2, "drawn plan has no torches or wrong mode");
            PROT_SOLID = audit(s)[3];
            MineGuard g0 = MineGuard.of(l, w, st);
            for (BlockPos q : plan.dug()) if (g0.blocked(q) && solid(l, q)) PROT_AIR.add(q.immutable());
            // the first steps are the access stair: a slope from the marker down to the tunnel level, 2 wide
            boolean stairFirst = !plan.steps().isEmpty() && plan.steps().getFirst().kind() == MinePlanner.Kind.DIG && plan.steps().getFirst().pos().getY() >= M.getY() - 1
                    && plan.steps().getFirst().pos().distManhattan(M) <= 4;
            check(stairFirst, "the plan does not start with the access stair next to the marker: " + plan.steps().getFirst());
            Set<Integer> stairYs = new HashSet<>();
            for (BlockPos q : plan.dug()) if (q.getZ() <= M.getZ() + 5 && Math.abs(q.getX() - M.getX()) <= 1 && q.getZ() > M.getZ()) stairYs.add(q.getY());
            check(stairYs.contains(M.getY() - 1) && stairYs.contains(M.getY() - 2) && stairYs.contains(LY), "the access stair does not descend 1:1 to the tunnel layer: " + stairYs);
        });
        ctx.waitTicks(10);

        // ---- 3. the miner digs the stair and every painted cell; the vein is mined whole; the protected cell is skipped; gravel is cleared
        sp.getServer().runOnServer(s -> {
            site(s).setProgress(0);
            miner(s).markArrived();
            log("J3 digging: started");
        });
        long[] t0 = {System.currentTimeMillis()};
        int tDone = await(ctx, sp, 9000, s -> {
            WorkSiteBlockEntity w = site(s);
            if (w == null || w.progress() < MinePlanner.plan(w).steps().size()) return false;
            int[] a = audit(s);
            return a[1] == a[4];
        });
        int[] au = new int[5];
        sp.getServer().runOnServer(s -> {
            int[] a = audit(s);
            System.arraycopy(a, 0, au, 0, 5);
        });
        log("J3 digging: plan finished after " + tDone + " ticks (" + (System.currentTimeMillis() - t0[0]) / 1000 + " s): plan blocks=" + au[0] + " dug(air)=" + au[1] + " of " + au[4] + " unprotected; protected=" + au[2] + " still solid=" + au[3]);
        check(tDone >= 0, "the miner did not finish the drawn plan in time");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            WorkSiteBlockEntity w = site(s);
            int[] a = audit(s);
            // let sand and gravel settle, then the finished plan is rechecked by the miner itself; here we check the end state
            int ore = 0, ironLeft = 0;
            for (int x = 9; x <= 20; x++) if (l.getBlockState(new BlockPos(M.getX() + x, LY, M.getZ() + 15)).is(Blocks.IRON_ORE)) ironLeft++;
            int gravel = 0;
            for (BlockPos q : BlockPos.betweenClosed(M.offset(10, -4, 4), M.offset(18, 0, 24))) if (l.getBlockState(q).is(Blocks.GRAVEL) || l.getBlockState(q).is(Blocks.SAND)) gravel++;
            int ironItems = 0;
            for (ItemStack st : miner(s).inventory().getItems()) if (st.is(Items.RAW_IRON)) ironItems += st.getCount();
            if (l.getBlockEntity(M.east()) instanceof Container c) for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.RAW_IRON)) ironItems += c.getItem(i).getCount();
            log("J3 result: air " + a[1] + "/" + a[4] + ", protected solid " + a[3] + "/" + a[2] + ", iron ore left in the vein=" + ironLeft + " (raw iron collected " + ironItems + "), gravel/sand left in the work area=" + gravel
                    + ", ledger intact=" + l.getBlockState(LEDGER).is(ModBlocks.WAREHOUSE_LEDGER) + ", status=" + w.status());
            check(a[1] == a[4], "not every unprotected plan block is air: " + a[1] + "/" + a[4]);
            int keptSolid = 0;
            for (BlockPos q : PROT_AIR) if (solid(l, q)) keptSolid++;
            log("J3 protected: " + PROT_AIR.size() + " solid blocks inside protected places before, still solid after " + keptSolid + " (audit " + a[3] + "/" + a[2] + ")");
            check(!PROT_AIR.isEmpty() && keptSolid == PROT_AIR.size(), "protected blocks were dug: " + keptSolid + " of " + PROT_AIR.size() + " still solid");
            check(ironLeft == 0 && ironItems >= 8, "the iron vein crossing the tunnel was not mined whole (left " + ironLeft + ", raw iron " + ironItems + ")");
            check(l.getBlockState(LEDGER).is(ModBlocks.WAREHOUSE_LEDGER), "the ledger was destroyed");
            // gravel handling: the roof gravel/sand fell and was dug out of the tunnel (no loose gravel inside the plan)
            boolean looseInPlan = false;
            for (BlockPos q : MinePlanner.plan(w).dug()) if (l.getBlockState(q).is(Blocks.GRAVEL) || l.getBlockState(q).is(Blocks.SAND)) looseInPlan = true;
            check(!looseInPlan, "sand or gravel is still lying in the dug tunnel");
        });

        // ---- 4. an above-ground hill (a stone pile) is painted with height 3 and cleared completely
        AtomicInteger hillBefore = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(M.offset(-16, 0, 4), M.offset(-11, 2, 9))) if (l.getBlockState(q).is(Blocks.STONE)) n++;
            hillBefore.set(n);
            boolean ok = ZoneMapService.handleEdit(p, paint(M.getY(), 3, rect(17, 27, 19, 29)));
            WorkSiteBlockEntity w = site(s);
            MinePlanner.Plan plan = MinePlanner.plan(w);
            boolean stairToHill = false;
            for (BlockPos q : plan.dug()) if (q.getX() < M.getX() - 9 && q.getY() < M.getY()) stairToHill = true;
            log("J3 hill: stone blocks=" + n + " painted=" + ok + " plan blocks=" + plan.dug().size() + " (a stair below the hill: " + stairToHill + ")");
            check(ok && n == 108, "hill set-up wrong: " + n);
            check(!stairToHill, "an above-ground hill got an access stair");
            w.setProgress(0);
        });
        int tHill = await(ctx, sp, 7200, s -> {
            ServerLevel l = s.overworld();
            for (BlockPos q : BlockPos.betweenClosed(M.offset(-16, 0, 4), M.offset(-11, 2, 9))) if (!l.getBlockState(q).isAir()) return false;
            return true;
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            int left = 0;
            for (BlockPos q : BlockPos.betweenClosed(M.offset(-16, 0, 4), M.offset(-11, 2, 9))) if (!l.getBlockState(q).isAir()) left++;
            int stone = 0;
            for (ItemStack st : miner(s).inventory().getItems()) if (st.is(Items.COBBLESTONE)) stone += st.getCount();
            if (l.getBlockEntity(M.east()) instanceof Container c) for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.COBBLESTONE)) stone += c.getItem(i).getCount();
            log("J3 hill result: cleared after " + tHill + " ticks, blocks left=" + left + " cobblestone in pockets/chest=" + stone + " ground below intact=" + !l.getBlockState(M.offset(-14, -1, 6)).isAir());
            check(tHill >= 0 && left == 0, "the hill was not cleared completely (left " + left + ")");
            check(!l.getBlockState(M.offset(-14, -1, 6)).isAir(), "the ground under the hill was dug");
        });

        // ---- 5. an empty zone: the automatic spiral works exactly as before
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            WorkSiteBlockEntity w = site(s);
            boolean ok = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, LY, Payloads.ZoneEdit.CLEAR_EVERYTHING, List.of()));
            log("J3 cleared: ok=" + ok + " hasZone=" + w.hasZone() + " zoneNull=" + (w.zone() == null) + " plan option=" + w.option("plan"));
            check(ok && !w.hasZone() && w.zone() == null, "clear everything did not drop the zone");
            w.setOption("plan", 0);
            w.setOption("mode", 0);
            w.setOption("depth", 6);
            w.setOption("branches", 0);
            w.setProgress(0);
            MinePlanner.Plan auto = MinePlanner.plan(w);
            check(auto.mode() == MinePlanner.SPIRAL && auto.autoFrom() < 0, "empty zone: the plan is not the automatic spiral");
            // an empty zone with the drawn plan chosen also falls back to the automatic layout
            w.setOption("plan", 1);
            check(MinePlanner.plan(w).mode() == MinePlanner.SPIRAL, "plan=drawn with an empty zone must behave as auto");
            w.setOption("plan", 0);
            // fresh ground for the spiral to dig
            miner(s).teleportTo(M.getX() + 0.5, M.getY(), M.getZ() - 1.5);
            w.setProgress(0);
        });
        int tSpiral = await(ctx, sp, 2400, s -> {
            ServerLevel l = s.overworld();
            WorkSiteBlockEntity w = site(s);
            MinePlanner.Plan auto = MinePlanner.plan(w);
            int air = 0, n = 0;
            for (var stp : auto.steps()) {
                if (stp.kind() != MinePlanner.Kind.DIG || n++ >= 40) continue;
                if (!solid(l, stp.pos())) air++;
            }
            return air >= 40;
        });
        log("J3 spiral: the first 40 spiral steps dug after " + tSpiral + " ticks");
        check(tSpiral >= 0, "with an empty zone the automatic spiral no longer digs");

        // ---- 6. the map screen: the miner's stone with painted layers, the slider and both layouts, RU/EN at two window sizes
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SettlerEntity j = miner(s);
            j.setProfession(Professions.UNEMPLOYED);   // the screens are the point now: no more digging
            WorkSiteBlockEntity w = site(s);
            w.setOption("plan", 1);
            p.teleportTo(l, M.getX() + 0.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            // a nice drawing: the L tunnel at the tunnel layer, a room one layer lower, a ramp one layer higher, a hill pad on the surface layer
            ZoneMapService.handleEdit(p, paint(LY, 2, rect(25, 28, 32, 28), rect(32, 29, 32, 36), rect(29, 26, 29, 27)));
            ZoneMapService.handleEdit(p, paint(LY - 1, 2, rect(31, 33, 34, 36), rect(20, 30, 24, 33)));
            ZoneMapService.handleEdit(p, paint(LY + 1, 2, rect(26, 22, 28, 28), rect(33, 30, 33, 32)));
            ZoneMapService.handleEdit(p, paint(LY, 3, rect(14, 31, 17, 35), rect(36, 24, 40, 27)));
            check(w.zone().layerKeys().equals(List.of(LY - 1, LY, LY + 1)), "screenshot zone layers: " + w.zone().layerKeys());
            log("J3 screenshot zone: " + w.zone().cellCount() + " cells in " + w.zone().layerKeys().size() + " layers");
            // the view layer is the tunnel layer: pick it with the slider code path through a request
        });
        try {
            setLang(ctx, "ru_ru");
        } catch (Throwable t) {
            fail("could not switch the language: " + t);
        }
        for (int[] size : new int[][]{{1280, 720}, {854, 480}}) {
            setWindow(ctx, size[0], size[1]);
            ctx.waitTicks(14);
            sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), M));
            ctx.waitForScreen(ZoneMapScreen.class);
            ctx.waitTicks(6);
            // jump to the tunnel layer through the screen's own layer request (what the slider and the +/- buttons send)
            ctx.waitTicks(12);
            ctx.runOnClient(mc -> ((ZoneMapScreen) mc.gui.screen()).showLayer(LY));
            ctx.waitTicks(40);
            List<String> cut = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.truncatedLabels() : List.of("not the map screen"));
            int shown = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.widget().layer() : -999);
            log("J3 map screen RU " + size[0] + "x" + size[1] + ": layer shown=" + shown + " truncated labels=" + cut);
            check(cut.isEmpty(), "RU map screen has cut labels " + cut + " at " + size[0] + "x" + size[1]);
            check(shown == LY, "the layer request did not change the map layer: " + shown);
            ctx.takeScreenshot("hh_j3_map_ru_" + size[0] + "x" + size[1]);
            if (size[0] == 1280) {
                // the automatic layout page (old options), once
                sp.getServer().runOnServer(s -> site(s).setOption("plan", 0));
                ctx.waitTicks(30);
                List<String> cutA = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.truncatedLabels() : List.of("not the map screen"));
                log("J3 map screen RU auto page: truncated labels=" + cutA);
                check(cutA.isEmpty(), "RU auto page has cut labels " + cutA);
                ctx.takeScreenshot("hh_j3_map_auto_ru");
                sp.getServer().runOnServer(s -> site(s).setOption("plan", 1));
                ctx.waitTicks(20);
            }
            ctx.setScreen(() -> null);
            ctx.waitTicks(10);
        }
        setLang(ctx, "en_us");
        setWindow(ctx, 854, 480);
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), M));
        ctx.waitForScreen(ZoneMapScreen.class);
        ctx.waitTicks(8);
        List<String> cutEn = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.truncatedLabels() : List.of("not the map screen"));
        log("J3 map screen EN 854x480: truncated labels=" + cutEn);
        check(cutEn.isEmpty(), "EN map screen has cut labels " + cutEn);

        // ---- 7. the client widget end to end: a 3-high rectangle painted with the widget reaches the server with its height
        AtomicInteger before = new AtomicInteger();
        sp.getServer().runOnServer(s -> before.set(site(s).zone().cellCount()));
        ctx.runOnClient(mc -> {
            ZoneMapWidget wd = ((ZoneMapScreen) mc.gui.screen()).widget();
            wd.setHeight(3);
            wd.setTool(ZoneMapWidget.Tool.RECT);
            int ppc = wd.ppc();
            double x0 = wd.x + 5 * ppc + 1, z0 = wd.y + 5 * ppc + 1;
            wd.mouseClicked(x0, z0, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            wd.mouseDragged(x0 + 3 * ppc, z0 + 1 * ppc, 0, 3 * ppc, ppc);
            wd.mouseReleased(x0 + 3 * ppc, z0 + 1 * ppc, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);   // 4 x 2 cells at the layer shown
        });
        ctx.waitTicks(12);
        AtomicInteger after = new AtomicInteger(), hgt = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            after.set(site(s).zone().cellCount());
            hgt.set(site(s).zone().height(M.getY(), 6, 5));
        });
        log("J3 widget paint over the network: cells " + before.get() + " -> " + after.get() + ", height of a new cell " + hgt.get());
        check(after.get() == before.get() + 8 && hgt.get() == 3, "client widget paint did not reach the server with its height: " + before.get() + " -> " + after.get() + " h=" + hgt.get());
        ctx.setScreen(() -> null);

        // ---- 7b. REAL simulated mouse input on both maps (the lumberjack's chopping block and the miner's stone)
        BlockPos forest = M.offset(-20, 0, 14);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            l.setBlock(forest, ModBlocks.FOREST_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, forest, p);
            p.teleportTo(l, forest.getX() + 0.5, forest.getY(), forest.getZ() - 3.5, Set.of(), 0f, 10f, false);
        });
        realPaint(ctx, sp, forest, "hh_j3_real_mouse_lumberjack");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            site(s).zoneForEdit().clearAll();
            site(s).zoneChanged();
            p.teleportTo(l, M.getX() + 0.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
        });
        realPaint(ctx, sp, M, null);

        // ---- 8. the stone in the world, then the dug L tunnel from inside
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, M.getX() + 0.9, M.getY(), M.getZ() - 2.6, Set.of(), 15f, 24f, false);
        });
        setWindow(ctx, 854, 480);
        ctx.runOnClient(mc -> mc.options.guiScale().set(0));
        ctx.waitTicks(30);
        ctx.takeScreenshot("hh_j3_stone");
        // a cross-section: the grass roof above the second leg is lifted so the lit tunnel can be seen from above (spectator view)
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            for (int dx = 12; dx <= 17; dx++) for (int dz = 6; dz <= 23; dz++) l.setBlock(M.offset(dx, -1, dz), Blocks.AIR.defaultBlockState(), 2);
            p.teleportTo(l, M.getX() + 15.0, M.getY() + 4.0, M.getZ() + 1.0, Set.of(), 0f, 40f, false);
        });
        sp.getServer().runCommand("gamemode spectator @a");
        ctx.waitTicks(40);
        ctx.takeScreenshot("hh_j3_tunnel");
        sp.getServer().runCommand("gamemode creative @a");
        Balance.BREAK_PACE_PERCENT = DEF_BREAK_PACE;
        if (!FAILS.isEmpty()) throw new AssertionError("wave J3 failed: " + FAILS);
        log("J3 OK");
    }
}
