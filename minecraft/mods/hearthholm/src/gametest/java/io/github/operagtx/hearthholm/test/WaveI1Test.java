package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.client.screen.TownHallScreen;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave I1: playability of the workers. GUI screenshots (RU and EN, two window sizes), walk-through markers, a slower builder that accepts
 * any bed colour / wood, a 2-wide miner with torches and vein mining, pickaxe-dependent mining speed, tools that break and are replaced,
 * and the XP curve. Run alone (plus the base) with HH_ONLY_I1=1.
 */
final class WaveI1Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos I, STAKE_I, MINE_I;
    private static int xpStart;

    private WaveI1Test() {}

    private static void fail(String m) { FAILS.add(m); log("I1 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static Item item(String path) { return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path)); }

    private static Block block(String path) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path)); }

    /** A tiny test blueprint (never shipped): oak planks floor, a red bed, red carpet, red wool, an oak stair. */
    private static Blueprint testBlueprint() {
        int w = 5, h = 3, d = 4;
        BlockState[] cells = new BlockState[w * h * d];
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) cells[(0 * d + z) * w + x] = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        cells[(1 * d + 1) * w + 1] = bed.setValue(BedBlock.PART, BedPart.FOOT);
        cells[(1 * d + 2) * w + 1] = bed.setValue(BedBlock.PART, BedPart.HEAD);
        cells[(1 * d + 1) * w + 3] = block("red_carpet").defaultBlockState();
        cells[(1 * d + 2) * w + 3] = block("red_wool").defaultBlockState();
        cells[(1 * d + 3) * w + 2] = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        return new Blueprint(Hearthholm.id("i1_test"), w, h, d, cells);
    }

    private static void setLang(ClientGameTestContext ctx, String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
        });
        var fut = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
        ctx.waitFor(mc -> fut.isDone(), 2400);
        ctx.waitTicks(120);   // the loading overlay fades out
    }

    private static void setWindow(ClientGameTestContext ctx, int w, int h) {
        ctx.getInput().resizeWindow(w, h);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
        });
        ctx.waitTicks(8);
    }

    private static void shots(ClientGameTestContext ctx, TestSingleplayerContext sp, String lang, int w, int h) {
        String tag = lang + "_" + w + "x" + h;
        setWindow(ctx, w, h);
        sp.getServer().runOnServer(s -> {
            SettlerEntity best = settlers(s).getFirst();
            for (SettlerEntity e : settlers(s)) if (e.profile().traitList().size() + e.moodEntries().size() > best.profile().traitList().size() + best.moodEntries().size()) best = e;
            best.setProfession(Professions.MINER);
            SettlementService.openSettler(player(s), best);
        });
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i1_settler_" + tag);
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), STAKE_I));
        ctx.waitForScreen(WorkSiteScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i1_stake_" + tag);
        ctx.setScreen(() -> null);
        log("I1 GUI screenshots " + tag + " done");
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");
        // real numbers again (the earlier waves run with an accelerated builder and miner)
        Balance.BUILD_DELAY_TICKS = HearthholmClientTest.DEF_BUILD_DELAY;
        Balance.BREAK_PACE_PERCENT = HearthholmClientTest.DEF_BREAK_PACE;
        AtomicLong placedStart = new AtomicLong();

        // the earlier waves left the player far away: bring the town's chunks (and its residents) back first
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            p.teleportTo(s.overworld(), DESK.getX() + 0.5, DESK.getY() + 1, DESK.getZ() - 1.5, Set.of(), 0f, 10f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(60);
        sp.getConnection().waitForChunksRender();

        // ---- 1. setup of the I1 area, collision, mining speed, XP curve, plan shape
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            st.alarm = false;
            st.renown = Math.max(st.renown, 400);
            // robust: earlier waves (raids, parties, spawned showcase settlers) leave a varying cast; gather the residents at the hall, hire more if needed
            for (SettlerEntity e : l.getEntitiesOfClass(SettlerEntity.class, new net.minecraft.world.phys.AABB(O).inflate(600))) e.teleportTo(DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 2.5);
            topUp(s, 7);
            log("I1 cast: settlers=" + settlers(s).size() + " residents=" + st.residents.size());
            I = O.offset(-4, 0, 30);
            for (int dx = -4; dx <= 36; dx++)
                for (int dz = -4; dz <= 24; dz++) {
                    for (int dy = 0; dy <= 8; dy++) l.setBlock(I.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    l.setBlock(I.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int dy = 2; dy <= 10; dy++) l.setBlock(I.offset(dx, -dy, dz), Blocks.STONE.defaultBlockState(), 2);
                }
            // the old base work sites would compete for the workers
            for (var rec : new ArrayList<>(st.sites)) {
                BlockPos sp0 = rec.pos();
                if (!sp0.equals(STAKE_I) && sp0.distSqr(I) > 100) l.setBlock(sp0, Blocks.AIR.defaultBlockState(), 3);
            }
            // marker collision: every zone marker is walk-through, its outline stays
            int n = 0, bad = 0;
            for (WorkSiteBlock b : ModBlocks.WORK_SITES) {
                BlockState bs = b.defaultBlockState();
                boolean noCol = bs.getCollisionShape(l, I, CollisionContext.empty()).isEmpty();
                boolean shape = !bs.getShape(l, I, CollisionContext.empty()).isEmpty();
                n++;
                if (!noCol || !shape) { bad++; log("I1 marker " + b + " collisionEmpty=" + noCol + " outline=" + shape); }
            }
            log("I1 stake collision empty: markers=" + n + " bad=" + bad);
            check(bad == 0, "markers are not walk-through");

            // mining speed: vanilla destroy speed of the held tool, no pickaxe = hand speed
            SettlerEntity e = settlers(s).getFirst();
            e.setProfession(Professions.MINER);
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockPos at = I.above(2);
            e.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            int hand = e.breakTicks(stone, at);
            e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
            int wood = e.breakTicks(stone, at);
            e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            int iron = e.breakTicks(stone, at);
            e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
            int dia = e.breakTicks(stone, at);
            int dirtIron = e.breakTicks(Blocks.DIRT.defaultBlockState(), at);
            int vanillaIron = Math.round(1.5f * 30f / 6f);
            log("I1 mining speed on stone (ticks per block): hand=" + hand + " wooden=" + wood + " iron=" + iron + " diamond=" + dia + " (vanilla iron " + vanillaIron
                    + ", pace " + Balance.BREAK_PACE_PERCENT + "%), dirt with iron=" + dirtIron);
            check(hand > wood && wood > iron && iron >= dia, "pickaxe tier does not change the mining speed");
            check(wood >= 2 * iron - 2, "wooden pickaxe is not clearly slower than iron");
            check(iron >= vanillaIron, "iron pickaxe is faster than a player (" + iron + " < " + vanillaIron + ")");
            e.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

            // XP curve: one simulated day of steady work (12000 active ticks)
            var prof = Professions.MINER.id();
            int before = e.xp(prof);
            for (int t = 0; t < 12000; t++) e.workTick();
            int day1 = e.xp(prof) - before;
            int lvlAfterDay = e.level(prof);
            double daysToL5 = Balance.LEVEL_XP[4] / (double) Math.max(1, day1);
            log("I1 xp after 1 simulated day = " + day1 + " -> level " + lvlAfterDay + " (level 2 at " + Balance.LEVEL_XP[1] + ", level 5 at " + Balance.LEVEL_XP[4]
                    + " = " + String.format("%.1f", daysToL5) + " days)");
            check(day1 >= Balance.LEVEL_XP[1] * 0.8 && day1 <= Balance.LEVEL_XP[1] * 1.5, "one day of work should give about level 2");
            check(daysToL5 >= 7.5 && daysToL5 <= 11, "level 5 should take 8-10 days");
            check(lvlAfterDay >= 2 && lvlAfterDay <= 2, "a day of work should be level 2, not " + lvlAfterDay);
            e.setProfession(Professions.UNEMPLOYED);
            // restore: this settler's miner xp is not used by the scenario
            xpStart = 0;

            // spiral plan: 2 lanes (more dug cells than a 1-wide staircase) and torches every <= 8 steps
            var plan = MinePlanner.plan(I.offset(30, 0, 20), Direction.SOUTH, MinePlanner.SPIRAL, 24, 8, false, true);
            int torches = 0, lastY = Integer.MIN_VALUE, maxGap = 0, digs = 0;
            for (var step : plan.steps()) {
                if (step.kind() == MinePlanner.Kind.DIG) digs++;
                if (step.kind() == MinePlanner.Kind.TORCH) {
                    torches++;
                    if (lastY != Integer.MIN_VALUE) maxGap = Math.max(maxGap, Math.abs(lastY - step.pos().getY()));   // one ring step = one block of depth
                    lastY = step.pos().getY();
                }
            }
            log("I1 spiral plan depth 24: dig steps=" + digs + " torches=" + torches + " max depth between torches=" + maxGap + " (every " + Balance.MINE_TORCH_EVERY_SPIRAL + ")");
            check(maxGap <= 8, "spiral torches too far apart: " + maxGap);
            check(torches >= 24 / 8, "spiral has too few torches");
            check(digs > 24 * 3 * 2, "spiral is not 2 wide");
            p.teleportTo(l, I.getX() + 6, I.getY() + 3, I.getZ() - 3, Set.of(), 0f, 20f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();

            // the stake for the GUI shots (long blueprint name) and, later, the build test
            STAKE_I = I.offset(4, 0, 2);
            l.setBlock(STAKE_I, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, STAKE_I, p);
            if (l.getBlockEntity(STAKE_I) instanceof WorkSiteBlockEntity w) {
                int idx = -1;
                for (int i = 0; i < BlueprintLibrary.all().size(); i++) if (BlueprintLibrary.byIndex(i).id().getPath().equals("workshop")) idx = i;
                w.setOption("blueprint", Math.max(0, idx));
            }
        });

        // ---- 2. GUI: settler screen and stake screen, RU and EN, two window sizes
        boolean langOk = true;
        try {
            setLang(ctx, "ru_ru");
        } catch (Throwable t) {
            langOk = false;
            fail("could not switch the language: " + t);
        }
        shots(ctx, sp, "ru", 854, 480);
        shots(ctx, sp, "ru", 1280, 720);
        sp.getServer().runOnServer(s -> SettlementService.openTownHall(player(s), DESK));
        ctx.waitForScreen(TownHallScreen.class);
        ctx.clickScreenButton("gui.hearthholm.tab.people");
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_i1_townhall_people_ru_1280x720");
        ctx.setScreen(() -> null);
        if (langOk) setLang(ctx, "en_us");
        shots(ctx, sp, "en", 1280, 720);
        shots(ctx, sp, "en", 854, 480);
        setWindow(ctx, 854, 480);
        ctx.runOnClient(mc -> mc.options.guiScale().set(0));
        ctx.waitTicks(4);

        // ---- 3. builder (any colour / any wood, slower pace) and miner (2 wide, veins, torches, tools) work at the same time
        AtomicInteger builderTotal = new AtomicInteger();
        AtomicBoolean builderDone = new AtomicBoolean(), minerDone = new AtomicBoolean();
        AtomicLong firstPlace = new AtomicLong(-1), lastPlace = new AtomicLong(-1), lastPlaced = new AtomicLong(-1);
        AtomicBoolean toolBroke = new AtomicBoolean(), toolReplaced = new AtomicBoolean(), torchSeen = new AtomicBoolean();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            Blueprint bp = testBlueprint();
            BlueprintLibrary.register(bp);
            int idx = BlueprintLibrary.ids().indexOf(bp.id());
            // storage: nothing red, no oak. The builder must use what is there: a blue bed, lime carpet, cyan wool, spruce planks and stairs
            List<Container> storage = Storage.resolve(l, st.storagePositions());
            for (Container c : storage) for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack it = c.getItem(i);
                if (it.is(net.minecraft.tags.ItemTags.BEDS) || it.is(net.minecraft.tags.ItemTags.WOOL) || it.is(net.minecraft.tags.ItemTags.WOOL_CARPETS)
                        || it.is(net.minecraft.tags.ItemTags.PLANKS) || it.is(net.minecraft.tags.ItemTags.WOODEN_STAIRS) || it.is(Items.IRON_PICKAXE)) c.setItem(i, ItemStack.EMPTY);
            }
            l.setBlock(STAKE_I.north(), Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(STAKE_I.north()) instanceof Container c) {
                c.setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 32));
                c.setItem(1, new ItemStack(Items.SPRUCE_STAIRS, 4));
                c.setItem(2, new ItemStack(Items.BED.blue(), 2));
                c.setItem(3, new ItemStack(item("lime_carpet"), 4));
                c.setItem(4, new ItemStack(item("cyan_wool"), 4));
            }
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(STAKE_I);
            w.setOption("blueprint", idx);
            w.setOption("build", 1);
            w.setProgress(0);
            builderTotal.set(bp.ops(STAKE_I, w.facing()).size());
            log("I1 builder: blueprint " + bp.id() + " ops=" + builderTotal.get() + " origin offset from stake=" + bp.toWorld(STAKE_I, w.facing(), 0, 0, 0).distManhattan(STAKE_I)
                    + " (stake at " + STAKE_I.toShortString() + ", first cell " + bp.toWorld(STAKE_I, w.facing(), bp.width() / 2, 0, 0).toShortString() + ")");
            // the mine: a 2-wide tunnel to the south with a vein of iron ore beside it and a chest with a pickaxe at the marker
            MINE_I = I.offset(24, 0, 2);
            for (int dx = -6; dx <= 5; dx++) for (int dz = 1; dz <= 12; dz++) for (int dy = -2; dy <= 3; dy++)
                l.setBlock(MINE_I.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
            for (int dx = 1; dx <= 3; dx++) for (int dz = 5; dz <= 7; dz++) for (int dy = 0; dy <= 1; dy++)
                l.setBlock(MINE_I.offset(dx, dy, dz), (dx + dz + dy) % 2 == 0 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState(), 2);
            l.setBlock(MINE_I, ModBlocks.MINE_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, MINE_I, p);
            l.setBlock(MINE_I.east(), Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(MINE_I.east()) instanceof Container c) {
                c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                c.setItem(1, new ItemStack(Items.TORCH, 16));
            }
            WorkSiteBlockEntity mw = (WorkSiteBlockEntity) l.getBlockEntity(MINE_I);
            mw.setOption("mode", 2);
            mw.setOption("length", 8);
            mw.setOption("branches", 0);
            mw.setOption("torches", 1);
            mw.setProgress(0);
            Balance.BREAK_PACE_PERCENT = 60;   // test-only acceleration of the miner; the speed itself was checked above
            List<SettlerEntity> all = settlers(s);
            SettlerEntity builder = all.get(1), miner = all.get(0);
            for (SettlerEntity e : all) if (e != builder && e != miner) e.setProfession(Professions.UNEMPLOYED);
            builder.setProfession(Professions.BUILDER);
            builder.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            builder.markArrived();
            builder.teleportTo(STAKE_I.getX() + 0.5, STAKE_I.getY(), STAKE_I.getZ() - 3.5);
            miner.setProfession(Professions.MINER);
            ItemStack worn = new ItemStack(Items.WOODEN_PICKAXE);
            worn.setDamageValue(worn.getMaxDamage() - 3);   // breaks after three blocks
            miner.setItemSlot(EquipmentSlot.MAINHAND, worn);
            miner.markArrived();
            miner.teleportTo(MINE_I.getX() + 0.5, MINE_I.getY(), MINE_I.getZ() - 1.5);
            xpStart = miner.xp(Professions.MINER.id());
            placedStart.set(st.stats.getOrDefault("placed", 0));
            log("I1 workers: builder " + builder.getName().getString() + ", miner " + miner.getName().getString() + " with a pickaxe at " + worn.getDamageValue() + "/" + worn.getMaxDamage());
        });
        long[] t0 = {0};
        sp.getServer().runOnServer(s -> t0[0] = s.overworld().getGameTime());
        for (int round = 0; round < 90 && !(builderDone.get() && minerDone.get()); round++) {
            ctx.waitTicks(40);
            sp.getServer().runCommand("time set 5000");
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                Settlement st = town(s);
                long now = l.getGameTime();
                long placed = st.stats.getOrDefault("placed", 0);
                if (placed != lastPlaced.get()) {
                    if (firstPlace.get() < 0 && placed > placedStart.get() + 1) firstPlace.set(now);
                    if (placed > placedStart.get() + 1) lastPlace.set(now);
                    lastPlaced.set(placed);
                }
                if (!(l.getBlockEntity(STAKE_I) instanceof WorkSiteBlockEntity sw) || !sw.flag("build")) builderDone.set(true);   // K1: the stake stays, building switches off
                SettlerEntity miner = null;
                for (SettlerEntity e : settlers(s)) if (e.professionId().equals(Professions.MINER.id())) miner = e;
                if (miner != null) {
                    ItemStack hand = miner.getMainHandItem();
                    if (hand.isEmpty() && !toolReplaced.get()) toolBroke.set(true);
                    // the breaking and the walk to the chest can happen between two polls: the wooden one is gone, the iron one is in the hand
                    if (hand.is(Items.IRON_PICKAXE)) { toolReplaced.set(true); toolBroke.set(true); }
                }
                if (l.getBlockEntity(MINE_I) instanceof WorkSiteBlockEntity mw && mw.progress() >= MinePlanner.plan(mw).steps().size()) minerDone.set(true);
                for (int k = 1; k <= 8 && !torchSeen.get(); k++)
                    for (int dy = 0; dy <= 2; dy++)
                        for (int dx = -2; dx <= 1; dx++)
                            if (l.getBlockState(MINE_I.offset(dx, dy, k)).getBlock() instanceof WallTorchBlock) torchSeen.set(true);
            });
            if (round % 6 == 5) sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                WorkSiteBlockEntity bw = l.getBlockEntity(STAKE_I) instanceof WorkSiteBlockEntity b ? b : null;
                WorkSiteBlockEntity mw = l.getBlockEntity(MINE_I) instanceof WorkSiteBlockEntity m ? m : null;
                log("I1 progress builder " + (bw == null ? "done" : bw.progress() + "/" + builderTotal.get()) + " miner " + (mw == null ? "?" : mw.progress() + "/" + MinePlanner.plan(mw).steps().size())
                        + " broke=" + toolBroke.get() + " replaced=" + toolReplaced.get());
            });
            if (round == 20) sp.getServer().runOnServer(s -> {   // a photo of the builder at work, away from the stake
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), STAKE_I.getX() + 7.5, STAKE_I.getY() + 3, STAKE_I.getZ() + 2.5, Set.of(), 90f, 25f, false);
            });
        }
        ctx.takeScreenshot("hh_i1_builder_work");

        // ---- 4. results
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Blueprint bp = testBlueprint();
            long ticks = lastPlace.get() - firstPlace.get();
            long blocks = lastPlaced.get() - placedStart.get() - 1;
            double per = blocks > 0 ? ticks / (double) blocks : -1;
            log("I1 build time per block: " + String.format("%.1f", per) + " ticks (blocks placed " + (blocks + 1) + " in " + ticks + " ticks, delay setting " + Balance.BUILD_DELAY_TICKS + ", old pace about 6)");
            check(builderDone.get(), "builder did not finish the test building");
            check(per >= 22, "builder is not slow enough: " + per + " ticks per block");
            // the blueprint's red bed / carpet / wool / oak became whatever was in the chest
            BlockPos bedPos = bp.toWorld(STAKE_I, Direction.SOUTH, 1, 1, 1), carpetPos = bp.toWorld(STAKE_I, Direction.SOUTH, 3, 1, 1), woolPos = bp.toWorld(STAKE_I, Direction.SOUTH, 3, 1, 2);
            BlockPos floorPos = bp.toWorld(STAKE_I, Direction.SOUTH, 0, 0, 0), stairPos = bp.toWorld(STAKE_I, Direction.SOUTH, 2, 1, 3);
            BlockState bed = l.getBlockState(bedPos), carpet = l.getBlockState(carpetPos), wool = l.getBlockState(woolPos), floor = l.getBlockState(floorPos), stairs = l.getBlockState(stairPos);
            log("I1 builder placed: bed=" + bed.getBlock() + " carpet=" + carpet.getBlock() + " wool=" + wool.getBlock() + " floor=" + floor.getBlock() + " stairs=" + stairs.getBlock());
            check(bed.is(BlockTags.BEDS) && bed.getBlock() != Blocks.BED.red() && bed.getBlock() == Blocks.BED.blue(), "bed is not the blue one from storage");
            check(carpet.getBlock() == block("lime_carpet"), "carpet is not lime");
            check(wool.getBlock() == block("cyan_wool"), "wool is not cyan");
            check(floor.getBlock() == Blocks.SPRUCE_PLANKS, "floor is not spruce");
            check(stairs.getBlock() == Blocks.SPRUCE_STAIRS && stairs.getValue(StairBlock.FACING) == Direction.NORTH, "stair variant lost its facing");
            BlockState bedHead = l.getBlockState(bedPos.south());
            check(bedHead.getBlock() == Blocks.BED.blue() && bedHead.getValue(BedBlock.PART) == BedPart.HEAD, "bed head half missing");
            log("I1 different colour OK: red bed in the blueprint, " + bed.getBlock() + " built");

            // 2-wide tunnel
            int open = 0, cells = 0;
            for (int k = 1; k <= 8; k++) for (int dx = -1; dx <= 0; dx++) for (int dy = 0; dy <= 1; dy++) {
                cells++;
                BlockState b = l.getBlockState(MINE_I.offset(dx, dy, k));
                if (b.getCollisionShape(l, MINE_I.offset(dx, dy, k)).isEmpty()) open++;
            }
            boolean wall = l.getBlockState(MINE_I.offset(-2, 0, 3)).getCollisionShape(l, MINE_I).isEmpty() == false;
            log("I1 2-wide tunnel: open cells " + open + "/" + cells + " (lanes x and x-1, two high, k=1..8), third lane still rock=" + wall);
            check(open == cells, "the tunnel is not 2 wide and 2 high");
            // the vein: 18 ore blocks beside the tunnel, all mined
            int ore = 0;
            for (int dx = 1; dx <= 3; dx++) for (int dz = 5; dz <= 7; dz++) for (int dy = 0; dy <= 1; dy++)
                if (io.github.operagtx.hearthholm.entity.ai.jobs.MinerJob.isOre(l.getBlockState(MINE_I.offset(dx, dy, dz)))) ore++;
            log("I1 vein: ore blocks left of 18 = " + ore + " (whole connected vein should be mined)");
            check(ore == 0, "vein not mined out, " + ore + " ore blocks left");
            check(minerDone.get(), "miner did not finish the tunnel");
            log("I1 torches placed in the tunnel: " + torchSeen.get());
            check(torchSeen.get(), "no torch in the tunnel");
            log("I1 tool broke=" + toolBroke.get() + " replaced from the chest=" + toolReplaced.get());
            check(toolBroke.get() && toolReplaced.get(), "broken pickaxe was not replaced from the chest");
        });

        // ---- 5. no chest at the marker: loot goes to the warehouse; no pickaxe anywhere: "need a pickaxe"
        AtomicInteger sticksBefore = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            l.setBlock(MINE_I.east(), Blocks.AIR.defaultBlockState(), 3);   // no chest next to the marker any more
            WorkSiteBlockEntity mw = (WorkSiteBlockEntity) l.getBlockEntity(MINE_I);
            mw.setOption("length", 16);   // more to dig, so the miner is busy again
            SettlerEntity miner = null;
            for (SettlerEntity e : settlers(s)) if (e.professionId().equals(Professions.MINER.id())) miner = e;
            for (int i = 0; i < miner.inventory().getContainerSize() - 2; i++) if (miner.inventory().getItem(i).isEmpty()) miner.inventory().setItem(i, new ItemStack(Items.STICK, 64));
            sticksBefore.set(stock(s, Items.STICK));
            log("I1 miner pockets full of sticks, no chest at the marker, warehouse sticks " + sticksBefore.get());
        });
        for (int i = 0; i < 40; i++) {
            ctx.waitTicks(40);
            sp.getServer().runCommand("time set 5000");
            AtomicBoolean got = new AtomicBoolean();
            sp.getServer().runOnServer(s -> got.set(stock(s, Items.STICK) > sticksBefore.get()));
            if (got.get()) break;
        }
        sp.getServer().runOnServer(s -> {
            int now = stock(s, Items.STICK);
            log("I1 loot to the warehouse without a marker chest: sticks " + sticksBefore.get() + " -> " + now);
            check(now > sticksBefore.get(), "miner did not carry the loot to the warehouse");
            // remove every pickaxe: the miner has to say it needs one
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            for (Container c : Storage.resolve(l, st.storagePositions())) for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(net.minecraft.tags.ItemTags.PICKAXES)) c.setItem(i, ItemStack.EMPTY);
            SettlerEntity miner = null;
            for (SettlerEntity e : settlers(s)) if (e.professionId().equals(Professions.MINER.id())) miner = e;
            miner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            for (int i = 0; i < miner.inventory().getContainerSize(); i++) if (miner.inventory().getItem(i).is(net.minecraft.tags.ItemTags.PICKAXES)) miner.inventory().setItem(i, ItemStack.EMPTY);
        });
        AtomicBoolean complained = new AtomicBoolean();
        AtomicInteger waited = new AtomicInteger();
        for (int i = 0; i < 20 && !complained.get(); i++) {
            ctx.waitTicks(40);
            sp.getServer().runCommand("time set 5000");
            sp.getServer().runOnServer(s -> {
                for (SettlerEntity e : settlers(s)) if (e.professionId().equals(Professions.MINER.id())) {
                    String status = e.status().getString();
                    if (status.contains("pickaxe") || status.contains("кирк")) complained.set(true);
                    if (e.hasBubble() && waited.incrementAndGet() > 0) log("I1 bubble: " + e.bubble().getString());
                    log("I1 miner without pickaxe: status=\"" + status + "\" thought=" + e.hasThought("no_tool") + " bubble=\"" + e.bubble().getString() + "\" hand=" + e.getMainHandItem().getItem());
                }
            });
        }
        check(complained.get(), "miner without a pickaxe did not say so");
        ctx.takeScreenshot("hh_i1_need_pickaxe");

        sp.getServer().runOnServer(s -> {
            // give the pickaxe back and the work site back to the base for later waves
            Balance.BREAK_PACE_PERCENT = HearthholmClientTest.DEF_BREAK_PACE;
        });
        if (!FAILS.isEmpty()) throw new AssertionError("wave I1 failed: " + FAILS);
        log("I1 OK");
    }
}
