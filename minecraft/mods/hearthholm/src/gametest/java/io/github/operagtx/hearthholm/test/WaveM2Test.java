package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.client.screen.ZoneMapScreen;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.crafting.MaterialOrders;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave M2: the construction stake shows what the storage holds (check mark / cross per material) and orders the missing from the workshops; the miner's
 * tunnel goes forward / right / left / back / all four ways from the SHARED staircase and the side branches are longer. Run alone with HH_ONLY_M2=1.
 */
final class WaveM2Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos S2;
    private static UUID MINER;

    private WaveM2Test() {}

    private static void fail(String m) { FAILS.add(m); log("M2 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    /** 6 planks (always in the warehouse), 5 cobblestone (put into the warehouse), 3 deepslate bricks (raw: no workshop makes them), 40 chests (carpenter). */
    private static Blueprint matsBp() {
        BlockState[] cells = new BlockState[9 * 1 * 6];
        int i = 0;
        for (; i < 6; i++) cells[i] = Blocks.OAK_PLANKS.defaultBlockState();
        for (int k = 0; k < 5; k++) cells[i++] = Blocks.COBBLESTONE.defaultBlockState();
        for (int k = 0; k < 3; k++) cells[i++] = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        for (; i < cells.length; i++) cells[i] = Blocks.CHEST.defaultBlockState();
        return new Blueprint(Hearthholm.id("m2_mats"), 9, 1, 6, cells);
    }

    private static int idx(Blueprint bp) { return BlueprintLibrary.ids().indexOf(bp.id()); }

    // ---- the planner geometry ------------------------------------------------------------

    private static int maxDot(Collection<BlockPos> cells, BlockPos origin, Direction d) {
        int best = Integer.MIN_VALUE;
        for (BlockPos p : cells) best = Math.max(best, (p.getX() - origin.getX()) * d.getStepX() + (p.getZ() - origin.getZ()) * d.getStepZ());
        return best;
    }

    private static void plannerChecks() {
        BlockPos marker = new BlockPos(100, 64, 100);
        Direction f = Direction.SOUTH, right = f.getClockWise();
        int length = 10;
        int plans = 0;
        for (int mode : new int[]{MinePlanner.SPIRAL, MinePlanner.LADDER, MinePlanner.TUNNEL}) {
            for (int depth : mode == MinePlanner.TUNNEL ? new int[]{4} : new int[]{4, 8, 12, 20}) {
                MinePlanner.Plan access = MinePlanner.plan(marker, f, mode, depth, 0, false, true, MinePlanner.FORWARD);   // same torch flag as the plans below
                int a = access.steps().size();
                Set<BlockPos> accDig = new HashSet<>(), accFloor = new HashSet<>();
                for (MinePlanner.Step st : access.steps()) {
                    if (st.kind() == MinePlanner.Kind.DIG) accDig.add(st.pos());
                    if (st.kind() == MinePlanner.Kind.FLOOR) accFloor.add(st.pos());
                }
                BlockPos c = access.shaft();
                Map<Integer, Set<BlockPos>> armCells = new HashMap<>();
                for (int dir = MinePlanner.FORWARD; dir <= MinePlanner.ALL; dir++) {
                    MinePlanner.Plan p = MinePlanner.plan(marker, f, mode, depth, length, false, true, dir);
                    plans++;
                    String tag = "mode " + mode + " depth " + depth + " dir " + dir;
                    check(p.steps().size() > a && p.steps().subList(0, a).equals(access.steps()), "the access of " + tag + " is not the shared prefix");
                    Set<BlockPos> arm = new HashSet<>();
                    for (MinePlanner.Step st : p.steps().subList(a, p.steps().size())) if (st.kind() == MinePlanner.Kind.DIG) arm.add(st.pos());
                    armCells.put(dir, arm);
                    List<Direction> want = MinePlanner.arms(f, dir);
                    for (Direction d : want) check(maxDot(arm, c, d) >= length, "no tunnel reaches " + length + " blocks towards " + d + " in " + tag);
                    if (dir != MinePlanner.ALL) {
                        Direction opp = want.getFirst().getOpposite();
                        check(maxDot(arm, c, opp) <= 1, "the tunnel of " + tag + " leaks " + maxDot(arm, c, opp) + " blocks the other way");
                    }
                    if (dir != MinePlanner.FORWARD && dir != MinePlanner.ALL && mode != MinePlanner.TUNNEL) {
                        Set<BlockPos> hit = new HashSet<>(arm);
                        hit.retainAll(accFloor);
                        check(hit.isEmpty(), "the tunnel of " + tag + " digs the stair's floor blocks: " + hit);
                        if (mode == MinePlanner.SPIRAL) {
                            Set<BlockPos> dig = new HashSet<>(arm);
                            dig.retainAll(accDig);
                            check(dig.isEmpty(), "the tunnel of " + tag + " re-digs the stair: " + dig);
                        }
                    }
                }
                Set<BlockPos> union = new HashSet<>();
                for (int dir = MinePlanner.FORWARD; dir < MinePlanner.ALL; dir++) union.addAll(armCells.get(dir));
                check(armCells.get(MinePlanner.ALL).equals(union), "'all' is not the union of the four single directions (mode " + mode + " depth " + depth + ")");
                if (mode == MinePlanner.SPIRAL && depth == 4) {   // the forward plan is exactly what it was before the option existed
                    MinePlanner.Plan legacy = MinePlanner.plan(marker, f, mode, depth, length, false, true);
                    check(legacy.steps().equals(MinePlanner.plan(marker, f, mode, depth, length, false, true, MinePlanner.FORWARD).steps()), "the 7-argument plan differs from forward");
                }
            }
        }
        // side branches reach MINE_BRANCH_LENGTH to both sides
        MinePlanner.Plan withBranches = MinePlanner.plan(marker, f, MinePlanner.LADDER, 8, 12, true, false, MinePlanner.FORWARD);
        BlockPos c = withBranches.shaft();
        Set<BlockPos> cells = new HashSet<>();
        for (MinePlanner.Step st : withBranches.steps()) if (st.kind() == MinePlanner.Kind.DIG) cells.add(st.pos());
        int east = maxDot(cells, c, right), west = maxDot(cells, c, right.getOpposite());
        log("M2 planner: " + plans + " plans checked; branches reach " + east + " (right) and " + west + " (left) blocks, MINE_BRANCH_LENGTH=" + Balance.MINE_BRANCH_LENGTH);
        check(east >= Balance.MINE_BRANCH_LENGTH && west >= Balance.MINE_BRANCH_LENGTH, "branches are shorter than " + Balance.MINE_BRANCH_LENGTH + ": " + east + "/" + west);
        check(Balance.MINE_BRANCH_LENGTH > 6, "the branches must be longer than the old 6");
    }

    private static boolean poll(ClientGameTestContext ctx, TestSingleplayerContext sp, int rounds, int step, java.util.function.Predicate<net.minecraft.server.MinecraftServer> done) {
        AtomicBoolean ok = new AtomicBoolean();
        for (int r = 0; r < rounds && !ok.get(); r++) {
            ctx.waitTicks(step);
            sp.getServer().runOnServer(s -> { if (done.test(s)) ok.set(true); });
        }
        return ok.get();
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("weather clear");
        sp.getServer().runCommand("time set 5000");
        int savedPace = Balance.BREAK_PACE_PERCENT;
        try {
            // ---- b. planner geometry (pure)
            plannerChecks();

            // ---- a. the stake: stock numbers, marks and the order button
            Blueprint bp = matsBp();
            BlueprintLibrary.register(bp);
            List<Item> before = new ArrayList<>();
            // a carpenter must exist (earlier waves in the same run, M1 among them, send everybody to other jobs)
            sp.getServer().runOnServer(s -> {
                SettlerEntity c = settlers(s).get(1);
                c.setProfession(Professions.UNEMPLOYED);
                c.setProfession(Professions.CARPENTER);
            });
            ctx.waitTicks(220);
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                Settlement st = town(s);
                ServerPlayer p = player(s);
                p.teleportTo(l, STAKE.getX() + 2.5, STAKE.getY() + 1, STAKE.getZ() + 2.5, Set.of(), 0f, 20f, false);
                WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(STAKE);
                w.setOption("blueprint", idx(bp));
                var rest = Storage.insert(Storage.resolve(l, st.storagePositions()), new ItemStack(Items.COBBLESTONE, 5));
                check(rest.isEmpty(), "no room in the warehouse for the test cobblestone");
                SettlementService.setOrder(st, Items.CHEST, 0);
                int[] have = MaterialOrders.stock(l, st, bp, STAKE);
                List<Blueprint.Material> mats = bp.materialList();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mats.size(); i++) sb.append(mats.get(i).icon()).append(' ').append(have[i]).append('/').append(mats.get(i).count()).append("  ");
                log("M2 stake stock: " + sb);
                Views.WorkSite view = SettlementService.siteView(l, w, st);
                for (int i = 0; i < mats.size(); i++)
                    check(view.values().getOrDefault("_h" + i, -1) == Math.min(have[i], 99999), "the view's stock number " + i + " is wrong: " + view.values().get("_h" + i) + " vs " + have[i]);
                int pl = -1, co = -1, ds = -1, ch = -1;
                for (int i = 0; i < mats.size(); i++) {
                    Item it = mats.get(i).icon();
                    if (it == Items.OAK_PLANKS) pl = i; else if (it == Items.COBBLESTONE) co = i; else if (it == Items.DEEPSLATE_BRICKS) ds = i; else if (it == Items.CHEST) ch = i;
                }
                check(pl >= 0 && co >= 0 && ds >= 0 && ch >= 0, "the test blueprint lost a material: " + mats);
                check(have[pl] >= mats.get(pl).count(), "planks should be in stock (check mark)");
                check(have[co] >= 5, "the 5 test cobblestone are not counted");
                check(have[ds] < 3 && have[ch] < 40, "deepslate bricks / chests should be missing (cross)");
                // the rules of the order
                check(MaterialOrders.blocker(l, st, Items.CHEST) == null, "the carpenter should be able to make chests: " + MaterialOrders.blocker(l, st, Items.CHEST));
                check(MaterialOrders.blocker(l, st, Items.DEEPSLATE_BRICKS) == MaterialOrders.Blocker.NO_WORKSHOP_KIND, "deepslate bricks are raw material");
                before.add(Items.CHEST);
            });
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                Settlement st = town(s);
                int orders0 = st.orders.size();
                SettlementService.handleWorkSite(player(s), new Payloads.WorkSiteAction(STAKE, Payloads.WorkSiteAction.ORDER_MISSING, 0));
                int chest1 = amount(st, Items.CHEST);
                int orders1 = st.orders.size();
                log("M2 order button: orders " + orders0 + " -> " + orders1 + ", chest order " + chest1);
                check(chest1 == 40, "the chests were not ordered with the blueprint's amount: " + chest1);
                check(amount(st, Items.DEEPSLATE_BRICKS) == 0 && amount(st, Items.COBBLESTONE) == 0 && amount(st, Items.OAK_PLANKS) == amount(st, Items.OAK_PLANKS), "raw materials must not be ordered");
                // pressing again changes nothing
                SettlementService.handleWorkSite(player(s), new Payloads.WorkSiteAction(STAKE, Payloads.WorkSiteAction.ORDER_MISSING, 0));
                check(st.orders.size() == orders1 && amount(st, Items.CHEST) == 40, "a second press duplicated or changed the orders: " + st.orders.size() + " / " + amount(st, Items.CHEST));
                var again = MaterialOrders.orderMissing(l, st, (Blueprint) BlueprintLibrary.get(Hearthholm.id("m2_mats")), STAKE);
                check(again.ordered().isEmpty() && again.unchanged().contains(Items.CHEST), "the second run must report the chests as already ordered: " + again);
                // a bigger order stays
                SettlementService.setOrder(st, Items.CHEST, 99);
                SettlementService.handleWorkSite(player(s), new Payloads.WorkSiteAction(STAKE, Payloads.WorkSiteAction.ORDER_MISSING, 0));
                check(amount(st, Items.CHEST) == 99, "a larger existing order was lowered: " + amount(st, Items.CHEST));
                SettlementService.setOrder(st, Items.CHEST, 0);   // the real button is pressed below
            });
            // screenshots: marks and button (EN, RU)
            sp.getConnection().waitForChunksRender();
            TestKit.setWindow(ctx, 854, 480);
            for (String code : new String[]{"en_us", "ru_ru"}) {
                TestKit.setLang(ctx, code);
                sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), STAKE));
                ctx.waitForScreen(WorkSiteScreen.class);
                ctx.waitTicks(6);
                ctx.takeScreenshot("hh_m2_stake_marks_" + code);
                if (code.equals("en_us")) {   // the real button: a click on the screen must put the chests into the orders
                    ctx.clickScreenButton("gui.hearthholm.order_missing");
                    ctx.waitTicks(10);
                    sp.getServer().runOnServer(s -> check(amount(town(s), Items.CHEST) == 40, "clicking the button did not order the chests: " + amount(town(s), Items.CHEST)));
                }
                ctx.setScreen(() -> null);
            }
            TestKit.setLang(ctx, "en_us");
            sp.getServer().runOnServer(s -> {
                Settlement st = town(s);
                SettlementService.setOrder(st, Items.CHEST, 0);   // leave the settlement as it was (the carpenter would build 40 chests)
                ((WorkSiteBlockEntity) s.overworld().getBlockEntity(STAKE)).setOption("blueprint", 0);
            });

            // ---- c. a real miner: forward first (stair + tunnel), then the direction is switched to "back": same stair, a tunnel the other way
            S2 = O.offset(-30, 0, -30).atY(O.getY() + 70);
            Balance.BREAK_PACE_PERCENT = 25;
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                ServerPlayer p = player(s);
                p.teleportTo(l, S2.getX() + 0.5, S2.getY() + 2, S2.getZ() - 6.5, Set.of(), 0f, 20f, false);
                p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            });
            ctx.waitTicks(80);
            AtomicInteger total1 = new AtomicInteger();
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                ServerPlayer p = player(s);
                Settlement st = town(s);
                for (Settlement.SiteRecord r : new ArrayList<>(st.sitesOf(WorkSiteTypes.MINE_ID))) if (l.isLoaded(r.pos())) l.removeBlock(r.pos(), false);
                for (int dx = -14; dx <= 14; dx++) for (int dz = -12; dz <= 16; dz++) {
                    for (int dy = -16; dy <= -1; dy++) l.setBlock(S2.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                    for (int dy = 0; dy <= 8; dy++) l.setBlock(S2.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
                l.setBlock(S2, ModBlocks.MINE_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
                SettlementService.onWorkSitePlaced(l, S2, p);
                l.setBlock(S2.east(), Blocks.CHEST.defaultBlockState(), 3);
                if (l.getBlockEntity(S2.east()) instanceof Container c) {
                    c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                    c.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
                }
                List<SettlerEntity> all = settlers(s);
                SettlerEntity m = all.get(0);
                for (SettlerEntity e : all) if (e != m) e.setProfession(Professions.UNEMPLOYED);
                m.setProfession(Professions.UNEMPLOYED);
                m.setProfession(Professions.MINER);
                m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                m.inventory().clearContent();
                m.markArrived();
                m.getNavigation().stop();
                m.teleportTo(S2.getX() + 0.5, S2.getY(), S2.getZ() + 2.5);
                MINER = m.getUUID();
                WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(S2);
                w.setOption("mode", MinePlanner.SPIRAL);
                w.setOption("depth", 4);
                w.setOption("length", 8);
                w.setOption("branches", 0);
                w.setOption("torches", 0);
                w.setOption("plan", 0);
                w.setOption("direction", MinePlanner.FORWARD);
                total1.set(MinePlanner.plan(w).steps().size());
                log("M2 miner: marker " + S2.toShortString() + " forward plan " + total1.get() + " steps");
            });
            boolean fwd = poll(ctx, sp, 150, 40, s -> {
                WorkSiteBlockEntity w = (WorkSiteBlockEntity) s.overworld().getBlockEntity(S2);
                return w.progress() >= total1.get();
            });
            AtomicInteger total2 = new AtomicInteger();
            int[] solidBefore = new int[1];
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(S2);
                log("M2 miner: forward done=" + fwd + " progress " + w.progress() + "/" + total1.get());
                check(fwd, "the miner did not finish the forward plan");
                MinePlanner.Plan fp = MinePlanner.plan(w);
                int air = 0, all = 0;
                for (BlockPos q : fp.dug()) { all++; if (!l.getBlockState(q).isSolidRender()) air++; }
                log("M2 miner: forward cells dug " + air + "/" + all);
                check(air * 100 >= all * 95, "the forward plan is not dug: " + air + "/" + all);
                // switch the direction as the player does (a real action): progress is reset so the shared stair is re-checked, not dug again
                SettlementService.handleWorkSite(player(s), new Payloads.WorkSiteAction(S2, "direction", MinePlanner.BACK));
                check(w.option("direction") == MinePlanner.BACK, "the direction option was not stored");
                check(w.progress() == 0, "changing the direction must restart the plan check (progress " + w.progress() + ")");
                total2.set(MinePlanner.plan(w).steps().size());
                for (BlockPos q : MinePlanner.plan(w).dug()) if (!l.getBlockState(q).isAir()) solidBefore[0]++;
                log("M2 miner: back plan " + total2.get() + " steps, " + solidBefore[0] + " cells still solid");
                check(solidBefore[0] > 20, "the back plan has nothing new to dig");
            });
            boolean back = poll(ctx, sp, 150, 40, s -> {
                WorkSiteBlockEntity w = (WorkSiteBlockEntity) s.overworld().getBlockEntity(S2);
                return w.progress() >= total2.get();
            });
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(S2);
                MinePlanner.Plan bp2 = MinePlanner.plan(w);
                int air = 0, all = 0, floorLost = 0;
                for (BlockPos q : bp2.dug()) { all++; if (!l.getBlockState(q).isSolidRender()) air++; }
                // the stair is intact: every floor block of the access part is still there
                MinePlanner.Plan access = MinePlanner.plan(w.getBlockPos(), w.facing(), w.option("mode"), w.option("depth"), 0, false, false, MinePlanner.FORWARD);
                for (MinePlanner.Step st : access.steps()) if (st.kind() == MinePlanner.Kind.FLOOR && !l.getBlockState(st.pos()).isFaceSturdy(l, st.pos(), Direction.UP)) floorLost++;
                // the back tunnel really exists behind the stair, at the bottom level
                BlockPos c = access.shaft();
                int bottom = access.bottomY();
                int backCells = 0;
                for (int k = 2; k <= 8; k++) if (l.getBlockState(new BlockPos(c.getX(), bottom, c.getZ() - k)).isAir()) backCells++;
                log("M2 miner: back done=" + back + " progress " + w.progress() + "/" + total2.get() + " dug " + air + "/" + all + " stair floors lost " + floorLost + " back tunnel cells " + backCells + "/7 at y=" + bottom);
                check(back, "the miner did not finish the back plan (progress " + w.progress() + "/" + total2.get() + ")");
                check(air * 100 >= all * 95, "the back plan is not dug: " + air + "/" + all);
                check(floorLost == 0, "the stair lost " + floorLost + " floor blocks");
                check(backCells >= 6, "no tunnel behind the stair: " + backCells + "/7");
            });
            // screenshot: the miner's map screen with the direction button
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), S2.getX() + 0.5, S2.getY() + 1, S2.getZ() - 2.5, Set.of(), 0f, 20f, false);
            });
            sp.getConnection().waitForChunksRender();
            for (String code : new String[]{"en_us", "ru_ru"}) {
                TestKit.setLang(ctx, code);
                sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), S2));
                ctx.waitForScreen(ZoneMapScreen.class);
                ctx.waitTicks(10);
                ctx.takeScreenshot("hh_m2_miner_direction_" + code);
                ctx.setScreen(() -> null);
            }
            TestKit.setLang(ctx, "en_us");
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                Settlement st = town(s);
                for (Settlement.SiteRecord r : new ArrayList<>(st.sitesOf(WorkSiteTypes.MINE_ID))) if (r.pos().equals(S2)) l.removeBlock(r.pos(), false);
                SettlerEntity m = (SettlerEntity) l.getEntity(MINER);
                if (m != null) m.setProfession(Professions.UNEMPLOYED);
            });
        } finally {
            Balance.BREAK_PACE_PERCENT = savedPace;
        }
        log("M2 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave M2 failed: " + FAILS);
    }

    private static int amount(Settlement st, Item item) {
        for (Settlement.Order o : st.orders) if (o.item() == item) return o.amount();
        return 0;
    }
}
