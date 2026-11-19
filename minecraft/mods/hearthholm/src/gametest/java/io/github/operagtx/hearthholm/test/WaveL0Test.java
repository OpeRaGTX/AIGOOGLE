package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave L0: levels 1.5x slower and the config migration, and the lumberjack that no longer stares at a tree he cannot get to:
 * a tree on a pillar (out of reach, nearer than a normal tree: the old code kept trying it forever) and a tree walled in by leaves
 * (felled from a little farther away). Run alone with HH_ONLY_L0=1.
 */
final class WaveL0Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos M;
    private static UUID JACK;

    private WaveL0Test() {}

    private static void fail(String m) { FAILS.add(m); log("L0 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static boolean logAt(MinecraftServer s, BlockPos p) { return s.overworld().getBlockState(p).is(BlockTags.LOGS); }

    /** Polls the server every second until {@code cond} holds (the time of day is held in the workday); returns the ticks waited, or -1 on timeout. */
    private static int await(ClientGameTestContext ctx, TestSingleplayerContext sp, int maxTicks, Predicate<MinecraftServer> cond) {
        for (int t = 0; t <= maxTicks; t += 20) {
            sp.getServer().runCommand("time set 5000");
            AtomicBoolean ok = new AtomicBoolean();
            final int at = t;
            sp.getServer().runOnServer(s -> {
                ok.set(cond.test(s));
                if (at % 200 == 0) {
                    SettlerEntity j = (SettlerEntity) s.overworld().getEntity(JACK);
                    log("L0 poll +" + at + ": lumberjack at " + j.blockPosition().toShortString() + " status='" + j.status().getString() + "' activity=" + j.brain().currentActivity()
                            + " | logs standing: pillar=" + logAt(s, M.offset(-3, 6, 3)) + " plain=" + logAt(s, M.offset(9, 0, 0)) + " walled=" + logAt(s, M.offset(-9, 0, -4)));
                }
            });
            if (ok.get()) return t;
            ctx.waitTicks(20);
        }
        return -1;
    }

    private static void tree(ServerLevel l, BlockPos base) {
        l.setBlock(base.below(), Blocks.DIRT.defaultBlockState(), 3);   // like worldgen: dirt under the trunk (grass is not in the dirt tag the lumberjack looks for)
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 5; dy++)
                    if (Math.abs(dx) + Math.abs(dz) < 4) l.setBlock(base.offset(dx, dy, dz), leaf(), 3);
        for (int dy = 0; dy < 5; dy++) l.setBlock(base.above(dy), Blocks.OAK_LOG.defaultBlockState(), 3);
    }

    /** Natural (not persistent) leaves: the lumberjack only fells trees, and a log with fewer than 3 natural leaves next to it is a building. */
    private static BlockState leaf() { return Blocks.OAK_LEAVES.defaultBlockState(); }

    private static void config() {
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        java.nio.file.Path file = dir.resolve("hearthholm.json"), bak = dir.resolve("hearthholm.json.v1.bak");
        int[] levelsBefore = Balance.LEVEL_XP.clone();
        int delayBefore = Balance.BUILD_DELAY_TICKS;
        try {
            String original = java.nio.file.Files.exists(file) ? java.nio.file.Files.readString(file) : null;
            java.nio.file.Files.deleteIfExists(bak);
            // an old file (no CONFIG_VERSION) pins the old level curve: it must be set aside and the new defaults used
            java.nio.file.Files.writeString(file, "{\"LEVEL_XP\":[0,100,300,600,950],\"BUILD_DELAY_TICKS\":26}");
            Balance.load();
            check(Arrays.equals(Balance.LEVEL_XP, new int[]{0, 150, 450, 900, 1425}), "old config pinned the old level curve: " + Arrays.toString(Balance.LEVEL_XP));
            check(java.nio.file.Files.exists(bak) && java.nio.file.Files.readString(bak).contains("[0,100,300,600,950]"), "the old config was not kept as a .bak file");
            check(java.nio.file.Files.readString(file).contains("\"CONFIG_VERSION\": 2"), "the new config has no version");
            // a current file is applied as before
            java.nio.file.Files.writeString(file, "{\"CONFIG_VERSION\":2,\"BUILD_DELAY_TICKS\":31}");
            Balance.load();
            check(Balance.BUILD_DELAY_TICKS == 31, "a current config is not applied: " + Balance.BUILD_DELAY_TICKS);
            log("L0 config: old file set aside (.bak), levels " + Arrays.toString(Balance.LEVEL_XP) + ", a current file applied (delay 31)");
            if (original != null) java.nio.file.Files.writeString(file, original); else java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(bak);
        } catch (java.io.IOException e) {
            fail("config test: " + e);
        } finally {
            System.arraycopy(levelsBefore, 0, Balance.LEVEL_XP, 0, levelsBefore.length);
            Balance.BUILD_DELAY_TICKS = delayBefore;
        }
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");

        // ---- 1. levels 1.5x slower, and an old config does not hide the new curve
        check(Arrays.equals(Balance.LEVEL_XP, new int[]{0, 150, 450, 900, 1425}), "level curve is not 1.5x slower: " + Arrays.toString(Balance.LEVEL_XP));
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = settlers(s).get(0);
            e.setXp(Professions.MINER.id(), 149);
            int l1 = e.level(Professions.MINER.id());
            e.setXp(Professions.MINER.id(), 150);
            int l2 = e.level(Professions.MINER.id());
            e.setXp(Professions.MINER.id(), 1425);
            int l5 = e.level(Professions.MINER.id());
            e.setXp(Professions.MINER.id(), 0);
            log("L0 levels: xp 149 -> " + l1 + ", 150 -> " + l2 + ", 1425 -> " + l5);
            check(l1 == 1 && l2 == 2 && l5 == 5, "levels do not follow the new curve");
        });
        config();

        // ---- 2. a grass platform in the sky with its own forest marker and one lumberjack
        M = O.offset(30, 0, -30).atY(O.getY() + 70);
        BlockPos pillarTree = M.offset(-3, 6, 3), plainTree = M.offset(9, 0, 0), walledTree = M.offset(-9, 0, -4);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, M.getX() + 0.5, M.getY() + 8, M.getZ() - 10.5, Set.of(), 0f, 25f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(80);   // the chunks around the platform load before anybody is moved there
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            l.removeBlock(FOREST, false);   // the base test's forest marker: this wave owns the only one
            List<SettlerEntity> all = settlers(s);
            SettlerEntity j = all.get(0);
            for (SettlerEntity e : all) if (e != j) e.setProfession(Professions.UNEMPLOYED);
            JACK = j.getUUID();
            for (int dx = -16; dx <= 16; dx++) for (int dz = -16; dz <= 16; dz++) {
                l.setBlock(M.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
                l.setBlock(M.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= 14; dy++) l.setBlock(M.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            l.setBlock(M, ModBlocks.FOREST_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, M, p);
            l.setBlock(M.east(), Blocks.CHEST.defaultBlockState(), 3);
            // a tree on a 1x1 pillar, 6 blocks above the platform: nearer than the plain tree, impossible to reach
            for (int dy = 0; dy <= 4; dy++) l.setBlock(M.offset(-3, dy, 3), Blocks.STONE.defaultBlockState(), 3);
            l.setBlock(M.offset(-3, 5, 3), Blocks.DIRT.defaultBlockState(), 3);
            tree(l, pillarTree);
            tree(l, plainTree);
            // a tree walled in by two rings of leaves (3 high): nobody can stand closer than 3 blocks
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy <= 2; dy++) l.setBlock(walledTree.offset(dx, dy, dz), leaf(), 3);
            }
            l.setBlock(walledTree.below(), Blocks.DIRT.defaultBlockState(), 3);
            for (int dy = 0; dy < 5; dy++) l.setBlock(walledTree.above(dy), Blocks.OAK_LOG.defaultBlockState(), 3);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.setBlock(walledTree.offset(dx, 3, dz), leaf(), 3);
            j.setProfession(Professions.UNEMPLOYED);   // the same-profession call below is a no-op: this drops a stale job
            j.setProfession(Professions.LUMBERJACK);
            j.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            j.inventory().clearContent();
            j.markArrived();
            j.teleportTo(M.getX() + 0.5, M.getY(), M.getZ() + 2.5);
            log("L0 set-up: lumberjack " + j.getName().getString() + " at " + j.blockPosition().toShortString() + " marker " + M.toShortString()
                    + " pillar tree " + pillarTree.toShortString() + " plain tree " + plainTree.toShortString() + " walled tree " + walledTree.toShortString());
            check(logAt(s, pillarTree) && logAt(s, plainTree) && logAt(s, walledTree), "the trees were not built");
        });

        // ---- 3. the unreachable tree is tried first, given up, and the plain tree beyond it is felled (the old code never got there)
        int tPlain = await(ctx, sp, 3000, s -> !logAt(s, plainTree));
        sp.getServer().runOnServer(s -> {
            SettlerEntity j = (SettlerEntity) s.overworld().getEntity(JACK);
            log("L0 plain tree felled after " + tPlain + " ticks; pillar tree standing=" + logAt(s, pillarTree) + " lumberjack at " + j.blockPosition().toShortString()
                    + " status='" + j.status().getString() + "' activity=" + j.brain().currentActivity());
        });
        check(tPlain >= 0, "the lumberjack kept staring at the tree he cannot reach (the plain tree was never felled)");
        sp.getServer().runOnServer(s -> check(logAt(s, pillarTree), "the pillar tree was felled although it is out of reach"));

        // ---- 4. the tree walled in by leaves is felled from a little farther away
        int tWalled = await(ctx, sp, 3000, s -> !logAt(s, walledTree));
        sp.getServer().runOnServer(s -> {
            SettlerEntity j = (SettlerEntity) s.overworld().getEntity(JACK);
            log("L0 walled tree felled after " + tWalled + " ticks; lumberjack at " + j.blockPosition().toShortString() + " status='" + j.status().getString() + "'");
        });
        check(tWalled >= 0, "the tree walled in by leaves was not felled");

        log("L0 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave L0 failed: " + FAILS);
    }
}
