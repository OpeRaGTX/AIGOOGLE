package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.combat.Cavalry;
import io.github.operagtx.hearthholm.combat.CavalryChargeGoal;
import io.github.operagtx.hearthholm.combat.CombatRoles;
import io.github.operagtx.hearthholm.combat.Distress;
import io.github.operagtx.hearthholm.combat.Promotions;
import io.github.operagtx.hearthholm.combat.Shields;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.mixin.MobAccessor;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave L1: combat roles. Guard / warrior / archer health and speed ratios, shields on guards, the guard's answer to a call for help from far away (and the
 * others' silence), the archer's sidestepping, promotions at 25 marks (guard to knight, archer to crossbowman who shoots a crossbow, warrior to cavalryman who
 * mounts a horse and charges: ram, ride on, turn, ram again). Run alone with HH_ONLY_L1=1.
 */
final class WaveL1Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos P;
    private static UUID A, B, C, VICTIM, HUSK;

    private WaveL1Test() {}

    private static void fail(String m) { FAILS.add(m); log("L1 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static SettlerEntity get(ServerLevel l, UUID id) { return (SettlerEntity) l.getEntity(id); }

    /** A clean fighter: given profession, no items, standing at the spot. */
    private static void reset(SettlerEntity e, Profession p, double x, double y, double z) {
        e.setProfession(Professions.UNEMPLOYED);
        e.setProfession(p);
        e.markArrived();
        e.inventory().clearContent();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            e.setItemSlot(slot, ItemStack.EMPTY);
        e.getNavigation().stop();
        e.teleportTo(x, y, z);
        e.refreshCombatStats();
        e.setHealth(e.getMaxHealth());
        e.setFood(1000f);   // a settler that has been alive through earlier waves is hungry (food runs out 2.5x faster since M1) and would walk off to eat
    }

    private static Husk husk(ServerLevel l, double x, double y, double z) {
        Husk h = EntityTypes.HUSK.spawn(l, BlockPos.containing(x, y, z), EntitySpawnReason.SPAWN_ITEM_USE);
        h.setNoAi(true);
        h.setPersistenceRequired();
        return h;
    }

    private static void marks(ServerLevel l, Settlement st, int n) {
        Storage.take(Storage.resolve(l, st.storagePositions()), x -> x.is(ModItems.HEARTH_MARK), 9999);
        Storage.insert(Storage.resolve(l, st.storagePositions()), new ItemStack(ModItems.HEARTH_MARK, n));
    }

    private static int marksIn(ServerLevel l, Settlement st) {
        return Storage.count(Storage.resolve(l, st.storagePositions()), x -> x.is(ModItems.HEARTH_MARK));
    }

    private static void setTime(TestSingleplayerContext sp, int t) { sp.getServer().runCommand("time set " + t); }

    private static boolean poll(ClientGameTestContext ctx, TestSingleplayerContext sp, int rounds, int step, java.util.function.Predicate<MinecraftServer> done) {
        AtomicBoolean ok = new AtomicBoolean();
        for (int r = 0; r < rounds && !ok.get(); r++) {
            ctx.waitTicks(step);
            sp.getServer().runOnServer(s -> { if (done.test(s)) ok.set(true); });
        }
        return ok.get();
    }

    private static CavalryChargeGoal chargeGoal(SettlerEntity e) {
        for (var w : ((MobAccessor) e).hearthholm$goalSelector().getAvailableGoals()) if (w.getGoal() instanceof CavalryChargeGoal g) return g;
        return null;
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("weather clear");
        setTime(sp, 5000);
        P = O.offset(60, 0, 60).atY(O.getY() + 70);

        // ---- 1. roles: one settler, three professions at level 1: health 100% / 90% / 81%, chase speed 80% / 100% / 100%
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = settlers(s).getFirst();
            double[] hp = new double[3], bps = new double[3];
            Profession[] profs = {Professions.GUARD, Professions.WARRIOR, Professions.ARCHER};
            for (int i = 0; i < 3; i++) {
                e.setProfession(profs[i]);
                e.setXp(profs[i].id(), 0);
                e.refreshCombatStats();
                hp[i] = e.getMaxHealth();
                double a = e.getAttributeValue(Attributes.MOVEMENT_SPEED);
                bps[i] = a * a * Balance.COMBAT_BPS_PER_ATTR_SQ;
            }
            log("L1 roles: hp guard " + hp[0] + " warrior " + hp[1] + " archer " + hp[2] + "; chase b/s guard " + bps[0] + " warrior " + bps[1] + " archer " + bps[2]);
            check(Math.abs(hp[1] / hp[0] - 0.9) < 0.02, "warrior health is not 90% of the guard's: " + hp[1] / hp[0]);
            check(Math.abs(hp[2] / hp[1] - 0.9) < 0.02, "archer health is not 90% of the warrior's: " + hp[2] / hp[1]);
            check(Math.abs(bps[0] / bps[1] - 0.8) < 0.02, "guard speed is not 80% of the warrior's: " + bps[0] / bps[1]);
            check(Math.abs(bps[1] - Balance.COMBAT_SPRINT_BPS_X10 / 10.0) < 0.1 && Math.abs(bps[2] - bps[1]) < 0.01, "warrior and archer keep the I2 chase speed");
            check(CombatRoles.of(Professions.GUARD.id()).distressRadius() > CombatRoles.of(Professions.WARRIOR.id()).distressRadius(), "the guard must hear farther than the warrior");
            check(Professions.isShooter(Professions.CROSSBOWMAN_ID) && Promotions.of(Professions.ARCHER.id()).to().equals(Professions.CROSSBOWMAN_ID)
                    && Promotions.of(Professions.GUARD.id()).to().equals(Professions.KNIGHT_ID) && Promotions.of(Professions.WARRIOR.id()).to().equals(Professions.CAVALRYMAN_ID), "promotion table");
            e.setProfession(Professions.UNEMPLOYED);
        });

        // ---- 2. shields: a shield in the off hand takes the frontal blow down to 35%, a rear blow in full; wears; no shield, no block
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity e = settlers(s).getFirst();
            reset(e, Professions.GUARD, O.getX() + 3.5, O.getY(), O.getZ() + 3.5);
            e.setNoAi(true);
            e.setYRot(0f); e.yBodyRot = 0f; e.yHeadRot = 0f;   // faces south (+z)
            Husk front = husk(l, e.getX(), e.getY(), e.getZ() + 2), back = husk(l, e.getX(), e.getY(), e.getZ() - 2);
            var srcFront = l.damageSources().mobAttack(front);
            var srcBack = l.damageSources().mobAttack(back);
            check(!Shields.blocks(e, srcFront), "no shield, no block");
            e.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            check(Shields.blocks(e, srcFront) && !Shields.blocks(e, srcBack), "a shield blocks from the front only");
            float full = 10f;
            float hurtFront = Shields.reduce(e, l, srcFront, full), hurtBack = Shields.reduce(e, l, srcBack, full);
            int wear = e.getOffhandItem().getDamageValue();
            log("L1 shield: frontal blow " + full + " -> " + hurtFront + ", rear blow " + hurtBack + ", shield wear " + wear);
            check(Math.abs(hurtFront - full * Balance.SHIELD_BLOCK_PERCENT / 100f) < 0.01 && hurtBack == full, "the shield's reduction is wrong");
            check(wear >= 1, "the shield does not wear");
            e.setHealth(e.getMaxHealth());
            e.hurtServer(l, srcFront, 10f);   // a real blow through hurtServer
            float lost = e.getMaxHealth() - e.getHealth();
            log("L1 shield: a real frontal blow of 10 cost " + lost + " health");
            check(lost > 0.5f && lost < 6f, "a real blow on a shielded guard should cost about 3.5, cost " + lost);
            front.discard(); back.discard();
            e.setNoAi(false);
            e.setProfession(Professions.UNEMPLOYED);
        });

        // ---- 3. the call for help: a platform in the sky, a civilian hurt by a husk 50 blocks from the guard; the warrior and the archer are as far and stay out of it
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, P.getX() + 25.5, P.getY() + 6, P.getZ() + 0.5, Set.of(), 0f, 20f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(80);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (int dx = -6; dx <= 66; dx++) for (int dz = -40; dz <= 40; dz++) {
                for (int dy = -2; dy <= -1; dy++) l.setBlock(P.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 8; dy++) l.setBlock(P.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        });
        ctx.waitTicks(20);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            List<SettlerEntity> all = settlers(s);
            SettlerEntity victim = all.get(0), guard = all.get(1), warrior = all.get(2), archer = all.get(3);
            for (SettlerEntity e : all) if (e != victim && e != guard && e != warrior && e != archer) e.setProfession(Professions.UNEMPLOYED);
            victim.setProfession(Professions.UNEMPLOYED);
            victim.setProfession(Professions.HAULER);
            victim.markArrived(); victim.getNavigation().stop(); victim.teleportTo(P.getX() + 0.5, P.getY(), P.getZ() + 0.5); victim.setNoAi(true);
            reset(guard, Professions.GUARD, P.getX() + 50.5, P.getY(), P.getZ() + 0.5);
            reset(warrior, Professions.WARRIOR, P.getX() + 50.5, P.getY(), P.getZ() + 4.5);
            reset(archer, Professions.ARCHER, P.getX() + 50.5, P.getY(), P.getZ() - 4.5);
            for (SettlerEntity f : List.of(guard, warrior, archer)) f.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(f == archer ? Items.BOW : Items.IRON_SWORD));
            archer.inventory().addItem(new ItemStack(Items.ARROW, 32));
            VICTIM = victim.getUUID(); A = guard.getUUID(); B = warrior.getUUID(); C = archer.getUUID();
            Husk husk = husk(l, P.getX() + 2.5, P.getY(), P.getZ() + 0.5);
            HUSK = husk.getUUID();
            List<SettlerEntity> answered = Distress.raise(victim, husk);
            List<String> who = new ArrayList<>();
            for (SettlerEntity e : answered) who.add(e.professionId().getPath());
            log("L1 distress: victim at " + victim.blockPosition().toShortString() + " husk at " + husk.blockPosition().toShortString() + " answered by " + who
                    + " (guard distance " + (int) Math.sqrt(guard.distanceToSqr(victim)) + ", radius guard " + CombatRoles.of(guard.professionId()).distressRadius() + " other " + CombatRoles.of(warrior.professionId()).distressRadius() + ")");
            check(who.equals(List.of("guard")), "only the guard may answer a call from 50 blocks away: " + who);
            check(guard.getTarget() == husk && warrior.getTarget() == null && archer.getTarget() == null, "targets after the call: guard " + guard.getTarget() + " warrior " + warrior.getTarget());
        });
        AtomicBoolean reached = new AtomicBoolean();
        boolean killed = poll(ctx, sp, 60, 20, s -> {
            ServerLevel l = s.overworld();
            SettlerEntity g = get(l, A);
            if (g.distanceToSqr(get(l, VICTIM)) < 36) reached.set(true);
            var h = l.getEntity(HUSK);
            return h == null || !h.isAlive();
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity g = get(l, A);
            log("L1 distress: guard reached the victim=" + reached.get() + ", husk dead=" + killed + ", guard now " + g.blockPosition().toShortString() + " hp " + g.getHealth() + "/" + g.getMaxHealth());
            check(reached.get(), "the guard did not run to the victim");
            check(killed, "the guard did not kill the husk");
            check(get(l, B).getTarget() == null || get(l, B).getTarget() == null || !get(l, B).getTarget().getUUID().equals(HUSK), "the warrior must not have taken on the husk of a distant call");
        });

        // ---- 4. the archer sidesteps while aiming: 10 blocks from a still husk, he shoots and his path is not a straight line
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity archer = get(l, C);
            for (SettlerEntity e : settlers(s)) if (e != archer) { e.setProfession(Professions.UNEMPLOYED); e.teleportTo(P.getX() + 40.5, P.getY(), P.getZ() + 12.5); }
            reset(archer, Professions.ARCHER, P.getX() + 30.5, P.getY(), P.getZ() - 8.5);
            archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            archer.inventory().addItem(new ItemStack(Items.ARROW, 64));
            Husk h = husk(l, P.getX() + 40.5, P.getY(), P.getZ() - 8.5);
            h.setHealth(h.getMaxHealth());
            HUSK = h.getUUID();
            archer.setTarget(h);
            log("L1 strafe: archer at " + archer.blockPosition().toShortString() + " husk at " + h.blockPosition().toShortString());
        });
        double[] lat = {1e9, -1e9};
        double[] dist = {1e9, -1e9};
        for (int i = 0; i < 40; i++) {
            ctx.waitTicks(5);
            sp.getServer().runOnServer(s -> {
                ServerLevel l = s.overworld();
                SettlerEntity a = get(l, C);
                var h = l.getEntity(HUSK);
                if (h == null || a == null || !a.isAlive()) return;
                double dx = h.getX() - a.getX(), dz = h.getZ() - a.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                double lateral = (-dz * (a.getX() - (P.getX() + 30.5)) + dx * (a.getZ() - (P.getZ() - 8.5))) / Math.max(0.1, d);   // sideways drift from the starting spot
                lat[0] = Math.min(lat[0], lateral); lat[1] = Math.max(lat[1], lateral);
                dist[0] = Math.min(dist[0], d); dist[1] = Math.max(dist[1], d);
            });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var h = (net.minecraft.world.entity.LivingEntity) l.getEntity(HUSK);
            log("L1 strafe: sideways spread " + (lat[1] - lat[0]) + " blocks, distance to the target " + dist[0] + ".." + dist[1] + ", husk hp " + (h == null ? "dead" : h.getHealth() + "/" + h.getMaxHealth()));
            check(get(l, C) != null && get(l, C).isAlive(), "the strafing archer died (walked off the platform?)");
            check(lat[1] - lat[0] >= 2.0, "the archer did not sidestep: spread " + (lat[1] - lat[0]));
            check(dist[0] >= 4.0 && dist[1] <= 18.0, "the archer lost his range: " + dist[0] + ".." + dist[1]);
            check(h == null || h.getHealth() < h.getMaxHealth(), "the archer never hit the target while sidestepping");
            if (h != null) h.discard();
        });

        // ---- 5. promotions cost 25 marks: guard to knight (armour + shield), archer to crossbowman (shoots a crossbow)
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer p = player(s);
            SettlerEntity g = get(l, A), a = get(l, C);
            reset(g, Professions.GUARD, P.getX() + 20.5, P.getY(), P.getZ() + 8.5);
            reset(a, Professions.ARCHER, P.getX() + 22.5, P.getY(), P.getZ() + 8.5);
            g.setXp(Professions.GUARD.id(), Balance.LEVEL_XP[4]);
            a.setXp(Professions.ARCHER.id(), Balance.LEVEL_XP[4]);
            check(SettlementService.canPromote(g) && SettlementService.canPromote(a), "level 5 guard and archer must be promotable");
            marks(l, st, 24);
            check(!SettlementService.promote(p, a, st) && a.professionId().equals(Professions.ARCHER.id()), "24 marks must not be enough for a promotion");
            marks(l, st, 60);
            for (var piece : new net.minecraft.world.item.Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS})
                Storage.insert(Storage.resolve(l, st.storagePositions()), new ItemStack(piece));
            int before = marksIn(l, st);
            check(SettlementService.promote(p, a, st) && a.professionId().equals(Professions.CROSSBOWMAN_ID), "the archer was not promoted to crossbowman");
            check(before - marksIn(l, st) == 25, "the crossbowman promotion must cost 25 marks, cost " + (before - marksIn(l, st)));
            before = marksIn(l, st);
            check(SettlementService.promote(p, g, st) && g.isKnight(), "the guard was not promoted to knight");
            check(before - marksIn(l, st) == 25, "the knight promotion must cost 25 marks, cost " + (before - marksIn(l, st)));
            check(g.getOffhandItem().is(Items.SHIELD) && Shields.holdsShield(g), "the new knight must hold a shield");
            log("L1 promotions: crossbowman " + a.professionId().getPath() + ", knight " + g.professionId().getPath() + ", marks left " + marksIn(l, st));
            // the crossbowman with a crossbow and arrows shoots and hits
            a.refreshCombatStats();
            a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            a.inventory().addItem(new ItemStack(Items.ARROW, 32));
            check(a.shootsWithHeld() && a.isRanged(), "a crossbowman with a crossbow and arrows must count as shooting");
            Husk h = husk(l, P.getX() + 32.5, P.getY(), P.getZ() + 8.5);
            HUSK = h.getUUID();
            a.teleportTo(P.getX() + 22.5, P.getY(), P.getZ() + 8.5);
            a.setTarget(h);
        });
        boolean hit = poll(ctx, sp, 60, 10, s -> {
            var h = (net.minecraft.world.entity.LivingEntity) s.overworld().getEntity(HUSK);
            return h == null || h.getHealth() < h.getMaxHealth();
        });
        sp.getServer().runOnServer(s -> {
            var h = s.overworld().getEntity(HUSK);
            if (h != null) h.discard();
            SettlerEntity a = get(s.overworld(), C);
            log("L1 crossbow: hit=" + hit + " arrows left " + a.count(x -> x.is(Items.ARROW)));
            check(hit, "the crossbowman never hit the husk");
            check(a.count(x -> x.is(Items.ARROW)) < 32, "the crossbowman did not use his arrows");
        });

        // ---- 6. the settler screen offers the right rank (screenshot)
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SettlerEntity w = get(l, B);
            reset(w, Professions.WARRIOR, P.getX() + 20.5, P.getY(), P.getZ() - 8.5);
            w.setXp(Professions.WARRIOR.id(), Balance.LEVEL_XP[4]);
            p.teleportTo(l, P.getX() + 20.5, P.getY() + 1, P.getZ() - 6.5, Set.of(), 0f, 10f, false);
            SettlementService.openSettler(p, w);
        });
        TestKit.setWindow(ctx, 854, 480);
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(6);
        ctx.takeScreenshot("hh_l1_settler_promote_cavalryman");
        ctx.setScreen(() -> null);

        // ---- 7. the cavalryman: promotion, saddle from his pockets, mounts, charges a still husk (ram, ride on, turn, ram again) and dismounts when the shift ends.
        // A flat stone field next to the town (the horse has to be inside the settlement territory, which the sky platform is not)
        BlockPos Q = O.offset(22, 0, -26);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, Q.getX() + 10.5, Q.getY() + 6, Q.getZ() + 0.5, Set.of(), 0f, 20f, false);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (int dx = -4; dx <= 40; dx++) for (int dz = -12; dz <= 12; dz++) {
                l.setBlock(Q.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 8; dy++) l.setBlock(Q.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        });
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            ServerPlayer p = player(s);
            SettlerEntity w = get(l, B);
            marks(l, st, 40);
            int before = marksIn(l, st);
            check(SettlementService.promote(p, w, st) && w.professionId().equals(Professions.CAVALRYMAN_ID), "the warrior was not promoted to cavalryman");
            check(before - marksIn(l, st) == 25, "the cavalryman promotion must cost 25 marks");
            w.teleportTo(Q.getX() + 2.5, Q.getY(), Q.getZ() + 0.5);
            w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
            w.inventory().addItem(new ItemStack(Items.SADDLE));
            Horse horse = EntityTypes.HORSE.spawn(l, BlockPos.containing(Q.getX() + 6.5, Q.getY(), Q.getZ() + 0.5), EntitySpawnReason.SPAWN_ITEM_USE);
            horse.setPersistenceRequired();
            HUSK = horse.getUUID();   // (the horse's id; the husk is spawned below)
            log("L1 cavalry: cavalryman at " + w.blockPosition().toShortString() + " horse at " + horse.blockPosition().toShortString() + " spear " + w.getMainHandItem().getItem());
        });
        UUID[] ids = new UUID[2];
        ids[0] = HUSK;
        boolean mounted = poll(ctx, sp, 40, 10, s -> get(s.overworld(), B).isCavalryMounted());
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity w = get(l, B);
            Horse horse = (Horse) l.getEntity(ids[0]);
            log("L1 cavalry: mounted=" + mounted + " status='" + w.status().getString() + "' horse saddled=" + Cavalry.saddled(horse) + " tamed=" + horse.isTamed() + " passengers=" + horse.getPassengers().size()
                    + " | activity=" + w.brain().currentActivity() + " food=" + w.food() + " work=" + w.isWorkTime() + " asleep=" + w.isSleeping() + " saddleInPockets=" + w.has(Items.SADDLE)
                    + " at " + w.blockPosition().toShortString() + " horse at " + horse.blockPosition().toShortString());
            check(mounted && Cavalry.saddled(horse), "the cavalryman did not saddle and mount the horse");
            // a still husk 24 blocks away on the platform
            Husk h = husk(l, horse.getX() + 24, Q.getY(), horse.getZ());
            h.setHealth(h.getMaxHealth());
            ids[1] = h.getUUID();
            w.setTarget(h);
        });
        double[] best = {0};
        boolean dead = poll(ctx, sp, 80, 10, s -> {
            ServerLevel l = s.overworld();
            var h = l.getEntity(ids[1]);
            SettlerEntity w = get(l, B);
            if (h != null && w.getVehicle() != null) best[0] = Math.max(best[0], Math.sqrt(w.getVehicle().distanceToSqr(h)));
            return h == null || !h.isAlive();
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity w = get(l, B);
            CavalryChargeGoal g = chargeGoal(w);
            log("L1 cavalry: husk dead=" + dead + " strikes=" + (g == null ? -1 : g.strikes) + " turns=" + (g == null ? -1 : g.turns) + " farthest distance seen " + best[0] + " still mounted=" + w.isCavalryMounted());
            check(g != null && g.strikes >= 2, "the cavalryman rammed less than twice: " + (g == null ? "no goal" : g.strikes));
            check(g != null && g.turns >= 1, "the cavalryman never turned around for a second charge");
            check(dead, "the charging cavalryman did not kill the husk");
        });
        // a photo of the rider
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SettlerEntity w = get(l, B);
            var h = l.getEntity(ids[1]);
            if (h != null) h.discard();
            w.setTarget(null);
            w.getNavigation().stop();
            p.teleportTo(l, w.getX() + 0.5, w.getY() + 1.6, w.getZ() - 5.5, Set.of(), 0f, 10f, false);
        });
        sp.getConnection().waitForChunksRender();
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {   // face the rider
            ServerPlayer p = player(s);
            SettlerEntity w = get(s.overworld(), B);
            double dx = w.getX() - p.getX(), dy = (w.getY() + 1.6) - p.getEyeY(), dz = w.getZ() - p.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz))), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            p.teleportTo(s.overworld(), p.getX(), p.getY(), p.getZ(), Set.of(), yaw, pitch, false);
        });
        ctx.waitTicks(6);
        ctx.takeScreenshot("hh_l1_cavalryman_on_horse");
        // off duty: he gets off the horse and the horse is an ordinary horse again
        setTime(sp, 12500);
        boolean off = poll(ctx, sp, 20, 20, s -> !get(s.overworld(), B).isPassenger());
        sp.getServer().runOnServer(s -> {
            Horse horse = (Horse) s.overworld().getEntity(ids[0]);
            log("L1 cavalry: dismounted at the end of the shift=" + off + " horse tag cleared=" + !horse.entityTags().contains(Cavalry.TAG));
            check(off && !horse.entityTags().contains(Cavalry.TAG), "the cavalryman did not get off when the shift ended");
            horse.discard();
            SettlerEntity w = get(s.overworld(), B);
            w.setProfession(Professions.UNEMPLOYED);
            for (UUID id : new UUID[]{A, C, VICTIM}) get(s.overworld(), id).setProfession(Professions.UNEMPLOYED);
        });
        setTime(sp, 5000);

        log("L1 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave L1 failed: " + FAILS);
    }
}
