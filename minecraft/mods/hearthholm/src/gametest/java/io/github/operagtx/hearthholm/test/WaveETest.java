package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.block.SettlementBannerBlock;
import io.github.operagtx.hearthholm.client.screen.BoardScreen;
import io.github.operagtx.hearthholm.client.screen.TradeScreen;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.crafting.Fuel;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ashborn.IgniteGoal;
import io.github.operagtx.hearthholm.entity.trader.TraderEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.Civic;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.threat.Threats;
import io.github.operagtx.hearthholm.town.Caravan;
import io.github.operagtx.hearthholm.town.Decor;
import io.github.operagtx.hearthholm.town.Quests;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/** Wave E checks: tier-locked blueprints, tavern evening, caravan trade, decor thought, bulletin board quests, fuel, fire markers. */
final class WaveETest {
    private static BlockPos TAV_KEG, TAV_CAM, TAV_KEEPER, MARKET, BULLETIN;
    private static int oldRenown, coal0;

    private WaveETest() {}

    private static void lookAt(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, false);
    }

    private static int bpIndex(String name) {
        return BlueprintLibrary.ids().indexOf(Hearthholm.id(name));
    }

    /** Builds a blueprint instantly (clear then place), the way the builder would, and returns its world-space helper. */
    private static Blueprint place(ServerLevel l, String name, BlockPos stake, Direction facing) {
        Blueprint bp = BlueprintLibrary.get(Hearthholm.id(name));
        for (var op : bp.ops(stake, facing)) {
            if (op.clear()) { l.setBlock(op.pos(), Blocks.AIR.defaultBlockState(), 2); continue; }
            l.setBlock(op.pos(), op.state(), 2);
            if (op.companionPos() != null) l.setBlock(op.companionPos(), op.companionState(), 2);
        }
        return bp;
    }

    private static void stockUp(MinecraftServer s, ItemStack... stacks) {
        Settlement st = town(s);
        for (ItemStack stack : stacks) {
            ItemStack rest = Storage.insert(Storage.resolve(s.overworld(), st.storagePositions()), stack);
            if (!rest.isEmpty()) throw new AssertionError("warehouse full for " + stack);
        }
    }

    private static List<SettlerEntity> civilians(MinecraftServer s) {
        List<SettlerEntity> out = new ArrayList<>();
        for (SettlerEntity e : settlers(s)) {
            if (e.inParty()) e.leaveParty();
            if (!e.isLeaving() && !e.professionId().equals(Professions.GUARD.id()) && !e.professionId().equals(Professions.ARCHER.id())) out.add(e);
        }
        return out;
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 6000");
        BlockPos stake = STAKE;

        // ---- 1. tier lock: the server refuses blueprints above the settlement's tier; renown unlocks them
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            st.alarm = false;
            p.teleportTo(l, stake.getX() + 0.5, stake.getY(), stake.getZ() + 3.5, Set.of(), 0f, 10f, false);
            if (!(l.getBlockEntity(stake) instanceof WorkSiteBlockEntity)) {   // the builder finished the house and took the stake away
                l.setBlock(stake, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(io.github.operagtx.hearthholm.worksite.WorkSiteBlock.FACING, Direction.SOUTH), 3);
                SettlementService.onWorkSitePlaced(l, stake, p);
            }
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(stake);
            w.setOption("build", 0);
            w.setOption("blueprint", 0);
            int tavern = bpIndex("tavern"), barracks = bpIndex("barracks"), workshop = bpIndex("workshop"), small = bpIndex("small_house");
            oldRenown = st.renown;
            String[] lines = new String[4];
            int[] renowns = {10, 70, 400, 1100};
            String[] names = {"camp", "hamlet", "village", "town"};
            for (int i = 0; i < 4; i++) {
                st.renown = renowns[i];
                String tier = st.tier().key();
                StringBuilder sb = new StringBuilder(tier + ":");
                for (int bp : new int[]{small, workshop, tavern, barracks}) {
                    w.setOption("blueprint", 0);
                    SettlementService.handleWorkSite(p, new Payloads.WorkSiteAction(stake, "blueprint", bp));
                    boolean ok = w.option("blueprint") == bp;
                    sb.append(' ').append(BlueprintLibrary.byIndex(bp).id().getPath()).append('=').append(ok ? "open" : "locked");
                    boolean expect = st.tier().index() >= BlueprintLibrary.requiredTier(BlueprintLibrary.byIndex(bp).id());
                    if (ok != expect) throw new AssertionError("tier gate wrong for " + sb);
                }
                lines[i] = sb.toString();
                if (!st.tier().key().equals(names[i])) throw new AssertionError("expected tier " + names[i] + " got " + st.tier().key());
            }
            for (String line : lines) log("TIER " + line);
            // a bought blueprint opens early
            st.renown = 10;
            st.civic.unlocked.add("tavern");
            w.setOption("blueprint", 0);
            SettlementService.handleWorkSite(p, new Payloads.WorkSiteAction(stake, "blueprint", tavern));
            log("TIER bought plans: tavern open at camp=" + (w.option("blueprint") == tavern));
            if (w.option("blueprint") != tavern) throw new AssertionError("bought blueprint not accepted");
            st.civic.unlocked.clear();
            // show the greyed entry in the builder UI (camp tier, barracks selected)
            w.setOption("blueprint", barracks);
            SettlementService.openWorkSite(p, stake);
        });
        ctx.waitForScreen(WorkSiteScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_e1_locked_blueprint");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) s.overworld().getBlockEntity(stake);
            w.setOption("blueprint", 0);
            w.setOption("build", 0);
            st.renown = Math.max(400, oldRenown);
            log("TIER restored renown=" + st.renown + " tier=" + st.tier().key());
        });

        // ---- 2. decor street + "Beautiful street" thought
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            Direction south = Direction.SOUTH;
            BlockState lamp = ModBlocks.LAMP_POST.defaultBlockState();
            l.setBlock(O.offset(1, 0, -3), lamp, 3);
            l.setBlock(O.offset(7, 0, -3), lamp, 3);
            l.setBlock(O.offset(3, 0, -2), ModBlocks.BENCH.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, south), 3);
            l.setBlock(O.offset(5, 0, -2), ModBlocks.FLOWER_BOX.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, south), 3);
            l.setBlock(O.offset(10, 0, -3), ModBlocks.WELL.defaultBlockState(), 3);
            l.setBlock(O.offset(12, 0, -2), ModBlocks.SIGNPOST.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, south), 3);
            l.setBlock(O.offset(4, 0, -4), ModBlocks.SETTLEMENT_BANNER.defaultBlockState().setValue(SettlementBannerBlock.CLOTH, DyeColor.BLUE).setValue(SettlementBannerBlock.EMBLEM, 2), 3);
            l.setBlock(O.offset(9, 0, -5), ModBlocks.SETTLEMENT_BANNER.defaultBlockState().setValue(SettlementBannerBlock.CLOTH, DyeColor.YELLOW).setValue(SettlementBannerBlock.EMBLEM, 0), 3);
            l.setBlock(O.offset(7, 0, -5), ModBlocks.SIGNS.get(2).defaultBlockState(), 3);
            l.setBlock(O.offset(2, 0, -5), ModBlocks.COUNTER.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, south), 3);
            log("DECOR registered=" + st.civic.decor.size());
            if (st.civic.decor.size() < 8) throw new AssertionError("decor not registered");
            List<SettlerEntity> cs = civilians(s);
            for (int i = 0; i < Math.min(3, cs.size()); i++) cs.get(i).teleportTo(O.getX() + 4.5 + i * 1.5, O.getY(), O.getZ() - 2.5);
            SettlerEntity walker = cs.getFirst();
            walker.markArrived();
            Decor.tick(walker);
            log("DECOR near=" + Decor.near(st, walker.blockPosition()) + " thought=" + walker.hasThought("beautiful_street")
                    + " thoughts=" + walker.thoughts().stream().map(t -> t.id() + t.value()).toList());
            if (!walker.hasThought("beautiful_street")) throw new AssertionError("no beautiful street thought");
            // a settler far from any decor does not get it
            SettlerEntity far = cs.getLast();
            far.thoughts().removeIf(t -> t.id().equals("beautiful_street"));
            far.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() + 22.5);
            Decor.tick(far);
            log("DECOR far settler near=" + Decor.near(st, far.blockPosition()) + " thought=" + far.hasThought("beautiful_street"));
        });

        // ---- 3. tavern: build the blueprint, an innkeeper, an evening gathering and a meal
        sp.getServer().runCommand("time set 11900");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            BlockPos tavStake = O.offset(-11, 0, -12);
            Blueprint bp = place(l, "tavern", tavStake, Direction.SOUTH);
            TAV_KEG = bp.toWorld(tavStake, Direction.SOUTH, 4, 1, 5);
            TAV_CAM = bp.toWorld(tavStake, Direction.SOUTH, 1, 1, 3);      // wave G: camera spot inside, keg and counter in view
            TAV_KEEPER = bp.toWorld(tavStake, Direction.SOUTH, 6, 1, 3);
            log("TAVERN built, keg at " + TAV_KEG.toShortString() + " block=" + l.getBlockState(TAV_KEG).getBlock().getDescriptionId());
            SettlementService.onWorkshopPlaced(l, TAV_KEG, p);
            boolean found = false;
            for (var b : st.buildings) if (b.is(Workshop.TAVERN.role())) {
                found = b.valid();
                log("TAVERN building roles=" + b.roles() + " valid=" + b.valid() + " problem=" + b.problem() + " title=" + b.title().getString());
            }
            if (!found) throw new AssertionError("tavern not recognised");
            stockUp(s, new ItemStack(Items.BREAD, 32));
            List<SettlerEntity> cs = civilians(s);
            if (cs.size() < 3) throw new AssertionError("need 3 civilians, have " + cs.size());
            cs.getFirst().setProfession(Professions.INNKEEPER);
            for (SettlerEntity e : cs) {
                e.markArrived();
                e.thoughts().removeIf(t -> t.id().startsWith("tavern"));
                e.setFood(10f);
                e.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() - 1.5);
            }
            log("TAVERN settlers=" + cs.size() + " innkeeper=" + cs.getFirst().getName().getString() + " time=" + cs.getFirst().dayTime()
                    + " offDuty=" + cs.stream().filter(e -> io.github.operagtx.hearthholm.town.Tavern.offDuty(e)).count());
        });
        for (int i = 0; i < 12; i++) {
            ctx.waitTicks(40);
            boolean[] ok = {false};
            sp.getServer().runOnServer(s -> {
                long patrons = civilians(s).stream().filter(io.github.operagtx.hearthholm.town.Tavern::isPatron).count();
                ok[0] = patrons >= 3 && town(s).stats.getOrDefault("tavern_served", 0) >= 2;
            });
            if (ok[0]) break;
        }
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            List<SettlerEntity> cs = civilians(s);
            long patrons = cs.stream().filter(io.github.operagtx.hearthholm.town.Tavern::isPatron).count();
            long fed = cs.stream().filter(e -> e.hasThought("tavern_meal")).count();
            for (SettlerEntity e : cs) log("TAVERN guest " + e.getName().getString() + " " + e.professionId().getPath() + " act=" + e.brain().currentActivity()
                    + " status='" + e.status().getString() + "' thoughts=" + e.thoughts().stream().map(t -> t.id() + t.value()).toList());
            log("TAVERN gathering patrons=" + patrons + " withMeal=" + fed + " served=" + st.stats.get("tavern_served") + " visits=" + st.stats.get("tavern_visits"));
            if (patrons < 2) throw new AssertionError("nobody gathered at the tavern");
            if (st.stats.getOrDefault("tavern_served", 0) < 1) throw new AssertionError("innkeeper served nobody");
        });
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            // inside the door, looking at the counter and the guests
            // wave G: guests step out for the shot, only the innkeeper stays (beside the counter); camera inside, keg + counter in view
            for (SettlerEntity e : settlers(s)) {
                if (e.professionId().equals(Professions.INNKEEPER.id())) e.teleportTo(TAV_KEEPER.getX() + 0.5, TAV_KEEPER.getY(), TAV_KEEPER.getZ() + 0.5);
                else e.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() + 30.5);
            }
            lookAt(p, TAV_CAM.getX() + 0.5, TAV_CAM.getY(), TAV_CAM.getZ() + 0.5, TAV_KEG.getX() + 0.5, TAV_KEG.getY() + 0.4, TAV_KEG.getZ() + 0.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_e2_tavern");

        // the decorated street at dusk (chat from the tier checks has faded by now)
        sp.getServer().runOnServer(s -> lookAt(player(s), O.getX() + 14.5, O.getY(), O.getZ() - 1.5, O.getX() + 5.0, O.getY() + 1.2, O.getZ() - 3.0));
        ctx.waitTicks(20);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_e3_decor_street");

        // ---- 4. caravan: arrives by itself a week after the last one, trades, leaves after a day
        sp.getServer().runCommand("time set 1500");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            BlockPos mStake = O.offset(21, 0, -12);
            Blueprint bp = place(l, "market_stall", mStake, Direction.SOUTH);
            MARKET = bp.toWorld(mStake, Direction.SOUTH, 3, 1, 3);
            log("MARKET stall at " + MARKET.toShortString() + " block=" + l.getBlockState(MARKET).getBlock().getDescriptionId()
                    + " registered=" + st.civic.markets.contains(MARKET) + " decor=" + st.civic.decor.size());
            if (!st.civic.markets.contains(MARKET)) throw new AssertionError("market stall not registered");
            st.civic.lastCaravan = SettlementService.day(l) - 8;
            st.civic.caravanEnd = -1;
            stockUp(s, new ItemStack(Items.COBBLESTONE, 300), new ItemStack(ModItems.HEARTH_MARK, 64), new ItemStack(ModItems.HEARTH_MARK, 40), new ItemStack(Items.COAL, 2));
            coal0 = stock(s, Items.COAL);
            log("CARAVAN due: tier=" + st.tier().key() + " lastCaravan=" + st.civic.lastCaravan + " day=" + SettlementService.day(l));
        });
        ctx.waitTicks(80);
        for (int i = 0; i < 8; i++) {
            boolean[] near = {false};
            sp.getServer().runOnServer(s -> {
                Settlement st = town(s);
                if (st.civic.trader != null && s.overworld().getEntity(st.civic.trader) instanceof TraderEntity t)
                    near[0] = t.blockPosition().distSqr(MARKET) < 100;
            });
            if (near[0]) break;
            ctx.waitTicks(50);
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            if (st.civic.trader == null) throw new AssertionError("caravan did not arrive");
            TraderEntity t = (TraderEntity) l.getEntity(st.civic.trader);
            var llama = l.getEntity(st.civic.llama);
            boolean walked = t.blockPosition().distSqr(MARKET) < 100;
            log("CARAVAN arrived: trader=" + t.blockPosition().toShortString() + " llama=" + (llama == null ? "none" : llama.blockPosition().toShortString())
                    + " market=" + MARKET.toShortString() + " walkedToMarket=" + walked + " endsIn=" + (st.civic.caravanEnd - l.getGameTime()) + " ticks");
            if (llama == null) throw new AssertionError("no llama");
            if (!walked) {
                t.teleportTo(MARKET.getX() + 0.5, MARKET.getY(), MARKET.getZ() + 2.5);
                llama.teleportTo(MARKET.getX() + 2.5, MARKET.getY(), MARKET.getZ() + 2.5);
            }
            ServerPlayer p = player(s);
            lookAt(p, MARKET.getX() + 0.5, MARKET.getY(), MARKET.getZ() + 6.5, MARKET.getX() + 0.5, MARKET.getY() + 1.2, MARKET.getZ() + 1.5);
        });
        ctx.waitTicks(30);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_e4_market_caravan");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer p = player(s);
            int marks0 = stock(s, ModItems.HEARTH_MARK), cobble0 = stock(s, Items.COBBLESTONE), seeds0 = stock(s, Items.WHEAT_SEEDS);
            var lines = Caravan.sells(l, st);
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).stack().is(Items.COBBLESTONE)) idx = i;
            if (idx < 0) throw new AssertionError("trader does not want the surplus cobblestone: " + lines);
            int earned = Caravan.sell(p, l, st, idx);
            int marks1 = stock(s, ModItems.HEARTH_MARK), cobble1 = stock(s, Items.COBBLESTONE);
            log("TRADE sold surplus cobblestone " + cobble0 + "->" + cobble1 + " for " + earned + " marks (" + marks0 + "->" + marks1 + ")");
            if (earned <= 0 || cobble1 >= cobble0 || marks1 != marks0 + earned) throw new AssertionError("sell failed");
            boolean bought = Caravan.buy(p, l, st, 0);
            int marks2 = stock(s, ModItems.HEARTH_MARK), seeds1 = stock(s, Items.WHEAT_SEEDS);
            log("TRADE bought wheat seeds ok=" + bought + " seeds " + seeds0 + "->" + seeds1 + " marks " + marks1 + "->" + marks2);
            if (!bought || seeds1 != seeds0 + 16 || marks2 != marks1 - 2) throw new AssertionError("buy failed");
            var offers = Caravan.offers(st);
            for (int i = 0; i < offers.size(); i++) {
                var o = offers.get(i);
                if (o.kind().equals("blueprint")) {
                    boolean ok = Caravan.buy(p, l, st, i);
                    log("TRADE bought plans " + o.ref() + " ok=" + ok + " unlocked=" + st.civic.unlocked + " price=" + o.price());
                    if (!ok || !st.civic.unlocked.contains(o.ref())) throw new AssertionError("blueprint purchase failed");
                } else if (o.kind().equals("hire")) {
                    int before = st.candidates.size();
                    boolean ok = Caravan.buy(p, l, st, i);
                    var c = st.candidates.getFirst();
                    log("TRADE bought rare hire ok=" + ok + " candidates " + before + "->" + st.candidates.size() + " " + c.name().getString()
                            + " legendary=" + c.legendary() + " traits=" + c.traits());
                    if (!ok || st.candidates.size() != before + 1 || !c.legendary()) throw new AssertionError("hire purchase failed");
                }
            }
            log("CARAVAN stats bought=" + st.stats.get("caravan_bought") + " sold=" + st.stats.get("caravan_sold"));
            p.teleportTo(l, MARKET.getX() + 0.5, MARKET.getY(), MARKET.getZ() + 4.5, Set.of(), 180f, 10f, false);
            Caravan.openFromBlock(p, MARKET);
        });
        ctx.waitForScreen(TradeScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_e5_trade_screen");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            st.civic.caravanEnd = l.getGameTime() + 10;
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            int traders = l.getEntitiesOfClass(TraderEntity.class, new AABB(O).inflate(120)).size();
            log("CARAVAN left: trader=" + st.civic.trader + " traderEntities=" + traders + " endsAt=" + st.civic.caravanEnd);
            if (st.civic.trader != null || traders != 0) throw new AssertionError("caravan did not leave");
        });

        // ---- 5. bulletin board: quests (bring and kill), rewards
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer p = player(s);
            BULLETIN = O.offset(6, 0, -1);
            l.setBlock(BULLETIN, ModBlocks.BULLETIN_BOARD.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.NORTH), 3);
            log("BOARD placed registered=" + st.civic.boards.contains(BULLETIN));
            Quests.refresh(l, st, true);
            log("BOARD quests=" + st.civic.quests.stream().map(q -> q.kind() + ":" + q.target().getPath() + "x" + q.count() + "->" + q.reward() + q.amount()).toList());
            if (st.civic.quests.size() != Quests.MAX) throw new AssertionError("board not filled");
            lookAt(p, O.getX() + 6.5, O.getY(), O.getZ() - 4.5, O.getX() + 6.5, O.getY() + 1.2, O.getZ() - 1.0);
            Quests.open(p, BULLETIN);
        });
        ctx.waitForScreen(BoardScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_e6_bulletin_board");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer p = player(s);
            Identifier air = Identifier.withDefaultNamespace("air");
            st.civic.quests.clear();
            int bringId = st.civic.nextQuestId++, killId = st.civic.nextQuestId++;
            st.civic.quests.add(new Civic.Quest(bringId, "bring", Identifier.withDefaultNamespace("apple"), 2, 0, BlockPos.ZERO, 0, "marks", 5, air, false, false));
            st.civic.quests.add(new Civic.Quest(killId, "kill", Identifier.withDefaultNamespace("spider"), 1, 0, MINE, 28, "renown", 25, air, false, false));
            p.getInventory().add(new ItemStack(Items.APPLE, 2));
            int marks0 = stock(s, ModItems.HEARTH_MARK);
            boolean early = Quests.claim(p, l, st, bringId);
            boolean delivered = Quests.deliver(p, l, st, bringId);
            boolean claimed = Quests.claim(p, l, st, bringId);
            int marks1 = stock(s, ModItems.HEARTH_MARK);
            log("QUEST bring: claimEarly=" + early + " delivered=" + delivered + " claimed=" + claimed + " marks " + marks0 + "->" + marks1
                    + " applesLeft=" + p.getInventory().countItem(Items.APPLE));
            if (early || !delivered || !claimed || marks1 != marks0 + 5) throw new AssertionError("bring quest failed");
            int renown0 = st.renown;
            var spider = EntityTypes.SPIDER.spawn(l, MINE.offset(2, 0, 2), EntitySpawnReason.COMMAND);
            spider.kill(l);
            var q = st.civic.quests.get(1);
            log("QUEST kill: progress=" + q.progress() + "/" + q.count() + " done=" + q.done());
            if (!q.done()) throw new AssertionError("kill quest not counted");
            boolean cl = Quests.claim(p, l, st, killId);
            log("QUEST kill claimed=" + cl + " renown " + renown0 + "->" + st.renown + " questsDone=" + st.stats.get("quests_done"));
            if (!cl || st.renown != renown0 + 25) throw new AssertionError("kill reward failed");
            // a pest far from the site does not count
            st.civic.quests.add(new Civic.Quest(st.civic.nextQuestId++, "kill", Identifier.withDefaultNamespace("zombie"), 1, 0, MINE, 28, "marks", 3, air, false, false));
            var zombie = EntityTypes.ZOMBIE.spawn(l, O.offset(-14, 0, -10), EntitySpawnReason.COMMAND);
            zombie.kill(l);
            log("QUEST far kill progress=" + st.civic.quests.getLast().progress());
            if (st.civic.quests.getLast().progress() != 0) throw new AssertionError("far kill counted");
        });

        // ---- 6. fuel: a cook turns raw beef into steaks and burns coal from the warehouse
        sp.getServer().runCommand("time set 4000");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            List<SettlerEntity> cs = civilians(s);
            SettlerEntity cook = null;
            for (SettlerEntity e : cs) if (e.professionId().equals(Professions.COOK.id())) cook = e;
            if (cook == null) for (SettlerEntity e : cs) if (!e.professionId().equals(Professions.INNKEEPER.id())) cook = e;
            cook.setProfession(Professions.COOK);
            cook.markArrived();
            cook.teleportTo(STOVE.getX() + 0.5, STOVE.getY(), STOVE.getZ() + 2.5);
            st.orders.clear();
            SettlementService.setOrder(st, Items.COOKED_BEEF, 3);
            st.stats.remove(Fuel.BUFFER);
            st.stats.remove(Fuel.USED);
            stockUp(s, new ItemStack(Items.BEEF, 3));
            coal0 = stock(s, Items.COAL);
            log("FUEL start: raw beef=" + stock(s, Items.BEEF) + " coal=" + coal0 + " cooked=" + stock(s, Items.COOKED_BEEF) + " cook=" + cook.getName().getString());
        });
        for (int i = 0; i < 14; i++) {
            ctx.waitTicks(100);
            boolean[] done = {false};
            sp.getServer().runOnServer(s -> {
                SettlerEntity cook = null;
                for (SettlerEntity e : civilians(s)) if (e.professionId().equals(Professions.COOK.id())) cook = e;
                Settlement st = town(s);
                log("  cook status='" + (cook == null ? "?" : cook.status().getString()) + "' beef=" + stock(s, Items.BEEF) + " cooked=" + stock(s, Items.COOKED_BEEF)
                        + " coal=" + stock(s, Items.COAL) + " buffer=" + st.stats.getOrDefault(Fuel.BUFFER, 0));
                done[0] = stock(s, Items.COOKED_BEEF) >= 3;
            });
            if (done[0]) break;
        }
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            int coal1 = stock(s, Items.COAL), cooked = stock(s, Items.COOKED_BEEF);
            log("FUEL consumed: coal " + coal0 + "->" + coal1 + " itemsBurned=" + st.stats.getOrDefault(Fuel.USED, 0) + " buffer=" + st.stats.getOrDefault(Fuel.BUFFER, 0)
                    + " cookedBeef=" + cooked);
            if (cooked < 1) throw new AssertionError("cook made no steaks");
            if (st.stats.getOrDefault(Fuel.USED, 0) < 1 || coal1 >= coal0) throw new AssertionError("no fuel was consumed");
        });

        // ---- 7. raid fire: roofs and walls are not targets, fire markers are saved and cleaned up after a restart
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            boolean roof = IgniteGoal.intendedTarget(Blocks.SPRUCE_PLANKS.defaultBlockState()) || IgniteGoal.intendedTarget(Blocks.OAK_LOG.defaultBlockState());
            boolean pal = IgniteGoal.intendedTarget(ModBlocks.PALISADE.defaultBlockState());
            boolean door = IgniteGoal.intendedTarget(Blocks.OAK_DOOR.defaultBlockState());
            boolean fence = IgniteGoal.intendedTarget(Blocks.OAK_FENCE.defaultBlockState());
            log("FIRE targets: planks/logs=" + roof + " palisade=" + pal + " door=" + door + " fence=" + fence);
            if (roof || !pal || !door || !fence) throw new AssertionError("fire target rules wrong");
            BlockPos at = O.offset(-12, 0, 18);
            l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            boolean lit = Threats.ignite(l, at);
            boolean fire = l.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock;
            log("FIRE lit=" + lit + " burning=" + fire + " savedMarkers=" + st.civic.fires.size());
            if (!lit || !fire || !st.civic.fires.contains(at)) throw new AssertionError("fire not tracked");
            Threats.resetFiresForTest();   // as after a server restart: only the saved list remembers it
        });
        ctx.waitTicks(100);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            BlockPos at = O.offset(-12, 0, 18);
            boolean fire = l.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock;
            log("FIRE after restart: burning=" + fire + " savedMarkers=" + town(s).civic.fires.size());
            if (fire) throw new AssertionError("restored fire marker did not put the fire out");
        });
        ctx.waitTicks(260);
        sp.getServer().runOnServer(s -> {
            int left = town(s).civic.fires.size();
            log("FIRE markers forgotten: saved=" + left + " active=" + Threats.activeFires());
            if (left != 0) throw new AssertionError("fire marker not cleaned");
            log("WAVE E OK");
        });
    }
}
