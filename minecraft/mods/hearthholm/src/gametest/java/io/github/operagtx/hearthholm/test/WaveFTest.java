package io.github.operagtx.hearthholm.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.client.screen.ChronicleScreen;
import io.github.operagtx.hearthholm.crafting.Recipes;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.item.ChronicleItem;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.settlement.Civic;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.town.Fx;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/** Wave F checks: guide book (given once, opens, fits), decor in the order catalogue, watchtower tier lock, sounds and particles. */
final class WaveFTest {
    private static int oldRenown;
    private static int fxBefore;

    private WaveFTest() {}

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, false);
    }

    private static JsonObject lang(String code) {
        try (var in = Hearthholm.class.getResourceAsStream("/assets/hearthholm/lang/" + code + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("no lang " + code, e);
        }
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 6000");

        // ---- 1. the guide book: given once on founding (persisted flag), old saves load, opens, text fits the page
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            Settlement st = town(s);
            boolean again = SettlementService.giveGuide(p, st);
            log("GUIDE persisted flag=" + st.civic.guideGiven + " givenAgain=" + again);
            if (!st.civic.guideGiven || again) throw new AssertionError("guide book must be given exactly once");
            var enc = Civic.CODEC.encodeStart(JsonOps.INSTANCE, st.civic).getOrThrow();
            boolean back = Civic.CODEC.parse(JsonOps.INSTANCE, enc).getOrThrow().guideGiven;
            var old = enc.getAsJsonObject().deepCopy();
            old.remove("guide_given");
            boolean oldFlag = Civic.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().guideGiven;
            log("GUIDE flag persists=" + back + " old save defaults to " + oldFlag);
            if (!back || oldFlag) throw new AssertionError("guide flag codec");
        });
        ctx.runOnClient(mc -> {
            JsonObject en = lang("en_us"), ru = lang("ru_ru");
            int maxEn = 0, maxRu = 0, keys = 0;
            for (int c = 1; c <= ChronicleScreen.CHAPTERS; c++) {
                for (String k : new String[]{"title", "p1", "p2"}) {
                    String key = "guide.hearthholm.ch" + c + "." + k;
                    if (!en.has(key) || !ru.has(key)) throw new AssertionError("missing guide text " + key);
                    keys++;
                    if (k.equals("title")) continue;
                    int w = ChronicleScreen.PANEL_W - ChronicleScreen.LIST_W - 30;
                    maxEn = Math.max(maxEn, mc.font.split(Component.literal(en.get(key).getAsString()), w).size());
                    maxRu = Math.max(maxRu, mc.font.split(Component.literal(ru.get(key).getAsString()), w).size());
                }
            }
            log("GUIDE text keys=" + keys + " (EN+RU) longest page lines EN=" + maxEn + " RU=" + maxRu);
            if (maxEn > 15 || maxRu > 15) throw new AssertionError("guide page too long: " + maxEn + "/" + maxRu);
            ChronicleItem.OPENER.run();   // what right-clicking the book does on the client
        });
        ctx.waitForScreen(ChronicleScreen.class);
        ctx.waitTicks(5);
        log("GUIDE opened via item, page=" + ((ChronicleScreen) ctxScreen(ctx)).page());
        ctx.takeScreenshot("hh_f1_guide_founding");
        ctx.setScreen(() -> null);
        ctx.runOnClient(mc -> ChronicleScreen.open(5, 1));   // "Tiers and blueprints", second page
        ctx.waitForScreen(ChronicleScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_f2_guide_tiers");
        log("GUIDE second screenshot page=" + ((ChronicleScreen) ctxScreen(ctx)).page());
        ctx.setScreen(() -> null);

        // ---- 2. decor blocks are in the carpenter / mason / smith catalogues, each with a usable recipe
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Item[] decor = {ModBlocks.TAVERN_KEG.asItem(), ModBlocks.BENCH.asItem(), ModBlocks.COUNTER.asItem(), ModBlocks.FLOWER_BOX.asItem(),
                    ModBlocks.SIGNPOST.asItem(), ModBlocks.LAMP_POST.asItem(), ModBlocks.WELL.asItem()};
            StringBuilder sb = new StringBuilder();
            for (Item it : decor) {
                Workshop w = Workshop.forItem(it);
                int opts = Recipes.options(l, it).size();
                sb.append(' ').append(BuiltInRegistries.ITEM.getKey(it).getPath()).append("->").append(w == null ? "NONE" : w.key()).append('/').append(opts);
                if (w == null || opts == 0) throw new AssertionError("decor not orderable: " + it);
            }
            for (var sign : ModBlocks.SIGNS) if (Workshop.forItem(sign.asItem()) == null) throw new AssertionError("sign not orderable " + sign);
            log("DECOR catalog:" + sb + " signs=" + ModBlocks.SIGNS.size());
            for (Workshop w : Workshop.values()) if (w.hasCatalog()) log("catalog " + w.key() + " items=" + w.catalog().size());
            // a real order: two benches, the carpenter should make them from planks and sticks in the warehouse
            Settlement st = town(s);
            var storage = Storage.resolve(l, st.storagePositions());
            st.alarm = false;
            // the warehouse may be full after wave E: free a few slots of cobblestone / sand first
            for (ItemStack add : new ItemStack[]{new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.STICK, 16)}) {
                ItemStack rest = Storage.insert(storage, add.copy());
                for (var c : storage) {
                    for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
                        if (c.getItem(i).is(Items.COBBLESTONE) || c.getItem(i).is(Items.SAND) || c.getItem(i).is(Items.GLASS_PANE)) {
                            c.setItem(i, ItemStack.EMPTY);
                            rest = Storage.insert(storage, rest);
                        }
                    }
                }
                log("DECOR stocked " + add.getHoverName().getString() + " leftover=" + rest.getCount());
            }
            if (settlers(s).stream().noneMatch(e -> e.professionId().equals(io.github.operagtx.hearthholm.core.Professions.CARPENTER.id()))) {
                // wave E's fuel test may have turned the only carpenter into the cook: give the trade back
                for (var e : settlers(s)) if (e.professionId().equals(io.github.operagtx.hearthholm.core.Professions.COOK.id())) {
                    e.setProfession(io.github.operagtx.hearthholm.core.Professions.CARPENTER);
                    log("DECOR no carpenter left, " + e.getName().getString() + " retrains as carpenter");
                    break;
                }
            }
            SettlementService.setOrder(st, ModBlocks.BENCH.asItem(), 2);
            log("DECOR order bench placed, benches in stock=" + stock(s, ModBlocks.BENCH.asItem()));
        });
        boolean[] made = {false};
        for (int i = 0; i < 12 && !made[0]; i++) {
            ctx.waitTicks(150);
            sp.getServer().runOnServer(s -> made[0] = stock(s, ModBlocks.BENCH.asItem()) >= 1);
        }
        sp.getServer().runOnServer(s -> {
            log("DECOR benches made by the carpenter: " + stock(s, ModBlocks.BENCH.asItem()));
            SettlementService.setOrder(town(s), ModBlocks.BENCH.asItem(), 0);
        });
        if (!made[0]) {
            sp.getServer().runOnServer(s -> {
                for (var e : settlers(s)) if (e.professionId().getPath().equals("carpenter"))
                    log("DECOR carpenter status='" + e.status().getString() + "' act=" + e.brain().currentActivity() + " sticks=" + stock(s, Items.STICK)
                            + " planks=" + stock(s, Items.OAK_PLANKS) + " orders=" + town(s).orders.size() + " alarm=" + town(s).alarm);
            });
            throw new AssertionError("carpenter did not craft a bench from the order");
        }

        // ---- 3. watchtower needs a village; a tower bought from the caravan opens early
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            st.alarm = false;
            BlockPos stake = STAKE;
            p.teleportTo(l, stake.getX() + 0.5, stake.getY(), stake.getZ() + 3.5, Set.of(), 0f, 10f, false);
            if (!(l.getBlockEntity(stake) instanceof WorkSiteBlockEntity)) {
                l.setBlock(stake, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
                SettlementService.onWorkSitePlaced(l, stake, p);
            }
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(stake);
            w.setOption("build", 0);
            int tower = BlueprintLibrary.ids().indexOf(Hearthholm.id("watchtower"));
            oldRenown = st.renown;
            List<String> bought = new ArrayList<>(st.civic.unlocked);   // wave E may have bought plans from the caravan
            st.civic.unlocked.clear();
            StringBuilder sb = new StringBuilder();
            boolean[] open = new boolean[3];
            int[] renowns = {10, 70, 400};
            for (int i = 0; i < 3; i++) {
                st.renown = renowns[i];
                w.setOption("blueprint", 0);
                SettlementService.handleWorkSite(p, new Payloads.WorkSiteAction(stake, "blueprint", tower));
                open[i] = w.option("blueprint") == tower;
                sb.append(' ').append(st.tier().key()).append('=').append(open[i] ? "open" : "locked");
            }
            log("TIER watchtower (requires tier " + BlueprintLibrary.requiredTier(Hearthholm.id("watchtower")) + "):" + sb);
            if (open[0] || open[1] || !open[2]) throw new AssertionError("watchtower gate wrong:" + sb);
            st.renown = 10;
            st.civic.unlocked.add("watchtower");
            w.setOption("blueprint", 0);
            SettlementService.handleWorkSite(p, new Payloads.WorkSiteAction(stake, "blueprint", tower));
            log("TIER watchtower bought plans open at camp=" + (w.option("blueprint") == tower));
            if (w.option("blueprint") != tower) throw new AssertionError("bought watchtower not accepted");
            st.civic.unlocked.clear();
            st.civic.unlocked.addAll(bought);
            w.setOption("blueprint", 0);
            // tier fanfare: renown jumps from camp to town, the server notices within a second
            st.renown = 1100;
            fxBefore = Fx.COUNTS.getOrDefault("levelUp", 0);
        });
        ctx.waitTicks(50);
        ctx.setScreen(() -> null);   // the work site replies of step 3 opened the stake screen

        // ---- 4. sounds and particles
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            int after = Fx.COUNTS.getOrDefault("levelUp", 0);
            log("FX tier-up fanfare fired=" + (after - fxBefore) + " tier=" + st.tier().key());
            if (after <= fxBefore) throw new AssertionError("no tier-up fanfare");
            st.renown = Math.max(oldRenown, 400);
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SoundEvent[] ev = {ModSounds.CHOP, ModSounds.PICK, ModSounds.LEVEL_UP, ModSounds.CARAVAN_HORN, ModSounds.QUEST_DONE,
                    ModSounds.SAW, ModSounds.CHISEL, ModSounds.HAMMER, ModSounds.SIZZLE, ModSounds.BELL, ModSounds.FESTIVAL, ModSounds.WAR_HORN, ModSounds.RAID_HORN};
            int n = 0;
            for (SoundEvent e : ev) {
                if (e == null || !BuiltInRegistries.SOUND_EVENT.containsKey(e.location())) throw new AssertionError("sound not registered " + e);
                n++;
            }
            log("FX sound events registered=" + n);
            look(p, DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 1.5, DESK.getX() + 0.5, DESK.getY() + 1.5, DESK.getZ() + 3.5);
            BlockPos at = DESK.offset(0, 0, 2);
            var settler = settlers(s).getFirst();
            settler.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            settler.gainXp(100000);   // levels up: sound + particles
            Fx.festival(l, at.above(), 10);
            Fx.caravan(l, at.offset(2, 0, 0));
            Fx.quest(l, at.offset(-2, 0, 0));
            Fx.forge(l, net.minecraft.world.phys.Vec3.atCenterOf(at.offset(1, 0, -1)));
            Fx.cook(l, net.minecraft.world.phys.Vec3.atCenterOf(at.offset(-1, 0, -1)));
        });
        ctx.waitTicks(6);
        ctx.takeScreenshot("hh_f3_fx_particles");
        ctx.runOnClient(mc -> {
            String[] files = {"workshop/chop1", "workshop/chop2", "workshop/pick1", "workshop/pick2", "social/level_up", "town/caravan_horn", "town/quest_done"};
            int found = 0;
            for (String f : files) {
                if (mc.getResourceManager().getResource(Hearthholm.id("sounds/" + f + ".ogg")).isPresent()) found++;
                else throw new AssertionError("missing sound file " + f);
            }
            log("FX sound files present=" + found + "/" + files.length);
        });
        sp.getServer().runOnServer(s -> {
            log("FX counts " + Fx.COUNTS + " bursts=" + Fx.bursts + " sounds=" + Fx.sounds);
            for (String k : new String[]{"levelUp", "festival", "caravan", "quest", "forge", "cook"})
                if (Fx.COUNTS.getOrDefault(k, 0) <= 0) throw new AssertionError("no fx " + k);
            log("WAVE F OK");
        });
    }

    private static net.minecraft.client.gui.screens.Screen ctxScreen(ClientGameTestContext ctx) {
        net.minecraft.client.gui.screens.Screen[] out = {null};
        ctx.runOnClient(mc -> out[0] = mc.gui.screen());
        return out[0];
    }
}
