package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.api.ProfessionModule;
import io.github.operagtx.hearthholm.api.ProfessionModules;
import io.github.operagtx.hearthholm.client.screen.SettlerInventoryScreen;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.jobs.LumberjackJob;
import io.github.operagtx.hearthholm.menu.SettlerMenu;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/**
 * Wave J1: the settler inventory screen (menu rules, shift-click routing, pause and resume, closing), the I2 leftovers
 * (archer retreat, the explicit "Always" shift, the lumberjack's sweep filter) and the ProfessionModule registry.
 * Run alone (plus the base) with HH_ONLY_J1=1.
 */
final class WaveJ1Test {
    private static final List<String> FAILS = new ArrayList<>();
    private static UUID MINER, OTHER, ARCHER, GUARD;
    private static BlockPos M;

    private WaveJ1Test() {}

    private static void fail(String m) { FAILS.add(m); log("J1 FAIL " + m); }

    private static void check(boolean ok, String what) { if (!ok) fail(what); }

    private static SettlerEntity get(MinecraftServer s, UUID id) { return (SettlerEntity) s.overworld().getEntity(id); }

    private static SettlerMenu menu(ServerPlayer p) { return p.containerMenu instanceof SettlerMenu m ? m : null; }

    private static void click(ServerPlayer p, int slot) { p.containerMenu.clicked(slot, 0, ContainerInput.QUICK_MOVE, p); }

    private static int has(net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.item.Item it) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(it)) n += inv.getItem(i).getCount();
        return n;
    }

    private static void remove(MinecraftServer s, java.util.function.Predicate<ItemStack> f) {
        for (Container c : Storage.resolve(s.overworld(), town(s).storagePositions()))
            for (int i = 0; i < c.getContainerSize(); i++) if (f.test(c.getItem(i))) c.setItem(i, ItemStack.EMPTY);
    }

    private static void setOwner(Settlement st, UUID id) {
        try {
            var f = Settlement.class.getField("owner");
            f.setAccessible(true);
            f.set(st, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setLang(ClientGameTestContext ctx, String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
        });
        var fut = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
        ctx.waitFor(mc -> fut.isDone(), 2400);
        ctx.waitTicks(120);
    }

    private static void setWindow(ClientGameTestContext ctx, int w, int h) {
        ctx.getInput().resizeWindow(w, h);
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.waitTicks(8);
    }

    private static net.minecraft.world.entity.Mob zombie(ServerLevel l, BlockPos at) {
        var z = EntityTypes.ZOMBIE.spawn(l, at, EntitySpawnReason.COMMAND);
        z.setNoAi(true);
        z.setPersistenceRequired();
        for (EquipmentSlot sl : EquipmentSlot.values()) z.setItemSlot(sl, ItemStack.EMPTY);
        return z;
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runCommand("weather clear");
        int platformY = O.getY() + 60;
        M = O.offset(20, 0, 32).atY(platformY);
        AtomicReference<net.minecraft.world.phys.Vec3> pos0 = new AtomicReference<>();
        AtomicInteger progress0 = new AtomicInteger();

        // ---- 0. set-up: a tunnel to dig on a stone platform in the sky, the miner without any pickaxe, the stranger rules
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, DESK.getX() + 0.5, DESK.getY() + 1, DESK.getZ() - 1.5, Set.of(), 0f, 10f, false);
            topUp(s, 7);
            Settlement st = town(s);
            List<SettlerEntity> all = settlers(s);
            SettlerEntity miner = all.get(0), other = all.get(1), archer = all.get(2), guard = all.get(3);
            for (SettlerEntity e : all) if (e != miner && e != other && e != archer && e != guard) e.setProfession(Professions.UNEMPLOYED);
            MINER = miner.getUUID(); OTHER = other.getUUID(); ARCHER = archer.getUUID(); GUARD = guard.getUUID();
            other.setProfession(Professions.UNEMPLOYED);
            for (int dx = -8; dx <= 8; dx++) for (int dz = -6; dz <= 14; dz++) {
                for (int dy = -2; dy <= 5; dy++) l.setBlock(M.offset(dx, dy, dz), (dy < 0 || (dz > 0 && dy <= 3 && dx >= -6 && dx <= 5)) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            l.setBlock(M, ModBlocks.MINE_MARKER.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkSitePlaced(l, M, p);
            l.setBlock(M.east(), Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(M.east()) instanceof Container c) c.setItem(0, new ItemStack(Items.TORCH, 8));
            WorkSiteBlockEntity mw = (WorkSiteBlockEntity) l.getBlockEntity(M);
            mw.setOption("mode", 2);
            mw.setOption("length", 8);
            mw.setOption("branches", 0);
            mw.setOption("torches", 0);
            mw.setProgress(0);
            Balance.BREAK_PACE_PERCENT = 60;
            remove(s, x -> x.is(net.minecraft.tags.ItemTags.PICKAXES));
            miner.setProfession(Professions.MINER);
            miner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            miner.inventory().clearContent();
            miner.markArrived();
            miner.teleportTo(M.getX() + 0.5, M.getY(), M.getZ() - 1.5);
            log("J1 set-up: miner " + miner.getName().getString() + " at " + M.toShortString() + " sites=" + st.sitesOf(WorkSiteTypes.MINE_ID).size());
        });
        ctx.waitTicks(10);
        sp.getServer().runCommand("gamemode survival @a");

        // ---- 1. who may open it
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SettlerEntity a = get(s, MINER);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            p.getInventory().setItem(1, new ItemStack(Items.IRON_HELMET));
            p.getInventory().setItem(2, new ItemStack(Items.STONE_PICKAXE));
            p.getInventory().setItem(3, new ItemStack(Items.DIRT, 10));
            p.getInventory().setItem(4, new ItemStack(Items.SHIELD));
            p.getInventory().setItem(5, new ItemStack(Items.IRON_CHESTPLATE));
            p.getInventory().setItem(6, new ItemStack(Items.DIAMOND, 3));
            p.teleportTo(l, M.getX() + 2.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            Settlement st = town(s);
            UUID real = st.owner;
            setOwner(st, UUID.randomUUID());
            boolean stranger = SettlementService.openInventory(p, a);
            setOwner(st, real);
            log("J1 stranger opened=" + stranger + " menu=" + p.containerMenu.getClass().getSimpleName());
            check(!stranger && menu(p) == null, "a stranger could open the settler inventory");
            p.teleportTo(l, M.getX() + 12.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            boolean far = SettlementService.openInventory(p, a);
            log("J1 owner from 12 blocks opened=" + far);
            check(!far && menu(p) == null, "inventory opened from too far away");
            p.teleportTo(l, M.getX() + 2.5, M.getY(), M.getZ() - 3.5, Set.of(), 0f, 10f, false);
            boolean owner = SettlementService.openInventory(p, a);
            log("J1 owner opened=" + owner + " menu=" + p.containerMenu.getClass().getSimpleName() + " slots=" + p.containerMenu.slots.size());
            check(owner && menu(p) != null && menu(p).slots.size() == 18 + 6 + 36, "owner could not open the settler inventory");
        });

        // ---- 2. shift-click routing and taking things out (all validated by the server menu)
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            SettlerEntity a = get(s, MINER);
            check(menu(p) != null, "menu closed too early");
            if (menu(p) == null) return;
            // hotbar slot i is menu slot 51 + i
            click(p, 51);   // iron pickaxe -> empty main hand
            check(a.getMainHandItem().is(Items.IRON_PICKAXE), "iron pickaxe did not go to the main hand: " + a.getMainHandItem());
            click(p, 53);   // stone pickaxe -> hand is taken: pockets
            check(a.getMainHandItem().is(Items.IRON_PICKAXE) && a.count(x -> x.is(Items.STONE_PICKAXE)) == 1, "second pickaxe should go to the pockets");
            click(p, 52);   // helmet
            check(a.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "helmet did not go to the head slot");
            click(p, 56);   // chestplate
            check(a.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "chestplate did not go to the chest slot");
            click(p, 55);   // shield -> off hand
            check(a.getOffhandItem().is(Items.SHIELD), "shield did not go to the off hand");
            click(p, 54);   // dirt -> pockets
            check(a.count(x -> x.is(Items.DIRT)) == 10, "dirt did not go to the pockets: " + a.count(x -> x.is(Items.DIRT)));
            click(p, 57);   // diamonds -> pockets
            log("J1 shift-click in: hand=" + a.getMainHandItem().getItem() + " head=" + a.getItemBySlot(EquipmentSlot.HEAD).getItem() + " chest=" + a.getItemBySlot(EquipmentSlot.CHEST).getItem()
                    + " off=" + a.getOffhandItem().getItem() + " pockets: dirt=" + a.count(x -> x.is(Items.DIRT)) + " diamond=" + a.count(x -> x.is(Items.DIAMOND)) + " stonepick=" + a.count(x -> x.is(Items.STONE_PICKAXE)));
            // armour in a taken slot goes to the pockets, the wrong item cannot be dropped into an armour slot
            p.getInventory().setItem(7, new ItemStack(Items.DIAMOND_HELMET));
            click(p, 58);
            check(a.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && a.count(x -> x.is(Items.DIAMOND_HELMET)) == 1, "second helmet should wait in the pockets");
            p.containerMenu.setCarried(new ItemStack(Items.DIRT, 4));
            p.containerMenu.clicked(SettlerMenu.EQUIP_START + 3, 0, ContainerInput.PICKUP, p);   // boots slot
            check(a.getItemBySlot(EquipmentSlot.FEET).isEmpty() && p.containerMenu.getCarried().getCount() == 4, "dirt was accepted into the boots slot");
            p.containerMenu.setCarried(ItemStack.EMPTY);
            // take out: diamonds and the chestplate leave the settler
            int di = -1;
            for (int i = 0; i < 18; i++) if (a.inventory().getItem(i).is(Items.DIAMOND)) di = i;
            click(p, di);
            click(p, SettlerMenu.EQUIP_START + 1);
            log("J1 taken out: settler diamonds=" + a.count(x -> x.is(Items.DIAMOND)) + " chest=" + a.getItemBySlot(EquipmentSlot.CHEST).getItem() + " player diamonds=" + has(p.getInventory(), Items.DIAMOND)
                    + " chestplate=" + has(p.getInventory(), Items.IRON_CHESTPLATE));
            check(a.count(x -> x.is(Items.DIAMOND)) == 0 && has(p.getInventory(), Items.DIAMOND) == 3, "diamonds were not taken out");
            check(a.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && has(p.getInventory(), Items.IRON_CHESTPLATE) == 1, "chestplate was not taken out");
            SettlerMenu m = menu(p);
            log("J1 pocket slot ok: " + (m.slots.get(0).container == a.inventory()) + " (the real container of the settler)");
            check(m.slots.get(0).container == a.inventory(), "pockets are not the settler's real container");
        });

        // ---- 3. while it is open the settler stands still and looks at the player; the job does not run
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, MINER);
            pos0.set(a.position());
            progress0.set(((WorkSiteBlockEntity) s.overworld().getBlockEntity(M)).progress());
        });
        ctx.waitTicks(80);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            SettlerEntity a = get(s, MINER);
            WorkSiteBlockEntity mw = (WorkSiteBlockEntity) s.overworld().getBlockEntity(M);
            double moved = a.position().distanceTo(pos0.get());
            double dx = p.getX() - a.getX(), dz = p.getZ() - a.getZ();
            float want = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
            float off = Math.abs(Mth.wrapDegrees(a.getYHeadRot() - want));
            log("J1 paused: moved=" + String.format("%.2f", moved) + " progress " + progress0.get() + " -> " + mw.progress() + " pickaxe damage=" + a.getMainHandItem().getDamageValue()
                    + " open=" + a.isInventoryOpen() + " look off by " + String.format("%.0f", off) + " deg status=" + a.status().getString());
            check(moved < 0.1, "settler moved while the inventory was open: " + moved);
            check(mw.progress() == progress0.get() && a.getMainHandItem().getDamageValue() == 0, "job ran while the inventory was open");
            check(a.isInventoryOpen(), "settler is not held");
            check(off < 25, "settler does not face the player: " + off);
            p.closeContainer();
        });
        AtomicBoolean worked = new AtomicBoolean();
        for (int i = 0; i < 40 && !worked.get(); i++) {
            ctx.waitTicks(20);
            sp.getServer().runCommand("time set 5000");
            sp.getServer().runOnServer(s -> {
                SettlerEntity a = get(s, MINER);
                WorkSiteBlockEntity mw = (WorkSiteBlockEntity) s.overworld().getBlockEntity(M);
                if (mw.progress() > progress0.get() || a.getMainHandItem().getDamageValue() > 0) worked.set(true);
            });
        }
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, MINER);
            WorkSiteBlockEntity mw = (WorkSiteBlockEntity) s.overworld().getBlockEntity(M);
            log("J1 resumed: open=" + a.isInventoryOpen() + " progress=" + mw.progress() + " hand=" + a.getMainHandItem().getItem() + " damage=" + a.getMainHandItem().getDamageValue() + " status=" + a.status().getString());
            check(!a.isInventoryOpen(), "settler still held after closing");
            check(worked.get(), "miner did not use the pickaxe from the menu for the next block");
        });

        // ---- 4. closing: the player walks away; the settler is dismissed
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, M.getX() + 2.5, M.getY(), M.getZ() - 4.5, Set.of(), 0f, 10f, false);
            SettlerEntity a = get(s, MINER);
            a.teleportTo(M.getX() + 0.5, M.getY(), M.getZ() - 2.5);
            check(SettlementService.openInventory(p, a), "could not reopen the inventory");
            p.teleportTo(l, M.getX() + 16.5, M.getY(), M.getZ() - 4.5, Set.of(), 0f, 10f, false);
        });
        ctx.waitTicks(6);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            log("J1 player walked away: menu closed=" + (menu(p) == null));
            check(menu(p) == null, "menu stayed open after the player moved away");
            p.teleportTo(l, M.getX() + 2.5, M.getY(), M.getZ() - 4.5, Set.of(), 0f, 10f, false);
            SettlerEntity o = get(s, OTHER);
            o.teleportTo(M.getX() + 1.5, M.getY(), M.getZ() - 5.5);
            check(SettlementService.openInventory(p, o), "could not open the inventory of the second settler");
            o.leave(Component.literal("dismissed in the test"));
        });
        ctx.waitTicks(6);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            log("J1 settler dismissed: menu closed=" + (menu(p) == null));
            check(menu(p) == null, "menu stayed open after the settler was dismissed");
        });

        // ---- 5. archers without arrows: retreat to the post / warehouse without a melee weapon or under 50 % health
        AtomicInteger dist0 = new AtomicInteger();
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 6.5, O.getY() + 1, O.getZ() + 10.5, Set.of(), 0f, 10f, false);
            sp.getServer().runCommand("gamemode creative @a");
            SettlerEntity a = get(s, ARCHER);
            a.setProfession(Professions.ARCHER);
            a.markArrived();
            a.inventory().clearContent();
            a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            a.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            remove(s, x -> x.is(Items.ARROW));
            a.setHealth(a.getMaxHealth());
            a.teleportTo(O.getX() + 11.5, O.getY(), O.getZ() + 8.5);
            var z = zombie(l, new BlockPos(O.getX() + 17, O.getY(), O.getZ() + 8));
            a.setTarget(z);
            boolean w1 = a.wantsArcherRetreat();
            a.inventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            boolean w2 = a.wantsArcherRetreat();
            float full = a.getHealth();
            a.setHealth(a.getMaxHealth() * 0.4f);
            boolean w3 = a.wantsArcherRetreat();
            a.setHealth(full);
            a.inventory().clearContent();
            a.inventory().setItem(0, new ItemStack(Items.ARROW, 5));
            boolean w4 = a.wantsArcherRetreat();
            a.inventory().clearContent();
            log("J1 archer retreat rule: noMelee=" + w1 + " withSword=" + w2 + " withSwordHurt=" + w3 + " withArrows=" + w4);
            check(w1 && !w2 && w3 && !w4, "archer retreat rule wrong: " + w1 + "," + w2 + "," + w3 + "," + w4);
            BlockPos spot = a.archerRetreatSpot();
            check(spot != null, "no retreat spot");
            dist0.set((int) Math.sqrt(a.blockPosition().distSqr(spot)));
            a.setTarget(z);
        });
        ctx.waitTicks(40);
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, ARCHER);
            BlockPos spot = a.archerRetreatSpot();
            int d = (int) Math.sqrt(a.blockPosition().distSqr(spot));
            log("J1 archer without arrows and sword: retreating=" + a.isRetreating() + " status=" + a.status().getString() + " distance to the spot " + dist0.get() + " -> " + d);
            check(a.isRetreating(), "archer without a melee weapon did not retreat");
            check(d < dist0.get() || d <= 3, "archer did not move toward the spot");
            // with a sword and full health the archer fights as before
            a.setTarget(null);
            a.getNavigation().stop();
            a.inventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            a.teleportTo(O.getX() + 11.5, O.getY(), O.getZ() + 8.5);
        });
        ctx.waitTicks(40);   // the retreat goal stops (no target any more)
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity a = get(s, ARCHER);
            for (var z : l.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, a.getBoundingBox().inflate(40))) z.discard();
            a.setRetreating(false);
            var z2 = zombie(l, new BlockPos(O.getX() + 14, O.getY(), O.getZ() + 8));
            a.setTarget(z2);
        });
        ctx.waitTicks(30);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity a = get(s, ARCHER);
            log("J1 archer with a sword and full health: retreating=" + a.isRetreating() + " target=" + (a.getTarget() != null));
            check(!a.isRetreating(), "archer with a sword retreated at full health");
            a.setTarget(null);
            for (var z : l.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, a.getBoundingBox().inflate(40))) z.discard();
            a.inventory().clearContent();
        });

        // ---- 6. an explicit "Always" shift overrides the post
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            BlockPos post = O.offset(4, 0, -3);
            if (!l.getBlockState(post).is(ModBlocks.PATROL_POST)) {
                l.setBlock(post, ModBlocks.PATROL_POST.defaultBlockState().setValue(WorkSiteBlock.FACING, Direction.SOUTH), 3);
                SettlementService.onWorkSitePlaced(l, post, p);
                town(s).rescan(l);
            }
            ((WorkSiteBlockEntity) l.getBlockEntity(post)).setOption("shift", 1);   // the post says: day shift
            SettlerEntity g = get(s, GUARD);
            g.setProfession(Professions.GUARD);
            g.markArrived();
            g.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 2.5);
            g.setShift(SettlerEntity.SHIFT_POST);
        });
        ctx.waitTicks(110);   // the settler caches the post's shift for 100 ticks
        sp.getServer().runCommand("time set 18000");
        ctx.waitTicks(4);
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            boolean postDuty = g.isOnDuty();
            int postEff = g.effectiveShift();
            g.setShift(SettlerEntity.SHIFT_ALWAYS);
            boolean alwaysDuty = g.isOnDuty();
            int alwaysEff = g.effectiveShift();
            log("J1 shift at midnight: post default effective=" + postEff + " onDuty=" + postDuty + "; always effective=" + alwaysEff + " onDuty=" + alwaysDuty);
            check(postEff == SettlerEntity.SHIFT_DAY && !postDuty, "post default did not follow the day-shift post");
            check(alwaysEff == SettlerEntity.SHIFT_POST && alwaysDuty, "always did not override the post");
            var l = (ServerLevel) g.level();
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess());
            g.saveWithoutId(out);
            SettlerEntity copy = io.github.operagtx.hearthholm.registry.ModEntities.SETTLER.create(l, EntitySpawnReason.LOAD);
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess(), out.buildResult()));
            log("J1 shift saved: " + copy.shift() + " (expected " + SettlerEntity.SHIFT_ALWAYS + ")");
            check(copy.shift() == SettlerEntity.SHIFT_ALWAYS, "always not saved");
            copy.discard();
            g.setShift(SettlerEntity.SHIFT_POST);
        });
        sp.getServer().runCommand("time set 5000");

        // ---- 7. lumberjack sweep filter and the profession modules
        sp.getServer().runOnServer(s -> {
            boolean ok = LumberjackJob.wanted(new ItemStack(Items.OAK_LOG)) && LumberjackJob.wanted(new ItemStack(Items.OAK_SAPLING)) && LumberjackJob.wanted(new ItemStack(Items.STICK))
                    && LumberjackJob.wanted(new ItemStack(Items.APPLE)) && LumberjackJob.wanted(new ItemStack(Items.OAK_PLANKS));
            boolean no = LumberjackJob.wanted(new ItemStack(Items.DIAMOND)) || LumberjackJob.wanted(new ItemStack(Items.COBBLESTONE)) || LumberjackJob.wanted(new ItemStack(Items.ROTTEN_FLESH))
                    || LumberjackJob.wanted(ItemStack.EMPTY);
            log("J1 lumberjack sweep: takes wood/sapling/stick/apple=" + ok + " ignores diamond/cobble/flesh=" + !no);
            check(ok && !no, "lumberjack sweep filter wrong");
            int n = 0, withSite = 0;
            for (Profession p : Professions.REGISTRY.values()) {
                ProfessionModule m = ProfessionModules.REGISTRY.get(p.id()).orElse(null);
                if (m == null) { fail("no module for " + p.id()); continue; }
                n++;
                if (m.workSite() != null) { withSite++; check(m.zone() != null && m.zone().kind() == ProfessionModule.ZoneData.Kind.BOX, "zone of " + p.id()); }
                check(m.jobFactory() != null && m.screenOpener() != null && m.profession() == p, "module of " + p.id());
            }
            ProfessionModule mm = ProfessionModules.of(Professions.MINER);
            log("J1 profession modules: " + n + "/" + Professions.REGISTRY.size() + " with site " + withSite + "; miner site=" + mm.workSite().id() + " zone=" + mm.zone().kind());
            check(n == Professions.REGISTRY.size() && mm.workSite().id().equals(WorkSiteTypes.MINE_ID), "module registry incomplete");
            check(ProfessionModules.of(Professions.UNEMPLOYED).workSite() == null, "unemployed has a site?");
            SettlementService.openWorkSite(player(s), M);   // the site screen opens through the module
        });
        ctx.waitForScreen(WorkSiteScreen.class);
        ctx.setScreen(() -> null);

        // ---- 8. screenshots of the inventory screen: RU 1280x720 and 854x480
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            SettlerEntity a = get(s, MINER);
            a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            a.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            a.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            a.inventory().clearContent();
            a.inventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));
            a.inventory().setItem(1, new ItemStack(Items.TORCH, 12));
            a.inventory().setItem(2, new ItemStack(Items.BREAD, 5));
            a.inventory().setItem(3, new ItemStack(Items.COBBLESTONE, 64));
            a.inventory().setItem(10, new ItemStack(Items.COAL, 17));
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            p.getInventory().setItem(1, new ItemStack(Items.BREAD, 12));
            p.getInventory().setItem(10, new ItemStack(Items.DIAMOND, 3));
            p.teleportTo(l, a.getX() + 2.0, a.getY(), a.getZ() - 1.0, Set.of(), 0f, 10f, false);
        });
        try {
            setLang(ctx, "ru_ru");
        } catch (Throwable t) {
            fail("could not switch the language: " + t);
        }
        for (int[] size : new int[][]{{1280, 720}, {854, 480}}) {
            setWindow(ctx, size[0], size[1]);
            sp.getServer().runOnServer(s -> check(SettlementService.openInventory(player(s), get(s, MINER)), "screen shot: menu did not open"));
            ctx.waitForScreen(SettlerInventoryScreen.class);
            ctx.waitTicks(6);
            ctx.takeScreenshot("hh_j1_inventory_ru_" + size[0] + "x" + size[1]);
            ctx.setScreen(() -> null);
            ctx.waitTicks(4);
        }
        setLang(ctx, "en_us");
        ctx.runOnClient(mc -> mc.options.guiScale().set(0));
        sp.getServer().runOnServer(s -> {
            Balance.BREAK_PACE_PERCENT = HearthholmClientTest.DEF_BREAK_PACE;
            player(s).closeContainer();
        });
        sp.getServer().runCommand("gamemode creative @a");
        if (!FAILS.isEmpty()) throw new AssertionError("wave J1 failed: " + FAILS);
        log("J1 OK");
    }
}
