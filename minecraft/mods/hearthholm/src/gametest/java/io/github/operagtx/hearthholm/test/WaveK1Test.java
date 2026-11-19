package io.github.operagtx.hearthholm.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Civic;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementEvents;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave K1: the real builder on a custom blueprint (panes, fences, double chest, torch on a support block that is missing at first),
 * skip-and-return, the stake after the build (stays / carried to the warehouse, never dropped), recipe collisions with vanilla,
 * the recipe book unlock, the Chronicle recipe chapter, co-owners. Run alone with HH_ONLY_K1=1.
 */
final class WaveK1Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos K;
    private static UUID BUILDER;

    private WaveK1Test() {}

    private static void fail(String m) { FAILS.add(m); log("K1 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static void setOwner(Settlement st, UUID id) {
        try {
            var f = Settlement.class.getField("owner");
            f.setAccessible(true);
            f.set(st, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Blueprint houseBp() {
        BlockState[] cells = new BlockState[5 * 4 * 4];
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int z = 0; z < 4; z++) for (int x = 0; x < 5; x++) cells[(0 * 4 + z) * 5 + x] = bricks;
        for (int x = 0; x < 5; x++) cells[(1 * 4 + 0) * 5 + x] = (x == 0 || x == 4) ? bricks : Blocks.GLASS_PANE.defaultBlockState();
        cells[(1 * 4 + 1) * 5 + 0] = Blocks.OAK_FENCE.defaultBlockState();
        cells[(1 * 4 + 1) * 5 + 1] = Blocks.OAK_FENCE.defaultBlockState();
        cells[(1 * 4 + 2) * 5 + 1] = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        cells[(1 * 4 + 2) * 5 + 2] = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        cells[(1 * 4 + 3) * 5 + 4] = Blocks.OAK_PLANKS.defaultBlockState();                       // the support block ...
        cells[(2 * 4 + 3) * 5 + 4] = Blocks.TORCH.defaultBlockState();                            // ... and the torch on top of it
        return new Blueprint(Hearthholm.id("k1_house"), 5, 4, 4, cells);
    }

    private static Blueprint tinyBp() {
        BlockState[] cells = new BlockState[1];
        cells[0] = Blocks.COBBLESTONE.defaultBlockState();
        return new Blueprint(Hearthholm.id("k1_tiny"), 1, 1, 1, cells);
    }

    private static int idx(Blueprint bp) { return BlueprintLibrary.ids().indexOf(bp.id()); }

    private static int itemsAround(ServerLevel l, BlockPos c, Item item) {
        int n = 0;
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(24))) if (e.getItem().is(item)) n += e.getItem().getCount();
        return n;
    }

    private static int stored(MinecraftServer s, Item item) {
        int n = 0;
        for (Container c : Storage.resolve(s.overworld(), town(s).storagePositions()))
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");
        Blueprint house = houseBp(), tiny = tinyBp();
        BlueprintLibrary.register(house);
        BlueprintLibrary.register(tiny);
        K = O.offset(-34, 0, 34).atY(O.getY() + 70);
        BlockPos torchPos = house.toWorld(K, Direction.SOUTH, 4, 2, 3), plankPos = house.toWorld(K, Direction.SOUTH, 4, 1, 3);
        BlockPos chestA = house.toWorld(K, Direction.SOUTH, 1, 1, 2), chestB = house.toWorld(K, Direction.SOUTH, 2, 1, 2);
        BlockPos pane2 = house.toWorld(K, Direction.SOUTH, 2, 1, 0), pane1 = house.toWorld(K, Direction.SOUTH, 1, 1, 0);
        BlockPos fence0 = house.toWorld(K, Direction.SOUTH, 0, 1, 1);

        // ---- 1. set-up: a platform in the sky, one builder, a stake with its own chest holding every material except planks
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, K.getX() + 0.5, K.getY() + 6, K.getZ() - 8.5, Set.of(), 0f, 25f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(80);   // let the chunks around the platform load before anybody is moved there
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            for (int dx = -9; dx <= 9; dx++) for (int dz = -4; dz <= 12; dz++) {
                for (int dy = -3; dy <= 6; dy++) l.setBlock(K.offset(dx, dy, dz), dy < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            if (l.getBlockEntity(STAKE) instanceof WorkSiteBlockEntity base) { base.setOption("build", 0); base.setWorkerId(null); }
            // every construction stake left over by earlier waves is switched off: a builder that still holds a claim on one of them
            // (setProfession is a no-op for the same profession, so its job keeps the old site) would walk away from the platform
            for (Settlement.SiteRecord rec : town(s).sitesOf(io.github.operagtx.hearthholm.worksite.WorkSiteTypes.CONSTRUCTION_ID)) {
                if (l.getBlockEntity(rec.pos()) instanceof WorkSiteBlockEntity other) { other.setOption("build", 0); other.setWorkerId(null); }
            }
            List<SettlerEntity> all = settlers(s);
            SettlerEntity builder = all.get(0);
            for (SettlerEntity e : all) if (e != builder) e.setProfession(Professions.UNEMPLOYED);
            builder.setProfession(Professions.UNEMPLOYED);   // drops a stale job (claimed site, running task) kept by a settler that already was a builder
            builder.setProfession(Professions.BUILDER);
            builder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            builder.inventory().clearContent();
            builder.markArrived();
            builder.teleportTo(K.getX() + 0.5, K.getY(), K.getZ() - 1.5);
            BUILDER = builder.getUUID();
            l.setBlock(K, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, K, p);
            l.setBlock(K.west(), Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(K.west()) instanceof Container c) {
                int slot = 0;
                for (var e : house.materials().entrySet()) {
                    if (e.getKey() == Items.OAK_PLANKS) continue;   // the missing material
                    c.setItem(slot++, new ItemStack(e.getKey(), Math.min(64, e.getValue() + 2)));
                }
            }
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(K);
            w.setOption("blueprint", idx(house));
            w.setOption("stake", 0);
            w.setOption("build", 1);
            check(house.materials().containsKey(Items.OAK_PLANKS), "test blueprint has no planks: " + house.materials());
            log("K1 set-up: builder " + builder.getName().getString() + " stake " + K.toShortString() + " torch " + torchPos.toShortString()
                    + " materials " + house.materials());
        });
        // (the player stays in creative and flies above the platform: a falling player would unload the chunks of the builder)

        // ---- 2. phase A: the builder builds everything except the planks and the torch (no planks anywhere): skip, never into air
        AtomicBoolean torchInAir = new AtomicBoolean();
        AtomicBoolean restDone = new AtomicBoolean();
        AtomicInteger waited = new AtomicInteger();
        for (int round = 0; round < 40 && !restDone.get(); round++) {
            ctx.waitTicks(60);
            sp.getServer().runCommand("time set 5000");
            waited.addAndGet(60);
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                BlockState t = l.getBlockState(torchPos);
                if (t.is(Blocks.TORCH) && !l.getBlockState(plankPos).is(Blocks.OAK_PLANKS)) torchInAir.set(true);
                boolean all = l.getBlockState(pane2).is(Blocks.GLASS_PANE) && l.getBlockState(pane1).is(Blocks.GLASS_PANE)
                        && l.getBlockState(chestA).is(Blocks.CHEST) && l.getBlockState(chestB).is(Blocks.CHEST) && l.getBlockState(fence0).is(Blocks.OAK_FENCE);
                if (all) restDone.set(true);
                if (waited.get() <= 360 || waited.get() % 600 == 0) {
                    SettlerEntity b = (SettlerEntity) l.getEntity(BUILDER);
                    WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(K);
                    log("K1 A poll: " + waited.get() + " resident=" + town(s).resident(BUILDER) + " builder=" + (b == null ? "GONE" : b.status().getString() + " alive=" + b.isAlive() + " pos=" + b.blockPosition().toShortString() + " prof=" + b.professionId()
                            + " inv=" + (18 - b.freeSlots())) + " progress=" + w.progress() + " worker=" + w.workerId() + " flag=" + w.flag("build") + " bp=" + w.option("blueprint"));
                }
            });
        }
        ctx.waitTicks(120);   // give a stuck builder time to (wrongly) place the torch
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity b = (SettlerEntity) l.getEntity(BUILDER);
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(K);
            BlockState torch = l.getBlockState(torchPos);
            log("K1 phase A: rest done=" + restDone.get() + " after " + waited.get() + " ticks, plank=" + l.getBlockState(plankPos).getBlock() + " torch=" + torch.getBlock()
                    + " status=" + (b == null ? "?" : b.status().getString()) + " progress=" + w.progress());
            check(restDone.get(), "the builder did not place the other blocks while the planks were missing (skip failed)");
            check(l.getBlockState(plankPos).isAir(), "planks placed without material");
            check(!torchInAir.get() && torch.isAir(), "the torch was placed with no support block (into the air)");
            BlockState p2 = l.getBlockState(pane2), p1 = l.getBlockState(pane1);
            log("K1 panes: " + p1 + " | " + p2);
            if (!(p1.getBlock() instanceof IronBarsBlock) || !(p2.getBlock() instanceof IronBarsBlock)) { fail("panes missing"); return; }
            check(p2.getValue(IronBarsBlock.EAST) && p2.getValue(IronBarsBlock.WEST), "middle pane is not connected to its neighbours: " + p2);
            check(p1.getValue(IronBarsBlock.WEST) && p1.getValue(IronBarsBlock.EAST), "pane next to the wall is not connected: " + p1);
            BlockState ca = l.getBlockState(chestA), cb = l.getBlockState(chestB);
            log("K1 chests: " + ca.getValue(ChestBlock.TYPE) + " " + cb.getValue(ChestBlock.TYPE));
            check(ca.getValue(ChestBlock.TYPE) != ChestType.SINGLE && cb.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && ca.getValue(ChestBlock.TYPE) != cb.getValue(ChestBlock.TYPE), "the two chests did not join into a double chest");
            check(l.getBlockState(fence0).getValue(FenceBlock.EAST), "fences are not connected");
        });
        // screenshot of the pane/chest result
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        sp.getServer().runOnServer(s -> {
            BlockPos c = house.toWorld(K, Direction.SOUTH, 2, 1, 1);
            ServerPlayer p = player(s);
            p.teleportTo(s.overworld(), c.getX() + 0.5, c.getY() + 3.0, c.getZ() - 6.5, Set.of(), 0f, 18f, false);
        });
        ctx.waitTicks(20);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_k1_builder_connected");
        ctx.getInput().pressKey(o -> o.keyToggleGui);

        // ---- 3. phase B: the planks arrive in the stake chest -> the builder returns to the skipped blocks and finishes
        sp.getServer().runOnServer(s -> {
            if (s.overworld().getBlockEntity(K.west()) instanceof Container c) c.setItem(c.getContainerSize() - 1, new ItemStack(Items.OAK_PLANKS, 8));
        });
        AtomicBoolean torchPlaced = new AtomicBoolean();
        for (int round = 0; round < 60 && !torchPlaced.get(); round++) {
            ctx.waitTicks(60);
            sp.getServer().runCommand("time set 5000");
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                if (l.getBlockState(torchPos).is(Blocks.TORCH)) torchPlaced.set(true);
            });
        }
        ctx.waitTicks(100);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            log("K1 phase B: planks=" + l.getBlockState(plankPos).getBlock() + " torch=" + l.getBlockState(torchPos).getBlock());
            check(l.getBlockState(plankPos).is(Blocks.OAK_PLANKS) && l.getBlockState(torchPos).is(Blocks.TORCH), "the builder did not return to the skipped blocks when the planks arrived");
            // stake option "stays": the block is still there, no item dropped, building switched off
            boolean stays = l.getBlockState(K).is(ModBlocks.CONSTRUCTION_STAKE);
            int drops = itemsAround(l, K, ModBlocks.CONSTRUCTION_STAKE.asItem());
            WorkSiteBlockEntity w = l.getBlockEntity(K) instanceof WorkSiteBlockEntity x ? x : null;
            log("K1 stake after build: stays=" + stays + " drops=" + drops + " buildFlag=" + (w == null ? "?" : w.flag("build")));
            check(stays && drops == 0, "stake did not stay in place / was dropped (stays=" + stays + " drops=" + drops + ")");
            check(w != null && !w.flag("build"), "the kept stake still has building switched on");
            check(l.getBlockState(torchPos).is(Blocks.TORCH), "torch missing");
        });

        // ---- 4. the "carry" option: the stake disappears (never an item on the ground) and ends up in the warehouse
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (int dy = 0; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = 0; dz <= 5; dz++) l.setBlock(K.offset(dx, dy - 1, dz + 2), dy == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(K);
            if (l.getBlockEntity(K.west()) instanceof Container c) { c.clearContent(); c.setItem(0, new ItemStack(Items.COBBLESTONE, 8)); }
            w.setOption("blueprint", idx(tiny));
            w.setOption("stake", 1);
            w.setOption("build", 1);
        });
        AtomicBoolean gone = new AtomicBoolean();
        for (int round = 0; round < 40 && !gone.get(); round++) {
            ctx.waitTicks(40);
            sp.getServer().runCommand("time set 5000");
            sp.getServer().runOnServer(s -> { if (!s.overworld().getBlockState(K).is(ModBlocks.CONSTRUCTION_STAKE)) gone.set(true); });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity b = (SettlerEntity) l.getEntity(BUILDER);
            int drops = itemsAround(l, K, ModBlocks.CONSTRUCTION_STAKE.asItem());
            boolean carries = b != null && b.has(ModBlocks.CONSTRUCTION_STAKE.asItem());
            log("K1 carry: stake gone=" + gone.get() + " drops=" + drops + " builderCarries=" + carries);
            check(gone.get(), "the stake did not disappear after the build (carry option)");
            check(drops == 0, "the stake was dropped as an item");
            check(carries, "the builder does not carry the stake");
            // bring the builder to the warehouse: he must put the stake into a warehouse chest
            List<BlockPos> st = town(s).storagePositions();
            if (b != null && !st.isEmpty()) b.teleportTo(st.get(0).getX() + 0.5, st.get(0).getY(), st.get(0).getZ() + 1.5);
        });
        int[] before = new int[1];
        sp.getServer().runOnServer(s -> before[0] = stored(s, ModBlocks.CONSTRUCTION_STAKE.asItem()));
        boolean delivered = false;
        for (int round = 0; round < 15 && !delivered; round++) {
            ctx.waitTicks(40);
            int[] now = new int[1];
            sp.getServer().runOnServer(s -> now[0] = stored(s, ModBlocks.CONSTRUCTION_STAKE.asItem()));
            delivered = now[0] > before[0];
        }
        log("K1 stake delivered to the warehouse chest: " + delivered);
        check(delivered, "the carried stake did not reach the warehouse chest");

        // ---- 5. recipes: collisions with vanilla, the recipe book, the Chronicle chapter
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            List<String> collisions = new ArrayList<>();
            List<RecipeHolder<?>> mine = new ArrayList<>();
            List<RecipeHolder<?>> all = new ArrayList<>(s.getRecipeManager().getRecipes());
            for (RecipeHolder<?> h : all) if (h.id().identifier().getNamespace().equals(Hearthholm.MOD_ID)) mine.add(h);
            int checked = 0;
            for (RecipeHolder<?> a : mine) {
                if (!(a.value() instanceof ShapedRecipe) && !(a.value() instanceof ShapelessRecipe)) continue;
                checked++;
                for (CraftingInput grid : grids(a.value())) {
                    for (RecipeHolder<?> b : all) {
                        if (b == a || !(b.value() instanceof CraftingRecipe cr)) continue;
                        boolean m;
                        try { @SuppressWarnings("unchecked") Recipe<CraftingInput> r = (Recipe<CraftingInput>) cr; m = r.matches(grid, l); } catch (RuntimeException e) { continue; }
                        if (m) {
                            String key = a.id().identifier() + " <-> " + b.id().identifier();
                            if (!collisions.contains(key)) collisions.add(key);
                        }
                    }
                }
            }
            log("K1 recipe collision check: " + checked + " mod crafting recipes, collisions=" + collisions);
            check(checked >= 60, "too few mod recipes found: " + checked);
            check(collisions.isEmpty(), "recipe collisions: " + collisions);
            // recipe book
            int added = SettlementEvents.unlockRecipes(p);
            int missing = 0;
            for (RecipeHolder<?> h : mine) if (!p.getRecipeBook().contains(h.id())) missing++;
            log("K1 recipe book: mod recipes=" + mine.size() + " newly awarded=" + added + " missing=" + missing);
            check(missing == 0 && !mine.isEmpty(), "mod recipes missing from the recipe book: " + missing);
        });
        ctx.runOnClient(mc -> {
            int shown = io.github.operagtx.hearthholm.client.screen.ChronicleScreen.recipes().size();
            log("K1 chronicle recipe pages: entries=" + shown + " pages=" + io.github.operagtx.hearthholm.client.screen.ChronicleScreen.recipePages());
            if (shown < 60) throw new AssertionError("chronicle recipe list too short: " + shown);
        });
        ctx.runOnClient(mc -> io.github.operagtx.hearthholm.client.screen.ChronicleScreen.open(0, 0));
        ctx.waitForScreen(io.github.operagtx.hearthholm.client.screen.ChronicleScreen.class);
        ctx.runOnClient(mc -> io.github.operagtx.hearthholm.client.screen.ChronicleScreen.open(io.github.operagtx.hearthholm.client.screen.ChronicleScreen.RECIPE_CHAPTER - 1, 0));
        ctx.waitForScreen(io.github.operagtx.hearthholm.client.screen.ChronicleScreen.class);
        ctx.runOnClient(mc -> mc.setScreenAndShow(new io.github.operagtx.hearthholm.client.screen.ChronicleScreen(io.github.operagtx.hearthholm.client.screen.ChronicleScreen.recipeStart() + 2)));
        ctx.waitTicks(15);
        ctx.takeScreenshot("hh_k1_chronicle_recipes");
        ctx.setScreen(() -> null);

        // ---- 6. co-owners (the real player is made a stranger by swapping the founder id; creative may always manage, so check in survival)
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            UUID real = st.owner;
            p.teleportTo(l, DESK.getX() + 0.5, DESK.getY() + 1, DESK.getZ() - 1.5, Set.of(), 0f, 10f, false);
            st.civic.coOwners.clear();
            setOwner(st, UUID.randomUUID());
            // a stranger (survival) cannot add
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.ADD_OWNER, 0, p.getUUID().toString()));
            check(st.civic.coOwners.isEmpty(), "a stranger could add a co-owner");
            check(!SettlementService.canManage(p, st), "stranger can manage");
            // creative acts like an admin and adds the player as co-owner
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.ADD_OWNER, 0, p.getUUID().toString()));
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.ADD_OWNER, 0, "not-a-uuid"));
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.ADD_OWNER, 0, UUID.randomUUID().toString()));   // offline
            log("K1 co-owners after add: " + st.civic.coOwners);
            check(st.civic.coOwners.size() == 1 && st.civic.coOwners.get(0).id().equals(p.getUUID()), "co-owner not added (or offline/invalid ids were accepted)");
            // as a survival co-owner: manages everything, but cannot remove co-owners or break the hall
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            check(SettlementService.canManage(p, st), "co-owner cannot manage");
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.REMOVE_OWNER, 0, p.getUUID().toString()));
            check(st.civic.coOwners.size() == 1, "a co-owner removed a co-owner");
            boolean mayBreak = net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(l, p, DESK, l.getBlockState(DESK), l.getBlockEntity(DESK));
            check(!mayBreak, "a co-owner may break the town hall");
            // co-owner manages a work site screen action and the inventory rules (same canManage gate)
            WorkSiteBlockEntity w = l.getBlockEntity(K) instanceof WorkSiteBlockEntity x ? x : null;
            if (w != null) {
                SettlementService.handleWorkSite(p, new Payloads.WorkSiteAction(K, "stake", 0));
                check(w.option("stake") == 0, "co-owner could not change a stake option");
            }
            // the founder returns: removing works, breaking the hall is allowed
            setOwner(st, real);
            boolean founderBreak = net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(l, p, DESK, l.getBlockState(DESK), l.getBlockEntity(DESK));
            check(founderBreak, "the founder may not break the town hall");
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.REMOVE_OWNER, 0, p.getUUID().toString()));
            check(st.civic.coOwners.isEmpty(), "the founder could not remove the co-owner");
            check(SettlementService.canManage(p, st), "founder cannot manage");
            // save compatibility: an old save without the field loads with an empty list; a new one round-trips
            st.civic.coOwners.add(new Civic.CoOwner(UUID.randomUUID(), "Alex"));
            JsonElement enc = Settlement.CODEC.encodeStart(JsonOps.INSTANCE, st).getOrThrow();
            Settlement back = Settlement.CODEC.parse(JsonOps.INSTANCE, enc).getOrThrow();
            check(back.civic.coOwners.size() == 1 && back.civic.coOwners.get(0).name().equals("Alex"), "co-owner list did not round-trip");
            JsonObject civic = enc.getAsJsonObject().has("civic") ? enc.getAsJsonObject().getAsJsonObject("civic") : null;
            check(civic != null && civic.has("co_owners"), "co_owners missing in the encoded save");
            if (civic != null) civic.remove("co_owners");
            Settlement old = Settlement.CODEC.parse(JsonOps.INSTANCE, enc).getOrThrow();
            check(old.civic.coOwners.isEmpty(), "an old save without co_owners did not load with an empty list");
            log("K1 co-owner permissions OK, codec default OK");
            // for the screenshot: one co-owner in the list
        });

        // ---- 7. the Owners tab screenshot
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            if (st.civic.coOwners.isEmpty()) st.civic.coOwners.add(new Civic.CoOwner(UUID.randomUUID(), "Alex"));
        });
        ctx.runOnClient(mc -> {
            try {
                var f = io.github.operagtx.hearthholm.client.screen.TownHallScreen.class.getDeclaredField("lastTab");
                f.setAccessible(true);
                f.setInt(null, 5);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        sp.getServer().runOnServer(s -> SettlementService.openTownHall(player(s), DESK));
        ctx.waitForScreen(io.github.operagtx.hearthholm.client.screen.TownHallScreen.class);
        ctx.waitTicks(25);
        ctx.takeScreenshot("hh_k1_owners_tab");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> town(s).civic.coOwners.clear());
        ctx.runOnClient(mc -> {
            try {
                var f = io.github.operagtx.hearthholm.client.screen.TownHallScreen.class.getDeclaredField("lastTab");
                f.setAccessible(true);
                f.setInt(null, 0);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });

        log("K1 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave K1 failed: " + FAILS);
    }

    /** Concrete crafting grids a recipe accepts: the first item of every ingredient, then every alternative of each slot. */
    private static List<CraftingInput> grids(Recipe<?> r) {
        List<CraftingInput> out = new ArrayList<>();
        int w, h;
        List<Optional<Ingredient>> slots = new ArrayList<>();
        if (r instanceof ShapedRecipe sr) {
            w = sr.getWidth(); h = sr.getHeight();
            slots.addAll(sr.getIngredients());
        } else if (r instanceof ShapelessRecipe sl) {
            slots.addAll(sl.placementInfo().ingredients().stream().map(Optional::of).toList());
            w = slots.size(); h = 1;
        } else return out;
        List<List<ItemStack>> options = new ArrayList<>();
        for (Optional<Ingredient> oi : slots) {
            List<ItemStack> o = new ArrayList<>();
            oi.ifPresent(i -> i.items().limit(24).forEach(hh -> o.add(new ItemStack(hh.value()))));
            options.add(o);
        }
        for (int k = 0; k < 3; k++) {   // all slots on the same alternative index
            List<ItemStack> g = new ArrayList<>();
            for (List<ItemStack> o : options) g.add(o.isEmpty() ? ItemStack.EMPTY : o.get(Math.min(k, o.size() - 1)).copy());
            out.add(CraftingInput.of(w, h, g));
        }
        for (int slot = 0; slot < options.size(); slot++)   // one slot varies, the rest stay on the first alternative
            for (ItemStack alt : options.get(slot)) {
                List<ItemStack> g = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) g.add(i == slot ? alt.copy() : options.get(i).isEmpty() ? ItemStack.EMPTY : options.get(i).get(0).copy());
                out.add(CraftingInput.of(w, h, g));
            }
        return out;
    }
}
