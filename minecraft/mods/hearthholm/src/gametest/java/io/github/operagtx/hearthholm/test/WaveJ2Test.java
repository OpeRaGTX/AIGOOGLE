package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.api.ProfessionModule;
import io.github.operagtx.hearthholm.api.ProfessionModules;
import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.client.screen.ZoneMapScreen;
import io.github.operagtx.hearthholm.client.screen.ZoneMapWidget;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave J2: the map-zone foundation (WorkZone, snapshot, edit validation, persistence), the lumberjack inside a drawn zone
 * (trees, drops, saplings), the empty-zone fallback to the radius, the map screen (RU/EN, two window sizes) and the settler
 * screen's bottom buttons. Run alone (plus the base) with HH_ONLY_J2=1.
 */
final class WaveJ2Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos M;
    private static UUID JACK;
    private static final int SURFACE = WorkZone.SURFACE;

    private WaveJ2Test() {}

    private static void fail(String m) { FAILS.add(m); log("J2 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static SettlerEntity jack(MinecraftServer s) { return (SettlerEntity) s.overworld().getEntity(JACK); }

    private static WorkSiteBlockEntity site(MinecraftServer s) { return (WorkSiteBlockEntity) s.overworld().getBlockEntity(M); }

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

    private static void tree(ServerLevel l, BlockPos base) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 5; dy++)
                    if (Math.abs(dx) + Math.abs(dz) < 4) l.setBlock(base.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        for (int dy = 0; dy < 5; dy++) l.setBlock(base.above(dy), Blocks.OAK_LOG.defaultBlockState(), 3);
    }

    private static boolean logAt(MinecraftServer s, BlockPos p) { return s.overworld().getBlockState(p).is(BlockTags.LOGS); }

    /** Polls the server every second until {@code cond} holds; returns the ticks waited, or -1 on timeout. */
    private static int await(ClientGameTestContext ctx, TestSingleplayerContext sp, int maxTicks, Predicate<MinecraftServer> cond) {
        for (int t = 0; t <= maxTicks; t += 20) {
            AtomicBoolean ok = new AtomicBoolean();
            sp.getServer().runOnServer(s -> {
                s.overworld().setDayTime(5000);
                ok.set(cond.test(s));
            });
            if (ok.get()) return t;
            ctx.waitTicks(20);
        }
        return -1;
    }

    private static Payloads.ZoneEdit edit(int op, Payloads.Rect... rects) { return new Payloads.ZoneEdit(M, SURFACE, op, List.of(rects)); }

    private static Payloads.Rect rect(int x0, int z0, int x1, int z1) { return new Payloads.Rect(x0, z0, x1, z1); }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");
        M = O.offset(-26, 0, 30).atY(O.getY() + 70);
        BlockPos treeA = M.offset(5, 0, 0), treeB = M.offset(-7, 0, 0);
        AtomicReference<Payloads.ZoneMapData> snap = new AtomicReference<>();

        // ---- 0. set-up: a grass platform in the sky, the chopping block with a chest, the lumberjack with an axe
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, M.getX() + 0.5, M.getY() + 1, M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            topUp(s, 7);
            Settlement st = town(s);
            l.removeBlock(FOREST, false);   // the base test's forest marker: this wave owns the only one
            List<SettlerEntity> all = settlers(s);
            SettlerEntity j = all.get(0);
            for (SettlerEntity e : all) if (e != j) e.setProfession(Professions.UNEMPLOYED);
            JACK = j.getUUID();
            for (int dx = -15; dx <= 15; dx++) for (int dz = -15; dz <= 15; dz++) {
                l.setBlock(M.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
                l.setBlock(M.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= 12; dy++) l.setBlock(M.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            l.setBlock(M, ModBlocks.FOREST_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, M, p);
            l.setBlock(M.east(), Blocks.CHEST.defaultBlockState(), 3);
            tree(l, treeA);
            tree(l, treeB);
            j.setProfession(Professions.LUMBERJACK);
            j.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            j.inventory().clearContent();
            j.markArrived();
            j.getNavigation().stop();
            j.teleportTo(M.getX() + 0.5, M.getY(), M.getZ() + 3.5);
            WorkSiteBlockEntity w = site(s);
            w.setOption("radius", 12);
            log("J2 set-up: site=" + (w != null) + " sites=" + st.sitesOf(WorkSiteTypes.FOREST_ID).size() + " lumberjack " + j.getName().getString());
            check(w != null && st.sitesOf(WorkSiteTypes.FOREST_ID).size() == 1, "set-up: the chopping block is not the settlement's only forest site");
            // the profession module: map zone + opener that is not the generic screen
            ProfessionModule m = ProfessionModules.of(Professions.LUMBERJACK);
            check(m.zone() != null && m.zone().kind() == ProfessionModule.ZoneData.Kind.MAP && m.screenOpener() != ProfessionModule.SiteScreenOpener.GENERIC, "lumberjack module is not a map module");
            check(WorkZone.specOf(WorkSiteTypes.FOREST_ID) != null && WorkZone.specOf(WorkSiteTypes.FOREST_ID).size() == 100, "no zone spec for the forest site");
        });
        ctx.waitTicks(10);
        sp.getServer().runCommand("gamemode survival @a");

        // ---- 1. map snapshot: size, bytes, trees flagged, markers, wire size, throttle
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            WorkSiteBlockEntity w = site(s);
            Payloads.ZoneMapData d = ZoneMapService.snapshot(l, w, st, SURFACE);
            snap.set(d);
            int known = 0;
            for (byte b : d.colors()) if (b != 0) known++;
            int n = d.size();
            int ia = (n / 2) * n + (n / 2 + 5), ib = (n / 2) * n + (n / 2 - 7), ic = (n / 2 + 10) * n + (n / 2 + 10);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), l.registryAccess());
            Payloads.ZoneMapData.CODEC.encode(buf, d);
            int wire = buf.readableBytes();
            Payloads.ZoneMapData back = Payloads.ZoneMapData.CODEC.decode(buf);
            log("J2 snapshot: size=" + n + " cell=" + d.cell() + " colors=" + d.colors().length + " known=" + known + " flags=" + d.flags().length
                    + " wire=" + wire + " bytes, markers=" + d.markers() + " flagA=" + d.flag(ia) + " flagB=" + d.flag(ib) + " flagFloor=" + d.flag(ic)
                    + " colorFloor=" + (d.colors()[ic] & 0xFF) + " origin=" + d.originX() + "," + d.originZ());
            check(n == 100 && d.colors().length == 10000 && d.flags().length == 2500, "snapshot dimensions");
            check(wire > 10000 && wire < 20000, "snapshot is not about 10-15 KB: " + wire);
            check(known >= 31 * 31, "snapshot lacks the platform: known cells " + known);
            check(d.flag(ia) == Payloads.ZoneMapData.LOG && d.flag(ib) == Payloads.ZoneMapData.LOG && d.flag(ic) == 0, "tree columns not flagged");
            check((d.colors()[ic] & 0xFF) >> 2 == net.minecraft.world.level.material.MapColor.GRASS.id, "grass floor has the wrong map colour id " + ((d.colors()[ic] & 0xFF) >> 2));
            boolean site = false, chest = false;
            for (Payloads.MapMarker mk : d.markers()) {
                if (mk.kind() == Payloads.MapMarker.SITE && mk.x() == M.getX() && mk.z() == M.getZ()) site = true;
                if (mk.kind() == Payloads.MapMarker.CHEST && mk.x() == M.getX() + 1 && mk.z() == M.getZ()) chest = true;
            }
            check(site && chest, "markers lack the site or the chest next to it");
            check(back.colors().length == 10000 && java.util.Arrays.equals(back.colors(), d.colors()) && java.util.Arrays.equals(back.flags(), d.flags()) && back.markers().equals(d.markers()), "snapshot did not survive the wire codec");
            // layer mode (J3): the block at a given Y
            Payloads.ZoneMapData layer = ZoneMapService.snapshot(l, w, st, M.getY() - 2);
            check((layer.colors()[(n / 2) * n + n / 2 + 20] & 0xFF) >> 2 == net.minecraft.world.level.material.MapColor.STONE.id, "layer mode: stone expected at Y-2");
            check(layer.layerY() == M.getY() - 2 && layer.flags().length == 2500, "layer snapshot header");
            // throttle: a second request within the gap is dropped
            ZoneMapService.served = 0; ZoneMapService.throttled = 0;
            boolean first = ZoneMapService.open(p, w, st);
            boolean second = ZoneMapService.open(p, w, st);
            log("J2 throttle: first=" + first + " second=" + second + " served=" + ZoneMapService.served + " throttled=" + ZoneMapService.throttled);
            check((first || ZoneMapService.throttled >= 1) && !second && ZoneMapService.throttled >= 1, "requests are not throttled");
        });
        ctx.waitForScreen(ZoneMapScreen.class);
        ctx.setScreen(() -> null);

        // ---- 2. painting through the payload: rect + brush + erase, validation, persistence
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            WorkSiteBlockEntity w = site(s);
            check(!w.hasZone(), "fresh site already has a zone");
            boolean r1 = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(52, 44, 60, 56)));
            boolean r2 = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(40, 44, 46, 56)));
            boolean r3 = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(49, 59, 51, 61)));   // a 3x3 brush stamp
            int afterSet = w.zone().cellCount();
            boolean r4 = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.CLEAR, rect(40, 40, 46, 60)));
            WorkZone z = w.zone();
            log("J2 paint: edits " + r1 + r2 + r3 + r4 + " cells after set=" + afterSet + " after erase=" + z.cellCount()
                    + " A in=" + z.contains(treeA) + " B in=" + z.contains(treeB));
            check(r1 && r2 && r3 && r4, "owner edits were rejected");
            check(afterSet == 117 + 91 + 9 && z.cellCount() == 117 + 9, "cell counts wrong: " + afterSet + " / " + z.cellCount());
            check(z.contains(treeA) && !z.contains(treeB) && z.get(SURFACE, 50, 60) && !z.get(SURFACE, 50, 62), "zone membership wrong");
            // helpers
            int[] visited = {0};
            z.forEachCell(SURFACE, (ix, iz) -> visited[0]++);
            check(visited[0] == z.cellCount() && z.layerKeys().equals(List.of(SURFACE)), "forEachCell / layers");
            // persistence: save the block entity and load it into a new one
            var tag = w.saveCustomOnly(l.registryAccess());
            WorkSiteBlockEntity w2 = new WorkSiteBlockEntity(M, l.getBlockState(M));
            w2.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess(), tag));
            log("J2 persisted: tag " + tag.toString().length() + " chars, reloaded cells=" + (w2.zone() == null ? -1 : w2.zone().cellCount())
                    + " A in=" + (w2.zone() != null && w2.zone().contains(treeA)) + " radius option kept=" + w2.option("radius"));
            check(w2.zone() != null && w2.zone().cellCount() == 126 && w2.zone().contains(treeA) && !w2.zone().contains(treeB) && w2.option("radius") == 12, "zone lost on save/reload");
            // a stranger, a far player, out-of-range cells, a wrong layer, an empty tail are all refused
            int before = z.cellCount(), rej0 = ZoneMapService.rejected;
            Settlement st = town(s);
            UUID real = st.owner;
            setOwner(st, UUID.randomUUID());
            boolean stranger = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(10, 10, 20, 20)));
            boolean strangerClear = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.CLEAR_ALL));
            setOwner(st, real);
            p.teleportTo(l, M.getX() + 30.5, M.getY(), M.getZ() + 0.5, Set.of(), 0f, 0f, false);
            boolean far = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(10, 10, 20, 20)));
            p.teleportTo(l, M.getX() + 0.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            boolean oob = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(90, 90, 100, 100)));
            boolean neg = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(-1, 5, 3, 7)));
            boolean flipped = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(20, 20, 10, 10)));
            boolean layer = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, 64, Payloads.ZoneEdit.SET, List.of(rect(1, 1, 2, 2))));
            boolean op = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, SURFACE, 9, List.of(rect(1, 1, 2, 2))));
            List<Payloads.Rect> many = new ArrayList<>();
            for (int i = 0; i < 600; i++) many.add(rect(1, 1, 1, 1));
            boolean flood = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M, SURFACE, Payloads.ZoneEdit.SET, many));
            boolean noSite = ZoneMapService.handleEdit(p, new Payloads.ZoneEdit(M.above(3), SURFACE, Payloads.ZoneEdit.SET, List.of(rect(1, 1, 2, 2))));
            log("J2 validation: stranger=" + stranger + "/" + strangerClear + " far=" + far + " outOfBounds=" + oob + " negative=" + neg + " flipped=" + flipped
                    + " layer=" + layer + " op=" + op + " flood=" + flood + " noSite=" + noSite + " rejected+" + (ZoneMapService.rejected - rej0) + " cells " + before + "->" + w.zone().cellCount());
            check(!stranger && !strangerClear && !far && !oob && !neg && !flipped && !layer && !op && !flood && !noSite, "an invalid edit was accepted");
            check(w.zone().cellCount() == before && ZoneMapService.rejected - rej0 == 10, "rejected edits changed the zone or were not counted");
            // an empty edit by the owner still works; a zone cleared to nothing is dropped from the block entity
            check(ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(5, 5, 5, 5))) && w.zone().cellCount() == before + 1, "owner edit after the refusals failed");
            check(ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.CLEAR, rect(5, 5, 5, 5))) && w.zone().cellCount() == before, "erase of one cell failed");
            // drops: one stick inside the zone, one outside
            ItemEntity in = new ItemEntity(l, M.getX() + 8.5, M.getY() + 1, M.getZ() + 3.5, new ItemStack(Items.STICK, 3));
            ItemEntity out = new ItemEntity(l, M.getX() - 8.5, M.getY() + 1, M.getZ() + 3.5, new ItemStack(Items.STICK, 2));
            in.setDeltaMovement(0, 0, 0); out.setDeltaMovement(0, 0, 0);
            in.setUnlimitedLifetime(); out.setUnlimitedLifetime();
            l.addFreshEntity(in); l.addFreshEntity(out);
            DROP_IN = in.getUUID(); DROP_OUT = out.getUUID();
            SettlerEntity j = jack(s);
            j.markArrived();
        });

        // ---- 3. the lumberjack works only inside the zone: the inside tree falls, the outside one stays, drops inside are taken
        long t0 = System.currentTimeMillis();
        int tA = await(ctx, sp, 2400, s -> !logAt(s, treeA));
        log("J2 zone chop: inside tree felled after " + tA + " ticks (" + (System.currentTimeMillis() - t0) / 1000 + " s)");
        check(tA >= 0, "the tree inside the zone was not chopped");
        int tIn = await(ctx, sp, 1200, s -> s.overworld().getEntity(DROP_IN) == null);
        ctx.waitTicks(500);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            boolean bStanding = logAt(s, treeB) && logAt(s, treeB.above(4));
            boolean outDrop = l.getEntity(DROP_OUT) != null;
            int sticks = 0;
            for (ItemStack st : jack(s).inventory().getItems()) if (st.is(Items.STICK)) sticks += st.getCount();
            log("J2 zone result: outside tree standing=" + bStanding + " inside drop taken=" + (tIn >= 0) + " (after " + tIn + ") outside drop left=" + outDrop + " sticks in pockets=" + sticks);
            check(bStanding, "the tree outside the zone was chopped");
            check(tIn >= 0 && sticks >= 3, "the drop inside the zone was not picked up");
            check(outDrop, "the drop outside the zone was picked up");
            // saplings in the pocket: the lumberjack plants them inside the zone (no tree left in it)
            SettlerEntity j = jack(s);
            j.inventory().addItem(new ItemStack(Items.OAK_SAPLING, 4));
        });
        int tP = await(ctx, sp, 2400, s -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(M.offset(-15, 0, -15), M.offset(15, 4, 15))) if (s.overworld().getBlockState(p).is(BlockTags.SAPLINGS)) n++;
            return n >= 1;
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            WorkZone z = site(s).zone();
            int in = 0, outside = 0;
            for (BlockPos p : BlockPos.betweenClosed(M.offset(-15, 0, -15), M.offset(15, 4, 15))) {
                if (!l.getBlockState(p).is(BlockTags.SAPLINGS)) continue;
                if (z.contains(p)) in++; else outside++;
            }
            log("J2 replant: saplings inside the zone=" + in + " outside=" + outside + " (first after " + tP + " ticks)");
            check(tP >= 0 && in >= 1 && outside == 0, "saplings were not planted inside the zone only (in=" + in + " out=" + outside + ")");
            // the plant toggle off: nothing more is planted
            site(s).setOption("plant", 0);
            site(s).setOption("replant", 0);
            check(!site(s).flag("plant"), "plant toggle did not switch");
        });
        // ---- 4. empty zone: the old radius behaviour (the tree outside the old zone is chopped again)
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            boolean cleared = ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.CLEAR_ALL));
            WorkSiteBlockEntity w = site(s);
            log("J2 cleared: ok=" + cleared + " hasZone=" + w.hasZone() + " zoneNull=" + (w.zone() == null));
            check(cleared && !w.hasZone() && w.zone() == null, "clear all did not drop the zone");
            check(logAt(s, treeB), "tree B vanished before the radius test");
            for (BlockPos q : BlockPos.betweenClosed(M.offset(-15, 0, -15), M.offset(15, 4, 15))) if (s.overworld().getBlockState(q).is(BlockTags.SAPLINGS)) s.overworld().removeBlock(q, false);
        });
        int tB = await(ctx, sp, 2400, s -> !logAt(s, treeB));
        int tOut = await(ctx, sp, 1200, s -> s.overworld().getEntity(DROP_OUT) == null);
        log("J2 empty zone: tree outside the old zone felled after " + tB + " ticks; old radius also took the outside drop after " + tOut);
        check(tB >= 0, "with an empty zone the radius behaviour no longer chops");
        check(tOut >= 0, "with an empty zone the radius sweep no longer picks up drops");

        // ---- 5. screenshots: a painted zone with trees, RU 1280x720 and 854x480; every button label must fit
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SettlerEntity j = jack(s);
            j.setProfession(Professions.UNEMPLOYED);   // the screens are the point now: no more work
            site(s).setOption("plant", 1);
            site(s).setOption("replant", 1);
            for (BlockPos q : BlockPos.betweenClosed(M.offset(-15, 0, -15), M.offset(15, 14, 15))) {
                if (!q.equals(M) && !q.equals(M.east())) l.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
            }
            int[][] spots = {{6, -5}, {9, 3}, {3, 8}, {-6, -6}, {-9, 4}, {-4, 9}, {11, -10}, {-11, -11}, {0, -9}};
            for (int[] sp2 : spots) tree(l, M.offset(sp2[0], 0, sp2[1]));
            // an irregular zone: a blob around the block painted from rectangles and 3x3 brush stamps, with a hole and a bite out of the edge
            p.teleportTo(l, M.getX() + 0.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(40, 38, 62, 60), rect(36, 44, 40, 54), rect(62, 46, 66, 52)));
            ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.SET, rect(45, 62, 47, 64), rect(48, 62, 50, 65), rect(51, 63, 53, 65)));
            ZoneMapService.handleEdit(p, edit(Payloads.ZoneEdit.CLEAR, rect(52, 40, 58, 44), rect(44, 52, 47, 55), rect(60, 56, 62, 60)));
            log("J2 screenshot zone: " + site(s).zone().cellCount() + " cells");
            SettlerEntity g = settlers(s).get(1);
            g.setProfession(Professions.GUARD);
            g.setXp(Professions.GUARD.id(), 1_000_000);
            GUARD_ID = g.getUUID();
        });
        try {
            setLang(ctx, "ru_ru");
        } catch (Throwable t) {
            fail("could not switch the language: " + t);
        }
        for (int[] size : new int[][]{{1280, 720}, {854, 480}}) {
            setWindow(ctx, size[0], size[1]);
            ctx.waitTicks(12);
            sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), M));
            ctx.waitForScreen(ZoneMapScreen.class);
            ctx.waitTicks(10);
            List<String> cut = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.truncatedLabels() : List.of("not the map screen"));
            log("J2 map screen RU " + size[0] + "x" + size[1] + ": truncated labels=" + cut);
            check(cut.isEmpty(), "RU map screen has cut labels " + cut + " at " + size[0] + "x" + size[1]);
            ctx.takeScreenshot("hh_j2_zone_ru_" + size[0] + "x" + size[1]);
            ctx.setScreen(() -> null);
            ctx.waitTicks(12);
            // the settler screen: Refresh / Inventory / Promote / Dismiss must read fully
            sp.getServer().runOnServer(s -> SettlementService.openSettler(player(s), (SettlerEntity) s.overworld().getEntity(GUARD_ID)));
            ctx.waitForScreen(SettlerScreen.class);
            ctx.waitTicks(6);
            List<String> cut2 = ctx.computeOnClient(mc -> mc.gui.screen() instanceof SettlerScreen z ? z.truncatedLabels() : List.of("not the settler screen"));
            List<String> labels = ctx.computeOnClient(mc -> {
                List<String> out = new ArrayList<>();
                if (mc.gui.screen() instanceof SettlerScreen z) for (var c : z.children()) if (c instanceof net.minecraft.client.gui.components.Button b && b.getY() > z.height / 2 + 60) out.add(b.getMessage().getString() + "@" + b.getX() + "+" + b.getWidth());
                return out;
            });
            log("J2 settler screen RU " + size[0] + "x" + size[1] + ": truncated=" + cut2 + " bottom buttons=" + labels);
            check(cut2.isEmpty(), "settler screen has cut labels " + cut2 + " at " + size[0] + "x" + size[1]);
            if (size[0] == 1280) ctx.takeScreenshot("hh_j2_settler_ru_" + size[0] + "x" + size[1]);
            else ctx.takeScreenshot("hh_j2_settler_ru_" + size[0] + "x" + size[1]);
            ctx.setScreen(() -> null);
            ctx.waitTicks(8);
        }
        // EN: both screens at the small size
        setLang(ctx, "en_us");
        setWindow(ctx, 854, 480);
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), M));
        ctx.waitForScreen(ZoneMapScreen.class);
        ctx.waitTicks(8);
        List<String> cutEn = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ZoneMapScreen z ? z.truncatedLabels() : List.of("not the map screen"));
        log("J2 map screen EN 854x480: truncated labels=" + cutEn);
        check(cutEn.isEmpty(), "EN map screen has cut labels " + cutEn);

        // ---- 6. the client widget end to end: rectangle, brush and eraser drags go over the network and change the server zone
        AtomicInteger before = new AtomicInteger();
        sp.getServer().runOnServer(s -> before.set(site(s).zone().cellCount()));
        ctx.runOnClient(mc -> {
            ZoneMapWidget wd = ((ZoneMapScreen) mc.gui.screen()).widget();
            int ppc = wd.ppc();
            wd.setTool(ZoneMapWidget.Tool.RECT);
            double x0 = wd.x + 20 * ppc + 1, z0 = wd.y + 20 * ppc + 1;
            wd.mouseClicked(x0, z0, 0);
            wd.mouseDragged(x0 + 4 * ppc, z0 + 4 * ppc, 0, 4 * ppc, 4 * ppc);
            wd.mouseReleased(x0 + 4 * ppc, z0 + 4 * ppc, 0);   // 5 x 5 = 25 new cells
            wd.setTool(ZoneMapWidget.Tool.BRUSH);
            wd.setBrush(3);
            double bx = wd.x + 10 * ppc + 1, bz = wd.y + 10 * ppc + 1;
            wd.mouseClicked(bx, bz, 0);
            wd.mouseDragged(bx + 6 * ppc, bz, 0, 6 * ppc, 0);   // a stroke of 3 high, 9 wide = 27 cells
            wd.mouseReleased(bx + 6 * ppc, bz, 0);
        });
        ctx.waitTicks(10);
        AtomicInteger after = new AtomicInteger(), sent = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            after.set(site(s).zone().cellCount());
            sent.set(ZoneMapService.applied);
        });
        log("J2 widget edits over the network: cells " + before.get() + " -> " + after.get() + " (expected +" + (25 + 27) + ")");
        check(after.get() == before.get() + 25 + 27, "client widget edits did not reach the server zone: " + before.get() + " -> " + after.get());
        ctx.runOnClient(mc -> {
            ZoneMapWidget wd = ((ZoneMapScreen) mc.gui.screen()).widget();
            int ppc = wd.ppc();
            wd.setTool(ZoneMapWidget.Tool.ERASE_RECT);
            double x0 = wd.x + 20 * ppc + 1, z0 = wd.y + 20 * ppc + 1;
            wd.mouseClicked(x0, z0, 0);
            wd.mouseDragged(x0 + 4 * ppc, z0 + 4 * ppc, 0, 4 * ppc, 4 * ppc);
            wd.mouseReleased(x0 + 4 * ppc, z0 + 4 * ppc, 0);   // the rectangle comes out again
            wd.setTool(ZoneMapWidget.Tool.ERASER);
            wd.setBrush(1);
            wd.mouseClicked(wd.x + 10 * ppc + 1, wd.y + 10 * ppc + 1, 0);   // one cell of the stroke
            wd.mouseReleased(wd.x + 10 * ppc + 1, wd.y + 10 * ppc + 1, 0);
            wd.setZoom(2);
        });
        ctx.waitTicks(10);
        AtomicInteger back = new AtomicInteger(), vis = new AtomicInteger();
        sp.getServer().runOnServer(s -> back.set(site(s).zone().cellCount()));
        ctx.runOnClient(mc -> vis.set(((ZoneMapScreen) mc.gui.screen()).widget().visible()));
        log("J2 widget erase: cells " + back.get() + " (expected " + (before.get() + 27 - 1) + "), 4x zoom shows " + vis.get() + " cells");
        check(back.get() == before.get() + 27 - 1, "erase tools did not reach the server: " + back.get());
        check(vis.get() == 25, "4x zoom shows " + vis.get() + " cells");
        ctx.setScreen(() -> null);
        ctx.waitTicks(6);

        // ---- 7. the chopping block in the world (screenshot), then breaking it removes the zone
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, M.getX() + 0.9, M.getY(), M.getZ() - 2.6, Set.of(), 15f, 24f, false);
        });
        setWindow(ctx, 854, 480);
        ctx.runOnClient(mc -> mc.options.guiScale().set(0));
        ctx.waitTicks(30);
        ctx.takeScreenshot("hh_j2_chopping_block");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            boolean had = site(s).hasZone();
            l.removeBlock(M, false);
            l.setBlock(M, ModBlocks.FOREST_MARKER.defaultBlockState(), 3);
            WorkSiteBlockEntity fresh = site(s);
            log("J2 removal: had zone=" + had + ", after breaking and placing again zone=" + fresh.zone() + " hasZone=" + fresh.hasZone());
            check(had && fresh.zone() == null && !fresh.hasZone(), "the zone survived breaking the block");
        });
        sp.getServer().runCommand("gamemode creative @a");
        if (!FAILS.isEmpty()) throw new AssertionError("wave J2 failed: " + FAILS);
        log("J2 OK");
    }

    private static UUID DROP_IN, DROP_OUT, GUARD_ID;
}
