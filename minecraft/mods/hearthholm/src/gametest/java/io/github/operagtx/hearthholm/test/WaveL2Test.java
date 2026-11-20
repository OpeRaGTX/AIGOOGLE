package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.api.ProfessionModule;
import io.github.operagtx.hearthholm.api.ProfessionModules;
import io.github.operagtx.hearthholm.client.screen.ZoneMapScreen;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.jobs.FisherJob;
import io.github.operagtx.hearthholm.entity.ai.jobs.WarriorJob;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.CropBlock;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave L2: map zones for the farmer (30 x 30), the fisher (10 x 10) and the warrior (50 x 50), and the new markers (fertilizer barrel, fish bucket, stone with a
 * sword). Farmer: works only inside the drawn zone. Fisher: picks the water inside the zone, not a nearer pond outside it. Warrior: patrols the zone and
 * defends only it. Map screens and the three blocks are photographed. Run alone with HH_ONLY_L2=1.
 */
final class WaveL2Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos Z, FARM, FISH, WAR;
    private static UUID FARMER, FISHER, WARRIOR;

    private WaveL2Test() {}

    private static void fail(String m) { FAILS.add(m); log("L2 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static SettlerEntity get(ServerLevel l, UUID id) { return (SettlerEntity) l.getEntity(id); }

    private static WorkSiteBlockEntity site(ServerLevel l, BlockPos p) { return (WorkSiteBlockEntity) l.getBlockEntity(p); }

    /** Marks the block rectangle [x0..x1] x [z0..z1] as zone (world coordinates). */
    private static void paint(WorkSiteBlockEntity w, int x0, int z0, int x1, int z1) {
        WorkZone z = w.zoneForEdit();
        z.setRect(WorkZone.SURFACE, z.cellX(x0), z.cellZ(z0), z.cellX(x1), z.cellZ(z1), true);
        w.zoneChanged();
        w.setChanged();
    }

    private static boolean poll(ClientGameTestContext ctx, TestSingleplayerContext sp, int rounds, int step, java.util.function.Predicate<MinecraftServer> done) {
        AtomicBoolean ok = new AtomicBoolean();
        for (int r = 0; r < rounds && !ok.get(); r++) {
            ctx.waitTicks(step);
            sp.getServer().runOnServer(s -> { if (done.test(s)) ok.set(true); });
        }
        return ok.get();
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("weather clear");
        sp.getServer().runCommand("time set 5000");
        // every marker must stand inside the settlement territory (village radius 72 around the hall), else it is not registered as a site
        Z = O.offset(-30, 0, 10).atY(O.getY() + 70);
        FARM = Z;
        FISH = Z.offset(0, 0, 28);
        WAR = Z.offset(0, 0, -34);

        // ---- 1. the grids and the modules
        sp.getServer().runOnServer(s -> {
            var farm = WorkZone.specOf(WorkSiteTypes.FARM_ID);
            var fish = WorkZone.specOf(WorkSiteTypes.FISHING_ID);
            var war = WorkZone.specOf(WorkSiteTypes.TERRITORY_ID);
            log("L2 specs: farmer " + farm + " fisher " + fish + " warrior " + war);
            check(farm != null && farm.size() == 30 && farm.cell() == 1 && !farm.layered(), "the farmer's zone must be 30 x 30 blocks");
            check(fish != null && fish.size() == 10 && fish.cell() == 1, "the fisher's zone must be 10 x 10 blocks");
            check(war != null && war.size() == 50 && war.cell() == 1, "the warrior's zone must be 50 x 50 blocks");
            for (var p : List.of(Professions.FARMER, Professions.FISHER, Professions.WARRIOR)) {
                ProfessionModule m = ProfessionModules.of(p);
                check(m.zone() != null && m.zone().kind() == ProfessionModule.ZoneData.Kind.MAP && m.screenOpener() != ProfessionModule.SiteScreenOpener.GENERIC,
                        p.id().getPath() + " has no map module");
            }
        });

        // ---- 2. set-up: a flat platform in the sky: dirt over stone, three markers 34 blocks apart, a pond inside and a nearer pond outside the fisher's zone
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, Z.getX() + 20.5, Z.getY() + 8, Z.getZ() + 0.5, Set.of(), 0f, 25f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(80);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            Settlement st = town(s);
            for (int dx = -26; dx <= 40; dx++) for (int dz = -60; dz <= 60; dz++) {
                l.setBlock(Z.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
                l.setBlock(Z.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState(), 2);
                for (int dy = 0; dy <= 6; dy++) l.setBlock(Z.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            // ponds around the fisher's marker: inside the zone (x +3..+4) and outside, nearer to the marker (x -3..-2)
            for (int dx : new int[]{3, 4, -3, -2}) for (int dz = -1; dz <= 1; dz++) l.setBlock(FISH.offset(dx, -1, dz), Blocks.WATER.defaultBlockState(), 2);
            check(st.contains(l.dimension(), FARM) && st.contains(l.dimension(), FISH) && st.contains(l.dimension(), WAR), "a marker would stand outside the settlement territory");
            for (var type : List.of(WorkSiteTypes.FARM_ID, WorkSiteTypes.FISHING_ID, WorkSiteTypes.TERRITORY_ID))
                for (Settlement.SiteRecord r : new ArrayList<>(st.sitesOf(type))) if (l.isLoaded(r.pos())) l.removeBlock(r.pos(), false);
            l.setBlock(FARM, ModBlocks.FARM_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            l.setBlock(FISH, ModBlocks.FISHING_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            l.setBlock(WAR, ModBlocks.TERRITORY_BANNER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, FARM, p);
            SettlementService.onWorkSitePlaced(l, FISH, p);
            SettlementService.onWorkSitePlaced(l, WAR, p);
            // zones: the farmer's field is the west half of the dirt (13 x 13), the fisher's zone covers the east pond, the warrior's 24 x 24
            paint(site(l, FARM), FARM.getX() - 14, FARM.getZ() - 6, FARM.getX() - 2, FARM.getZ() + 6);
            paint(site(l, FISH), FISH.getX() + 2, FISH.getZ() - 4, FISH.getX() + 5, FISH.getZ() + 4);
            paint(site(l, WAR), WAR.getX() - 12, WAR.getZ() - 12, WAR.getX() + 11, WAR.getZ() + 11);
            List<SettlerEntity> all = settlers(s);
            for (SettlerEntity e : all) e.setProfession(Professions.UNEMPLOYED);
            SettlerEntity farmer = all.get(0), fisher = all.get(1), warrior = all.get(2);
            farmer.setProfession(Professions.FARMER);
            fisher.setProfession(Professions.FISHER);
            warrior.setProfession(Professions.WARRIOR);
            for (SettlerEntity e : List.of(farmer, fisher, warrior)) { e.markArrived(); e.inventory().clearContent(); e.getNavigation().stop(); e.setFood(1000f); }   // fed: no walk to eat
            // a fully equipped warrior makes no trip to the warehouse for armour or a shield (earlier waves leave such items there)
            warrior.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            warrior.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            warrior.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            warrior.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            warrior.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            farmer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
            farmer.inventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 64));
            farmer.teleportTo(FARM.getX() + 0.5, FARM.getY(), FARM.getZ() + 2.5);
            fisher.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            fisher.teleportTo(FISH.getX() - 1.5, FISH.getY(), FISH.getZ() + 0.5);
            warrior.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            warrior.teleportTo(WAR.getX() + 0.5, WAR.getY(), WAR.getZ() + 2.5);
            FARMER = farmer.getUUID(); FISHER = fisher.getUUID(); WARRIOR = warrior.getUUID();
            log("L2 set-up: farm " + FARM.toShortString() + " zone cells " + site(l, FARM).zone().cellCount(WorkZone.SURFACE) + ", fish " + FISH.toShortString() + " zone cells "
                    + site(l, FISH).zone().cellCount(WorkZone.SURFACE) + ", war " + WAR.toShortString() + " zone cells " + site(l, WAR).zone().cellCount(WorkZone.SURFACE));
        });

        // ---- 3. the fisher takes the pond inside the zone, not the nearer one outside it
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity fisher = get(l, FISHER);
            BlockPos water = FisherJob.findZoneWater(l, FISH, site(l, FISH).zone(), fisher);
            log("L2 fisher: water chosen " + (water == null ? "none" : water.toShortString()) + " (zone pond at x+3/+4, the nearer outside pond at x-3/-2 of " + FISH.toShortString() + ")");
            check(water != null && water.getX() >= FISH.getX() + 2, "the fisher must pick the pond inside his zone, got " + water);
            site(l, FISH).zone().clearAll();   // without a zone the old behaviour: the nearest water (the outside pond)
            site(l, FISH).zoneChanged();
            check(!site(l, FISH).hasZone(), "an emptied zone must fall back to the old radius");
            paint(site(l, FISH), FISH.getX() + 2, FISH.getZ() - 4, FISH.getX() + 5, FISH.getZ() + 4);
        });

        // ---- 4. the farmer tills, sows and harvests only inside his zone
        boolean tilled = poll(ctx, sp, 60, 40, s -> {
            ServerLevel l = s.overworld();
            int in = 0;
            for (int dx = -14; dx <= -2; dx++) for (int dz = -6; dz <= 6; dz++) if (l.getBlockState(FARM.offset(dx, -1, dz)).getBlock() instanceof FarmlandBlock) in++;
            return in >= 12;
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            int in = 0, crops = 0, out = 0;
            for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) {
                boolean inside = dx >= -14 && dx <= -2 && dz >= -6 && dz <= 6;
                if (l.getBlockState(FARM.offset(dx, -1, dz)).getBlock() instanceof FarmlandBlock) { if (inside) in++; else out++; }
                if (inside && l.getBlockState(FARM.offset(dx, 0, dz)).getBlock() instanceof CropBlock) crops++;
            }
            SettlerEntity farmer = get(l, FARMER);
            log("L2 farmer: tilled enough=" + tilled + " farmland inside the zone " + in + ", sown " + crops + ", farmland OUTSIDE " + out + " status='" + farmer.status().getString() + "'");
            check(tilled && in >= 12, "the farmer did not till his zone: " + in);
            check(crops >= 4, "the farmer did not sow inside the zone: " + crops);
            check(out == 0, "the farmer worked outside his zone: " + out + " farmland blocks");
        });

        // ---- 5. the warrior patrols the zone and defends only it
        List<double[]> seen = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ctx.waitTicks(15);
            sp.getServer().runOnServer(s -> { SettlerEntity w = get(s.overworld(), WARRIOR); seen.add(new double[]{w.getX(), w.getZ()}); });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity w = get(l, WARRIOR);
            double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
            for (double[] p : seen) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
            log("L2 warrior: patrol x " + (int) minX + ".." + (int) maxX + " z " + (int) minZ + ".." + (int) maxZ + " (zone x " + (WAR.getX() - 12) + ".." + (WAR.getX() + 12) + " z " + (WAR.getZ() - 12) + ".." + (WAR.getZ() + 12) + ") status='" + w.status().getString() + "'");
            check(minX >= WAR.getX() - 15 && maxX <= WAR.getX() + 15 && minZ >= WAR.getZ() - 15 && maxZ <= WAR.getZ() + 15, "the warrior left his zone");
            check(maxX - minX >= 4 || maxZ - minZ >= 4, "the warrior did not patrol (stood still)");
            Husk inside = EntityTypes.HUSK.spawn(l, WAR.offset(2, 0, 2), EntitySpawnReason.SPAWN_ITEM_USE), outside = EntityTypes.HUSK.spawn(l, WAR.offset(30, 0, 0), EntitySpawnReason.SPAWN_ITEM_USE);
            inside.setNoAi(true); outside.setNoAi(true);
            var job = w.brain().job();
            boolean in = job instanceof WarriorJob wj && wj.defends(w, inside), out = job instanceof WarriorJob wj2 && wj2.defends(w, outside);
            log("L2 warrior: defends a husk inside the zone=" + in + " outside=" + out);
            inside.discard(); outside.discard();
            check(in, "the warrior must defend his zone");
            check(!out, "the warrior defends beyond his zone");
        });

        // ---- 6. photographs: the three markers, and the three zone maps
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            BlockPos row = Z.offset(24, 0, -4);
            l.setBlock(row, ModBlocks.FARM_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 2);
            l.setBlock(row.offset(2, 0, 0), ModBlocks.FISHING_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 2);
            l.setBlock(row.offset(4, 0, 0), ModBlocks.TERRITORY_BANNER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 2);
            p.getAbilities().flying = true;
            p.teleportTo(l, row.getX() + 2.5, row.getY() + 1.6, row.getZ() - 3.2, Set.of(), 0f, 22f, false);
        });
        sp.getConnection().waitForChunksRender();
        ctx.waitTicks(20);
        ctx.takeScreenshot("hh_l2_markers");
        sp.getServer().runOnServer(s -> {   // move the player next to the fisher's zone for the map screens (reach)
            ServerPlayer p = player(s);
            p.teleportTo(s.overworld(), FARM.getX() + 0.5, FARM.getY() + 1, FARM.getZ() + 2.5, Set.of(), 0f, 20f, false);
        });
        TestKit.setWindow(ctx, 854, 480);
        for (var entry : List.of(Map.entry("farmer", FARM), Map.entry("fisher", FISH), Map.entry("warrior", WAR))) {
            BlockPos pos = entry.getValue();
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5, Set.of(), 0f, 20f, false);
                SettlementService.openWorkSite(p, pos);
            });
            ctx.waitForScreen(ZoneMapScreen.class);
            ctx.waitTicks(20);
            ctx.takeScreenshot("hh_l2_map_" + entry.getKey());
            ctx.setScreen(() -> null);
        }
        TestKit.setLang(ctx, "ru_ru");
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), WAR));
        ctx.waitForScreen(ZoneMapScreen.class);
        ctx.waitTicks(20);
        ctx.takeScreenshot("hh_l2_map_warrior_ru");
        ctx.setScreen(() -> null);
        TestKit.setLang(ctx, "en_us");

        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (UUID id : new UUID[]{FARMER, FISHER, WARRIOR}) get(l, id).setProfession(Professions.UNEMPLOYED);
        });
        log("L2 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave L2 failed: " + FAILS);
    }
}
