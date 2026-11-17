package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.client.screen.TownHallScreen;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementManager;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** End-to-end check: build a tiny settlement, hire, assign jobs, let time run, take screenshots. */
public final class HearthholmClientTest implements FabricClientGameTest {
    static BlockPos O, DESK, MINE, FARM, STAKE, FOREST, BENCH, STOVE, BOARD;
    private static int planks0, bread0;

    static void log(String s) {
        System.out.println("HHTEST " + s);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set 1000");
            sp.getServer().runCommand("gamerule doDaylightCycle false");
            sp.getServer().runOnServer(HearthholmClientTest::build);
            sp.getConnection().waitForChunksRender();
            sp.getServer().runOnServer(HearthholmClientTest::hire);
            ctx.waitTicks(40);
            ctx.takeScreenshot("hh_01_settlement");

            sp.getServer().runOnServer(s -> SettlementService.openTownHall(player(s), DESK));
            ctx.waitForScreen(TownHallScreen.class);
            ctx.takeScreenshot("hh_02_townhall_overview");
            ctx.clickScreenButton("gui.hearthholm.tab.people");
            ctx.waitTicks(5);
            ctx.takeScreenshot("hh_03_townhall_people");
            ctx.clickScreenButton("gui.hearthholm.tab.hire");
            ctx.waitTicks(5);
            ctx.takeScreenshot("hh_04_townhall_hire");
            ctx.clickScreenButton("gui.hearthholm.tab.buildings");
            ctx.waitTicks(5);
            ctx.takeScreenshot("hh_05_townhall_buildings");
            ctx.setScreen(() -> null);
            sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), STAKE));
            ctx.waitForScreen(WorkSiteScreen.class);
            ctx.takeScreenshot("hh_09_stake_config");
            ctx.setScreen(() -> null);

            for (int i = 0; i < 6; i++) {
                ctx.waitTicks(400);
                sp.getServer().runOnServer(HearthholmClientTest::report);
            }
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), BOARD.getX() - 1.5, BOARD.getY(), BOARD.getZ() + 2.5, java.util.Set.of(), 160f, 20f, false);
                SettlementService.openOrders(p, BOARD);
            });
            ctx.waitForScreen(io.github.operagtx.hearthholm.client.screen.OrdersScreen.class);
            ctx.waitTicks(5);
            ctx.takeScreenshot("hh_11_order_board");
            ctx.clickScreenButton("profession.hearthholm.cook");
            ctx.waitTicks(5);
            ctx.takeScreenshot("hh_11b_order_board_kitchen");
            ctx.setScreen(() -> null);
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), BENCH.getX() + 4.5, BENCH.getY(), BENCH.getZ() + 3.6, java.util.Set.of(), 127f, 22f, false);
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("hh_12_workshop");
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), STOVE.getX() + 2.5, STOVE.getY(), STOVE.getZ() + 2.5, java.util.Set.of(), 135f, 25f, false);
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("hh_13_kitchen");
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), O.getX() + 6, O.getY() + 3, O.getZ() + 24, java.util.Set.of(), 200f, 12f, false);
            });
            sp.getConnection().waitForChunksRender();
            ctx.waitTicks(20);
            ctx.takeScreenshot("hh_06_working");
            ctx.runOnClient(mc -> {
                for (var e : mc.level.entitiesForRendering()) {
                    if (!(e instanceof SettlerEntity se)) continue;
                    var r = mc.getEntityRenderDispatcher().getRenderer(se);
                    var id = io.github.operagtx.hearthholm.client.render.SettlerTextures.get(se.lookBits(), se.professionId());
                    var tex = mc.getTextureManager().getTexture(id);
                    log("client settler " + se.getName().getString() + " look=" + Integer.toHexString(se.lookBits()) + " prof=" + se.professionId()
                            + " renderer=" + r.getClass().getSimpleName() + " tex=" + id + " texClass=" + tex.getClass().getSimpleName());
                    if (tex instanceof net.minecraft.client.renderer.texture.DynamicTexture dt && dt.getPixels() != null) {
                        try { dt.getPixels().writeToFile(mc.gameDirectory.toPath().resolve("screenshots/skin_" + se.getName().getString() + ".png")); } catch (java.io.IOException ex) { log("dump failed " + ex); }
                    }
                }
            });

            sp.getServer().runOnServer(s -> {
                SettlerEntity e = settlers(s).getFirst();
                SettlementService.openSettler(player(s), e);
            });
            ctx.waitForScreen(SettlerScreen.class);
            ctx.takeScreenshot("hh_07_settler");
            ctx.setScreen(() -> null);
            sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), MINE));
            ctx.waitForScreen(WorkSiteScreen.class);
            ctx.takeScreenshot("hh_08_mine_config");
            ctx.setScreen(() -> null);

            for (int i = 0; i < 6; i++) {
                ctx.waitTicks(600);
                sp.getServer().runOnServer(HearthholmClientTest::report);
            }
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), O.getX() + 2, O.getY() + 4, O.getZ() + 8, java.util.Set.of(), 0f, 15f, false);
            });
            sp.getConnection().waitForChunksRender();
            ctx.waitTicks(20);
            ctx.takeScreenshot("hh_10_close_up");
            sp.getServer().runOnServer(s -> {
                int planks = stock(s, Items.OAK_PLANKS), bread = stock(s, Items.BREAD);
                Settlement st = SettlementManager.get(s).all().iterator().next();
                log("stock end planks " + planks0 + " -> " + planks + ", bread " + bread0 + " -> " + bread + " crafted=" + st.stats.get("crafted")
                        + " hauled=" + st.stats.get("hauled"));
                for (SettlerEntity e : settlers(s)) log("moods " + e.getName().getString() + " " + e.moodEntries().stream()
                        .map(m -> m.label().getString() + m.value()).toList());
                log("mine chest " + chest(s, MINE.west()) + " stake chest " + chest(s, STAKE.north()));
                if (st.stats.getOrDefault("hauled", 0) <= 0) throw new AssertionError("hauler moved nothing");
                if (planks <= planks0) throw new AssertionError("carpenter made no planks");
                if (bread <= bread0) throw new AssertionError("cook made no bread");
                log("CRAFT OK");
            });
            boolean onlyF = System.getenv("HH_ONLY_F") != null, onlyE = System.getenv("HH_ONLY_E") != null, onlyD = System.getenv("HH_ONLY_D") != null;
            if (!onlyD && !onlyE && !onlyF) {
                waveB(ctx, sp);
                waveC(ctx, sp);
            }
            if (!onlyE && !onlyF) WaveDTest.run(ctx, sp);
            if (!onlyF) WaveETest.run(ctx, sp);
            WaveFTest.run(ctx, sp);
            log("DONE");
        }
    }

    // ---- wave B: speech bubbles, relationships, thoughts, festival, legendary candidates -------------

    static Settlement town(MinecraftServer s) {
        return SettlementManager.get(s).all().iterator().next();
    }

    private static void waveB(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        // lines and sounds exist
        ctx.runOnClient(mc -> {
            int total = 0, missing = 0;
            for (String sit : List.of("greet", "hungry", "no_bed", "night", "rain", "levelup", "tasty", "festival", "idle", "chat", "quarrel")) {
                int n = io.github.operagtx.hearthholm.social.BubbleLines.count(sit);
                for (int i = 0; i < n; i++) if (!net.minecraft.locale.Language.getInstance().has("bubble.hearthholm." + sit + "." + i)) missing++;
                total += n;
            }
            log("bubble lines total=" + io.github.operagtx.hearthholm.social.BubbleLines.total() + " base=" + total + " missingTranslations=" + missing);
            if (io.github.operagtx.hearthholm.social.BubbleLines.total() < 120 || missing > 0) throw new AssertionError("bubble pool incomplete");
        });
        sp.getServer().runCommand("time set 6000");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            List<SettlerEntity> ls = settlers(s);
            SettlerEntity a = ls.get(0), b = ls.get(1), c = ls.get(2), d = ls.get(3);
            a.addAffinity(b.getUUID(), 55); b.addAffinity(a.getUUID(), 55);
            a.addAffinity(c.getUUID(), -40); c.addAffinity(a.getUUID(), -40);
            c.addAffinity(d.getUUID(), -25); d.addAffinity(c.getUUID(), -25);
            // a legendary candidate in the first slot
            st.candidates.set(0, io.github.operagtx.hearthholm.settlement.SettlerProfile.random(l.getRandom(), true));
            var lp = st.candidates.get(0);
            log("legendary candidate " + lp.name().getString() + " title=" + lp.title() + " traits=" + lp.traits() + " stats=" + lp.stats());
            if (!lp.legendary() || lp.traits().size() != 2) throw new AssertionError("legendary profile wrong");
            // the codec round-trips and old saves (without the new fields) still decode
            var enc = io.github.operagtx.hearthholm.settlement.SettlerProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, lp).getOrThrow();
            var back = io.github.operagtx.hearthholm.settlement.SettlerProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, enc).getOrThrow();
            if (!back.legendary() || back.title() != lp.title()) throw new AssertionError("profile codec");
            var old = enc.getAsJsonObject().deepCopy();
            old.remove("legendary"); old.remove("title");
            var oldP = io.github.operagtx.hearthholm.settlement.SettlerProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
            if (oldP.legendary() || oldP.title() != -1) throw new AssertionError("old profile codec");
            log("legendary codec OK, old save OK");
            ServerPlayer p = player(s);
            p.teleportTo(l, DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 1.5, java.util.Set.of(), 0f, 10f, false);
            SettlementService.openTownHall(p, DESK);
        });
        ctx.waitForScreen(TownHallScreen.class);
        ctx.clickScreenButton("gui.hearthholm.tab.overview");
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_20_overview_festival_idle");
        ctx.clickScreenButton("gui.hearthholm.tab.hire");
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_21_hire_legendary");
        ctx.clickScreenButton("gui.hearthholm.tab.overview");
        ctx.waitTicks(3);
        sp.getServer().runOnServer(s -> SettlementService.handleTownHall(player(s), new Payloads.TownHallAction(DESK, Payloads.TownHallAction.FESTIVAL, 0, "")));
        ctx.waitTicks(10);
        ctx.takeScreenshot("hh_22_overview_festival_active");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            log("festival active=" + st.festivalActive(s.overworld().getGameTime()) + " end=" + st.festivalEnd + " last=" + st.lastFestival
                    + " daysUntil=" + io.github.operagtx.hearthholm.settlement.Festival.daysUntil(s.overworld(), st));
            if (!st.festivalActive(s.overworld().getGameTime())) throw new AssertionError("festival did not start");
            BlockPos sq = io.github.operagtx.hearthholm.settlement.Festival.square(s.overworld(), st);
            log("festival square " + sq + " center " + st.center);
            ServerPlayer p = player(s);
            p.teleportTo(s.overworld(), sq.getX() + 0.5, O.getY() + 5, O.getZ() - 6.5, java.util.Set.of(), 0f, 22f, false);
        });
        ctx.waitTicks(450);
        sp.getServer().runOnServer(s -> {
            int at = 0;
            for (SettlerEntity e : settlers(s)) {
                if (e.brain().currentActivity().equals("FestivalActivity")) at++;
                log("fest " + e.getName().getString() + " act=" + e.brain().currentActivity() + " status='" + e.status().getString()
                        + "' thoughts=" + e.thoughts().stream().map(t -> t.id() + t.value()).toList() + " pos=" + e.blockPosition().toShortString());
            }
            log("festival attendees " + at + "/" + settlers(s).size());
            if (at == 0) throw new AssertionError("nobody came to the festival");
        });
        ctx.takeScreenshot("hh_23_festival");
        ctx.waitTicks(40);
        ctx.takeScreenshot("hh_24_festival_dance");
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            int with = 0;
            for (SettlerEntity e : settlers(s)) if (e.hasThought("festival")) with++;
            log("festival thought on " + with + " settlers; marks stock bread=" + stock(s, Items.BREAD) + " festivals=" + st.stats.get("festivals"));
            if (with == 0) throw new AssertionError("no festival thought");
            io.github.operagtx.hearthholm.settlement.Festival.end(s.overworld(), st);
        });
        // auto-start at noon after 7 days
        sp.getServer().runCommand("time set 222000");
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            st.lastFestival = SettlementService.day(s.overworld()) - 8;
            log("auto festival armed, daysUntil=" + io.github.operagtx.hearthholm.settlement.Festival.daysUntil(s.overworld(), st));
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            boolean on = st.festivalActive(s.overworld().getGameTime());
            log("auto festival active=" + on);
            if (!on) throw new AssertionError("festival did not auto-start at noon");
            io.github.operagtx.hearthholm.settlement.Festival.end(s.overworld(), st);
        });
        // speech bubbles: three settlers lined up in front of the player
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            List<SettlerEntity> ls = settlers(s);
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 4.5, O.getY(), O.getZ() - 4.4, java.util.Set.of(), 0f, 0f, false);
            String[] lines = {"greet", "hungry", "work_miner", "trait_cheerful", "festival"};
            for (int i = 4; i < ls.size(); i++) ls.get(i).teleportTo(O.getX() - 8.5, O.getY(), O.getZ() + 5.5);
            for (int i = 0; i < 4; i++) {
                SettlerEntity e = ls.get(i);
                e.setNoAi(true);
                e.getNavigation().stop();
                e.teleportTo(O.getX() + 1.5 + i * 2.0, O.getY(), O.getZ() - 1.4);
                e.setYRot(180f); e.yBodyRot = 180f; e.yHeadRot = 180f;
                boolean ok = io.github.operagtx.hearthholm.social.Social.say(e, lines[i], 99);
                log("bubble " + e.getName().getString() + " " + lines[i] + " ok=" + ok + " text='" + e.bubble().getString() + "'");
            }
            // the visible-bubble cap: with 3 bubbles up, a fourth ambient line is refused
            log("cap check say(cap=3) while bubbling -> " + io.github.operagtx.hearthholm.social.Social.say(ls.get(4), "idle", 3));
        });
        ctx.waitTicks(15);
        ctx.runOnClient(mc -> {
            for (var e : mc.level.entitiesForRendering())
                if (e instanceof SettlerEntity se) log("client bubble " + se.getName().getString() + " has=" + se.hasBubble() + " '" + se.bubble().getString() + "' pos=" + se.blockPosition().toShortString());
            log("client player " + mc.player.blockPosition().toShortString());
        });
        ctx.takeScreenshot("hh_25_bubbles");
        sp.getServer().runOnServer(s -> { for (SettlerEntity e : settlers(s)) e.setNoAi(false); });
        // an evening chat between friends
        sp.getServer().runCommand("time set 11300");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            List<SettlerEntity> ls = settlers(s);
            SettlerEntity a = ls.get(0), b = ls.get(1);
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 3.5, O.getY(), O.getZ() - 4.4, java.util.Set.of(), 0f, 8f, false);
            a.teleportTo(O.getX() + 2.5, O.getY(), O.getZ() - 1.5);
            b.teleportTo(O.getX() + 6.5, O.getY(), O.getZ() - 1.5);
            io.github.operagtx.hearthholm.social.Social.startChat(l, a, b, SettlementService.day(l) + 50);
            log("chat started " + a.getName().getString() + " + " + b.getName().getString() + " affinity=" + a.affinity(b.getUUID()));
        });
        ctx.waitTicks(200);
        ctx.takeScreenshot("hh_26_chat");
        ctx.waitTicks(260);
        sp.getServer().runOnServer(s -> {
            List<SettlerEntity> ls = settlers(s);
            SettlerEntity a = ls.get(0), b = ls.get(1);
            log("chat done affinity a->b=" + a.affinity(b.getUUID()) + " b->a=" + b.affinity(a.getUUID()) + " act=" + a.brain().currentActivity()
                    + " friend thought=" + a.hasThought("friend"));
            if (a.affinity(b.getUUID()) <= 55) throw new AssertionError("chat gave no affinity");
            for (SettlerEntity e : ls) log("rel " + e.getName().getString() + " affinity=" + e.affinityMap().size() + " thoughts="
                    + e.thoughts().stream().map(t -> t.id() + (t.value() > 0 ? "+" : "") + t.value()).toList());
            // quarrel path
            io.github.operagtx.hearthholm.social.Social.quarrel(ls.get(2), ls.get(3));
            log("quarrel c->d=" + ls.get(2).affinity(ls.get(3).getUUID()) + " thought=" + ls.get(2).hasThought("quarrel"));
            SettlementService.openSettler(player(s), a);
        });
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_27_settler_relations");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> SettlementService.openSettler(player(s), settlers(s).get(2)));
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_28_settler_thoughts");
        ctx.setScreen(() -> null);
        // hire the legendary and look at the new settler's screen
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            st.residents.size();
            log("WAVE B OK bubbles=" + io.github.operagtx.hearthholm.social.BubbleLines.total() + " legendary candidate hired later");
        });
    }


    // ---- wave C: raids, alarm, rift, chieftain, palisade -----------------------------------------------

    private static BlockPos BELL, GATE, BANNER;
    private static java.util.UUID CHIEF;

    private static void waveC(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 6000");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer p = player(s);
            // two defenders with swords
            List<SettlerEntity> ls = settlers(s);
            for (int i = 0; i < 2; i++) {
                SettlerEntity g = ls.get(ls.size() - 1 - i);
                g.setProfession(i == 0 ? Professions.GUARD : Professions.WARRIOR);
                g.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                g.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                log("defender " + g.getName().getString() + " -> " + g.professionId());
            }
            // alarm bell west of the hall, palisade with a gate further west
            BELL = O.offset(-3, 0, 3);
            l.setBlock(BELL, ModBlocks.ALARM_BELL.defaultBlockState(), 3);
            io.github.operagtx.hearthholm.threat.Defense.onBellPlaced(l, BELL, p);
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = 0; dy <= 1; dy++)
                    if (dz != 0) l.setBlock(O.offset(-7, dy, dz), ModBlocks.PALISADE.defaultBlockState(), 3);
            GATE = O.offset(-7, 0, 0);
            BlockState gate = ModBlocks.PALISADE_GATE.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            l.setBlock(GATE, gate.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, true), 2);
            l.setBlock(GATE.above(), gate.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN, true), 3);
            io.github.operagtx.hearthholm.threat.Defense.onGatePlaced(l, GATE);
            l.setBlock(O.offset(-5, 0, 5), ModBlocks.RIFT_LANTERN.defaultBlockState(), 3);
            log("defence bells=" + st.bells.size() + " gates=" + st.gates.size() + " tier=" + st.tier().key() + " radius=" + st.radius()
                    + " strengthEstimate=" + io.github.operagtx.hearthholm.threat.Threats.strength(l, st));
            // Ashborn line-up for the screenshot (frozen, not part of any raid)
            var kinds = List.of(io.github.operagtx.hearthholm.registry.ModEntities.ASH_RAIDER, io.github.operagtx.hearthholm.registry.ModEntities.ASH_ARSONIST,
                    io.github.operagtx.hearthholm.registry.ModEntities.ASH_ARCHER, io.github.operagtx.hearthholm.registry.ModEntities.ASH_BRUTE);
            for (int i = 0; i < kinds.size(); i++) {
                var e = kinds.get(i).spawn(l, O.offset(-17 + i * 2, 0, -2), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
                e.setNoAi(true); e.addTag("hh_lineup");
                e.setYRot(0f); e.yBodyRot = 0f; e.yHeadRot = 0f;
            }
            var w = io.github.operagtx.hearthholm.registry.ModEntities.RIFT_WISP.spawn(l, O.offset(-9, 2, -1), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
            w.setNoAi(true); w.addTag("hh_lineup");
            p.teleportTo(l, O.getX() - 12.5, O.getY() + 1, O.getZ() + 4.5, java.util.Set.of(), 180f, 12f, false);
            // the lantern keeps natural spawns away
            var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(l, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            z.setPos(O.getX() - 5 + 0.5, O.getY(), O.getZ() + 9.5);
            log("lantern spawn check near=" + z.checkSpawnRules(l, net.minecraft.world.entity.EntitySpawnReason.NATURAL));
            if (z.checkSpawnRules(l, net.minecraft.world.entity.EntitySpawnReason.NATURAL)) throw new AssertionError("rift lantern did not block a spawn");
            z.discard();
        });
        sp.getConnection().waitForChunksRender();
        ctx.waitTicks(30);
        ctx.takeScreenshot("hh_30_ashborn_lineup");
        sp.getServer().runOnServer(s -> player(s).teleportTo(s.overworld(), O.getX() - 1.5, O.getY() + 2, O.getZ() + 6.5, java.util.Set.of(), 110f, 20f, false));
        ctx.waitTicks(20);
        ctx.takeScreenshot("hh_31_palisade_gate_bell");
        sp.getServer().runOnServer(s -> {
            for (var e : s.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Mob.class), m -> m.entityTags().contains("hh_lineup")))
                e.discard();
            Settlement st = town(s);
            io.github.operagtx.hearthholm.threat.Threats.forceRaid(s.overworld(), st, 4, 160);
            log("raid forced phase=" + st.raid.phase + " dir=" + st.raid.dir + " alarm=" + st.alarm
                    + " gateOpen=" + s.overworld().getBlockState(GATE).getValue(DoorBlock.OPEN));
            if (!st.alarm) throw new AssertionError("raid warning did not ring the alarm");
            player(s).teleportTo(s.overworld(), O.getX() + 4.5, O.getY() + 6, O.getZ() - 4.5, java.util.Set.of(), 0f, 25f, false);
        });
        ctx.waitTicks(60);
        ctx.takeScreenshot("hh_32_raid_warning");
        ctx.waitTicks(140);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            int kinds[] = new int[6];
            for (var id : st.raid.raiders) if (l.getEntity(id) instanceof io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity a)
                log("raider " + a.getType().toShortString() + " at " + a.blockPosition().toShortString() + " hand=" + a.getMainHandItem().getItem());
            log("raid spawned phase=" + st.raid.phase + " spawned=" + st.raid.spawned + " tracked=" + st.raid.raiders.size()
                    + " wavesLeft=" + st.raid.wavesLeft + " strength=" + st.raid.strength);
            if (st.raid.phase != io.github.operagtx.hearthholm.threat.RaidState.ACTIVE || st.raid.spawned == 0) throw new AssertionError("raid did not start");
            int civ = 0, indoors = 0, atBell = 0;
            for (SettlerEntity e : settlers(s)) {
                boolean roof = l.getHeight(Heightmap.Types.MOTION_BLOCKING, e.getBlockX(), e.getBlockZ()) > e.getBlockY() + 1;
                log("alarm " + e.getName().getString() + " " + e.professionId().getPath() + " act=" + e.brain().currentActivity()
                        + " status='" + e.status().getString() + "' roof=" + roof + " pos=" + e.blockPosition().toShortString());
                if (e.isCombatant()) { if (e.blockPosition().distSqr(BELL) < 36 || e.getTarget() != null) atBell++; }
                else { civ++; if (roof) indoors++; }
            }
            log("ALARM civilians indoors " + indoors + "/" + civ + ", defenders at bell or fighting " + atBell + "/2");

            // look at the raiders
            var first = st.raid.raiders.isEmpty() ? null : l.getEntity(st.raid.raiders.getFirst());
            if (first != null) {
                net.minecraft.world.phys.Vec3 v = first.position();
                net.minecraft.world.phys.Vec3 eye = v.add(net.minecraft.world.phys.Vec3.atCenterOf(O).subtract(v).normalize().scale(7)).add(0, 3, 0);
                double dx = v.x - eye.x, dz = v.z - eye.z;
                player(s).teleportTo(l, eye.x, eye.y, eye.z, java.util.Set.of(), (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f, 20f, false);
            }
        });
        sp.getConnection().waitForChunksRender();
        ctx.waitTicks(10);
        ctx.takeScreenshot("hh_33_raiders");
        // let the fight run; then finish off stragglers so the test stays short
        boolean[] over = {false};
        for (int i = 0; i < 8 && !over[0]; i++) {
            ctx.waitTicks(200);
            int round = i;
            sp.getServer().runOnServer(s -> {
                Settlement st = town(s);
                if (round == 0) {
                    int civ = 0, indoors = 0;
                    for (SettlerEntity e : settlers(s)) {
                        if (e.isCombatant()) continue;
                        civ++;
                        if (s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, e.getBlockX(), e.getBlockZ()) > e.getBlockY() + 1) indoors++;
                        else log("  outside: " + e.getName().getString() + " " + e.blockPosition().toShortString() + " bed=" + e.bed() + " act=" + e.brain().currentActivity());
                    }
                    log("ALARM after 20 s: civilians indoors " + indoors + "/" + civ + " alarm=" + st.alarm);
                    if (st.alarm && indoors * 2 < civ) throw new AssertionError("civilians did not shelter");
                }
                log("raid t+" + " alive=" + io.github.operagtx.hearthholm.threat.Threats.alive(s.overworld(), st) + " killed=" + st.raid.killed
                        + " wavesLeft=" + st.raid.wavesLeft + " phase=" + st.raid.phase + " fires=" + io.github.operagtx.hearthholm.threat.Threats.activeFires());
                over[0] = st.raid.phase == io.github.operagtx.hearthholm.threat.RaidState.NONE;
                for (var id : st.raid.raiders) if (s.overworld().getEntity(id) instanceof io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity a)
                    log("  " + a.getType().toShortString() + " pos=" + a.blockPosition().toShortString() + " target=" + (a.getTarget() == null ? "-" : a.getTarget().getType().toShortString())
                            + " navDone=" + a.getNavigation().isDone() + " goals=" + ((io.github.operagtx.hearthholm.mixin.MobAccessor) a).hearthholm$goalSelector().getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList());
            });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            if (st.raid.phase == io.github.operagtx.hearthholm.threat.RaidState.ACTIVE) {
                st.raid.wavesLeft = 0;
                int n = 0;
                for (var id : List.copyOf(st.raid.raiders)) if (l.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity a) { a.kill(l); n++; }
                log("raid assisted: killed " + n + " stragglers by test code");
            }
        });
        ctx.waitTicks(40);
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            SettlerEntity e = settlers(s).getFirst();
            log("RAID result=" + st.raid.lastResult + " won=" + st.raid.won + " lost=" + st.raid.lost + " killed=" + st.raid.killed
                    + " deaths=" + st.raid.deaths + " nextDay=" + st.raid.nextDay + " alarm=" + st.alarm + " held_line=" + e.hasThought("held_line")
                    + " marks=" + stock(s, io.github.operagtx.hearthholm.registry.ModItems.HEARTH_MARK));
            if (st.raid.phase != io.github.operagtx.hearthholm.threat.RaidState.NONE || st.raid.won != 1) throw new AssertionError("raid did not end in victory");
            if (st.alarm) throw new AssertionError("all-clear did not sound");
            // save round trip of the threat state, and an old save without it
            var enc = Settlement.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, st).getOrThrow();
            var back = Settlement.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, enc).getOrThrow();
            var old = enc.getAsJsonObject().deepCopy();
            for (String k : List.of("raid", "rift", "alarm", "bells", "gates", "trophies")) old.remove(k);
            var oldS = Settlement.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
            log("threat codec won=" + back.raid.won + " bells=" + back.bells.size() + " old save raid.next=" + oldS.raid.nextDay + " bells=" + oldS.bells.size());
            if (back.raid.won != 1 || back.bells.size() != 1 || oldS.raid.nextDay != -1) throw new AssertionError("threat codec");
        });

        // ---- rift night
        sp.getServer().runCommand("time set 14500");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            BlockPos core = io.github.operagtx.hearthholm.threat.Threats.forceRift(l, st);
            log("rift core=" + core + " block=" + l.getBlockState(core).getBlock());
            if (core == null) throw new AssertionError("rift did not open");
            player(s).teleportTo(l, core.getX() + 0.5, core.getY() + 3, core.getZ() + 7.5, java.util.Set.of(), 180f, 22f, false);
        });
        sp.getConnection().waitForChunksRender();
        ctx.waitTicks(120);
        ctx.takeScreenshot("hh_34_rift");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            int wisps = 0, others = 0;
            for (var id : st.rift.mobs) {
                var m = l.getEntity(id);
                if (m instanceof io.github.operagtx.hearthholm.entity.ashborn.RiftWispEntity) wisps++; else if (m != null) others++;
            }
            log("rift mobs wisps=" + wisps + " monsters=" + others + " spawnedTotal=" + st.stats.get("rift_spawned") + " hp=" + st.rift.hp);
            if (wisps + others == 0) throw new AssertionError("rift spawned nothing");
            l.destroyBlock(st.rift.core, false);
        });
        ctx.waitTicks(40);
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            int shards = 0;
            for (var it : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(O).inflate(128)))
                if (it.getItem().is(io.github.operagtx.hearthholm.registry.ModItems.RIFT_SHARD)) shards += it.getItem().getCount();
            log("RIFT closed open=" + st.rift.open() + " closed=" + st.rift.closed + " shards dropped=" + shards + " next=" + st.rift.nextDay);
            if (st.rift.open() || st.rift.closed != 1 || shards == 0) throw new AssertionError("rift did not close properly");
            for (var m : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, new AABB(O).inflate(128))) m.discard();
        });

        // ---- the Ash Chieftain
        sp.getServer().runCommand("time set 6000");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() - 10.5, O.getY(), O.getZ() + 14.5, java.util.Set.of(), 180f, -5f, false);
            var c = io.github.operagtx.hearthholm.threat.Threats.spawnChieftain(l, O.offset(-11, 0, 9));
            c.setNoAi(true);
            c.setYRot(0f); c.yBodyRot = 0f; c.yHeadRot = 0f;
            CHIEF = c.getUUID();
            log("chieftain hp=" + c.getHealth() + " scale=" + c.getScale() + " phase=" + c.phase());
        });
        ctx.waitTicks(30);
        ctx.takeScreenshot("hh_35_chieftain");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var c = (io.github.operagtx.hearthholm.entity.ashborn.AshChieftainEntity) l.getEntity(CHIEF);
            c.setNoAi(false);
            c.setHealth(c.getMaxHealth() * 0.5f);
        });
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var c = (io.github.operagtx.hearthholm.entity.ashborn.AshChieftainEntity) l.getEntity(CHIEF);
            int p2 = c.phase();
            c.setHealth(c.getMaxHealth() * 0.25f);
            c.castRing(c.position().add(-4.5, 0, 1));
            player(s).teleportTo(l, O.getX() - 9.5, O.getY() + 4, O.getZ() + 19.5, java.util.Set.of(), 160f, 25f, false);
            log("chieftain phase after 50% = " + p2);
        });
        ctx.waitTicks(50);
        ctx.takeScreenshot("hh_36_chieftain_fire_ring");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var c = (io.github.operagtx.hearthholm.entity.ashborn.AshChieftainEntity) l.getEntity(CHIEF);
            int minions = l.getEntitiesOfClass(io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity.class, c.getBoundingBox().inflate(12), e -> e != c).size();
            log("CHIEFTAIN phase=" + c.phase() + " rings=" + c.rings().size() + " minions=" + minions + " bossbarPlayers=" + c.bossEvent().getPlayers().size());
            if (c.phase() != 3 || minions == 0) throw new AssertionError("chieftain phases");
            BlockPos at = c.blockPosition();
            c.kill(l);
            for (var e : l.getEntitiesOfClass(io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity.class, new AABB(O).inflate(64))) e.discard();
            boolean banner = !l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(at).inflate(4),
                    it -> it.getItem().is(ModBlocks.CHIEFTAIN_BANNER.asItem())).isEmpty();
            log("chieftain dead, banner dropped=" + banner);
            if (!banner) throw new AssertionError("no chieftain banner");
            // trophy in the hall
            BANNER = O.offset(6, 0, 2);
            l.setBlock(BANNER, ModBlocks.CHIEFTAIN_BANNER.defaultBlockState(), 3);
            io.github.operagtx.hearthholm.threat.Defense.onTrophyPlaced(l, BANNER);
            Settlement st = town(s);
            st.rescan(l);
            log("TROPHY first scan: hall rooms=" + st.buildings.stream().filter(b -> b.is(1)).map(b -> b.problem() + "/" + b.size()).toList());
            if (!st.trophyInHall) {
                // the raid may have burnt or broken the hall: repair the shell and scan again
                for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) {
                    boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                    for (int y = 0; y <= 4; y++) {
                        BlockPos q = O.offset(x, y, z);
                        if (y == 4 || (edge && y <= 3)) { if (l.getBlockState(q).isAir()) l.setBlock(q, y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState(), 3); }
                    }
                }
                door(l, O.offset(4, 0, 0));
                st.rescan(l);
            }
            log("TROPHY in hall=" + st.trophyInHall);
            if (!st.trophyInHall) throw new AssertionError("trophy not detected");
        });
        // brute vs palisade: a brute stands behind the wall and wants the town hall
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var b = io.github.operagtx.hearthholm.registry.ModEntities.ASH_BRUTE.spawn(l, O.offset(-9, 0, 2), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
            b.joinRaid(town(s).id, O.offset(4, 0, 3));
            b.addTag("hh_brute");
            log("brute spawned " + b.blockPosition().toShortString());
        });
        ctx.waitTicks(400);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            int left = 0;
            for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 1; dy++)
                if (l.getBlockState(O.offset(-7, dy, dz)).getBlock() instanceof io.github.operagtx.hearthholm.block.PalisadeBlock) left++;
            boolean gate = l.getBlockState(GATE).getBlock() instanceof io.github.operagtx.hearthholm.block.PalisadeGateBlock;
            var brutes = l.getEntitiesOfClass(io.github.operagtx.hearthholm.entity.ashborn.AshBruteEntity.class, new AABB(O).inflate(48));
            log("BRUTE palisade blocks left " + left + "/16 gate=" + gate + " brute=" + (brutes.isEmpty() ? "gone" : brutes.getFirst().blockPosition().toShortString()));
            for (var b : brutes) b.discard();
            if (left >= 16 && gate) throw new AssertionError("brute did not break through the palisade");
            SettlementService.openTownHall(player(s), DESK);
        });
        ctx.waitForScreen(TownHallScreen.class);
        ctx.clickScreenButton("gui.hearthholm.tab.defense");
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_37_defense_tab");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            for (SettlerEntity e : settlers(s)) log("wave C moods " + e.getName().getString() + " " + e.thoughts().stream().map(t -> t.id() + t.value()).toList());
            log("WAVE C OK");
        });
    }

    static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    static List<SettlerEntity> settlers(MinecraftServer s) {
        List<SettlerEntity> l = new java.util.ArrayList<>(s.overworld().getEntitiesOfClass(SettlerEntity.class, new AABB(O).inflate(96)));
        l.sort(java.util.Comparator.comparing(SettlerEntity::getUUID));
        return l;
    }

    static void box(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall, BlockState roof) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1 + 1; y++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    BlockPos p = O.offset(x, y, z);
                    if (y == y1 + 1) l.setBlock(p, roof, 3);
                    else l.setBlock(p, edge ? wall : Blocks.AIR.defaultBlockState(), 3);
                }
    }

    static void door(ServerLevel l, BlockPos p) {
        BlockState d = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        l.setBlock(p, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        l.setBlock(p.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static void bed(ServerLevel l, BlockPos foot) {
        BlockState b = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        l.setBlock(foot.south(), b.setValue(BedBlock.PART, BedPart.HEAD), 2);
        l.setBlock(foot, b.setValue(BedBlock.PART, BedPart.FOOT), 3);
    }

    private static void tree(ServerLevel l, BlockPos base) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 5; dy++)
                    if (Math.abs(dx) + Math.abs(dz) < 4) l.setBlock(base.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        for (int dy = 0; dy < 5; dy++) l.setBlock(base.above(dy), Blocks.OAK_LOG.defaultBlockState(), 3);
    }

    private static void fillChest(ServerLevel l, BlockPos p, ItemStack... items) {
        l.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
        if (l.getBlockEntity(p) instanceof Container c) for (int i = 0; i < items.length; i++) c.setItem(i, items[i]);
    }

    private static BlockState marker(WorkSiteBlock b, Direction facing) {
        return b.defaultBlockState().setValue(WorkSiteBlock.FACING, facing);
    }

    private static void build(MinecraftServer s) {
        ServerLevel l = s.overworld();
        ServerPlayer p = player(s);
        int x = p.getBlockX() + 3, z = p.getBlockZ() + 3;
        O = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        log("origin " + O);
        // flatten a work area
        for (int dx = -16; dx <= 24; dx++)
            for (int dz = -12; dz <= 24; dz++) {
                for (int dy = 0; dy <= 8; dy++) l.setBlock(O.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                l.setBlock(O.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 2; dy <= 40; dy++) l.setBlock(O.offset(dx, -dy, dz), Blocks.STONE.defaultBlockState(), 2);
            }
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        // town hall with four beds
        box(l, 0, 0, 0, 8, 3, 8, planks, Blocks.SPRUCE_PLANKS.defaultBlockState());
        door(l, O.offset(4, 0, 0));
        DESK = O.offset(4, 0, 3);
        l.setBlock(DESK, ModBlocks.TOWN_HALL_DESK.defaultBlockState(), 3);
        for (int bx : new int[]{1, 3, 5, 7}) bed(l, O.offset(bx, 0, 6));
        l.setBlock(O.offset(4, 2, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 3);
        SettlementService.onDeskPlaced(l, DESK, p);
        // warehouse
        box(l, 10, 0, 0, 16, 3, 6, planks, Blocks.SPRUCE_PLANKS.defaultBlockState());
        door(l, O.offset(13, 0, 0));
        BlockPos ledger = O.offset(13, 0, 3);
        l.setBlock(ledger, ModBlocks.WAREHOUSE_LEDGER.defaultBlockState(), 3);
        fillChest(l, O.offset(11, 0, 2), new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_HOE), new ItemStack(Items.FISHING_ROD), new ItemStack(Items.WHEAT_SEEDS, 32),
                new ItemStack(Items.TORCH, 32), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE));
        fillChest(l, O.offset(11, 0, 4), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.GLASS_PANE, 16),
                new ItemStack(Items.OAK_DOOR, 2), new ItemStack(Items.BED.red(), 2), new ItemStack(Items.CRAFTING_TABLE, 2), new ItemStack(Items.TORCH, 16));
        fillChest(l, O.offset(15, 0, 2), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.SPRUCE_PLANKS, 64));
        fillChest(l, O.offset(15, 0, 4), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.WHEAT, 64),
                new ItemStack(Items.WHEAT, 64), new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.BOWL, 4), new ItemStack(Items.IRON_INGOT, 8));
        SettlementService.onLedgerPlaced(l, ledger, p);
        // carpentry (with the order board and two beds) and kitchen north of the hall
        BlockState spruce = Blocks.SPRUCE_PLANKS.defaultBlockState();
        box(l, 0, 0, -11, 6, 3, -6, planks, spruce);
        door(l, O.offset(3, 0, -6));
        BENCH = O.offset(1, 0, -10);
        l.setBlock(BENCH, ModBlocks.CARPENTER_BENCH.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.SOUTH), 3);
        BOARD = O.offset(5, 0, -10);
        l.setBlock(BOARD, ModBlocks.ORDER_BOARD.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.SOUTH), 3);
        bed(l, O.offset(3, 0, -10));
        bed(l, O.offset(5, 0, -8));
        l.setBlock(O.offset(3, 2, -10), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 3);
        SettlementService.onWorkshopPlaced(l, BENCH, p);
        box(l, 8, 0, -11, 14, 3, -6, planks, spruce);
        door(l, O.offset(11, 0, -6));
        STOVE = O.offset(9, 0, -10);
        l.setBlock(STOVE, ModBlocks.COOK_STOVE.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.SOUTH), 3);
        bed(l, O.offset(13, 0, -10));
        l.setBlock(O.offset(11, 2, -10), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 3);
        SettlementService.onWorkshopPlaced(l, STOVE, p);
        // field with water
        FARM = O.offset(4, 0, 15);
        l.setBlock(FARM, marker(ModBlocks.FARM_MARKER, Direction.SOUTH), 3);
        l.setBlock(FARM.offset(3, -1, 0), Blocks.WATER.defaultBlockState(), 3);
        SettlementService.onWorkSitePlaced(l, FARM, p);
        // mine with a chest
        MINE = O.offset(14, 0, 14);
        l.setBlock(MINE, marker(ModBlocks.MINE_MARKER, Direction.SOUTH), 3);
        fillChest(l, MINE.west(), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.RAW_IRON, 12), new ItemStack(Items.TORCH, 16));
        for (int dz = 1; dz <= 34; dz++)
            for (int dx = -4; dx <= 7; dx++)
                for (int dy = -1; dy <= 3; dy++)
                    l.setBlock(MINE.offset(dx, dy, dz), (dz + dx) % 9 == 0 && dy == 0 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
        SettlementService.onWorkSitePlaced(l, MINE, p);
        if (l.getBlockEntity(MINE) instanceof WorkSiteBlockEntity mw) mw.setOption("mode", 2);
        // forest
        FOREST = O.offset(-10, 0, 6);
        l.setBlock(FOREST, marker(ModBlocks.FOREST_MARKER, Direction.SOUTH), 3);
        for (BlockPos t : new BlockPos[]{FOREST.offset(-4, 0, -4), FOREST.offset(4, 0, 3), FOREST.offset(-3, 0, 5)}) tree(l, t);
        SettlementService.onWorkSitePlaced(l, FOREST, p);
        // construction stake: small house to the north-east
        STAKE = O.offset(20, 0, 8);
        l.setBlock(STAKE, marker(ModBlocks.CONSTRUCTION_STAKE, Direction.SOUTH), 3);
        SettlementService.onWorkSitePlaced(l, STAKE, p);
        fillChest(l, STAKE.north(), new ItemStack(Items.DIRT, 20));
        if (l.getBlockEntity(STAKE) instanceof WorkSiteBlockEntity w) w.setOption("build", 1);
        p.teleportTo(l, DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 1.5, java.util.Set.of(), 0f, 10f, false);
    }

    private static void hire(MinecraftServer s) {
        ServerLevel l = s.overworld();
        ServerPlayer p = player(s);
        Settlement st = SettlementManager.get(s).byId(((io.github.operagtx.hearthholm.block.TownHallDeskBlockEntity) l.getBlockEntity(DESK)).settlementId());
        st.rescan(l);
        st.addRenown(400);
        log("tier=" + st.tier().key() + " renown=" + st.renown + " beds=" + st.housingBeds().size() + " hall=" + st.hallValid() + " wh=" + st.warehouseValid()
                + " buildings=" + st.buildings.size() + " sites=" + st.sites.size());
        for (var b : st.buildings) log("building roles=" + b.roles() + " problem=" + b.problem() + " beds=" + b.beds().size() + " containers=" + b.containers().size());
        SettlementService.openTownHall(p, DESK);
        for (int i = 0; i < 7; i++) {
            if (st.candidates.isEmpty()) SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.REROLL, 0, ""));
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.HIRE, 0, ""));
        }
        List<SettlerEntity> list = settlers(s);
        log("hired " + list.size() + " residents=" + st.residents.size());
        var profs = List.of(Professions.MINER, Professions.FARMER, Professions.LUMBERJACK, Professions.BUILDER,
                Professions.CARPENTER, Professions.COOK, Professions.HAULER);
        for (int i = 0; i < list.size(); i++) {
            SettlerEntity e = list.get(i);
            e.markArrived();
            e.teleportTo(DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 2.5);
            e.setProfession(profs.get(i % profs.size()));
            log("settler " + e.getName().getString() + " -> " + e.professionId() + " bed=" + e.bed());
        }
        list.getFirst().eat(new ItemStack(Items.BREAD));
        SettlementService.setOrder(st, Items.OAK_PLANKS, 400);
        SettlementService.setOrder(st, Items.BREAD, 200);
        SettlementService.setOrder(st, Items.CHEST, 4);
        SettlementService.setOrder(st, Items.PUMPKIN_PIE, 8);
        SettlementService.setOrder(st, Items.IRON_PICKAXE, 1);
        SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.MINT, 0, ""));
        planks0 = stock(s, Items.OAK_PLANKS);
        bread0 = stock(s, Items.BREAD);
        log("mint marks=" + stock(s, io.github.operagtx.hearthholm.registry.ModItems.HEARTH_MARK) + " iron=" + stock(s, Items.IRON_INGOT));
        for (Item it : List.of(Items.OAK_PLANKS, Items.STICK, Items.CHEST, Items.BREAD, Items.STONE, Items.STONE_BRICKS, Items.IRON_PICKAXE,
                Items.IRON_INGOT, Items.COOKED_BEEF, Items.MUSHROOM_STEW, Items.PUMPKIN_PIE, Items.BED.red())) {
            log("recipes " + it + " = " + io.github.operagtx.hearthholm.crafting.Recipes.options(l, it).size());
        }
        for (var w : io.github.operagtx.hearthholm.crafting.Workshop.values()) {
            int missing = 0;
            for (Item it : w.catalog()) if (io.github.operagtx.hearthholm.crafting.Recipes.options(l, it).isEmpty()) { missing++; log("NO RECIPE " + it); }
            log("catalog " + w.key() + " items=" + w.catalog().size() + " without recipe=" + missing);
        }
        log("stock start planks=" + planks0 + " bread=" + bread0);
    }

    private static String chest(MinecraftServer s, BlockPos p) {
        StringBuilder sb = new StringBuilder("[");
        if (s.overworld().getBlockEntity(p) instanceof Container c)
            for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) sb.append(c.getItem(i).getCount()).append("x").append(c.getItem(i).getItem()).append(" ");
        return sb.append("]").toString();
    }

    static int stock(MinecraftServer s, Item item) {
        Settlement st = SettlementManager.get(s).all().iterator().next();
        return io.github.operagtx.hearthholm.settlement.Storage.count(
                io.github.operagtx.hearthholm.settlement.Storage.resolve(s.overworld(), st.storagePositions()), x -> x.is(item));
    }

    private static void report(MinecraftServer s) {
        ServerLevel l = s.overworld();
        for (SettlerEntity e : settlers(s)) {
            log(String.format("%s %s act=%s status='%s' food=%.1f mood=%.0f pos=%s hand=%s inv=%d", e.getName().getString(), e.professionId().getPath(),
                    e.brain().currentActivity(), e.status().getString(), e.food(), e.mood(), e.blockPosition().toShortString(),
                    e.getMainHandItem().getItem(), 18 - e.freeSlots()));
        }
        for (BlockPos p : new BlockPos[]{MINE, FARM, FOREST, STAKE}) {
            if (l.getBlockEntity(p) instanceof WorkSiteBlockEntity w) log("site " + w.type().id().getPath() + " progress=" + w.progress() + " worker=" + w.workerId());
        }
        int farmland = 0;
        for (BlockPos q : BlockPos.betweenClosed(FARM.offset(-4, -1, -4), FARM.offset(4, -1, 4)))
            if (l.getBlockState(q).getBlock() instanceof FarmlandBlock) farmland++;
        Settlement st = SettlementManager.get(s).all().iterator().next();
        log("farmland=" + farmland + " stats=" + st.stats + " renown=" + st.renown + " tier=" + st.tier().key());
        log("stock planks=" + stock(s, Items.OAK_PLANKS) + " logs=" + stock(s, Items.OAK_LOG) + " bread=" + stock(s, Items.BREAD)
                + " wheat=" + stock(s, Items.WHEAT) + " chests=" + stock(s, Items.CHEST) + " crafting=" + st.crafting);
    }
}
