package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.jobs.LumberjackJob;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave K2: the civilian workday (sunrise until ~15:00, guards keep their shifts), the end-of-shift unload (chest by the work site, else the
 * warehouse; tool/weapon, armour and a couple of food items stay; once per workday), the miner's pace (2x slower at level 1, the old pace at
 * level 5) and the lumberjack picking up every drop again. Run alone with HH_ONLY_K2=1.
 */
final class WaveK2Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static UUID LUMBER, OTHER;

    private WaveK2Test() {}

    private static void fail(String m) { FAILS.add(m); log("K2 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static int inPockets(SettlerEntity e, Item item) { return e.count(st -> st.is(item)); }

    /** What a shift-end unload must leave: the tool or weapon, the armour, at most two food items, no junk. */
    private static void fillPockets(SettlerEntity e, Item toolOrWeapon) {
        e.inventory().clearContent();
        e.inventory().addItem(new ItemStack(toolOrWeapon));
        e.inventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        e.inventory().addItem(new ItemStack(Items.COBBLESTONE, 40));
        e.inventory().addItem(new ItemStack(Items.DIAMOND, 2));
        e.inventory().addItem(new ItemStack(Items.OAK_LOG, 20));
        e.inventory().addItem(new ItemStack(Items.ARROW, 16));
        e.inventory().addItem(new ItemStack(Items.BREAD, 5));
    }

    private static boolean pocketsLight(SettlerEntity e, Item toolOrWeapon) {
        return inPockets(e, Items.COBBLESTONE) == 0 && inPockets(e, Items.DIAMOND) == 0 && inPockets(e, Items.OAK_LOG) == 0 && inPockets(e, Items.ARROW) == 0
                && inPockets(e, Items.BREAD) <= Balance.SHIFT_END_KEEP_FOOD && inPockets(e, toolOrWeapon) == 1 && inPockets(e, Items.IRON_CHESTPLATE) == 1;
    }

    private static int near(ServerLevel l, BlockPos site, Item item) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-1, -1, -1), site.offset(1, 1, 1)))
            if (l.getBlockEntity(p) instanceof Container c) n += Storage.count(List.of(c), st -> st.is(item));
        return n;
    }

    private static int warehouse(MinecraftServer s, Item item) {
        return Storage.count(Storage.resolve(s.overworld(), town(s).storagePositions()), st -> st.is(item));
    }

    private static void setTime(TestSingleplayerContext sp, int t) { sp.getServer().runCommand("time set " + t); }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("weather clear");
        setTime(sp, 5000);

        // ---- 1. the workday: civilians 0..9000, leisure until the evening sleep, guards unchanged
        sp.getServer().runOnServer(s -> {
            List<SettlerEntity> all = settlers(s);
            SettlerEntity lj = all.stream().filter(e -> e.professionId().equals(Professions.LUMBERJACK.id())).findFirst().orElse(all.get(0));
            lj.setProfession(Professions.LUMBERJACK);
            LUMBER = lj.getUUID();
            OTHER = all.stream().filter(e -> e != lj).findFirst().orElseThrow().getUUID();
            log("K2 workday: start " + Balance.WORKDAY_START_TICK + " length " + Balance.workdayTicks() + " (" + Balance.WORKDAY_DAYLIGHT_PERCENT + "% of daylight)");
        });
        int[] workAt = {0, 100, 4500, 8999};
        int[] restAt = {9000, 10000, 11500, 12000, 23500, 23999};   // leisure: no work, not asleep (the late-evening limit is the night owl's 14500, so no 12600+)
        int[] sleepAt = {15000, 20000, 23000};
        for (int t : workAt) check(workState(sp, t, Professions.LUMBERJACK) == 1, "civilian must work at " + t);
        for (int t : restAt) check(workState(sp, t, Professions.LUMBERJACK) == 0, "civilian must be at leisure at " + t);
        for (int t : sleepAt) check(workState(sp, t, Professions.LUMBERJACK) == 2, "civilian must sleep at " + t);
        // guards keep their shifts (explicit "always": every waking hour, night window 3000..9000 asleep; explicit "day": until 11800), as before
        check(workState(sp, 12000, Professions.GUARD, SettlerEntity.SHIFT_ALWAYS) == 1, "guard (always) must work at 12000");
        check(workState(sp, 2000, Professions.GUARD, SettlerEntity.SHIFT_ALWAYS) == 1, "guard (always) must work at 2000");
        check(workState(sp, 5000, Professions.GUARD, SettlerEntity.SHIFT_ALWAYS) == 2, "guard (always) sleeps at 5000");
        check(workState(sp, 10000, Professions.GUARD, SettlerEntity.SHIFT_DAY) == 1, "a day-shift guard must still work at 10000 (until 11800)");
        check(workState(sp, 11900, Professions.GUARD, SettlerEntity.SHIFT_DAY) == 0, "a day-shift guard is off duty at 11900");
        log("K2 workday checked: civilians 0..8999 work, 9000.. leisure, 15000 sleep; guards unchanged");
        sp.getServer().runOnServer(s -> ((SettlerEntity) s.overworld().getEntity(LUMBER)).setProfession(Professions.LUMBERJACK));

        // ---- 2. leisure: after the workday no civilian runs the work activity
        setTime(sp, 10000);
        ctx.waitTicks(100);
        sp.getServer().runOnServer(s -> {
            StringBuilder sb = new StringBuilder();
            int working = 0;
            for (SettlerEntity e : settlers(s)) {
                if (e.isCombatant() || e.profession() == Professions.UNEMPLOYED) continue;
                if (e.brain().currentActivity().equals("WorkActivity")) working++;
                sb.append(e.professionId().getPath()).append('=').append(e.brain().currentActivity()).append(' ');
            }
            log("K2 leisure at 10000: " + sb);
            check(working == 0, "civilians still work after the workday: " + sb);
        });

        // the workday of the base test ended just now: let every shift-end trip finish first (a trip already under way keeps the target it chose)
        AtomicBoolean calm = new AtomicBoolean();
        for (int round = 0; round < 30 && !calm.get(); round++) {
            ctx.waitTicks(40);
            sp.getServer().runOnServer(s -> {
                boolean busy = false;
                for (SettlerEntity e : settlers(s)) if (e.brain().currentActivity().equals("ShiftEndActivity")) busy = true;
                calm.set(!busy);
            });
        }
        ctx.waitTicks(40);

        // ---- 3. unload at the end of the shift: the lumberjack hands everything to the chest by his forest marker
        BlockPos[] chestHolder = new BlockPos[1];
        int[] before = new int[3];
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            chestHolder[0] = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = FOREST.relative(d);
                if (l.getBlockState(p).isAir() && !l.getBlockState(p.below()).isAir()) { chestHolder[0] = p; break; }
            }
            if (chestHolder[0] == null) { fail("no free spot for the forest chest"); return; }
            l.setBlock(chestHolder[0], Blocks.CHEST.defaultBlockState(), 3);
            SettlerEntity e = (SettlerEntity) l.getEntity(LUMBER);
            e.markArrived();
            e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            fillPockets(e, Items.IRON_SWORD);   // a spare melee weapon counts as a tool/weapon to keep
            before[0] = near(l, FOREST, Items.COBBLESTONE);
            before[1] = warehouse(s, Items.COBBLESTONE);
            e.markWorked();
            String claimed = "?";
            try {
                var f = io.github.operagtx.hearthholm.entity.ai.jobs.SiteJob.class.getDeclaredField("sitePos");
                f.setAccessible(true);
                claimed = String.valueOf(f.get(e.brain().job()));
            } catch (ReflectiveOperationException ex) { claimed = ex.toString(); }
            log("K2 unload A: job=" + e.brain().job().getClass().getSimpleName() + " claimed site=" + claimed + " forest worker="
                    + (l.getBlockEntity(FOREST) instanceof io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity fw ? String.valueOf(fw.workerId()) : "no site") + " lumberjack=" + e.getUUID());
            log("K2 unload A: chest at " + chestHolder[0].toShortString() + " forest=" + FOREST.toShortString() + " near before=" + before[0] + " warehouse before=" + before[1]
                    + " lumberjack at " + e.blockPosition().toShortString());
        });
        setTime(sp, 9100);
        AtomicBoolean lightA = new AtomicBoolean();
        for (int round = 0; round < 40 && !lightA.get(); round++) {
            ctx.waitTicks(40);
            setTime(sp, 9100);
            sp.getServer().runOnServer(s -> {
                SettlerEntity e = (SettlerEntity) s.overworld().getEntity(LUMBER);
                if (pocketsLight(e, Items.IRON_SWORD)) lightA.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity e = (SettlerEntity) l.getEntity(LUMBER);
            int cobbleNear = near(l, FOREST, Items.COBBLESTONE) - before[0], cobbleWh = warehouse(s, Items.COBBLESTONE) - before[1];
            log("K2 unload A: light=" + lightA.get() + " cobble to the forest chest +" + cobbleNear + " to the warehouse +" + cobbleWh + " pockets bread=" + inPockets(e, Items.BREAD)
                    + " sword=" + inPockets(e, Items.IRON_SWORD) + " chestplate=" + inPockets(e, Items.IRON_CHESTPLATE) + " status=" + e.status().getString());
            check(lightA.get(), "the lumberjack did not unload at the end of the shift");
            check(cobbleNear == 40, "the junk did not go to the chest by the work site (+" + cobbleNear + ", warehouse +" + cobbleWh + ")");
            check(near(l, FOREST, Items.DIAMOND) >= 2 && near(l, FOREST, Items.OAK_LOG) >= 20 && near(l, FOREST, Items.BREAD) >= 3, "not everything reached the chest by the work site");
            check(e.getMainHandItem().is(Items.IRON_AXE), "the held tool was taken away");
        });
        ctx.waitTicks(60);   // the activity notices the light pockets and ends
        // once per workday: what he picks up during the evening stays with him until the next shift
        sp.getServer().runOnServer(s -> ((SettlerEntity) s.overworld().getEntity(LUMBER)).inventory().addItem(new ItemStack(Items.COBBLESTONE, 7)));
        ctx.waitTicks(200);
        setTime(sp, 9100);
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = (SettlerEntity) s.overworld().getEntity(LUMBER);
            log("K2 unload once: evening pick-up still in pockets=" + inPockets(e, Items.COBBLESTONE));
            check(inPockets(e, Items.COBBLESTONE) == 7, "the unload repeated within the same evening");
        });

        // ---- 4. no chest by a work site: a hauler hands everything to the warehouse
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity e = (SettlerEntity) l.getEntity(OTHER);
            e.setProfession(Professions.HAULER);
            e.markArrived();
            e.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            fillPockets(e, Items.IRON_SWORD);
            before[2] = warehouse(s, Items.COBBLESTONE);
            e.markWorked();
            log("K2 unload B: warehouse cobble before=" + before[2] + " hauler at " + e.blockPosition().toShortString());
        });
        setTime(sp, 9100);
        AtomicBoolean lightB = new AtomicBoolean();
        for (int round = 0; round < 40 && !lightB.get(); round++) {
            ctx.waitTicks(40);
            setTime(sp, 9100);
            sp.getServer().runOnServer(s -> {
                SettlerEntity e = (SettlerEntity) s.overworld().getEntity(OTHER);
                if (pocketsLight(e, Items.IRON_SWORD)) lightB.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = (SettlerEntity) s.overworld().getEntity(OTHER);
            int cobbleWh = warehouse(s, Items.COBBLESTONE) - before[2];
            log("K2 unload B: light=" + lightB.get() + " warehouse +" + cobbleWh + " pockets bread=" + inPockets(e, Items.BREAD) + " status=" + e.status().getString());
            check(lightB.get(), "the hauler did not unload at the end of the shift");
            check(cobbleWh == 40, "the junk did not reach the warehouse (+" + cobbleWh + ")");
        });

        // ---- 5. the miner's pace: 2x slower at level 1, the old pace at level 5; other professions unaffected
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity m = (SettlerEntity) l.getEntity(OTHER);
            m.setProfession(Professions.MINER);
            m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);   // bare hand on stone: slow enough that the 4-tick minimum never hides the factor
            BlockPos at = m.blockPosition();
            var stone = Blocks.STONE.defaultBlockState();
            int[] now = new int[5], old = new int[5];
            float[] factor = new float[5];
            int slow = Balance.MINER_SLOW_PERCENT_AT_LEVEL1;
            for (int lvl = 1; lvl <= 5; lvl++) {
                m.setXp(Professions.MINER.id(), Balance.LEVEL_XP[lvl - 1]);
                check(m.level(Professions.MINER.id()) == lvl, "level " + lvl + " not reached");
                factor[lvl - 1] = m.minerSlowdown();
                now[lvl - 1] = m.breakTicks(stone, at);
            }
            Balance.MINER_SLOW_PERCENT_AT_LEVEL1 = 100;   // the same formula without the K2 slowdown = the old pace
            try {
                for (int lvl = 1; lvl <= 5; lvl++) {
                    m.setXp(Professions.MINER.id(), Balance.LEVEL_XP[lvl - 1]);
                    old[lvl - 1] = m.breakTicks(stone, at);
                }
            } finally {
                Balance.MINER_SLOW_PERCENT_AT_LEVEL1 = slow;
            }
            log("K2 miner pace: factor=" + Arrays.toString(factor) + " ticks per stone now=" + Arrays.toString(now) + " old=" + Arrays.toString(old));
            check(Math.abs(factor[0] - 2f) < 0.01f && Math.abs(factor[4] - 1f) < 0.01f && factor[1] < factor[0] && factor[2] < factor[1] && factor[3] < factor[2], "miner factor is not 2.0 falling to 1.0: " + Arrays.toString(factor));
            check(Math.abs(now[0] - 2 * old[0]) <= 1, "level 1 miner is not 2x slower: " + now[0] + " vs old " + old[0]);
            check(now[4] == old[4], "level 5 miner does not reach the old pace: " + now[4] + " vs old " + old[4]);
            check(now[1] > old[1] && now[2] > old[2] && now[3] > old[3], "levels 2-4 are not slower than the old pace");
            // the builder clearing a plot (and anybody else) digs at the old pace
            m.setProfession(Professions.LUMBERJACK);
            check(m.minerSlowdown() == 1f, "a non-miner is slowed down");
            m.setProfession(Professions.UNEMPLOYED);
        });

        // ---- 6. the lumberjack picks up every drop again (the J1 filter is reverted)
        sp.getServer().runOnServer(s -> {
            boolean all = LumberjackJob.wanted(new ItemStack(Items.DIAMOND)) && LumberjackJob.wanted(new ItemStack(Items.COBBLESTONE))
                    && LumberjackJob.wanted(new ItemStack(Items.ROTTEN_FLESH)) && LumberjackJob.wanted(new ItemStack(Items.OAK_LOG)) && !LumberjackJob.wanted(ItemStack.EMPTY);
            log("K2 lumberjack sweep takes everything again: " + all);
            check(all, "the lumberjack sweep is still filtered");
        });

        log("K2 RESULT: " + (FAILS.isEmpty() ? "OK" : FAILS.size() + " failures " + FAILS));
        if (!FAILS.isEmpty()) throw new AssertionError("Wave K2 failed: " + FAILS);
    }

    /** 1 = works, 2 = sleeps, 0 = leisure, for the first lumberjack/guard settler at the given time of day. */
    private static int workState(TestSingleplayerContext sp, int t, io.github.operagtx.hearthholm.core.Profession p) {
        return workState(sp, t, p, SettlerEntity.SHIFT_POST);
    }

    private static int workState(TestSingleplayerContext sp, int t, io.github.operagtx.hearthholm.core.Profession p, int shift) {
        int[] r = new int[1];
        setTime(sp, t);
        sp.getServer().runOnServer(s -> {
            SettlerEntity e = (SettlerEntity) s.overworld().getEntity(p == Professions.LUMBERJACK ? LUMBER : OTHER);
            if (p != Professions.LUMBERJACK) { e.setProfession(p); e.setShift(shift); }
            r[0] = e.isSleepTime() ? 2 : e.isWorkTime() ? 1 : 0;
            if (p != Professions.LUMBERJACK) { e.setShift(SettlerEntity.SHIFT_POST); e.setProfession(Professions.UNEMPLOYED); }
        });
        return r[0];
    }
}
