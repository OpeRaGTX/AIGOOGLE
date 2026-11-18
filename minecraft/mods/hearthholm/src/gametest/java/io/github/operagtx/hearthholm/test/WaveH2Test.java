package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModEntities;
import io.github.operagtx.hearthholm.settlement.Appearance;
import io.github.operagtx.hearthholm.settlement.RoomScanner;
import io.github.operagtx.hearthholm.settlement.SettlerProfile;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave H2: pitched-roof furnished blueprints, new settler skins. Builds every new or changed blueprint the way the builder places
 * blocks (clear pass, then bottom-up with neighbour updates), checks that each block survived, that houses are detected as rooms,
 * that every material can be sourced from a workshop catalogue or raw gathering, and takes the screenshots.
 * Run alone (plus the base) with HH_ONLY_H2=1.
 */
final class WaveH2Test {
    private static BlockPos R;   // origin of the showcase area
    private static final List<String> FAILS = new ArrayList<>();   // collected so one run shows every problem and still takes the screenshots

    private static void fail(String m) { FAILS.add(m); log("H2 FAIL " + m); }

    private WaveH2Test() {}

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, false);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    /** Items a builder can always get: logs from the lumberjack, stone and gravel from the miner. */
    private static final Set<Item> RAW = Set.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.COBBLESTONE, Items.GRAVEL);

    private static Set<Item> sourced() {
        Set<Item> out = new HashSet<>(RAW);
        for (Workshop w : Workshop.values()) if (w.hasCatalog()) out.addAll(w.catalog());
        out.add(Items.TORCH);
        return out;
    }

    private record Built(Blueprint bp, BlockPos stake, int ok, int total, String firstMiss) {}

    /** Places a blueprint like the builder: clear pass, then place ops in order with neighbour updates (flag 3). */
    private static Built build(ServerLevel l, String name, BlockPos stake) {
        Blueprint bp = BlueprintLibrary.get(Hearthholm.id(name));
        if (bp == null) throw new AssertionError("blueprint missing: " + name);
        var ops = bp.ops(stake, Direction.SOUTH);
        for (var op : ops) {
            if (op.clear()) { l.setBlock(op.pos(), Blocks.AIR.defaultBlockState(), 2); continue; }
            l.setBlock(op.pos(), op.state(), 3);
            if (op.companionPos() != null) l.setBlock(op.companionPos(), op.companionState(), 3);
        }
        int ok = 0, total = 0;
        String miss = "";
        for (var op : ops) {
            if (op.clear() || op.state().isAir()) continue;
            total++;
            BlockState now = l.getBlockState(op.pos());
            if (now.getBlock() == op.state().getBlock()) ok++;
            else if (miss.isEmpty()) miss = op.pos().toShortString() + " wanted " + op.state().getBlock().getDescriptionId() + " got " + now.getBlock().getDescriptionId();
        }
        return new Built(bp, stake, ok, total, miss);
    }

    /** Scans the room around a bed head of the blueprint, or around the given author-frame cell. */
    private static RoomScanner.Room scan(ServerLevel l, Built b, int[] seed) {
        BlockPos anchor = null;
        for (var op : b.bp().ops(b.stake(), Direction.SOUTH)) {
            if (!op.clear() && op.state().getBlock() instanceof BedBlock && op.companionPos() != null && op.state().getValue(BedBlock.PART) == BedPart.FOOT) { anchor = op.companionPos(); break; }
        }
        if (anchor == null) anchor = b.bp().toWorld(b.stake(), Direction.SOUTH, seed[0], seed[1], seed[2]);
        return RoomScanner.scan(l, anchor);
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");

        // ---- 0. a flat showcase area far east of the town
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            R = O.offset(80, 0, -4);
            p.teleportTo(s.overworld(), R.getX() + 30.5, R.getY() + 8, R.getZ() + 20.5, Set.of(), 0f, 30f, false);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (int dx = -4; dx <= 72; dx++)
                for (int dz = -4; dz <= 66; dz++) {
                    for (int dy = 0; dy <= 16; dy++) l.setBlock(R.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    l.setBlock(R.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int dy = 2; dy <= 4; dy++) l.setBlock(R.offset(dx, -dy, dz), Blocks.DIRT.defaultBlockState(), 2);
                }
            log("H2 area ready at " + R.toShortString());
        });
        ctx.waitTicks(20);

        // ---- 1. the street (row A) and a second row (row B): every new or changed house
        String[][] rows = {
                {"cottage", "6", "small_house", "16", "townhouse", "25", "farmhouse", "37"},
                {"l_house", "8", "fisher_hut", "21", "workshop", "33", "barracks", "47"}};
        int[] rowZ = {10, 34};
        int[][] seeds = {{4, 1, 3}, {3, 1, 3}, {3, 1, 3}, {5, 1, 3}, {3, 1, 8}, {3, 1, 3}, {4, 1, 3}, {5, 1, 3}};
        List<Built> builtAll = new ArrayList<>();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Set<Item> ok = sourced();
            int k = 0;
            for (int r = 0; r < rows.length; r++) {
                for (int dx = -3; dx <= 70; dx++) for (int dz = rowZ[r] - 4; dz <= rowZ[r] - 3; dz++) l.setBlock(R.offset(dx, -1, dz), Blocks.GRAVEL.defaultBlockState(), 2);   // street
                for (int i = 0; i < rows[r].length; i += 2) {
                    String name = rows[r][i];
                    BlockPos stake = R.offset(Integer.parseInt(rows[r][i + 1]), 0, rowZ[r]);
                    Built b = build(l, name, stake);
                    builtAll.add(b);
                    RoomScanner.Room room = scan(l, b, seeds[k++]);
                    Set<Item> missing = new HashSet<>(b.bp().materials().keySet());
                    missing.removeAll(ok);
                    log("H2 blueprint " + name + " built=" + b.ok() + "/" + b.total() + " size=" + b.bp().width() + "x" + b.bp().height() + "x" + b.bp().depth()
                            + " detected=" + room.enclosed() + " problem='" + room.problem() + "' roomCells=" + room.interior().size() + " beds=" + room.beds().size()
                            + " doors=" + room.doors() + " comfort=" + room.comfort() + " unsourced=" + missing + (b.firstMiss().isEmpty() ? "" : " MISS " + b.firstMiss()));
                    if (b.ok() != b.total()) fail(name + " not fully built: " + b.firstMiss());
                    if (!room.enclosed()) fail(name + " not detected as a room: " + room.problem());
                    if (!missing.isEmpty()) fail(name + " needs materials nobody supplies: " + missing);
                    if (!name.equals("workshop") && !name.equals("barracks") && room.beds().isEmpty()) fail(name + " has no bed");
                }
            }
        });
        // the remaining changed blueprints: tavern, market stall, watchtower (further south)
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Set<Item> ok = sourced();
            for (Object[] e : new Object[][]{{"tavern", 16, 60}, {"market_stall", 40, 60}, {"watchtower", 58, 60}}) {
                String name = (String) e[0];
                Built b = build(l, name, R.offset((int) e[1], 0, (int) e[2]));
                builtAll.add(b);
                Set<Item> missing = new HashSet<>(b.bp().materials().keySet());
                missing.removeAll(ok);
                String room = "n/a";
                if (name.equals("tavern")) {
                    RoomScanner.Room r = scan(l, b, new int[]{6, 1, 2});
                    room = "enclosed=" + r.enclosed() + " cells=" + r.interior().size() + " doors=" + r.doors() + " problem='" + r.problem() + "'";
                    if (!r.enclosed()) fail("tavern not detected: " + r.problem());
                }
                log("H2 blueprint " + name + " built=" + b.ok() + "/" + b.total() + " size=" + b.bp().width() + "x" + b.bp().height() + "x" + b.bp().depth()
                        + " room " + room + " unsourced=" + missing + (b.firstMiss().isEmpty() ? "" : " MISS " + b.firstMiss()));
                if (b.ok() != b.total()) fail(name + " not fully built: " + b.firstMiss());
                if (!missing.isEmpty()) fail(name + " needs materials nobody supplies: " + missing);
            }
            log("H2 BLUEPRINTS " + builtAll.size() + " built and verified (house detection, block survival, material sources)");
        });

        // ---- 2. skins: counts and the female share
        sp.getServer().runOnServer(s -> {
            RandomSource r = RandomSource.create(7);
            int females = 0, fromPreset = 0, n = 400;
            Set<Integer> distinct = new HashSet<>();
            for (int i = 0; i < n; i++) {
                SettlerProfile p = SettlerProfile.random(r, false);
                if (p.female()) females++;
                Appearance a = Appearance.unpack(p.appearance());
                if (a.female() != p.female()) throw new AssertionError("look gender differs from name gender");
                if (a.outfit() > 0 || a.headwear() > 0 || a.elder()) fromPreset++;
                distinct.add(p.appearance());
            }
            log("H2 SKINS presets=" + Appearance.presetCount() + " female=" + Appearance.femalePresets() + " (" + (100 * Appearance.femalePresets() / Appearance.presetCount())
                    + "%) hairStyles m=" + Appearance.MALE_HAIR + " f=" + Appearance.FEMALE_HAIR + " outfits=" + (Appearance.OUTFITS - 1) + " headwear=" + (Appearance.HEADWEAR - 1)
                    + " | " + n + " rolled settlers: female=" + (100 * females / n) + "% withNewOutfitOrHat=" + (100 * fromPreset / n) + "% distinctLooks=" + distinct.size());
            if (Appearance.presetCount() < 16 || Appearance.femalePresets() * 2 < Appearance.presetCount() - 2) throw new AssertionError("skin presets");
            // old saves: a look without the H2 bits unpacks to the plain outfit
            int old = new Appearance(2, 1, 3, 1, 0, 4, 2, true).pack();
            Appearance back = Appearance.unpack(old);
            log("H2 SKINS old look " + Integer.toHexString(old) + " -> outfit=" + back.outfit() + " headwear=" + back.headwear() + " elder=" + back.elder() + " female=" + back.female());
            if (back.outfit() != 0 || back.headwear() != 0 || back.elder() || !back.female()) throw new AssertionError("old look changed");
            int packed = Appearance.PRESETS.get(5).pack();
            if (!Appearance.unpack(packed).equals(Appearance.PRESETS.get(5))) throw new AssertionError("pack/unpack of a preset");
        });

        // ---- 3. the builder's real run on the stake of the base test (small house): progress report
        sp.getServer().runOnServer(s -> {
            if (s.overworld().getBlockEntity(STAKE) instanceof io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity w) {
                Blueprint bp = BlueprintLibrary.byIndex(w.option("blueprint"));
                int total = bp == null ? 0 : bp.ops(STAKE, w.facing()).size();
                log("H2 real builder: " + (bp == null ? "-" : bp.id().getPath()) + " progress=" + w.progress() + "/" + total);
            }
        });

        // ---- 4. screenshots
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        // 4a. the street from the road, daytime
        shot(ctx, sp, 0, "street");
        shot(ctx, sp, 1, "street2");
        // 4b. interior of the farmhouse (row A), standing inside by the door
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos fs = R.offset(37, 0, rowZ[0]);
            Blueprint bp = BlueprintLibrary.get(Hearthholm.id("farmhouse"));
            BlockPos cam = bp.toWorld(fs, Direction.SOUTH, 3, 1, 2), tgt = bp.toWorld(fs, Direction.SOUTH, 7, 1, 5);
            look(p, cam.getX() + 0.5, cam.getY() + 1.1, cam.getZ() + 0.5, tgt.getX() + 0.5, tgt.getY() + 0.9, tgt.getZ() + 0.5);
            p.getInventory().setSelectedSlot(8);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h2_interior");
        // 4c. the tavern from outside
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos ts = R.offset(16, 0, 60);
            look(p, ts.getX() - 9.0, ts.getY() + 5.0, ts.getZ() - 8.5, ts.getX() + 0.5, ts.getY() + 3.0, ts.getZ() + 5.0);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h2_tavern_outside");
        // 4d. market stall and watchtower
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos ms = R.offset(40, 0, 60);
            look(p, ms.getX() + 9.0, ms.getY() + 4.0, ms.getZ() - 9.5, ms.getX() + 9.0, ms.getY() + 3.5, ms.getZ() + 3.0);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h2_market_tower");
        // 4e. the tavern interior from the door
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos ts = R.offset(16, 0, 60);
            Blueprint bp = BlueprintLibrary.get(Hearthholm.id("tavern"));
            BlockPos cam = bp.toWorld(ts, Direction.SOUTH, 6, 1, 2), tgt = bp.toWorld(ts, Direction.SOUTH, 6, 1, 8);
            look(p, cam.getX() + 0.5, cam.getY() + 0.9, cam.getZ() + 0.5, tgt.getX() + 0.5, tgt.getY() + 1.4, tgt.getZ() + 0.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h2_tavern_inside");

        // 4f. line-ups of settlers: women, men, then a mixed row
        List<UUID> spawned = new ArrayList<>();
        int[][] orders = {{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {10, 11, 12, 13, 14, 15, 16, 17, 18, 19}, {0, 10, 1, 11, 5, 13, 3, 15, 9, 17}};
        String[] names = {"hh_h2_skins_women", "hh_h2_skins_men", "hh_h2_skins_mixed"};
        for (int row = 0; row < 3; row++) {
            final int[] order = orders[row];
            final int rr = row;
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                ServerPlayer p = player(s);
                for (UUID u : spawned) { var e = l.getEntity(u); if (e != null) e.discard(); }
                spawned.clear();
                var U = Professions.UNEMPLOYED;
                var prof = new io.github.operagtx.hearthholm.core.Profession[]{U, Professions.FARMER, U, U, U, U, Professions.INNKEEPER, U, U, U};
                var profM = new io.github.operagtx.hearthholm.core.Profession[]{Professions.SMITH, Professions.GUARD, U, U, Professions.FISHER, U, U, U, U, U};
                BlockPos base = R.offset(30, 0, 52);
                for (int i = 0; i < order.length; i++) {
                    Appearance a = Appearance.PRESETS.get(order[i]);
                    SettlerEntity e = ModEntities.SETTLER.create(l, EntitySpawnReason.MOB_SUMMONED);
                    SettlerProfile pr = SettlerProfile.random(RandomSource.create(100 + order[i]), false);
                    e.setup(new SettlerProfile(order[i] * 3 % SettlerProfile.NAMES_PER_GENDER, a.female(), a.pack(), pr.stats(), pr.traits()), UUID.randomUUID());
                    e.setProfession(a.female() ? prof[order[i] % 10] : profM[order[i] % 10]);
                    e.markArrived();
                    e.setNoAi(true);
                    double x = base.getX() + 0.5 + (i - (order.length - 1) / 2.0) * 1.1;
                    e.teleportTo(x, base.getY(), base.getZ() + 0.5);
                    e.setYRot(180f);
                    e.setYHeadRot(180f);
                    e.setYBodyRot(180f);
                    l.addFreshEntity(e);
                    spawned.add(e.getUUID());
                }
                look(p, base.getX() + 0.5, base.getY() + 1.3, base.getZ() - 5.2, base.getX() + 0.5, base.getY() + 1.15, base.getZ() + 0.5);
            });
            ctx.waitTicks(20);
            sp.getConnection().waitForChunksRender();
            ctx.takeScreenshot(names[rr]);
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (UUID u : spawned) { var e = l.getEntity(u); if (e != null) e.discard(); }
        });
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        log("H2 screenshots: hh_h2_street hh_h2_street2 hh_h2_interior hh_h2_tavern_outside hh_h2_market_tower hh_h2_tavern_inside hh_h2_skins_women hh_h2_skins_men hh_h2_skins_mixed");
        if (!FAILS.isEmpty()) throw new AssertionError("wave H2: " + FAILS);
        log("WAVE H2 OK");
    }

    /** Overview of one row of houses, from the street side at some height. */
    private static void shot(ClientGameTestContext ctx, TestSingleplayerContext sp, int row, String name) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            int z = row == 0 ? 10 : 34;
            look(p, R.getX() + 24.0, R.getY() + 7.5, R.getZ() + z - 14.0, R.getX() + 24.0, R.getY() + 3.0, R.getZ() + z + 4.0);
            p.getInventory().setSelectedSlot(8);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h2_" + name);
    }
}
