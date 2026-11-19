package io.github.operagtx.hearthholm.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.entity.ai.jobs.BaseJob;
import io.github.operagtx.hearthholm.entity.ai.jobs.ToolKit;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Civic;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.util.Labels;
import io.github.operagtx.hearthholm.worksite.SiteOption;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave M1 (bugs and small things from the owner's messages 162-165): blueprint names (+ fallback + RU/EN guard), furnace hint only for smelting workshops,
 * owners and co-owners are never fought by settlers, co-owners get village notifications, hunger x2.5, shift-end keeps work supplies (miner: torches, ladders,
 * 8 food), the builder fetches and uses a pickaxe. Run alone with HH_ONLY_M1=1.
 */
final class WaveM1Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static UUID MINER, BUILDER;
    private static BlockPos P;

    private WaveM1Test() {}

    private static void fail(String m) { FAILS.add(m); log("M1 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static int keptOf(SettlerEntity e, int[] keep, Item item) {
        int n = 0;
        for (int i = 0; i < keep.length; i++) if (e.inventory().getItem(i).is(item)) n += keep[i];
        return n;
    }

    private static int inPockets(SettlerEntity e, Item item) { return e.count(st -> st.is(item)); }

    private static JsonObject lang(String code) {
        try (var in = new InputStreamReader(WaveM1Test.class.getResourceAsStream("/assets/hearthholm/lang/" + code + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static List<String> blueprintIndex() {
        try (var in = new InputStreamReader(WaveM1Test.class.getResourceAsStream("/data/hearthholm/blueprints/index.json"), StandardCharsets.UTF_8)) {
            List<String> out = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("blueprints")) out.add(e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("id").getAsString());
            return out;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Blueprint floorBp() {
        BlockState[] cells = new BlockState[5 * 1 * 3];
        Arrays.fill(cells, Blocks.COBBLESTONE.defaultBlockState());
        return new Blueprint(Hearthholm.id("m1_floor"), 5, 1, 3, cells);
    }

    private static void setTime(TestSingleplayerContext sp, int t) { sp.getServer().runCommand("time set " + t); }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("weather clear");
        setTime(sp, 5000);

        // ---- a. blueprint names: every blueprint of index.json has EN+RU text, a missing key falls back to a readable name
        sp.getServer().runOnServer(s -> {
            JsonObject en = lang("en_us"), ru = lang("ru_ru");
            List<String> ids = blueprintIndex();
            List<String> missing = new ArrayList<>();
            for (String id : ids) {
                String key = "blueprint.hearthholm." + id;
                if (!en.has(key) || en.get(key).getAsString().isBlank()) missing.add("en:" + id);
                if (!ru.has(key) || ru.get(key).getAsString().isBlank()) missing.add("ru:" + id);
            }
            log("M1 names: " + ids.size() + " blueprints in index.json, missing translations " + missing);
            check(ids.size() >= 16 && missing.isEmpty(), "blueprints without a name: " + missing);
            check(Labels.humanize("blueprint.hearthholm.fisher_hut").equals("Fisher hut"), "humanize: " + Labels.humanize("blueprint.hearthholm.fisher_hut"));
            String shown = Labels.orHumanized("blueprint.hearthholm.zz_no_such_blueprint").getString();
            String real = Labels.orHumanized("blueprint.hearthholm.cottage").getString();
            log("M1 names: fallback shows '" + shown + "', translated shows '" + real + "'");
            check(shown.equals("Zz no such blueprint"), "a missing key must show a humanized name, got '" + shown + "'");
            check(real.equals(en.get("blueprint.hearthholm.cottage").getAsString()), "a translated key must keep its text, got '" + real + "'");
            int fh = BlueprintLibrary.ids().indexOf(Hearthholm.id("fisher_hut"));
            String viaOption = SiteOption.dynamic("blueprint", BlueprintLibrary::labelKeys).valueLabel(fh).getString();
            check(viaOption.equals(en.get("blueprint.hearthholm.fisher_hut").getAsString()), "the stake option shows '" + viaOption + "' instead of the blueprint name");
            // the blueprint cycle: no entry shows a raw key
            for (String key : BlueprintLibrary.labelKeys()) {
                String text = Labels.orHumanized(key).getString();
                check(!text.startsWith("blueprint."), "raw key shown: " + text);
            }
        });

        // ---- b. furnace hint only for workshops that smelt/cook
        sp.getServer().runOnServer(s -> {
            log("M1 smelts: carpentry=" + Workshop.CARPENTRY.smelts() + " masonry=" + Workshop.MASONRY.smelts() + " smithy=" + Workshop.SMITHY.smelts() + " kitchen=" + Workshop.KITCHEN.smelts());
            check(!Workshop.CARPENTRY.smelts(), "the carpenter must not get the furnace hint");
            check(Workshop.MASONRY.smelts() && Workshop.SMITHY.smelts() && Workshop.KITCHEN.smelts(), "mason, smith and cook do smelt/cook");
        });

        // ---- c/d. owners and co-owners are never fought; strangers are; co-owners are in the notification audience
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer owner = player(s);
            GameProfile pc = new GameProfile(UUID.nameUUIDFromBytes("m1-coowner".getBytes()), "CoOwnerM1"), ps = new GameProfile(UUID.nameUUIDFromBytes("m1-stranger".getBytes()), "StrangerM1");
            FakePlayer co = FakePlayer.get(l, pc), stranger = FakePlayer.get(l, ps);
            st.civic.coOwners.add(new Civic.CoOwner(co.getUUID(), "CoOwnerM1"));
            try {
                SettlerEntity guard = settlers(s).getFirst();
                guard.setProfession(Professions.GUARD);
                BlockPos at = guard.blockPosition();
                co.setPos(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
                stranger.setPos(at.getX() - 1.5, at.getY(), at.getZ() + 0.5);
                check(guard.isProtectedOwner(owner) && guard.isProtectedOwner(co) && !guard.isProtectedOwner(stranger), "protected owner predicate");
                check(!guard.canAttack(owner) && !guard.canAttack(co) && guard.canAttack(stranger), "canAttack must refuse owners and co-owners only");
                guard.setTarget(owner);
                check(guard.getTarget() == null, "a guard accepted the owner as a target");
                guard.setTarget(co);
                check(guard.getTarget() == null, "a guard accepted a co-owner as a target");
                float hp = guard.getHealth();
                // hurt-resistance frames swallow a blow that is not stronger than the previous one: each blow below is stronger
                guard.hurtServer(l, l.damageSources().playerAttack(co), 1f);
                guard.hurtServer(l, l.damageSources().playerAttack(owner), 2f);
                log("M1 owners: the blows still hurt (hp " + hp + " -> " + guard.getHealth() + "), lastHurtBy=" + guard.getLastHurtByMob() + " target=" + guard.getTarget());
                check(guard.getHealth() < hp, "the owner's blow should still hurt (no invulnerability)");
                check(guard.getLastHurtByMob() == null && guard.getTarget() == null, "the guard remembers or targets an owner who hit it");
                guard.hurtServer(l, l.damageSources().playerAttack(stranger), 3f);
                log("M1 owners: stranger hit -> lastHurtBy=" + (guard.getLastHurtByMob() == null ? "none" : guard.getLastHurtByMob().getName().getString()));
                check(guard.getLastHurtByMob() == stranger, "a stranger's blow must be remembered (old behaviour)");
                guard.setLastHurtByMob(null);
                guard.setProfession(Professions.UNEMPLOYED);

                // notifications: founder + online co-owners, no duplicates, offline ones ignored
                Map<UUID, ServerPlayer> online = new HashMap<>(Map.of(owner.getUUID(), owner, co.getUUID(), co));
                List<ServerPlayer> both = SettlementService.onlineManagers(st, online::get);
                check(both.size() == 2 && both.contains(owner) && both.contains(co), "the audience must be founder + online co-owner: " + both);
                online.remove(co.getUUID());
                check(SettlementService.onlineManagers(st, online::get).size() == 1, "an offline co-owner must be skipped");
                st.civic.coOwners.add(new Civic.CoOwner(owner.getUUID(), "founder-as-co"));
                online.put(co.getUUID(), co);
                check(SettlementService.onlineManagers(st, online::get).size() == 2, "the founder must not be listed twice");
                st.civic.coOwners.removeIf(c -> c.id().equals(owner.getUUID()));
                check(SettlementService.isOwnerOrCoOwner(st, co.getUUID()) && !SettlementService.isOwnerOrCoOwner(st, stranger.getUUID()), "isOwnerOrCoOwner");
                SettlementService.notify(l, st, net.minecraft.network.chat.Component.literal("M1 test notification"));   // must not throw for the founder
                log("M1 notify: audience " + both.size() + " (founder + co-owner)");
            } finally {
                st.civic.coOwners.removeIf(c -> c.id().equals(co.getUUID()));
            }
        });

        // ---- e. hunger x2.5
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = settlers(s).getFirst();
            int saved = Balance.HUNGER_RATE_PERCENT;
            float now = e.hungerPerTick();
            Balance.HUNGER_RATE_PERCENT = 100;
            float old = e.hungerPerTick();
            Balance.HUNGER_RATE_PERCENT = saved;
            log("M1 hunger: default " + saved + "% -> " + now * 1800 + " food per 1800 ticks (old " + old * 1800 + "), ratio " + now / old);
            check(saved == 250, "the default hunger rate must be 250%, is " + saved);
            check(Math.abs(now / old - 2.5f) < 0.01f, "hunger is not 2.5x the old rate: " + now / old);
        });

        // ---- f. shift-end keeps: miner (torches, ladders, one stack of filler, 8 food), civilians (2 food), builder (shovel + pickaxes)
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = settlers(s).getFirst();
            Job miner = Professions.MINER.createJob(e), lumber = Professions.LUMBERJACK.createJob(e), builder = Professions.BUILDER.createJob(e);
            e.inventory().clearContent();
            e.inventory().addItem(new ItemStack(Items.IRON_PICKAXE));
            e.inventory().addItem(new ItemStack(Items.IRON_SHOVEL));
            e.inventory().addItem(new ItemStack(Items.TORCH, 30));
            e.inventory().addItem(new ItemStack(Items.LADDER, 10));
            e.inventory().addItem(new ItemStack(Items.COBBLESTONE, 100));
            e.inventory().addItem(new ItemStack(Items.BREAD, 12));
            e.inventory().addItem(new ItemStack(Items.DIAMOND, 5));
            e.inventory().addItem(new ItemStack(Items.OAK_LOG, 20));
            int[] km = BaseJob.keepPlan(e, miner), kl = BaseJob.keepPlan(e, lumber), kb = BaseJob.keepPlan(e, builder);
            log("M1 keep miner: pick=" + keptOf(e, km, Items.IRON_PICKAXE) + " torch=" + keptOf(e, km, Items.TORCH) + " ladder=" + keptOf(e, km, Items.LADDER) + " cobble=" + keptOf(e, km, Items.COBBLESTONE)
                    + " bread=" + keptOf(e, km, Items.BREAD) + " diamond=" + keptOf(e, km, Items.DIAMOND) + " log=" + keptOf(e, km, Items.OAK_LOG));
            check(keptOf(e, km, Items.IRON_PICKAXE) == 1 && keptOf(e, km, Items.TORCH) == 30 && keptOf(e, km, Items.LADDER) == 10, "the miner must keep pickaxe, torches and ladders");
            check(keptOf(e, km, Items.COBBLESTONE) == Balance.SHIFT_END_KEEP_FILLER_MINER, "the miner keeps one stack of filler, kept " + keptOf(e, km, Items.COBBLESTONE));
            check(keptOf(e, km, Items.BREAD) == 8 && Balance.SHIFT_END_KEEP_FOOD_MINER == 8, "the miner keeps 8 food, kept " + keptOf(e, km, Items.BREAD));
            check(keptOf(e, km, Items.DIAMOND) == 0 && keptOf(e, km, Items.OAK_LOG) == 0, "the miner must hand in ore and logs");
            check(keptOf(e, kl, Items.TORCH) == 0 && keptOf(e, kl, Items.LADDER) == 0 && keptOf(e, kl, Items.COBBLESTONE) == 0, "a lumberjack hands in torches, ladders and cobble");
            check(keptOf(e, kl, Items.BREAD) == 2, "everybody else keeps 2 food, kept " + keptOf(e, kl, Items.BREAD));
            check(keptOf(e, kb, Items.IRON_SHOVEL) == 1 && keptOf(e, kb, Items.IRON_PICKAXE) == 1 && keptOf(e, kb, Items.TORCH) == 0, "the builder keeps shovel and pickaxe, hands in the rest");
            check(BaseJob.hasUnloadable(e, miner) && BaseJob.hasUnloadable(e, lumber), "there is something to unload");
            e.inventory().clearContent();
            e.inventory().addItem(new ItemStack(Items.IRON_PICKAXE));
            e.inventory().addItem(new ItemStack(Items.TORCH, 12));
            e.inventory().addItem(new ItemStack(Items.BREAD, 8));
            check(!BaseJob.hasUnloadable(e, miner), "a miner with only his supplies has nothing to unload");
            check(BaseJob.hasUnloadable(e, lumber), "the same pockets are junk for a lumberjack (torches, 6 surplus bread)");
            e.inventory().clearContent();
            check(ToolKit.kindFor(Blocks.STONE.defaultBlockState()) == ToolKit.Kind.PICKAXE && ToolKit.kindFor(Blocks.IRON_ORE.defaultBlockState()) == ToolKit.Kind.PICKAXE
                    && ToolKit.kindFor(Blocks.DIRT.defaultBlockState()) == ToolKit.Kind.SHOVEL && ToolKit.kindFor(Blocks.OAK_LOG.defaultBlockState()) == null, "ToolKit.kindFor");
            MINER = settlers(s).get(1).getUUID();
        });

        // ---- f2. the miner's live unload at the end of the shift: supplies stay, junk goes to the warehouse
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity m = (SettlerEntity) l.getEntity(MINER);
            m.setProfession(Professions.UNEMPLOYED);
            m.setProfession(Professions.MINER);
            m.markArrived();
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            m.inventory().clearContent();
            m.inventory().addItem(new ItemStack(Items.TORCH, 30));
            m.inventory().addItem(new ItemStack(Items.LADDER, 10));
            m.inventory().addItem(new ItemStack(Items.COBBLESTONE, 100));
            m.inventory().addItem(new ItemStack(Items.BREAD, 12));
            m.inventory().addItem(new ItemStack(Items.DIAMOND, 5));
            m.markWorked();
            log("M1 miner unload: set up at " + m.blockPosition().toShortString());
        });
        setTime(sp, 9100);
        AtomicBoolean light = new AtomicBoolean();
        for (int round = 0; round < 50 && !light.get(); round++) {
            ctx.waitTicks(40);
            setTime(sp, 9100);
            sp.getServer().runOnServer(s -> {
                SettlerEntity m = (SettlerEntity) s.overworld().getEntity(MINER);
                if (inPockets(m, Items.DIAMOND) == 0 && inPockets(m, Items.BREAD) <= 8) light.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity m = (SettlerEntity) s.overworld().getEntity(MINER);
            log("M1 miner unload: done=" + light.get() + " torch=" + inPockets(m, Items.TORCH) + " ladder=" + inPockets(m, Items.LADDER) + " cobble=" + inPockets(m, Items.COBBLESTONE)
                    + " bread=" + inPockets(m, Items.BREAD) + " diamond=" + inPockets(m, Items.DIAMOND) + " status=" + m.status().getString());
            check(light.get(), "the miner did not unload at the end of the shift");
            check(inPockets(m, Items.TORCH) == 30 && inPockets(m, Items.LADDER) == 10, "the miner handed in torches or ladders");
            check(inPockets(m, Items.BREAD) == 8, "the miner must keep exactly 8 food, has " + inPockets(m, Items.BREAD));
            check(inPockets(m, Items.COBBLESTONE) <= Balance.SHIFT_END_KEEP_FILLER_MINER && inPockets(m, Items.DIAMOND) == 0, "the miner kept too much junk");
            m.setProfession(Professions.UNEMPLOYED);
            m.inventory().clearContent();
        });
        setTime(sp, 5000);

        // ---- g. the builder fetches a pickaxe from the stake chest and digs stone with it (floor: stone and dirt in a checkerboard)
        Blueprint floor = floorBp();
        BlueprintLibrary.register(floor);
        P = O.offset(34, 0, -34).atY(O.getY() + 70);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, P.getX() + 0.5, P.getY() + 6, P.getZ() - 8.5, Set.of(), 0f, 25f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(80);
        AtomicInteger total = new AtomicInteger(), places = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            for (int dx = -9; dx <= 9; dx++) for (int dz = -4; dz <= 12; dz++)
                for (int dy = -3; dy <= 6; dy++) l.setBlock(P.offset(dx, dy, dz), dy == -1 ? (((dx + dz) & 1) == 0 ? Blocks.STONE : Blocks.DIRT).defaultBlockState() : dy < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            for (Settlement.SiteRecord rec : town(s).sitesOf(io.github.operagtx.hearthholm.worksite.WorkSiteTypes.CONSTRUCTION_ID))
                if (l.getBlockEntity(rec.pos()) instanceof WorkSiteBlockEntity other) { other.setOption("build", 0); other.setWorkerId(null); }
            List<SettlerEntity> all = settlers(s);
            SettlerEntity b = all.get(0);
            for (SettlerEntity e : all) if (e != b) e.setProfession(Professions.UNEMPLOYED);
            b.setProfession(Professions.UNEMPLOYED);
            b.setProfession(Professions.BUILDER);
            b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));   // a shovel only: stone with it is the old slow way
            b.inventory().clearContent();
            b.markArrived();
            b.teleportTo(P.getX() + 0.5, P.getY(), P.getZ() - 1.5);
            BUILDER = b.getUUID();
            l.setBlock(P, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, P, p);
            l.setBlock(P.west(), Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(P.west()) instanceof Container c) {
                c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                c.setItem(1, new ItemStack(Items.COBBLESTONE, 40));
            }
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(P);
            w.setOption("blueprint", BlueprintLibrary.ids().indexOf(floor.id()));
            w.setOption("stake", 0);
            w.setOption("build", 1);
            total.set(floor.ops(P, Direction.SOUTH).size());
            places.set((int) floor.ops(P, Direction.SOUTH).stream().filter(op -> !op.clear()).count());
            log("M1 builder: set-up at " + P.toShortString() + ", ops " + total.get() + ", chest holds an iron pickaxe, the builder only a shovel");
        });
        AtomicBoolean pickTaken = new AtomicBoolean(), finished = new AtomicBoolean();
        StringBuilder trace = new StringBuilder();
        for (int round = 0; round < 90 && !finished.get(); round++) {
            ctx.waitTicks(40);
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                SettlerEntity b = (SettlerEntity) l.getEntity(BUILDER);
                WorkSiteBlockEntity w = l.getBlockEntity(P) instanceof WorkSiteBlockEntity x ? x : null;
                if (b == null || w == null) return;
                boolean hasPick = inPockets(b, Items.IRON_PICKAXE) > 0 || b.getMainHandItem().is(Items.IRON_PICKAXE);
                if (hasPick) pickTaken.set(true);
                if (trace.length() < 600) trace.append(w.progress()).append(':').append(b.getMainHandItem().getItem().toString().replace("minecraft:", "")).append(' ');
                if (w.progress() >= total.get() || !w.flag("build")) finished.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity b = (SettlerEntity) l.getEntity(BUILDER);
            int cobble = 0;
            for (BlockPos q : BlockPos.betweenClosed(P.offset(-6, -2, -2), P.offset(6, 0, 6))) if (l.getBlockState(q).is(Blocks.COBBLESTONE)) cobble++;
            boolean chestHas = l.getBlockEntity(P.west()) instanceof Container c && io.github.operagtx.hearthholm.settlement.Storage.count(List.of(c), st -> st.is(Items.IRON_PICKAXE)) > 0;
            boolean shovelKept = inPockets(b, Items.IRON_SHOVEL) > 0 || b.getMainHandItem().is(Items.IRON_SHOVEL);
            log("M1 builder: finished=" + finished.get() + " pickaxeTaken=" + pickTaken.get() + " pickaxeStillInChest=" + chestHas + " shovelKept=" + shovelKept + " cobble placed=" + cobble
                    + " hand trace (progress:item) " + trace);
            check(pickTaken.get() && !chestHas, "the builder did not fetch the pickaxe from the chest");
            check(shovelKept, "the builder lost his shovel");
            check(finished.get() && cobble >= places.get(), "the builder did not finish the floor (cobble " + cobble + "/" + places.get() + ")");
        });

        // ---- screenshots: the stake list with the formerly raw names, EN and RU
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 2.5, O.getY() + 2, O.getZ() + 2.5, Set.of(), 0f, 20f, false);
            for (Settlement.SiteRecord rec : town(s).sitesOf(io.github.operagtx.hearthholm.worksite.WorkSiteTypes.CONSTRUCTION_ID))
                if (l.getBlockEntity(rec.pos()) instanceof WorkSiteBlockEntity other && rec.pos().equals(STAKE)) other.setOption("blueprint", BlueprintLibrary.ids().indexOf(Hearthholm.id("fisher_hut")));
        });
        sp.getConnection().waitForChunksRender();
        TestKit.setWindow(ctx, 854, 480);
        for (String code : new String[]{"en_us", "ru_ru"}) {
            TestKit.setLang(ctx, code);
            sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), STAKE));
            ctx.waitForScreen(WorkSiteScreen.class);
            ctx.waitTicks(6);
            ctx.takeScreenshot("hh_m1_stake_names_" + code);
            ctx.setScreen(() -> null);
        }
        TestKit.setLang(ctx, "en_us");

        log("M1 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave M1 failed: " + FAILS);
    }
}
