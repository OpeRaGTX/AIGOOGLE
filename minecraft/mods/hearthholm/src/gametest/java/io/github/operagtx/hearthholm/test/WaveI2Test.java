package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.town.Loot;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave I2: guards and archers. Walking and chasing speed, damage by weapon, weapon wear and re-arming, arrows (consumed, refilled,
 * missing), loot pickup, shifts and the alarm. Run alone (plus the base) with HH_ONLY_I2=1.
 */
final class WaveI2Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static UUID GUARD, ARCHER;
    private static BlockPos POST;
    private static UUID DROP;
    private static ItemEntity DROP_E;

    private WaveI2Test() {}

    private static boolean until(String step) {
        String u = System.getenv("HH_I2_UNTIL");
        if (u != null && u.equals(step)) { log("I2 stopped after " + step + " fails=" + FAILS); return true; }
        return false;
    }

    private static void fail(String m) { FAILS.add(m); log("I2 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static SettlerEntity get(MinecraftServer s, UUID id) { return (SettlerEntity) s.overworld().getEntity(id); }

    private static boolean statusIs(SettlerEntity e, String key) {
        return e.status().getContents() instanceof TranslatableContents tc && tc.getKey().equals("status.hearthholm." + key);
    }

    private static SettlerEntity pick(MinecraftServer s, UUID not, boolean wantBed) {
        for (SettlerEntity e : settlers(s)) if (!e.getUUID().equals(not) && (!wantBed || e.bed() != null)) return e;
        for (SettlerEntity e : settlers(s)) if (!e.getUUID().equals(not)) return e;
        throw new AssertionError("no settler");
    }

    private static void stock(MinecraftServer s, ItemStack st) {
        Settlement t = town(s);
        Storage.insert(Storage.resolve(s.overworld(), t.storagePositions()), st);
    }

    private static void remove(MinecraftServer s, java.util.function.Predicate<ItemStack> f) {
        for (Container c : Storage.resolve(s.overworld(), town(s).storagePositions()))
            for (int i = 0; i < c.getContainerSize(); i++) if (f.test(c.getItem(i))) c.setItem(i, ItemStack.EMPTY);
    }

    private static net.minecraft.world.entity.Mob zombie(ServerLevel l, BlockPos at) {
        var z = EntityTypes.ZOMBIE.spawn(l, at, EntitySpawnReason.COMMAND);
        z.setNoAi(true);
        z.setPersistenceRequired();
        for (EquipmentSlot sl : EquipmentSlot.values()) z.setItemSlot(sl, ItemStack.EMPTY);
        return z;
    }

    /** Hits a fresh zombie with the guard (damage per blow as in a fight) until it dies; returns the number of blows. */
    private static int hitsToKill(SettlerEntity g, ItemStack weapon, BlockPos at) {
        ServerLevel l = (ServerLevel) g.level();
        var z = zombie(l, at);
        z.setHealth(z.getMaxHealth());
        int n = 0;
        while (z.isAlive() && n < 60) {
            z.damageCooldownTime = 0;
            g.doHurtTarget(l, z);
            n++;
        }
        z.discard();
        return n;
    }

    private static final class Walk extends Goal {
        private final SettlerEntity g;
        private final Vec3 to;
        private int t;
        Walk(SettlerEntity g, Vec3 to) { this.g = g; this.to = to; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { if (t++ % 10 == 0) g.getNavigation().moveTo(to.x, to.y, to.z, 0.6); }
    }

    private static double flatDist(Vec3 a, Vec3 b) { return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z)); }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 10000");
        sp.getServer().runCommand("weather clear");
        int platformY = O.getY() + 50;
        AtomicReference<Vec3> p0 = new AtomicReference<>();
        AtomicReference<Goal> walk = new AtomicReference<>();
        AtomicReference<UUID> foe = new AtomicReference<>();

        // ---- 0. set-up: bring the town back, a patrol post next to the hall, a stone platform in the sky for the speed measurements
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, DESK.getX() + 0.5, DESK.getY() + 1, DESK.getZ() - 1.5, Set.of(), 0f, 10f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            topUp(s, 7);
            Settlement st = town(s);
            POST = O.offset(4, 0, -3);
            l.setBlock(POST, ModBlocks.PATROL_POST.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, POST, p);
            st.rescan(l);
            log("I2 patrol posts: " + st.sitesOf(WorkSiteTypes.PATROL_ID).size());
            SettlerEntity g = pick(s, null, true);
            g.setProfession(Professions.GUARD);
            g.setXp(Professions.GUARD.id(), 0);
            GUARD = g.getUUID();
            SettlerEntity a = pick(s, GUARD, true);
            a.setProfession(Professions.ARCHER);
            a.setXp(Professions.ARCHER.id(), 0);
            ARCHER = a.getUUID();
            // the lumberjack's forest zone covers the hall and sweeps up every drop: the loot test needs the guard to be the only collector
            for (SettlerEntity e : settlers(s)) if (e.professionId().equals(Professions.LUMBERJACK.id()) && !e.getUUID().equals(GUARD) && !e.getUUID().equals(ARCHER)) e.setProfession(Professions.UNEMPLOYED);
            for (int x = -12; x <= 50; x++) for (int z = -2; z <= 2; z++) {
                BlockPos b = O.offset(x, 0, 40 + z).atY(platformY);
                l.setBlock(b.below(), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 4; y++) l.setBlock(b.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
            // nobody but the two test settlers needs to fight or sleep now: keep the others calm
            log("I2 guard " + g.getName().getString() + " str=" + g.profile().stat(io.github.operagtx.hearthholm.core.Stat.STRENGTH) + " attr atk="
                    + g.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) + " speed attr="
                    + g.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        });
        ctx.waitTicks(20);

        // ---- 1. speed: walking along the platform, then chasing a zombie
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity g = get(s, GUARD);
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 20.5, platformY + 1, O.getZ() + 36.5, Set.of(), 0f, 20f, false);
            g.getNavigation().stop();
            g.teleportTo(O.getX() - 8.5, platformY, O.getZ() + 40.5);
            Goal w = new Walk(g, new Vec3(O.getX() + 24.5, platformY, O.getZ() + 40.5));
            g.addTestGoal(w);
            walk.set(w);
        });
        ctx.waitTicks(30);
        sp.getServer().runOnServer(s -> p0.set(get(s, GUARD).position()));
        for (int i = 0; i < 3; i++) {
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                log("I2 diag walk pos=" + g.position() + " nav=" + g.getNavigation().isInProgress() + " speed=" + g.getSpeed() + " ground=" + g.onGround() + " dm=" + g.getDeltaMovement()
                        + " act=" + g.brain().currentActivity() + " noAi=" + g.isNoAi() + " sleeping=" + g.isSleeping());
            });
        }
        AtomicInteger walkTenths = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            double bps = flatDist(p0.get(), g.position()) / 3.0;
            walkTenths.set((int) Math.round(bps * 10));
            log("I2 guard speed walking = " + String.format("%.2f", bps) + " b/s (player walk 4.3)");
            check(bps > 3.7 && bps < 4.9, "walking speed " + bps);
            g.removeTestGoal(walk.get());
            g.getNavigation().stop();
            g.teleportTo(O.getX() - 8.5, platformY, O.getZ() + 40.5);
            var z = zombie(s.overworld(), new BlockPos(O.getX() + 24, platformY, O.getZ() + 40));
            foe.set(z.getUUID());
            g.setTarget(z);
        });
        ctx.waitTicks(30);
        sp.getServer().runOnServer(s -> p0.set(get(s, GUARD).position()));
        for (int i = 0; i < 2; i++) {
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                log("I2 diag chase pos=" + g.position() + " nav=" + g.getNavigation().isInProgress() + " speed=" + g.getSpeed() + " ground=" + g.onGround() + " dm=" + g.getDeltaMovement()
                        + " act=" + g.brain().currentActivity() + " target=" + g.getTarget());
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            double bps = flatDist(p0.get(), g.position()) / 2.0;
            log("I2 guard speed chasing = " + String.format("%.2f", bps) + " b/s (player sprint 5.6) target=" + (g.getTarget() != null));
            check(bps > 4.9 && bps < 6.3, "chasing speed " + bps);
            g.setTarget(null);
            s.overworld().getEntity(foe.get()).discard();
            g.getNavigation().stop();
            g.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() - 2.5);
        });

        if (until("speed")) return;
        // ---- 2. damage per blow by weapon (hits to kill a 20 hp zombie), wear
        AtomicInteger[] hitCount = {new AtomicInteger(), new AtomicInteger(), new AtomicInteger()};
        ItemStack[] weapons = {ItemStack.EMPTY, new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.IRON_SWORD)};
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            g.setHealth(g.getMaxHealth());
            g.setNoAi(true);
            g.inventory().clearContent();
        });
        for (int w = 0; w < 3; w++) {
            int idx = w;
            sp.getServer().runOnServer(s -> get(s, GUARD).setItemSlot(EquipmentSlot.MAINHAND, weapons[idx]));
            ctx.waitTicks(4);   // the weapon's attribute modifiers apply on the next ticks
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                hitCount[idx].set(hitsToKill(g, g.getMainHandItem(), O.offset(-10, 0, 10)));
                log("I2 weapon " + g.getMainHandItem().getItem() + ": attack attribute=" + g.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                        + " attack speed=" + g.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) + " hits=" + hitCount[idx].get());
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            int bare = hitCount[0].get(), wood = hitCount[1].get(), ironHits = hitCount[2].get();
            log("I2 hits to kill a zombie: bare hand=" + bare + " wooden sword=" + wood + " iron sword=" + ironHits);
            check(bare >= 10 && bare <= 24, "bare hand hits " + bare);
            check(wood >= 4 && wood <= 6, "wooden sword hits " + wood);
            check(ironHits >= 3 && ironHits <= 5 && ironHits < wood, "iron sword hits " + ironHits);
            ItemStack iron = g.getMainHandItem();
            log("I2 weapon wear: iron sword damage " + iron.getDamageValue() + "/" + iron.getMaxDamage() + " after " + ironHits + " hits");
            check(iron.getDamageValue() == ironHits, "iron sword not worn by each hit");
            ItemStack frail = new ItemStack(Items.WOODEN_SWORD);
            frail.setDamageValue(frail.getMaxDamage() - 1);
            g.setItemSlot(EquipmentSlot.MAINHAND, frail);
        });
        ctx.waitTicks(4);
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            hitsToKill(g, g.getMainHandItem(), O.offset(-10, 0, 10));
            check(g.getMainHandItem().isEmpty(), "broken sword still in the hand");
            log("I2 sword broke: hand=" + g.getMainHandItem().getItem());
            g.setNoAi(false);
            // the warehouse has a sword: the guard (on duty) fetches the best one by himself
            remove(s, x -> x.is(net.minecraft.tags.ItemTags.SWORDS) || x.is(net.minecraft.tags.ItemTags.AXES));
            stock(s, new ItemStack(Items.STONE_SWORD));
            stock(s, new ItemStack(Items.IRON_SWORD));
            g.inventory().clearContent();
            g.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() - 4.5);
            log("I2 shift of the guard: " + g.shift() + " effective=" + g.effectiveShift() + " onDuty=" + g.isOnDuty() + " time=" + g.dayTime());
        });
        AtomicBoolean armed = new AtomicBoolean();
        for (int i = 0; i < 24 && !armed.get(); i++) {
            ctx.waitTicks(40);
            sp.getServer().runCommand("time set 10000");
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                if (g.getMainHandItem().is(Items.IRON_SWORD)) armed.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            log("I2 broken weapon replaced from the warehouse: hand=" + g.getMainHandItem().getItem() + " status=" + g.status().getString());
            check(armed.get(), "guard did not fetch a new weapon");
        });

        if (until("damage")) return;
        // ---- 3. loot: a zombie killed by the guard drops an item; it is picked up and carried to the warehouse
        AtomicInteger emeralds0 = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity g = get(s, GUARD);
            Settlement st = town(s);
            emeralds0.set(HearthholmClientTest.stock(s, Items.EMERALD));
            SettlerEntity ar = get(s, ARCHER);   // the archer patrols the same post and loots as well
            for (int i = 0; i < 15; i++) { g.inventory().setItem(i, new ItemStack(Items.DIRT)); ar.inventory().setItem(i, new ItemStack(Items.DIRT)); }   // pockets nearly full
            BlockPos at = POST.offset(2, 0, -1).atY(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, POST.getX() + 2, POST.getZ() - 1));
            var z = zombie(l, at);
            ItemEntity drop = new ItemEntity(l, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, new ItemStack(Items.EMERALD, 3));
            drop.setNoPickUpDelay();
            l.addFreshEntity(drop);
            int n = 0;
            while (z.isAlive() && n++ < 60) { z.damageCooldownTime = 0; g.doHurtTarget(l, z); }
            z.discard();
            boolean tagged = drop.entityTags().contains(Loot.TAG);
            DROP = drop.getUUID();
            DROP_E = drop;
            log("I2 loot: zombie killed by the guard, drop tagged=" + tagged + " at " + at.toShortString() + " post=" + POST.toShortString());
            check(tagged, "drop of the killed zombie is not claimed");
        });
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicBoolean picked = new AtomicBoolean();
        for (int i = 0; i < 40 && !delivered.get(); i++) {
            ctx.waitTicks(40);
            sp.getServer().runCommand("time set 10000");
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                if (g.count(x -> x.is(Items.EMERALD)) > 0 || get(s, ARCHER).count(x -> x.is(Items.EMERALD)) > 0) picked.set(true);
                var d = s.overworld().getEntity(DROP);
                if (d == null) {
                    StringBuilder near = new StringBuilder();
                    for (SettlerEntity o : settlers(s)) if (o.distanceToSqr(DROP_E) < 100) near.append(o.getName().getString()).append(':').append(o.professionId().getPath()).append('@').append(o.blockPosition().toShortString()).append(" inv=").append(o.count(x -> x.is(Items.EMERALD))).append(' ');
                    log("I2 diag drop removal=" + DROP_E.getRemovalReason() + " item=" + DROP_E.getItem() + " near: " + near);
                }
                log("I2 diag drop " + (d == null ? "GONE" : d.blockPosition().toShortString() + " alive=" + d.isAlive() + " tags=" + d.entityTags() + " age=" + d.tickCount));
                var near = Loot.nearest(g, Vec3.atBottomCenterOf(POST), 12);
                log("I2 diag loot guard=" + g.blockPosition().toShortString() + " act=" + g.brain().currentActivity() + " status=" + g.status().getString() + " free=" + g.freeSlots()
                        + " nearest=" + (near == null ? "none" : near.blockPosition().toShortString()) + " posts=" + town(s).sitesOf(WorkSiteTypes.PATROL_ID).size());
                if (HearthholmClientTest.stock(s, Items.EMERALD) > emeralds0.get()) delivered.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            log("I2 loot picked up=" + picked.get() + " delivered to the warehouse=" + delivered.get() + " (emeralds " + emeralds0.get() + " -> " + HearthholmClientTest.stock(s, Items.EMERALD) + ")");
            check(picked.get() || delivered.get(), "guard did not pick up the loot");
            check(delivered.get(), "loot was not delivered to the warehouse");
        });

        if (until("loot")) return;
        // ---- 4. archer: refills from the warehouse, consumes arrows, bow wears, no arrows -> status
        AtomicInteger arrowsAfterFetch = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity g = get(s, GUARD);
            g.setHealth(g.getMaxHealth());
            g.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() - 2.5);
            SettlerEntity a = get(s, ARCHER);
            a.inventory().clearContent();
            a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            remove(s, x -> x.is(Items.ARROW));
            stock(s, new ItemStack(Items.ARROW, 40));
            a.teleportTo(O.getX() + 11.5, O.getY(), O.getZ() + 8.5);
            log("I2 archer set up: arrows in the warehouse=" + HearthholmClientTest.stock(s, Items.ARROW) + " pockets=" + a.count(x -> x.is(Items.ARROW)));
        });
        for (int i = 0; i < 12; i++) {
            ctx.waitTicks(60);
            sp.getServer().runCommand("time set 10000");
            boolean[] got = {false};
            sp.getServer().runOnServer(s -> {
                SettlerEntity ar = get(s, ARCHER);
                log("I2 diag archer pos=" + ar.blockPosition().toShortString() + " act=" + ar.brain().currentActivity() + " status=" + ar.status().getString() + " prof=" + ar.professionId() + " leaving=" + ar.isLeaving() + " arrived=" + ar.hasArrived() + " work=" + ar.isWorkTime() + " t=" + ar.dayTime());
                int n = get(s, ARCHER).count(x -> x.is(Items.ARROW));
                got[0] = n > 0;
                arrowsAfterFetch.set(n);
            });
            if (got[0]) break;
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity a = get(s, ARCHER);
            log("I2 archer took arrows from the warehouse: " + arrowsAfterFetch.get() + ", warehouse now " + HearthholmClientTest.stock(s, Items.ARROW));
            check(arrowsAfterFetch.get() > 0, "archer did not fetch arrows");
            // on the open platform: later waves put houses between the old firing line and the target
            a.teleportTo(O.getX() + 10.5, platformY, O.getZ() + 40.5);
            a.getNavigation().stop();
            var z = zombie(l, new BlockPos(O.getX() + 22, platformY, O.getZ() + 40));
            z.setHealth(z.getMaxHealth());
            foe.set(z.getUUID());
            a.setTarget(z);
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 4.5, platformY + 1, O.getZ() + 40.5, Set.of(), 90f, 8f, false);
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, ARCHER);
            ServerPlayer p = player(s);
            p.teleportTo(s.overworld(), a.getX() - 4.5, a.getY() + 0.5, a.getZ(), Set.of(), -90f, 10f, false);
            double dx = a.getX() - p.getX(), dz = a.getZ() - p.getZ(), dy = a.getY() + 1.2 - (p.getY() + 1.62);
            p.teleportTo(s.overworld(), p.getX(), p.getY(), p.getZ(), Set.of(), (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f, (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI), false);
        });
        for (int i = 0; i < 25; i++) {
            ctx.waitTicks(3);
            boolean[] drawing = {false};
            sp.getServer().runOnServer(s -> drawing[0] = get(s, ARCHER).isUsingItem() && get(s, ARCHER).getTicksUsingItem() > 12);
            if (drawing[0]) break;
        }
        ctx.takeScreenshot("hh_i2_archer_shooting");
        ctx.waitTicks(200);
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, ARCHER);
            var z = (LivingEntity) s.overworld().getEntity(foe.get());
            int used = arrowsAfterFetch.get() - a.count(x -> x.is(Items.ARROW));
            int bowWear = a.getMainHandItem().getDamageValue();
            log("I2 archer shot: arrows used=" + used + " remaining=" + a.count(x -> x.is(Items.ARROW)) + " bow damage=" + bowWear + " target="
                    + (z == null || !z.isAlive() ? "dead" : "hp " + z.getHealth() + "/20") + " draw ticks=" + io.github.operagtx.hearthholm.core.Balance.ARCHER_DRAW_TICKS);
            check(used > 0, "archer consumed no arrows");
            check(bowWear > 0, "bow did not wear");
            check(z == null || !z.isAlive() || z.getHealth() < 20f, "arrows did not hurt the target");
            if (z != null) z.discard();
            a.setTarget(null);
            // below the low mark: refill from the warehouse again
            a.inventory().clearContent();
            a.store(new ItemStack(Items.ARROW, 3));
            stock(s, new ItemStack(Items.ARROW, 40));
            arrowsAfterFetch.set(3);
            a.teleportTo(O.getX() + 11.5, O.getY(), O.getZ() + 8.5);
            player(s).teleportTo(s.overworld(), DESK.getX() + 0.5, DESK.getY() + 1, DESK.getZ() - 1.5, Set.of(), 0f, 10f, false);
        });
        AtomicInteger refilled = new AtomicInteger();
        for (int i = 0; i < 12 && refilled.get() <= 3; i++) {
            ctx.waitTicks(60);
            sp.getServer().runCommand("time set 10000");
            sp.getServer().runOnServer(s -> refilled.set(get(s, ARCHER).count(x -> x.is(Items.ARROW))));
        }
        sp.getServer().runOnServer(s -> {
            log("I2 archer refilled from the warehouse: 3 -> " + refilled.get());
            check(refilled.get() > 3, "archer did not refill arrows");
            // no arrows anywhere
            SettlerEntity a = get(s, ARCHER);
            a.inventory().clearContent();
            remove(s, x -> x.is(Items.ARROW));
            a.teleportTo(O.getX() + 4.5, O.getY(), O.getZ() - 2.5);
        });
        AtomicBoolean needs = new AtomicBoolean(), bubble = new AtomicBoolean();
        for (int i = 0; i < 160 && !(needs.get() && bubble.get()); i++) {
            ctx.waitTicks(10);
            sp.getServer().runCommand("time set 10000");
            sp.getServer().runOnServer(s -> {
                SettlerEntity a = get(s, ARCHER);
                if (statusIs(a, "needs_arrows")) needs.set(true);
                if (a.hasBubble() && a.bubble().getString().length() > 0) { bubble.set(true); log("I2 archer bubble: " + a.bubble().getString()); }
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, ARCHER);
            log("I2 archer without arrows: status needs_arrows=" + needs.get() + " (" + a.status().getString() + ") bubble=" + bubble.get() + " ranged=" + a.isRanged() + " needsArrows=" + a.needsArrows());
            check(needs.get(), "archer without arrows shows no status");
            check(bubble.get(), "archer without arrows shows no bubble");
            stock(s, new ItemStack(Items.ARROW, 48));   // later waves
        });

        // ---- 5. shifts: night shift sleeps at noon and patrols at midnight; day shift the other way round; the alarm calls everybody
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            g.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            g.inventory().clearContent();
            g.setShift(SettlerEntity.SHIFT_NIGHT);
            if (g.bed() != null) g.teleportTo(g.bed().getX() + 0.5, g.bed().getY(), g.bed().getZ() + 2.5);
            log("I2 shift test guard bed=" + g.bed() + " shift=" + g.shift());
        });
        for (int[] c : new int[][]{{6000, SettlerEntity.SHIFT_NIGHT}, {18000, SettlerEntity.SHIFT_NIGHT}, {18000, SettlerEntity.SHIFT_DAY}, {6000, SettlerEntity.SHIFT_DAY}}) {
            int time = c[0], mode = c[1];
            sp.getServer().runOnServer(s -> get(s, GUARD).setShift(mode));
            AtomicBoolean ok = new AtomicBoolean();
            for (int i = 0; i < 24 && !ok.get(); i++) {
                sp.getServer().runCommand("time set " + time);
                ctx.waitTicks(30);
                sp.getServer().runOnServer(s -> {
                    SettlerEntity g = get(s, GUARD);
                    boolean asleepExpected = (mode == SettlerEntity.SHIFT_NIGHT) == (time == 6000);
                    if (asleepExpected) ok.set(g.isSleeping() && !g.isOnDuty());
                    else ok.set(g.isOnDuty() && !g.isSleeping() && g.brain().currentActivity().equals("WorkActivity"));
                });
            }
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                log("I2 shift " + (mode == SettlerEntity.SHIFT_NIGHT ? "NIGHT" : "DAY") + " at time " + time + ": sleeping=" + g.isSleeping() + " onDuty=" + g.isOnDuty() + " activity=" + g.brain().currentActivity()
                        + " status=" + g.status().getString() + " ok=" + ok.get());
                check(ok.get(), "shift " + mode + " at " + time);
            });
        }
        // the alarm calls an off-duty (sleeping) guard
        sp.getServer().runOnServer(s -> get(s, GUARD).setShift(SettlerEntity.SHIFT_NIGHT));
        sp.getServer().runCommand("time set 6000");
        AtomicBoolean asleep = new AtomicBoolean();
        for (int i = 0; i < 24 && !asleep.get(); i++) {
            ctx.waitTicks(30);
            sp.getServer().runCommand("time set 6000");
            sp.getServer().runOnServer(s -> asleep.set(get(s, GUARD).isSleeping()));
        }
        sp.getServer().runOnServer(s -> {
            log("I2 before the alarm: guard asleep=" + asleep.get());
            check(asleep.get(), "off-duty guard is not asleep before the alarm");
            io.github.operagtx.hearthholm.threat.Defense.setAlarm(s.overworld(), town(s), true, player(s));
        });
        AtomicBoolean called = new AtomicBoolean();
        for (int i = 0; i < 20 && !called.get(); i++) {
            ctx.waitTicks(20);
            sp.getServer().runCommand("time set 6000");
            sp.getServer().runOnServer(s -> {
                SettlerEntity g = get(s, GUARD);
                if (!g.isSleeping() && g.brain().currentActivity().equals("AlarmActivity")) called.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            log("I2 alarm calls the off-duty guard: awake=" + !g.isSleeping() + " activity=" + g.brain().currentActivity() + " status=" + g.status().getString() + " onDuty=" + g.isOnDuty());
            check(called.get(), "alarm did not call the off-duty guard");
            io.github.operagtx.hearthholm.threat.Defense.setAlarm(s.overworld(), town(s), false, player(s));
            g.setShift(SettlerEntity.SHIFT_POST);
            // codec: the shift survives a save, and an old save without it reads as always
            ServerLevel l = s.overworld();
            g.setShift(SettlerEntity.SHIFT_DAY);
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess());
            g.saveWithoutId(out);
            SettlerEntity copy = io.github.operagtx.hearthholm.registry.ModEntities.SETTLER.create(l, EntitySpawnReason.LOAD);
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess(), out.buildResult()));
            log("I2 shift saved: " + copy.shift() + " (expected " + SettlerEntity.SHIFT_DAY + ")");
            check(copy.shift() == SettlerEntity.SHIFT_DAY, "shift not saved");
            copy.discard();
            g.setShift(SettlerEntity.SHIFT_ALWAYS);
            // the post screen shows its shift: set it on the post and read the guard's effective shift from it
            if (l.getBlockEntity(POST) instanceof WorkSiteBlockEntity w) {
                w.setOption("shift", SettlerEntity.SHIFT_NIGHT);
                log("I2 post shift night -> guard effective shift " + g.effectiveShift());
                w.setOption("shift", SettlerEntity.SHIFT_ALWAYS);
            }
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 4.5, O.getY() + 1, O.getZ() - 1.5, Set.of(), 0f, 10f, false);
            g.setShift(SettlerEntity.SHIFT_NIGHT);
        });
        sp.getServer().runCommand("time set 10000");

        // ---- 6. screenshots: guard post with the shift (RU 1280x720), settler screen of the guard (RU)
        boolean langOk = true;
        try {
            ctx.runOnClient(mc -> {
                mc.options.languageCode = "ru_ru";
                mc.getLanguageManager().setSelected("ru_ru");
            });
            var fut = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
            ctx.waitFor(mc -> fut.isDone(), 2400);
            ctx.waitTicks(120);
        } catch (Throwable t) {
            langOk = false;
            fail("could not switch the language: " + t);
        }
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.waitTicks(8);
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), POST));
        ctx.waitForScreen(WorkSiteScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i2_post_shift_ru_1280x720");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> SettlementService.openSettler(player(s), get(s, GUARD)));
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i2_guard_ru_1280x720");
        ctx.setScreen(() -> null);
        ctx.getInput().resizeWindow(854, 480);
        ctx.waitTicks(8);
        sp.getServer().runOnServer(s -> SettlementService.openSettler(player(s), get(s, GUARD)));
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i2_guard_ru_854x480");
        ctx.setScreen(() -> null);
        if (langOk) {
            ctx.runOnClient(mc -> {
                mc.options.languageCode = "en_us";
                mc.getLanguageManager().setSelected("en_us");
            });
            var fut = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
            ctx.waitFor(mc -> fut.isDone(), 2400);
            ctx.waitTicks(120);
        }
        sp.getServer().runOnServer(s -> SettlementService.openSettler(player(s), get(s, GUARD)));
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i2_guard_en_854x480");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> SettlementService.openWorkSite(player(s), POST));
        ctx.waitForScreen(WorkSiteScreen.class);
        ctx.waitTicks(4);
        ctx.takeScreenshot("hh_i2_post_en_854x480");
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            g.setShift(SettlerEntity.SHIFT_ALWAYS);
            get(s, ARCHER).setShift(SettlerEntity.SHIFT_ALWAYS);
        });

        if (!FAILS.isEmpty()) throw new AssertionError("wave I2 failed: " + FAILS);
        log("I2 OK");
    }
}
