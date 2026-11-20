package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.client.GhostPreview;
import io.github.operagtx.hearthholm.client.ZonePreview;
import io.github.operagtx.hearthholm.client.particle.HearthParticles;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.registry.ModParticles;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.town.Fx;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave L3: the mod's own particle types and the ghost blueprint. The four types are registered; the ghost preview lists exactly the blueprint blocks that are
 * not built yet (30% opacity pictures, renewed every 10 ticks while a Hearthholm item is held near a construction stake); sparks, embers and dust are sent from
 * the server and photographed together with a ghost house. Run alone with HH_ONLY_L3=1.
 */
final class WaveL3Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos G, STAKE3;

    private WaveL3Test() {}

    private static void fail(String m) { FAILS.add(m); log("L3 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    /** 3x3 stone-brick floor, two oak-log pillars on it: 9 + 2 = 11 blocks to build. */
    private static Blueprint ghostBp() {
        BlockState[] cells = new BlockState[3 * 2 * 3];
        for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) cells[(0 * 3 + z) * 3 + x] = Blocks.STONE_BRICKS.defaultBlockState();
        cells[(1 * 3 + 0) * 3 + 0] = Blocks.OAK_LOG.defaultBlockState();
        cells[(1 * 3 + 0) * 3 + 2] = Blocks.OAK_LOG.defaultBlockState();
        return new Blueprint(Hearthholm.id("l3_ghost"), 3, 2, 3, cells);
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("weather clear");
        sp.getServer().runCommand("time set 5000");
        G = O.offset(40, 0, 30).atY(O.getY() + 70);   // inside the settlement territory (radius 72), clear of the other waves' sky platforms
        STAKE3 = G;

        // ---- 1. the types
        sp.getServer().runOnServer(s -> {
            check(ModParticles.HEARTH_SPARK != null && ModParticles.ASH_EMBER != null && ModParticles.BUILD_DUST != null && ModParticles.GHOST_BLOCK != null, "a particle type is not registered");
            for (String id : List.of("hearth_spark", "ash_ember", "build_dust", "ghost_block"))
                check(BuiltInRegistries.PARTICLE_TYPE.containsKey(Hearthholm.id(id)), "particle type " + id + " is not in the registry");
            check(Math.abs(HearthParticles.GhostBlockParticle.ALPHA - 0.30f) < 0.001f, "the ghost opacity must be about 30%");
        });

        // ---- 2. a stake with a small blueprint on a platform in the sky
        Blueprint bp = ghostBp();
        BlueprintLibrary.register(bp);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, G.getX() + 0.5, G.getY() + 4, G.getZ() - 7.5, Set.of(), 0f, 20f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(80);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            for (int dx = -10; dx <= 10; dx++) for (int dz = -12; dz <= 14; dz++) {
                l.setBlock(G.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
                l.setBlock(G.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= 8; dy++) l.setBlock(G.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            l.setBlock(STAKE3, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            check(town(s).contains(l.dimension(), STAKE3), "the stake would stand outside the settlement territory");
            SettlementService.onWorkSitePlaced(l, STAKE3, p);
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(STAKE3);
            w.setOption("blueprint", BlueprintLibrary.ids().indexOf(bp.id()));
            w.setOption("build", 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FOUNDERS_WHISTLE));   // any Hearthholm item shows the previews
            log("L3 set-up: stake " + STAKE3.toShortString() + ", blueprint " + bp.id() + " ops " + bp.ops(STAKE3, Direction.SOUTH).size());
        });
        sp.getConnection().waitForChunksRender();
        ctx.waitTicks(30);

        // ---- 3. the ghost lists the unbuilt blocks and shrinks as blocks get built; it does not list air or clears
        int[] counts = new int[3];
        counts[0] = ctx.computeOnClient(mc -> GhostPreview.pending(mc.level, mc.player, (WorkSiteBlockEntity) mc.level.getBlockEntity(STAKE3)).size());
        int expected = (int) bp.ops(STAKE3, Direction.SOUTH).stream().filter(op -> !op.clear() && !op.state().isAir()).count();
        log("L3 ghost: " + counts[0] + " blocks shown, blueprint has " + expected + " blocks to place");
        check(counts[0] == expected && expected == 11, "the ghost must show every block that still has to be built: " + counts[0] + " vs " + expected);
        ctx.takeScreenshot("hh_l3_ghost_empty");
        sp.getServer().runOnServer(s -> {   // the builder "built" the floor's first row: those ghosts must go
            ServerLevel l = s.overworld();
            for (Blueprint.BuildOp op : bp.ops(STAKE3, Direction.SOUTH)) {
                if (op.clear() || op.state().isAir()) continue;
                if (op.state().getBlock() == Blocks.STONE_BRICKS && op.pos().getZ() == bp.ops(STAKE3, Direction.SOUTH).stream().filter(o -> !o.clear()).mapToInt(o -> o.pos().getZ()).min().orElse(0))
                    l.setBlock(op.pos(), op.state(), 3);
            }
        });
        ctx.waitTicks(12);
        counts[1] = ctx.computeOnClient(mc -> GhostPreview.pending(mc.level, mc.player, (WorkSiteBlockEntity) mc.level.getBlockEntity(STAKE3)).size());
        log("L3 ghost: after building one row " + counts[1] + " blocks are still shown");
        check(counts[1] == expected - 3, "a built block must lose its ghost: " + counts[1] + " vs " + (expected - 3));
        int shown = ctx.computeOnClient(mc -> ZonePreview.lastGhosts);
        check(shown > 0 && shown <= GhostPreview.MAX_GHOSTS, "the preview round showed " + shown + " ghosts");
        // the real thing: a village blueprint, ghost photographed from the front
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(STAKE3);
            for (int dx = -10; dx <= 10; dx++) for (int dz = -2; dz <= 12; dz++) for (int dy = 0; dy <= 8; dy++)
                if (!G.offset(dx, dy, dz).equals(STAKE3)) l.setBlock(G.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);   // everything but the stake itself
            w.setOption("blueprint", BlueprintLibrary.ids().indexOf(Hearthholm.id("small_house")));
            p.teleportTo(l, G.getX() + 0.5, G.getY() + 3.2, G.getZ() - 6.5, Set.of(), 0f, 18f, false);
        });
        ctx.waitTicks(30);
        counts[2] = ctx.computeOnClient(mc -> GhostPreview.pending(mc.level, mc.player, (WorkSiteBlockEntity) mc.level.getBlockEntity(STAKE3)).size());
        log("L3 ghost: the small house shows " + counts[2] + " ghost blocks");
        check(counts[2] > 40 && counts[2] <= GhostPreview.MAX_GHOSTS, "the small house must show a ghost: " + counts[2]);
        ctx.waitTicks(4);
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(true));   // the placement messages would cover the picture
        ctx.waitTicks(2);
        ctx.takeScreenshot("hh_l3_ghost_house");

        // ---- 4. the server's own particles: sparks, embers and dust at once in front of the camera
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, G.getX() + 0.5, G.getY() + 2.2, G.getZ() + 14.5, Set.of(), 180f, 8f, false);   // the other side of the platform, looking north at the stake
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);   // no ghosts in this photo
        });
        int before = Fx.COUNTS.getOrDefault("sparks", 0) + Fx.COUNTS.getOrDefault("embers", 0) + Fx.COUNTS.getOrDefault("buildDust", 0);
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(true));
        for (int i = 0; i < 24; i++) {
            sp.getServer().runOnServer(s -> {   // three effects a few blocks in front of the camera (the player looks north from G + 14.5)
                ServerLevel l = s.overworld();
                ServerPlayer p = player(s);
                double x = p.getX(), y = p.getY() + 0.6, z = p.getZ() - 4.0;
                Fx.sparks(l, x - 1.6, y + 0.4, z, 8, 0.5);
                Fx.embers(l, x + 1.6, y, z, 8, 0.5);
                Fx.buildDust(l, BlockPos.containing(x, y - 1.0, z));
                Fx.buildDust(l, BlockPos.containing(x + 0.4, y - 1.0, z));
            });
            ctx.waitTicks(2);
        }
        ctx.takeScreenshot("hh_l3_particles");
        int after = Fx.COUNTS.getOrDefault("sparks", 0) + Fx.COUNTS.getOrDefault("embers", 0) + Fx.COUNTS.getOrDefault("buildDust", 0);
        check(after - before >= 72, "the effects were not fired: " + (after - before));
        log("L3 particles: " + (after - before) + " effect bursts sent, the client drew them without errors");

        log("L3 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave L3 failed: " + FAILS);
    }
}
