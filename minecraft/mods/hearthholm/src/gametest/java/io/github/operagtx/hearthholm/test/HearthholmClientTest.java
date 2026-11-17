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
    private static BlockPos O, DESK, MINE, FARM, STAKE, FOREST;

    private static void log(String s) {
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
            log("DONE");
        }
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static List<SettlerEntity> settlers(MinecraftServer s) {
        return s.overworld().getEntitiesOfClass(SettlerEntity.class, new AABB(O).inflate(96));
    }

    private static void box(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall, BlockState roof) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1 + 1; y++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    BlockPos p = O.offset(x, y, z);
                    if (y == y1 + 1) l.setBlock(p, roof, 3);
                    else l.setBlock(p, edge ? wall : Blocks.AIR.defaultBlockState(), 3);
                }
    }

    private static void door(ServerLevel l, BlockPos p) {
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
        SettlementService.onLedgerPlaced(l, ledger, p);
        // field with water
        FARM = O.offset(4, 0, 15);
        l.setBlock(FARM, marker(ModBlocks.FARM_MARKER, Direction.SOUTH), 3);
        l.setBlock(FARM.offset(3, -1, 0), Blocks.WATER.defaultBlockState(), 3);
        SettlementService.onWorkSitePlaced(l, FARM, p);
        // mine with a chest
        MINE = O.offset(14, 0, 14);
        l.setBlock(MINE, marker(ModBlocks.MINE_MARKER, Direction.SOUTH), 3);
        fillChest(l, MINE.west());
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
        if (l.getBlockEntity(STAKE) instanceof WorkSiteBlockEntity w) w.setOption("build", 1);
        p.teleportTo(l, DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 1.5, java.util.Set.of(), 0f, 10f, false);
    }

    private static void hire(MinecraftServer s) {
        ServerLevel l = s.overworld();
        ServerPlayer p = player(s);
        Settlement st = SettlementManager.get(s).byId(((io.github.operagtx.hearthholm.block.TownHallDeskBlockEntity) l.getBlockEntity(DESK)).settlementId());
        st.rescan(l);
        log("tier=" + st.tier().key() + " renown=" + st.renown + " beds=" + st.housingBeds().size() + " hall=" + st.hallValid() + " wh=" + st.warehouseValid()
                + " buildings=" + st.buildings.size() + " sites=" + st.sites.size());
        for (var b : st.buildings) log("building roles=" + b.roles() + " problem=" + b.problem() + " beds=" + b.beds().size() + " containers=" + b.containers().size());
        SettlementService.openTownHall(p, DESK);
        for (int i = 0; i < 4; i++) {
            if (st.candidates.isEmpty()) SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.REROLL, 0, ""));
            SettlementService.handleTownHall(p, new Payloads.TownHallAction(DESK, Payloads.TownHallAction.HIRE, 0, ""));
        }
        List<SettlerEntity> list = settlers(s);
        log("hired " + list.size() + " residents=" + st.residents.size());
        var profs = List.of(Professions.MINER, Professions.FARMER, Professions.LUMBERJACK, Professions.BUILDER);
        for (int i = 0; i < list.size(); i++) {
            SettlerEntity e = list.get(i);
            e.markArrived();
            e.teleportTo(DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 2.5);
            e.setProfession(profs.get(i % profs.size()));
            log("settler " + e.getName().getString() + " -> " + e.professionId() + " bed=" + e.bed());
        }
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
    }
}
