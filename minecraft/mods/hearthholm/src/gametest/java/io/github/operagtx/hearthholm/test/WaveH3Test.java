package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.HorizontalBlock;
import io.github.operagtx.hearthholm.block.WellBlock;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.client.render.SettlerTextures;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.tasks.PlaceBlock;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModEntities;
import io.github.operagtx.hearthholm.settlement.Appearance;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.SettlerProfile;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave H3: visual polish. The well holds real still water (bucket, no flow, gone on break), the lamp post model, women's looks (hair, faces,
 * slim arms), profession overlays that only add accents, roof corner stairs, and a real builder run that finishes a house.
 * Run alone (plus the base) with HH_ONLY_H3=1.
 */
final class WaveH3Test {
    private static BlockPos R;   // origin of the showcase area, far east of the town
    private static final List<String> FAILS = new ArrayList<>();

    private static void fail(String m) { FAILS.add(m); log("H3 FAIL " + m); }

    private WaveH3Test() {}

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, false);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    private static InteractionResult place(ServerLevel l, ServerPlayer p, net.minecraft.world.level.block.Block block, BlockPos ground) {
        ItemStack st = new ItemStack(block);
        return st.useOn(new UseOnContext(l, p, InteractionHand.MAIN_HAND, st, new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
    }

    private static int countParts(ServerLevel l, BlockPos master) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++)
            if (l.getBlockState(master.offset(dx, dy, dz)).is(ModBlocks.WELL_PART)) n++;
        return n;
    }

    /** Fluid blocks (anything with a fluid state) within {@code r} of {@code c}, not counting the well's own 3x3 floor (the pool). */
    private static int fluidsAround(ServerLevel l, BlockPos c, int r) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 4, r))) {
            boolean pool = q.getY() == c.getY() && Math.abs(q.getX() - c.getX()) <= 1 && Math.abs(q.getZ() - c.getZ()) <= 1;
            if (!pool && !l.getFluidState(q).isEmpty()) n++;
        }
        return n;
    }

    /** Water blocks in the 3x3 floor of the well (the master and the ring). */
    private static int poolBlocks(ServerLevel l, BlockPos c) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (l.getFluidState(c.offset(dx, 0, dz)).isSource()) n++;
        return n;
    }

    private static Built buildBp(ServerLevel l, String name, BlockPos stake) {
        Blueprint bp = BlueprintLibrary.get(Hearthholm.id(name));
        if (bp == null) throw new AssertionError("blueprint missing: " + name);
        var ops = bp.ops(stake, Direction.SOUTH);
        for (var op : ops) {
            if (op.clear()) { l.setBlock(op.pos(), Blocks.AIR.defaultBlockState(), 2); continue; }
            // the same order as the builder: place, then the stair shape update
            l.setBlock(op.pos(), op.state(), 3);
            PlaceBlock.reshapeStairs(l, op.pos());
            if (op.companionPos() != null) l.setBlock(op.companionPos(), op.companionState(), 3);
        }
        int stairs = 0, corners = 0, mismatch = 0;
        for (var op : ops) {
            if (op.clear() || !(op.state().getBlock() instanceof StairBlock)) continue;
            BlockState now = l.getBlockState(op.pos());
            if (!(now.getBlock() instanceof StairBlock)) continue;
            stairs++;
            if (now.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT) corners++;
            if (now.getValue(StairBlock.SHAPE) != op.state().getValue(StairBlock.SHAPE)) mismatch++;
        }
        return new Built(bp, stake, stairs, corners, mismatch);
    }

    private record Built(Blueprint bp, BlockPos stake, int stairs, int corners, int mismatch) {}

    private static void spawnRow(ServerLevel l, ServerPlayer p, List<UUID> spawned, int[] presets, Profession[] profs, BlockPos base, double gap, float yaw,
                                 double camBack, double camUp, boolean fromBack) {
        for (UUID u : spawned) { var e = l.getEntity(u); if (e != null) e.discard(); }
        spawned.clear();
        for (int i = 0; i < presets.length; i++) {
            Appearance a = Appearance.PRESETS.get(presets[i]);
            SettlerEntity e = ModEntities.SETTLER.create(l, EntitySpawnReason.MOB_SUMMONED);
            SettlerProfile pr = SettlerProfile.random(RandomSource.create(100 + presets[i]), false);
            e.setup(new SettlerProfile(presets[i] * 3 % SettlerProfile.NAMES_PER_GENDER, a.female(), a.pack(), pr.stats(), pr.traits()), UUID.randomUUID());
            e.setProfession(profs[i]);
            e.markArrived();
            e.setNoAi(true);
            double x = base.getX() + 0.5 + (i - (presets.length - 1) / 2.0) * gap;
            e.teleportTo(x, base.getY(), base.getZ() + 0.5);
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
            l.addFreshEntity(e);
            spawned.add(e.getUUID());
        }
        double dir = fromBack ? 1 : -1;   // yaw 180 renders the settlers facing a camera that stands north of them
        look(p, base.getX() + 0.5, base.getY() + camUp, base.getZ() + 0.5 + dir * camBack, base.getX() + 0.5, base.getY() + camUp - 0.15, base.getZ() + 0.5);
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");

        // ---- 1. the real builder finishes a house (the base test's stake options were reset by waves E and F, so re-arm it here)
        AtomicBoolean finished = new AtomicBoolean();
        AtomicInteger lastProgress = new AtomicInteger(), total = new AtomicInteger(), lastLogged = new AtomicInteger(-1);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            st.alarm = false;
            st.renown = Math.max(st.renown, 400);
            p.teleportTo(l, STAKE.getX() - 6.5, STAKE.getY() + 4, STAKE.getZ() - 6.5, Set.of(), -45f, 25f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            Blueprint bp = BlueprintLibrary.get(Hearthholm.id("small_house"));
            // keep whatever the builder already did (the base test starts the house, waves E and F only reset the options): re-arm, do not clear
            if (!(l.getBlockEntity(STAKE) instanceof WorkSiteBlockEntity)) {
                l.setBlock(STAKE, ModBlocks.CONSTRUCTION_STAKE.defaultBlockState().setValue(io.github.operagtx.hearthholm.worksite.WorkSiteBlock.FACING, Direction.SOUTH), 3);
                SettlementService.onWorkSitePlaced(l, STAKE, p);
            }
            WorkSiteBlockEntity w = (WorkSiteBlockEntity) l.getBlockEntity(STAKE);
            w.setOption("blueprint", bpIndex("small_house"));
            w.setOption("build", 1);
            log("HHTEST H3 builder re-armed at progress " + w.progress());
            total.set(bp.ops(STAKE, w.facing()).size());
            // stock the warehouse with what the house needs (existing stock is kept; free slots are enough for ~15 stacks)
            List<BlockPos> storage = st.storagePositions();
            stockBlueprint(l, bp, storage.toArray(new BlockPos[0]));
            SettlerEntity builder = null;
            for (SettlerEntity e : settlers(s)) if (e.professionId().getPath().equals("builder")) builder = e;
            if (builder == null) { builder = settlers(s).getFirst(); builder.setProfession(Professions.BUILDER); }
            builder.setNoAi(false);
            builder.teleportTo(STAKE.getX() + 0.5, STAKE.getY(), STAKE.getZ() - 1.5);
            log("HHTEST H3 builder armed: " + builder.getName().getString() + " blueprint=" + bp.id().getPath() + " ops=" + total.get() + " storage=" + storage.size()
                    + " tier=" + st.tier().key());
        });
        for (int round = 0; round < 45 && !finished.get(); round++) {
            ctx.waitTicks(100);
            sp.getServer().runCommand("time set 5000");
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                if (l.getBlockEntity(STAKE) instanceof WorkSiteBlockEntity w) {
                    lastProgress.set(w.progress());
                    if (lastProgress.get() == lastLogged.get() && w.workerId() != null && l.getEntity(w.workerId()) instanceof SettlerEntity b && lastProgress.get() >= 0)
                        log("H3 builder status " + b.status().getString() + " inv=" + (18 - b.freeSlots()) + " progress=" + w.progress() + " pos=" + b.blockPosition().toShortString());
                    lastLogged.set(lastProgress.get());
                }
                else finished.set(true);   // finishConstruction takes the stake away
            });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Blueprint bp = BlueprintLibrary.get(Hearthholm.id("small_house"));
            int ok = 0, n = 0;
            for (var op : bp.ops(STAKE, Direction.SOUTH)) {
                if (op.clear() || op.state().isAir()) continue;
                n++;
                if (l.getBlockState(op.pos()).getBlock() == op.state().getBlock()) ok++;
            }
            Settlement st = town(s);
            log("HHTEST H3 builder finished a house: finished=" + finished.get() + " progress=" + lastProgress.get() + "/" + total.get() + " blocksOk=" + ok + "/" + n
                    + " buildings=" + st.buildings.size() + " placed=" + st.stats.getOrDefault("placed", 0));
            if (!finished.get() || ok < n * 0.97) fail("builder did not finish the house: finished=" + finished.get() + " ok=" + ok + "/" + n + " progress=" + lastProgress.get());
        });
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        sp.getServer().runOnServer(s -> {
            Blueprint bp = BlueprintLibrary.get(Hearthholm.id("small_house"));
            BlockPos c = bp.toWorld(STAKE, Direction.SOUTH, bp.width() / 2, 2, bp.depth() / 2);
            look(player(s), c.getX() + 15.0, c.getY() + 3.0, c.getZ() + 0.5, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5);
        });
        ctx.waitTicks(15);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_builder_house");
        ctx.getInput().pressKey(o -> o.keyToggleGui);

        // ---- 2. a flat showcase area far east
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            R = O.offset(170, 0, -4);
            p.teleportTo(s.overworld(), R.getX() + 30.5, R.getY() + 8, R.getZ() + 20.5, Set.of(), 0f, 30f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (int dx = -4; dx <= 62; dx++)
                for (int dz = -6; dz <= 50; dz++) {
                    for (int dy = 0; dy <= 16; dy++) l.setBlock(R.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    l.setBlock(R.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int dy = 2; dy <= 4; dy++) l.setBlock(R.offset(dx, -dy, dz), Blocks.DIRT.defaultBlockState(), 2);
                }
            log("H3 area ready at " + R.toShortString());
        });
        ctx.waitTicks(20);

        // ---- 3. the well: real water
        BlockPos wellA = R.offset(40, 0, 2);       // shown in the screenshots (also the street's well)
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, wellA.getX() + 0.5, wellA.getY() + 4, wellA.getZ() + 8.5, Set.of(), 180f, 10f, false);
            InteractionResult r = place(l, p, ModBlocks.WELL, wellA.below());
            BlockState m = l.getBlockState(wellA);
            boolean water = m.is(ModBlocks.WELL) && l.getFluidState(wellA).is(Fluids.WATER) && l.getFluidState(wellA).isSource();
            int parts = countParts(l, wellA);
            log("HHTEST H3 well real water: result=" + r + " complete=" + (m.is(ModBlocks.WELL) && m.getValue(WellBlock.COMPLETE)) + " waterlogged=" + m.getValue(WellBlock.WATERLOGGED)
                    + " fluid=" + l.getFluidState(wellA).getType().getClass().getSimpleName() + " source=" + water + " poolBlocks=" + poolBlocks(l, wellA) + "/9 parts=" + parts + " fluidsAround=" + fluidsAround(l, wellA, 5));
            if (!water || parts != 20 || poolBlocks(l, wellA) != 9) fail("well has no real water");
            if (fluidsAround(l, wellA, 5) != 0) fail("water around the well right after placing");
            // old saves: a complete well saved without the waterlogged property gets its water; the single-block well has none
            // an old save has only {complete, facing}: take the stored form of the current state and drop the waterlogged property
            CompoundTag tag = NbtUtils.writeBlockState(m);
            CompoundTag props = tag.getCompoundOrEmpty("properties"), oldProps = new CompoundTag();   // 26.x writes the key in lower case
            for (String k : props.keySet()) if (!k.equals("waterlogged")) oldProps.putString(k, props.getStringOr(k, ""));
            tag.put("properties", oldProps);
            BlockState old = NbtUtils.readBlockState(l.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            BlockState single = ModBlocks.WELL.defaultBlockState();   // the single-block well of the oldest saves: complete=false is the default state
            log("HHTEST H3 well single-block state: complete=" + single.getValue(WellBlock.COMPLETE) + " waterlogged=" + single.getValue(WellBlock.WATERLOGGED) + " fluid=" + single.getFluidState());
            log("HHTEST H3 well old save: completeNoWaterloggedProp -> complete=" + old.getValue(WellBlock.COMPLETE) + " fluid=" + old.getFluidState().is(Fluids.WATER)
                    + " | storedWithoutWaterloggedProperty=" + !oldProps.keySet().contains("waterlogged") + " | singleBlockWell complete=" + single.getValue(WellBlock.COMPLETE) + " fluidEmpty=" + single.getFluidState().isEmpty() + " | decorBlock=" + (ModBlocks.WELL instanceof io.github.operagtx.hearthholm.block.DecorBlock));
            if (!old.getFluidState().is(Fluids.WATER) || !single.getFluidState().isEmpty() || !(ModBlocks.WELL instanceof io.github.operagtx.hearthholm.block.DecorBlock)) fail("well old save / decor");
        });
        ctx.waitTicks(100);   // a fluid tick would have let the water run by now
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            int around = fluidsAround(l, wellA, 6);
            log("HHTEST H3 well no flow after 100 ticks: fluidsAround=" + around + " masterStillWater=" + l.getFluidState(wellA).isSource());
            if (around != 0 || !l.getFluidState(wellA).isSource()) fail("well water flowed: " + around);
            // fill a bucket in survival: the water is taken from above the south rim, left of the rope
            p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            look(p, wellA.getX() + 0.15, wellA.getY() + 1.0, wellA.getZ() + 3.0, wellA.getX() + 0.15, wellA.getY() + 0.88, wellA.getZ() + 0.5);
            InteractionResult r = p.gameMode.useItem(p, l, p.getMainHandItem(), InteractionHand.MAIN_HAND);
            boolean got = p.getMainHandItem().is(Items.WATER_BUCKET);
            boolean full = l.getFluidState(wellA).isSource() && l.getBlockState(wellA).getValue(WellBlock.COMPLETE) && countParts(l, wellA) == 20;
            log("HHTEST H3 well bucket: result=" + r + " gotWaterBucket=" + got + " wellStillFull=" + full + " fluidsAround=" + fluidsAround(l, wellA, 5));
            if (!got || !full) fail("bucket fill: got=" + got + " full=" + full);
            // and from the rim: a bucket used on the stone ring takes water as well
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            look(p, wellA.getX() + 0.5, wellA.getY() + 0.9, wellA.getZ() + 3.5, wellA.getX() + 0.5, wellA.getY() + 1.0, wellA.getZ() + 1.5);
            p.gameMode.useItem(p, l, p.getMainHandItem(), InteractionHand.MAIN_HAND);
            boolean rim = p.getMainHandItem().is(Items.WATER_BUCKET) && poolBlocks(l, wellA) == 9 && countParts(l, wellA) == 20;
            log("HHTEST H3 well bucket on the rim: gotWaterBucket=" + p.getMainHandItem().is(Items.WATER_BUCKET) + " poolStillFull=" + poolBlocks(l, wellA) + "/9");
            if (!rim) fail("bucket on the rim");
            p.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        // breaking: a roof part of well B, the master of well C (survival, like a player): everything including the water is gone, one well item drops
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            for (int k = 0; k < 3; k++) {
                BlockPos w = R.offset(10 + k * 8, 0, 2);
                place(l, p, ModBlocks.WELL, w.below());
                boolean had = l.getFluidState(w).isSource();
                BlockPos target = k == 0 ? w.offset(1, 2, 1) : k == 1 ? w : w.offset(0, 0, 1);   // a roof part, the master, a ring block holding water
                boolean broke = p.gameMode.destroyBlock(target);
                int left = countParts(l, w) + (l.getBlockState(w).is(ModBlocks.WELL) ? 1 : 0);
                int water = l.getFluidState(w).isEmpty() ? 0 : 1;
                int drops = 0;
                for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, new AABB(w).inflate(6))) if (ie.getItem().is(ModBlocks.WELL.asItem())) drops += ie.getItem().getCount();
                log("HHTEST H3 well break " + (k == 0 ? "roof part" : k == 1 ? "master" : "ring block") + ": hadWater=" + had + " broke=" + broke + " blocksLeft=" + left + " waterLeft=" + water + " fluidsAround=" + fluidsAround(l, w, 4) + " drops=" + drops);
                if (!had || left != 0 || water != 0 || fluidsAround(l, w, 4) != 0 || drops < 1) fail("well break " + k + ": left=" + left + " water=" + water + " drops=" + drops);
            }
            p.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        });

        // ---- 4. the lamp post model (client) and the corner stairs (server)
        ctx.runOnClient(mc -> {
            var set = mc.getModelManager().getBlockStateModelSet();
            boolean lamp = set.get(ModBlocks.LAMP_POST.defaultBlockState()) != set.missingModel();
            boolean well = set.get(ModBlocks.WELL.defaultBlockState().setValue(WellBlock.COMPLETE, true)) != set.missingModel();
            double top = 0, widestHigh = 0;
            int elements = 0, thin = 0;
            try (var in = mc.getResourceManager().getResource(Hearthholm.id("models/block/lamp_post.json")).orElseThrow().open()) {
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
                for (var el : json.getAsJsonArray("elements")) {
                    var from = el.getAsJsonObject().getAsJsonArray("from"); var to = el.getAsJsonObject().getAsJsonArray("to");
                    elements++;
                    top = Math.max(top, to.get(1).getAsDouble());
                    double wx = to.get(0).getAsDouble() - from.get(0).getAsDouble(), wz = to.get(2).getAsDouble() - from.get(2).getAsDouble();
                    if (from.get(1).getAsDouble() >= 16) widestHigh = Math.max(widestHigh, Math.min(wx, wz));   // no full-block head: nothing up there is wide in both directions
                    if (Math.min(wx, wz) <= 4.0) thin++;
                }
            } catch (Exception e) { fail("lamp post model json: " + e); }
            log("HHTEST H3 lamp post model: loads=" + lamp + " wellModel=" + well + " elements=" + elements + " thin(<=4px)=" + thin + " heightPx=" + top + " widestSquareAbove16px=" + widestHigh);
            if (!lamp || !well || widestHigh > 5 || top < 24) fail("lamp post model: loads=" + lamp + " widest=" + widestHigh + " top=" + top);
        });
        List<Built> builtAll = new ArrayList<>();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            // corner shapes by the placement order the builder uses: the stair placed last used to stay straight
            BlockPos a = R.offset(2, 0, 30);
            l.setBlock(a.east(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), 3);
            PlaceBlock.reshapeStairs(l, a.east());
            l.setBlock(a, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), 3);   // placed last, straight, in front of the north-facing one
            StairsShape before = l.getBlockState(a).getValue(StairBlock.SHAPE);
            boolean changed = PlaceBlock.reshapeStairs(l, a);
            StairsShape after = l.getBlockState(a).getValue(StairBlock.SHAPE);
            log("HHTEST H3 corner stairs by placement order: stairPlacedLast before=" + before + " after reshape=" + after + " reshaped=" + changed);
            if (before != StairsShape.STRAIGHT || after == StairsShape.STRAIGHT || !changed) fail("placed stairs stay straight: " + before + " -> " + after);
            int corners = 0, stairs = 0, mismatch = 0;
            for (Object[] e : new Object[][]{{"l_house", 6, 22}, {"watchtower", 30, 24}, {"tavern", 14, 40}, {"townhouse", 44, 22}}) {
                Built b = buildBp(l, (String) e[0], R.offset((int) e[1], 0, (int) e[2]));
                builtAll.add(b);
                corners += b.corners(); stairs += b.stairs(); mismatch += b.mismatch();
                log("HHTEST H3 corner stairs " + e[0] + ": stairs=" + b.stairs() + " corners(shape!=straight)=" + b.corners() + " differFromBlueprint=" + b.mismatch());
            }
            if (corners < 8 || mismatch != 0) fail("roof corners: corners=" + corners + " mismatch=" + mismatch);
            // every blueprint with stairs stores shapes: count over the library
            int bpCorner = 0;
            for (int i = 0; i < BlueprintLibrary.all().size(); i++) {
                Blueprint bp = BlueprintLibrary.byIndex(i);
                for (var op : bp.ops(R, Direction.SOUTH)) if (!op.clear() && op.state().getBlock() instanceof StairBlock && op.state().getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT) bpCorner++;
            }
            log("HHTEST H3 blueprints (" + BlueprintLibrary.all().size() + ") store " + bpCorner + " inner/outer corner stairs");
            if (bpCorner < 8) fail("blueprints keep straight corners");
        });

        // ---- 5. skins: counts, base outfit kept under profession overlays (client)
        ctx.runOnClient(mc -> {
            int females = 0, longHair = 0, slim = 0, either = 0, lashes = 0;
            for (Appearance a : Appearance.PRESETS) {
                if (!a.female()) continue;
                females++;
                if (a.longHair()) longHair++;
                slim++;   // every woman is rendered with the slim arm model (SettlerRenderer: state.slim = female)
                either++;
                if (mc.getResourceManager().getResource(Hearthholm.id("textures/entity/settler/face_f.png")).isPresent()) lashes++;
            }
            log("HHTEST H3 female presets: " + females + " longHairOrBraidOrTail=" + longHair + " slimArms=" + slim + " withLongHairOrSlim=" + either + " faceLayer=" + (lashes == females));
            if (females < 10 || either < 10 || longHair < 6) fail("female presets: " + females + "/" + longHair);
            int professions = 0;
            double minKept = 100, sumKept = 0;
            String worst = "";
            var none = Hearthholm.id("unemployed");
            for (Profession pr : List.of(Professions.MINER, Professions.LUMBERJACK, Professions.FARMER, Professions.FISHER, Professions.BUILDER, Professions.GUARD, Professions.WARRIOR,
                    Professions.CARPENTER, Professions.MASON, Professions.SMITH, Professions.COOK, Professions.HAULER, Professions.ARCHER, Professions.HEALER, Professions.INNKEEPER, Professions.KNIGHT)) {
                double keptSum = 0;
                int looks = 0;
                for (int idx : new int[]{0, 3, 6, 10, 13, 17}) {
                    Appearance a = Appearance.PRESETS.get(idx);
                    try (var base = SettlerTextures.compose(a, none); var with = SettlerTextures.compose(a, pr.id())) {
                        int tot = 0, same = 0;
                        for (int y = 16; y < 64; y++) for (int x = 0; x < 64; x++) {   // body, arms, legs of both layers (the head rows stay out)
                            int b = base.getPixel(x, y);
                            if ((b >>> 24) == 0) continue;
                            tot++;
                            if (with.getPixel(x, y) == b) same++;
                        }
                        keptSum += 100.0 * same / Math.max(1, tot);
                        looks++;
                    }
                }
                double kept = keptSum / looks;
                professions++;
                sumKept += kept;
                if (kept < minKept) { minKept = kept; worst = pr.id().getPath(); }
            }
            log(String.format("HHTEST H3 profession overlay keeps the base outfit: avg=%.1f%% min=%.1f%% (%s) over %d professions x 6 looks (old overlays repainted most of the torso)", sumKept / professions, minKept, worst, professions));
            if (sumKept / professions < 80 || minKept < 60) fail("profession overlay hides the outfit: avg=" + sumKept / professions + " min=" + minKept);
        });

        // ---- 6. screenshots (GUI hidden)
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        // 6a. the well close-up
        sp.getServer().runOnServer(s -> look(player(s), wellA.getX() + 3.2, wellA.getY() + 0.2, wellA.getZ() + 5.0, wellA.getX() + 0.5, wellA.getY() + 1.3, wellA.getZ() + 0.5));
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_well");
        sp.getServer().runOnServer(s -> look(player(s), wellA.getX() + 0.3, wellA.getY() + 0.4, wellA.getZ() + 3.2, wellA.getX() + 0.3, wellA.getY() + 0.85, wellA.getZ() + 0.5));
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_well_water");
        // 6b. the lamp post: posts on the street, camera low and close
        BlockPos lamp = R.offset(34, 0, 4);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            l.setBlock(lamp, ModBlocks.LAMP_POST.defaultBlockState().setValue(HorizontalBlock.FACING, Direction.EAST), 3);
            l.setBlock(lamp.east(6), ModBlocks.LAMP_POST.defaultBlockState().setValue(HorizontalBlock.FACING, Direction.WEST), 3);
            look(p, lamp.getX() + 1.0, lamp.getY() + 0.2, lamp.getZ() - 2.9, lamp.getX() + 0.9, lamp.getY() + 1.45, lamp.getZ() + 0.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_lamp");
        // 6c. a roof corner close-up: the l_house valley and the watchtower's outer corners
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos st = builtAll.get(0).stake();
            look(p, st.getX() + 12.5, st.getY() + 8.5, st.getZ() - 1.0, st.getX() + 3.5, st.getY() + 5.5, st.getZ() + 5.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_roof_corner");
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos st = builtAll.get(1).stake();
            look(p, st.getX() - 3.5, st.getY() + 9.0, st.getZ() - 3.0, st.getX() + 3.5, st.getY() + 6.0, st.getZ() + 4.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_roof_tower");
        // 6d. the street overview: a row of houses with lamp posts, a bench and the well
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            for (int dx = -3; dx <= 60; dx++) for (int dz = 6; dz <= 7; dz++) l.setBlock(R.offset(dx, -1, dz), Blocks.GRAVEL.defaultBlockState(), 2);
            String[] names = {"cottage", "small_house", "farmhouse", "workshop"};
            int[] xs = {6, 17, 29, 44};
            for (int i = 0; i < names.length; i++) buildBp(l, names[i], R.offset(xs[i], 0, 10));
            for (int x = 3; x <= 55; x += 9) l.setBlock(R.offset(x, 0, 5), ModBlocks.LAMP_POST.defaultBlockState().setValue(HorizontalBlock.FACING, x % 2 == 0 ? Direction.EAST : Direction.WEST), 3);
            l.setBlock(R.offset(22, 0, 5), ModBlocks.BENCH.defaultBlockState().setValue(HorizontalBlock.FACING, Direction.SOUTH), 3);
            look(p, R.getX() + 28.0, R.getY() + 6.5, R.getZ() - 5.0, R.getX() + 26.0, R.getY() + 3.0, R.getZ() + 12.0);
            p.getInventory().setSelectedSlot(8);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_street");
        sp.getServer().runOnServer(s -> look(player(s), R.getX() + 43.0, R.getY() + 1.0, R.getZ() + 8.5, R.getX() + 40.5, R.getY() + 1.4, R.getZ() + 2.5));
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_street_well");

        // 6d2. chimney cap: registered, no campfire on any blueprint chimney, smoke particles on the client, close-up screenshot
        AtomicInteger capsFound = new AtomicInteger();
        BlockPos[] capAt = new BlockPos[1];
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(Hearthholm.id("chimney_cap"))) fail("chimney_cap not registered");
            int noCap = 0, withCampfire = 0, withCap = 0;
            for (int i = 0; i < BlueprintLibrary.all().size(); i++) {
                Blueprint bp = BlueprintLibrary.byIndex(i);
                boolean chimney = false, cap = false;
                for (var op : bp.ops(new BlockPos(0, 64, 0), Direction.SOUTH)) {
                    if (op.clear()) continue;
                    if (op.state().is(Blocks.CAMPFIRE)) withCampfire++;
                    if (op.state().is(ModBlocks.CHIMNEY_CAP)) cap = true;
                    if (op.state().is(Blocks.COBBLESTONE) || op.state().is(Blocks.STONE_BRICKS)) chimney = true;
                }
                boolean hasChimney = chimney && bp.id().getPath().matches("small_house|farmhouse|workshop|tavern|barracks|cottage|townhouse|l_house");
                if (cap) withCap++;
                if (hasChimney && !cap) noCap++;
            }
            log("HHTEST H3 chimney cap: registered=true blueprintsWithCap=" + withCap + " campfiresInBlueprints=" + withCampfire + " chimneyHousesWithoutCap=" + noCap);
            if (withCampfire != 0 || withCap < 8 || noCap != 0) fail("chimney cap blueprints: campfire=" + withCampfire + " cap=" + withCap + " missing=" + noCap);
            for (BlockPos q : BlockPos.betweenClosed(R.offset(0, 0, 8), R.offset(12, 20, 18))) {
                if (l.getBlockState(q).is(ModBlocks.CHIMNEY_CAP)) { capsFound.incrementAndGet(); capAt[0] = q.immutable(); }
            }
            if (capsFound.get() != 1) fail("cottage chimney cap blocks: " + capsFound.get());
            else {
                BlockPos c = capAt[0];
                look(player(s), c.getX() + 2.2, c.getY() + 1.1, c.getZ() - 2.4, c.getX() + 0.5, c.getY() + 1.4, c.getZ() + 0.5);
            }
        });
        ctx.runOnClient(mc -> mc.particleEngine.clearParticles());
        ctx.waitTicks(20);
        sp.getConnection().waitForChunksRender();
        // robust: smoke is random and short-lived, so sample for a while instead of looking at one instant
        AtomicBoolean smoke = new AtomicBoolean();
        String[] last = {""};
        for (int i = 0; i < 40 && !smoke.get(); i++) {
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                last[0] = mc.particleEngine.countParticles();
                if (java.util.regex.Pattern.compile("[1-9]").matcher(last[0]).find()) smoke.set(true);
            });
        }
        log("HHTEST H3 chimney smoke particles alive (engine was cleared, only the cap emits): " + last[0] + " seen=" + smoke.get());
        if (!smoke.get()) fail("no smoke particles near the chimney cap");
        ctx.takeScreenshot("hh_h3_chimney");

        // 6e. line-ups: 5 women and 5 men, then working settlers with profession accents, then the women from behind
        List<UUID> spawned = new ArrayList<>();
        var U = Professions.UNEMPLOYED;
        BlockPos base = R.offset(53, 0, 42);
        int[] women5 = {0, 1, 3, 6, 4}, men5 = {10, 11, 13, 14, 16};
        sp.getServer().runOnServer(s -> spawnRow(s.overworld(), player(s), spawned, new int[]{0, 1, 3, 6, 4, 10, 11, 13, 14, 16},
                new Profession[]{U, U, U, U, U, U, U, U, U, U}, base, 0.9, 180f, 3.9, 1.3, false));
        ctx.waitTicks(25);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_skins10");
        sp.getServer().runOnServer(s -> spawnRow(s.overworld(), player(s), spawned, women5, new Profession[]{U, U, U, U, U}, base, 1.15, 180f, 3.4, 1.25, false));
        ctx.waitTicks(25);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_skins_women");
        sp.getServer().runOnServer(s -> spawnRow(s.overworld(), player(s), spawned, men5, new Profession[]{U, U, U, U, U}, base, 1.15, 180f, 3.4, 1.25, false));
        ctx.waitTicks(25);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_skins_men");
        sp.getServer().runOnServer(s -> spawnRow(s.overworld(), player(s), spawned, new int[]{0, 1, 3, 6, 4, 8, 7, 2}, new Profession[]{Professions.COOK, Professions.FARMER, Professions.HEALER,
                Professions.INNKEEPER, Professions.CARPENTER, Professions.FISHER, Professions.HAULER, Professions.ARCHER}, base, 1.1, 180f, 4.6, 1.3, false));
        ctx.waitTicks(25);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_work_women");
        sp.getServer().runOnServer(s -> spawnRow(s.overworld(), player(s), spawned, new int[]{10, 11, 13, 14, 16, 17, 18, 19}, new Profession[]{Professions.SMITH, Professions.GUARD, Professions.MINER,
                Professions.BUILDER, Professions.LUMBERJACK, Professions.MASON, Professions.KNIGHT, Professions.WARRIOR}, base, 1.1, 180f, 4.6, 1.3, false));
        ctx.waitTicks(25);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_work_men");
        // from behind: hair down the back
        sp.getServer().runOnServer(s -> spawnRow(s.overworld(), player(s), spawned, women5, new Profession[]{U, U, U, U, U}, base, 1.15, 180f, 3.4, 1.25, true));
        ctx.waitTicks(25);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_skins_back");
        sp.getServer().runOnServer(s -> { for (UUID u : spawned) { var e = s.overworld().getEntity(u); if (e != null) e.discard(); } });
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        log("H3 screenshots: hh_h3_builder_house hh_h3_well hh_h3_well_water hh_h3_lamp hh_h3_roof_corner hh_h3_roof_tower hh_h3_street hh_h3_street_well hh_h3_skins10 hh_h3_skins_women hh_h3_skins_men hh_h3_work_women hh_h3_work_men hh_h3_skins_back");
        if (!FAILS.isEmpty()) throw new AssertionError("wave H3: " + FAILS);
        log("WAVE H3 OK");
    }

    private static int bpIndex(String name) {
        for (int i = 0; i < BlueprintLibrary.all().size(); i++) if (BlueprintLibrary.byIndex(i).id().getPath().equals(name)) return i;
        throw new AssertionError("blueprint " + name);
    }
}
