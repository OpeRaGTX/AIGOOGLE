package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.party.Party;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/** Wave D checks: war horn party, archer, healer, salves, knight promotion, barracks and a balanced raid. */
final class WaveDTest {
    private static UUID GUARD, WARRIOR, ARCHER, HEALER, PATIENT, LUMBER;
    private static UUID TARGET;
    private static BlockPos HERB, RACK;
    private static int arrowsBefore;

    private WaveDTest() {}

    private static SettlerEntity get(MinecraftServer s, UUID id) {
        return (SettlerEntity) s.overworld().getEntity(id);
    }

    private static SettlerEntity byProf(MinecraftServer s, Profession p) {
        for (SettlerEntity e : settlers(s)) if (e.professionId().equals(p.id())) return e;
        return null;
    }

    private static final java.util.Set<UUID> USED = new java.util.HashSet<>();

    /** A settler of this profession not used by another check, or a free civilian turned into one. */
    private static SettlerEntity ensure(MinecraftServer s, Profession p, Profession... spare) {
        SettlerEntity e = null;
        List<Profession> order = new java.util.ArrayList<>();
        order.add(p);
        order.addAll(List.of(spare));
        for (Profession q : order) {
            for (SettlerEntity x : settlers(s)) if (x.professionId().equals(q.id()) && !USED.contains(x.getUUID()) && x.isAlive() && !x.isLeaving()) { e = x; break; }
            if (e != null) break;
        }
        if (e == null) for (SettlerEntity x : settlers(s)) if (!x.isCombatant() && !USED.contains(x.getUUID()) && x.isAlive() && !x.isLeaving()) { e = x; break; }
        if (e == null) for (SettlerEntity x : settlers(s)) if (!USED.contains(x.getUUID())) { e = x; break; }   // robust: casualties of earlier waves
        if (e == null) for (SettlerEntity x : settlers(s)) { e = x; break; }
        if (e == null) throw new AssertionError("no free settler for " + p.id());
        if (!e.professionId().equals(p.id())) e.setProfession(p);
        USED.add(e.getUUID());
        return e;
    }

    private static void gear(SettlerEntity e) {
        e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
    }

    private static Container warehouse(MinecraftServer s) {
        Settlement st = town(s);
        return (Container) s.overworld().getBlockEntity(st.storagePositions().getFirst());
    }

    private static void stockUp(MinecraftServer s, ItemStack... stacks) {
        ServerLevel l = s.overworld();
        Settlement st = town(s);
        for (ItemStack stack : stacks) {
            ItemStack rest = Storage.insert(Storage.resolve(l, st.storagePositions()), stack);
            if (!rest.isEmpty()) throw new AssertionError("warehouse full");
        }
    }

    private static void lookAt(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, java.util.Set.of(), yaw, pitch, false);
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        USED.clear();
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 10000");
        sp.getServer().runOnServer(s -> {
            topUp(s, 7);
            ServerLevel l = s.overworld();
            SettlerEntity guard = ensure(s, Professions.GUARD, Professions.HAULER);
            SettlerEntity warrior = ensure(s, Professions.WARRIOR, Professions.BUILDER);
            gear(guard);
            gear(warrior);
            GUARD = guard.getUUID();
            WARRIOR = warrior.getUUID();
            log("D combat settlers: guard hp=" + guard.getMaxHealth() + " atk=" + guard.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    + " armor=" + guard.getArmorValue() + " | warrior hp=" + warrior.getMaxHealth());
            for (SettlerEntity e : settlers(s)) { e.setHealth(e.getMaxHealth()); }
            // everybody at the hall, player in front of them
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 4.5, O.getY(), O.getZ() + 10.5, java.util.Set.of(), 0f, 5f, false);
            guard.teleportTo(O.getX() + 3.5, O.getY(), O.getZ() + 12.5);
            warrior.teleportTo(O.getX() + 5.5, O.getY(), O.getZ() + 12.5);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WAR_HORN));
        });
        ctx.waitTicks(20);

        // ---- 1. the war horn: party of 2 follows after a teleport, eats, fights, dismissed with a second horn
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            var r = ModItems.WAR_HORN.use(l, p, InteractionHand.MAIN_HAND);
            List<SettlerEntity> party = Party.members(s, p.getUUID());
            for (int attempt = 0; attempt < 3 && party.size() != 2; attempt++) {   // robust: a member that is not awake/near yet is fetched again
                log("PARTY retry " + attempt + " size=" + party.size());
                if (!party.isEmpty()) ModItems.WAR_HORN.use(l, p, InteractionHand.MAIN_HAND);   // second horn dismisses
                for (UUID id : List.of(GUARD, WARRIOR)) {
                    SettlerEntity m = l.getEntity(id) instanceof SettlerEntity x && x.isAlive() && !x.isLeaving() ? x : null;
                    log("PARTY member " + id.toString().substring(0, 4) + " state=" + (l.getEntity(id) instanceof SettlerEntity y ? y.professionId().getPath() + " alive=" + y.isAlive() + " leaving=" + y.isLeaving() + " arrived=" + y.hasArrived() + " inParty=" + y.inParty() + " settlement=" + (y.settlement() != null) : "missing"));
                    if (m == null && id.equals(WARRIOR)) {   // wave I2: slower defenders lose a fighter to the brute of wave C more often: replace him
                        m = ensure(s, Professions.WARRIOR, Professions.BUILDER, Professions.HAULER);
                        gear(m);
                        WARRIOR = m.getUUID();
                    }
                    if (m == null) continue;
                    m.markArrived();
                    m.stopSleeping();
                    m.teleportTo(p.getX() + 1.5, p.getY(), p.getZ() + 1.5);
                }
                ModItems.WAR_HORN.use(l, p, InteractionHand.MAIN_HAND);
                party = Party.members(s, p.getUUID());
            }
            log("PARTY called by horn use=" + r + " members=" + party.size() + " " + party.stream().map(e -> e.professionId().getPath()).toList());
            if (party.size() != 2) throw new AssertionError("expected a party of 2 (guard + warrior), got " + party.size());
            // hunger: one member eats from its own pockets
            SettlerEntity hungry = party.getFirst();
            hungry.inventory().addItem(new ItemStack(Items.BREAD, 4));
            hungry.setFood(3f);
            log("party hunger test: " + hungry.getName().getString() + " food=" + hungry.food());
        });
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            int x = O.getX() + 48, z = O.getZ() + 6;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            l.getChunkAt(new BlockPos(x, y, z));
            p.teleportTo(l, x + 0.5, y + 1, z + 0.5, java.util.Set.of(), 270f, 5f, false);
            log("player teleported 48 blocks away to " + p.blockPosition().toShortString());
        });
        ctx.waitTicks(90);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            List<SettlerEntity> party = Party.members(s, p.getUUID());
            int near = 0;
            for (SettlerEntity e : party) {
                double d = Math.sqrt(e.distanceToSqr(p));
                log("  party " + e.professionId().getPath() + " dist=" + String.format("%.1f", d) + " status='" + e.status().getString() + "' food=" + e.food()
                        + " act=" + e.brain().currentActivity());
                if (d < 14) near++;
            }
            log("PARTY follows after teleport: " + near + "/" + party.size());
            if (near != party.size()) throw new AssertionError("party did not follow the player");
            if (party.getFirst().food() <= 3.5f) throw new AssertionError("party member did not eat from its pockets");
            // a hostile in front: the party fights it
            ServerLevel l = s.overworld();
            var h = EntityTypes.HUSK.spawn(l, p.blockPosition().relative(Direction.WEST, 7), EntitySpawnReason.SPAWN_ITEM_USE);
            TARGET = h.getUUID();
            int k = 0;
            for (SettlerEntity e : party) {
                e.teleportTo(p.getX() + 6.0, p.getY(), p.getZ() + (k++ == 0 ? -1.5 : 1.5));
                e.getNavigation().stop();
            }
            p.teleportTo(l, p.getX(), p.getY(), p.getZ(), java.util.Set.of(), -90f, 6f, false);
        });
        ctx.waitTicks(12);
        ctx.takeScreenshot("hh_40_party_following");
        ctx.waitTicks(110);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var h = l.getEntity(TARGET);
            log("PARTY fight: husk " + (h == null || !h.isAlive() ? "dead" : "alive hp=" + ((LivingEntity) h).getHealth()));
            if (h != null && h.isAlive()) h.discard();
            ServerPlayer p = player(s);
            // saved on the settler: round trip through NBT keeps the party owner
            SettlerEntity e = Party.members(s, p.getUUID()).getFirst();
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess());
            e.saveWithoutId(out);
            SettlerEntity copy = io.github.operagtx.hearthholm.registry.ModEntities.SETTLER.create(l, EntitySpawnReason.LOAD);
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess(), out.buildResult()));
            log("PARTY saved on settler: owner after reload=" + copy.partyOwner() + " expected=" + p.getUUID());
            if (!p.getUUID().equals(copy.partyOwner())) throw new AssertionError("party owner not saved");
            copy.discard();
            // a second horn sends everybody home
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WAR_HORN));
            ModItems.WAR_HORN.use(l, p, InteractionHand.MAIN_HAND);
            log("PARTY after second horn: members=" + Party.members(s, p.getUUID()).size());
            if (!Party.members(s, p.getUUID()).isEmpty()) throw new AssertionError("party not dismissed");
            p.teleportTo(l, O.getX() + 4.5, O.getY(), O.getZ() + 10.5, java.util.Set.of(), 0f, 5f, false);
        });
        ctx.waitTicks(20);

        // ---- 2. archer: picks arrows from the warehouse, then shoots a target and uses them up
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            SettlerEntity a = ensure(s, Professions.ARCHER, Professions.LUMBERJACK, Professions.MINER);
            a.setProfession(Professions.ARCHER);
            a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            a.inventory().clearContent();
            a.setXp(Professions.ARCHER.id(), 0);
            ARCHER = a.getUUID();
            stockUp(s, new ItemStack(Items.ARROW, 40));
            a.teleportTo(O.getX() + 11.5, O.getY(), O.getZ() + 8.5);
            log("ARCHER set up: hp=" + a.getMaxHealth() + " arrowsInWarehouse=" + stock(s, Items.ARROW));
        });
        for (int i = 0; i < 6; i++) {
            ctx.waitTicks(100);
            boolean[] got = {false};
            sp.getServer().runOnServer(s -> {
                SettlerEntity a = get(s, ARCHER);
                log("  archer arrows=" + a.count(x -> x.is(Items.ARROW)) + " status='" + a.status().getString() + "' act=" + a.brain().currentActivity());
                got[0] = a.count(x -> x.is(Items.ARROW)) > 0;
            });
            if (got[0]) break;
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity a = get(s, ARCHER);
            arrowsBefore = a.count(x -> x.is(Items.ARROW));
            log("ARCHER fetched arrows from the warehouse: " + arrowsBefore);
            if (arrowsBefore == 0) throw new AssertionError("archer did not fetch arrows");
            a.teleportTo(O.getX() - 3.5, O.getY(), O.getZ() + 22.5);
            a.getNavigation().stop();
            var z = EntityTypes.HUSK.spawn(l, new BlockPos(O.getX() - 3, O.getY(), O.getZ() + 11), EntitySpawnReason.SPAWN_ITEM_USE);
            z.setNoAi(true);
            z.setPersistenceRequired();
            TARGET = z.getUUID();
            a.setTarget(z);
            ServerPlayer p = player(s);
            p.teleportTo(l, O.getX() + 0.5, O.getY() + 1, O.getZ() + 21.5, java.util.Set.of(), 90f, 8f, false);
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            SettlerEntity a = get(s, ARCHER);
            ServerPlayer p = player(s);
            p.teleportTo(s.overworld(), a.getX() - 4.5, a.getY() + 0.5, a.getZ(), java.util.Set.of(), -90f, 10f, false);
            lookAt(p, p.getX(), p.getY(), p.getZ(), a.getX(), a.getY() + 1.2, a.getZ());
        });
        for (int i = 0; i < 25; i++) {
            ctx.waitTicks(3);
            boolean[] drawing = {false};
            sp.getServer().runOnServer(s -> drawing[0] = get(s, ARCHER).isUsingItem() && get(s, ARCHER).getTicksUsingItem() > 8);
            if (drawing[0]) break;
        }
        ctx.takeScreenshot("hh_41_archer_shooting");
        ctx.waitTicks(200);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity a = get(s, ARCHER);
            var z = (LivingEntity) l.getEntity(TARGET);
            int used = Math.max(0, arrowsBefore - a.count(x -> x.is(Items.ARROW)));
            log("ARCHER shot: arrows used=" + used + " (stat " + town(s).stats.get("arrows_used") + ") remaining=" + a.count(x -> x.is(Items.ARROW))
                    + " target=" + (z == null || !z.isAlive() ? "dead" : "hp " + z.getHealth() + "/" + z.getMaxHealth()) + " dist="
                    + (z == null ? "-" : String.format("%.1f", Math.sqrt(a.distanceToSqr(z)))) + " lvl=" + a.level(Professions.ARCHER.id()));
            if (used == 0) throw new AssertionError("archer used no arrows");
            if (z != null && z.isAlive() && z.getHealth() >= z.getMaxHealth()) throw new AssertionError("archer arrows did not hit");
            if (z != null) z.discard();
            a.setTarget(null);
            a.teleportTo(O.getX() + 11.5, O.getY(), O.getZ() + 8.5);
        });

        // ---- 3. salves: with no healer around a hurt settler drinks one
        sp.getServer().runOnServer(s -> {
            SettlerEntity m = ensure(s, Professions.MINER, Professions.CARPENTER);
            m.teleportTo(O.getX() - 10.5, O.getY(), O.getZ() + 14.5);
            m.setHealth(Math.min(5f, m.getMaxHealth() * 0.2f));
            m.store(new ItemStack(ModItems.HEALING_SALVE));
            PATIENT = m.getUUID();
            log("SALVE test: hp=" + m.getHealth() + "/" + m.getMaxHealth() + " healerNear=" + io.github.operagtx.hearthholm.entity.ai.jobs.HealerJob.healerNear(m, 24));
        });
        ctx.waitTicks(60);
        sp.getServer().runOnServer(s -> {
            SettlerEntity m = get(s, PATIENT);
            log("SALVE used: hp=" + m.getHealth() + " salves left=" + m.count(x -> x.is(ModItems.HEALING_SALVE)) + " stat=" + town(s).stats.get("salves_used"));
            if (m.count(x -> x.is(ModItems.HEALING_SALVE)) != 0 || m.getHealth() < 10f) throw new AssertionError("salve was not used");
        });

        // ---- 4. healer: herbalist hut, heals the wounded and brews salves from warehouse herbs
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            var planks = Blocks.OAK_PLANKS.defaultBlockState();
            var spruce = Blocks.SPRUCE_PLANKS.defaultBlockState();
            box(l, 17, 0, -11, 23, 3, -6, planks, spruce);
            door(l, O.offset(20, 0, -6));
            HERB = O.offset(18, 0, -10);
            l.setBlock(HERB, ModBlocks.HERBALIST_TABLE.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.SOUTH), 3);
            l.setBlock(O.offset(20, 2, -10), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkshopPlaced(l, HERB, p);
            Settlement st = town(s);
            for (var b : st.buildings) if (b.is(io.github.operagtx.hearthholm.crafting.Workshop.HERBALISM.role()))
                log("HERBALIST building roles=" + b.roles() + " valid=" + b.valid() + " problem=" + b.problem() + " title=" + b.title().getString());
            stockUp(s, new ItemStack(Items.GLASS_BOTTLE, 4), new ItemStack(Items.SWEET_BERRIES, 12), new ItemStack(Items.DANDELION, 4));
            SettlerEntity h = ensure(s, Professions.HEALER, Professions.FARMER, Professions.CARPENTER);
            h.setProfession(Professions.HEALER);
            h.teleportTo(O.getX() + 20.5, O.getY(), O.getZ() - 4.5);
            HEALER = h.getUUID();
            // a wounded cook and a wounded guard in the open
            SettlerEntity cook = get(s, PATIENT);
            cook.teleportTo(O.getX() + 18.5, O.getY(), O.getZ() - 2.5);
            cook.setHealth(4f);
            LUMBER = cook.getUUID();
            log("HEALER set up: warehouse bottles=" + stock(s, Items.GLASS_BOTTLE) + " herbs=" + (stock(s, Items.SWEET_BERRIES) + stock(s, Items.DANDELION)) + " patient hp=" + cook.getHealth() + "/" + cook.getMaxHealth());
        });
        ctx.waitTicks(200);
        sp.getServer().runOnServer(s -> {
            SettlerEntity h = get(s, HEALER), c = get(s, LUMBER);
            log("HEALER after 10 s: healer status='" + h.status().getString() + "' patient hp=" + c.getHealth() + "/" + c.getMaxHealth() + " thought patched_up=" + c.hasThought("patched_up"));
            player(s).teleportTo(s.overworld(), O.getX() + 19.5, O.getY() + 1, O.getZ() - 1.5, java.util.Set.of(), 0f, 15f, false);
        });
        ctx.waitTicks(250);
        sp.getServer().runOnServer(s -> {
            SettlerEntity h = get(s, HEALER), c = get(s, LUMBER);
            Settlement st = town(s);
            log("HEALER healed: patient hp=" + c.getHealth() + "/" + c.getMaxHealth() + " healed stat=" + st.stats.get("healed") + " brewed=" + st.stats.get("brewed")
                    + " salvesInWarehouse=" + stock(s, ModItems.HEALING_SALVE) + " healer status='" + h.status().getString() + "' inv salves=" + h.count(x -> x.is(ModItems.HEALING_SALVE)));
            if (c.getHealth() < c.getMaxHealth() * 0.7f) throw new AssertionError("healer did not heal the settler");
        });
        for (int i = 0; i < 5; i++) {
            boolean[] ok = {false};
            sp.getServer().runOnServer(s -> ok[0] = town(s).stats.getOrDefault("brewed", 0) >= 2 && stock(s, ModItems.HEALING_SALVE) >= 1);
            if (ok[0]) break;
            ctx.waitTicks(150);
        }
        sp.getServer().runOnServer(s -> {
            Settlement st = town(s);
            SettlerEntity h = get(s, HEALER);
            for (SettlerEntity e : settlers(s)) if (e.getHealth() < e.getMaxHealth() * 0.8f)
                log("  hurt: " + e.getName().getString() + " " + e.professionId().getPath() + " hp=" + e.getHealth() + "/" + e.getMaxHealth() + " pos=" + e.blockPosition().toShortString()
                        + " act=" + e.brain().currentActivity() + " status='" + e.status().getString() + "' food=" + e.food() + " healerPos=" + h.blockPosition().toShortString());
            log("HEALER brewed: brewed=" + st.stats.get("brewed") + " salvesInWarehouse=" + stock(s, ModItems.HEALING_SALVE) + " healer inv salves=" + h.count(x -> x.is(ModItems.HEALING_SALVE))
                    + " status='" + h.status().getString() + "' bottles left=" + stock(s, Items.GLASS_BOTTLE));
            if (st.stats.getOrDefault("brewed", 0) < 1) throw new AssertionError("healer brewed nothing");
        });

        // ---- 5. promotion: a level 5 warrior becomes a knight through the settler screen
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity w = get(s, WARRIOR);
            w.setXp(Professions.WARRIOR.id(), io.github.operagtx.hearthholm.core.Balance.LEVEL_XP[4]);
            w.teleportTo(O.getX() + 6.5, O.getY(), O.getZ() + 11.5);
            w.setHealth(w.getMaxHealth());
            stockUp(s, new ItemStack(ModItems.HEARTH_MARK, 20), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
            log("KNIGHT before: level=" + w.level(Professions.WARRIOR.id()) + " hp=" + w.getMaxHealth() + " armor=" + w.getArmorValue() + " marks=" + stock(s, ModItems.HEARTH_MARK));
            SettlementService.openSettler(player(s), w);
        });
        ctx.waitForScreen(SettlerScreen.class);
        ctx.waitTicks(5);
        ctx.takeScreenshot("hh_42_settler_promote");
        ctx.clickScreenButton("gui.hearthholm.promote");
        ctx.waitTicks(10);
        ctx.setScreen(() -> null);
        sp.getServer().runOnServer(s -> {
            SettlerEntity w = get(s, WARRIOR);
            log("KNIGHT promoted: profession=" + w.professionId() + " hp=" + w.getMaxHealth() + " armor=" + w.getArmorValue() + " atk="
                    + w.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) + " marks left=" + stock(s, ModItems.HEARTH_MARK)
                    + " offhand=" + w.getOffhandItem().getItem() + " head=" + w.getItemBySlot(EquipmentSlot.HEAD).getItem());
            if (!w.isKnight()) throw new AssertionError("promotion failed");
            ServerPlayer p = player(s);
            ServerLevel l = s.overworld();
            w.getNavigation().stop();
            w.teleportTo(O.getX() + 8.5, O.getY(), O.getZ() + 14.5);
            w.setYRot(0f); w.yBodyRot = 0f; w.yHeadRot = 0f;   // faces south, towards the player
            p.teleportTo(l, O.getX() + 8.5, O.getY() + 1.2, O.getZ() + 18.5, java.util.Set.of(), 180f, 5f, false);
            lookAt(p, p.getX(), p.getY(), p.getZ(), w.getX(), w.getY() + 1.4, w.getZ());
            w.setNoAi(true);
        });
        ctx.waitTicks(20);
        ctx.takeScreenshot("hh_43_knight");
        // shield: a frontal hit hurts much less than one from behind
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity w = get(s, WARRIOR);
            var z = EntityTypes.HUSK.spawn(l, w.blockPosition().relative(Direction.SOUTH, 2), EntitySpawnReason.SPAWN_ITEM_USE);
            z.setNoAi(true);
            w.setHealth(w.getMaxHealth());
            float before = w.getHealth();
            w.hurtServer(l, l.damageSources().mobAttack(z), 10f);
            float front = before - w.getHealth();
            z.teleportTo(w.getX(), w.getY(), w.getZ() - 2);
            TARGET = z.getUUID();
            log("KNIGHT shield: frontal 10-dmg hit cost " + front);
            FRONT_COST = front;
        });
        ctx.waitTicks(25);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity w = get(s, WARRIOR);
            var z = (LivingEntity) l.getEntity(TARGET);
            w.setHealth(w.getMaxHealth());
            float before = w.getHealth();
            w.hurtServer(l, l.damageSources().mobAttack(z), 10f);
            float back = before - w.getHealth();
            log("KNIGHT shield: rear 10-dmg hit cost " + back + " vs frontal " + FRONT_COST);
            if (!(FRONT_COST < back * 0.6f)) throw new AssertionError("shield did not reduce frontal damage");
            z.discard();
            // taunt: a husk fighting someone else is pulled to the knight
            SettlerEntity civ = get(s, LUMBER);
            civ.teleportTo(w.getX() + 6, w.getY(), w.getZ());
            var t = EntityTypes.HUSK.spawn(l, w.blockPosition().offset(5, 0, 2), EntitySpawnReason.SPAWN_ITEM_USE);
            t.setTarget(civ);
            TARGET = t.getUUID();
            w.setNoAi(false);
        });
        ctx.waitTicks(50);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            var t = (net.minecraft.world.entity.Mob) l.getEntity(TARGET);
            log("KNIGHT taunt: husk target=" + (t.getTarget() == null ? "-" : t.getTarget().getName().getString()) + " taunts stat=" + town(s).stats.get("taunts"));
            if (town(s).stats.getOrDefault("taunts", 0) < 1) throw new AssertionError("knight did not taunt");
            t.discard();
        });

        // ---- 6. barracks: a hut with a weapon rack; a wounded fighter withdraws there and mends fast
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            var planks = Blocks.OAK_PLANKS.defaultBlockState();
            var spruce = Blocks.SPRUCE_PLANKS.defaultBlockState();
            box(l, 17, 0, -3, 24, 3, 3, planks, spruce);
            door(l, O.offset(20, 0, 3));
            RACK = O.offset(19, 0, -2);
            l.setBlock(RACK, ModBlocks.WEAPON_RACK.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.SOUTH), 3);
            l.setBlock(O.offset(22, 0, -2), ModBlocks.WEAPON_RACK.defaultBlockState().setValue(io.github.operagtx.hearthholm.block.HorizontalBlock.FACING, Direction.SOUTH), 3);
            l.setBlock(O.offset(20, 2, -2), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);
            SettlementService.onWorkshopPlaced(l, RACK, p);
            Settlement st = town(s);
            boolean found = false;
            for (var b : st.buildings) if (b.is(io.github.operagtx.hearthholm.crafting.Workshop.BARRACKS.role())) {
                found = b.valid();
                log("BARRACKS building roles=" + b.roles() + " valid=" + b.valid() + " problem=" + b.problem() + " racks=" + b.stations().size() + " title=" + b.title().getString());
            }
            if (!found) throw new AssertionError("barracks not recognised");
            SettlerEntity g = get(s, GUARD);
            g.setNoAi(false);
            g.teleportTo(O.getX() - 3.5, O.getY(), O.getZ() + 12.5);
            g.setHealth(g.getMaxHealth() * 0.25f);
            log("BARRACKS test: guard hp=" + g.getHealth() + "/" + g.getMaxHealth() + " retreatSpot=" + g.retreatSpot());
        });
        boolean[] rested = {false};
        for (int i = 0; i < 15 && !rested[0]; i++) {   // poll: with a healer around the guard can mend and leave quickly
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> rested[0] = get(s, GUARD).inBarracks());
        }
        ctx.waitTicks(200);   // time to mend
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            log("BARRACKS rest: guard hp=" + g.getHealth() + "/" + g.getMaxHealth() + " reachedBarracks=" + rested[0] + " status='" + g.status().getString()
                    + "' comrades=" + g.hasThought("comrades") + " pos=" + g.blockPosition().toShortString());
            if (!rested[0]) throw new AssertionError("wounded guard did not go to the barracks");
            if (g.getHealth() < g.getMaxHealth() * 0.4f) throw new AssertionError("no healing in the barracks");
        });
        ctx.waitTicks(400);   // the thought is checked on a slow cadence; give it room on a slow machine
        sp.getServer().runOnServer(s -> {
            SettlerEntity g = get(s, GUARD);
            log("BARRACKS mood: hp=" + g.getHealth() + "/" + g.getMaxHealth() + " comrades=" + g.hasThought("comrades") + " thoughts="
                    + g.thoughts().stream().map(t -> t.id() + t.value()).toList());
            if (!g.hasThought("comrades")) throw new AssertionError("no comrades thought in the barracks");
        });

        // ---- 7. a small raid against three defenders (knight, guard, archer) with a healer and a barracks
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            for (UUID id : new UUID[]{GUARD, WARRIOR, ARCHER}) {
                SettlerEntity e = get(s, id);
                e.setNoAi(false);
                e.setHealth(e.getMaxHealth());
                e.teleportTo(O.getX() + 1.5 + (id == GUARD ? 0 : id == WARRIOR ? 2 : 4), O.getY(), O.getZ() + 10.5);
            }
            SettlerEntity a = get(s, ARCHER);
            a.inventory().addItem(new ItemStack(Items.ARROW, 32));
            a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, new AABB(O).inflate(120))) e.discard();
            int deathsBefore = st.raid.deaths;
            if (st.raid.phase != io.github.operagtx.hearthholm.threat.RaidState.NONE) io.github.operagtx.hearthholm.threat.Threats.endRaid(l, st, false);
            io.github.operagtx.hearthholm.threat.Threats.forceRaid(l, st, 4, 100);
            log("SMALL RAID forced: defenders knight hp=" + get(s, WARRIOR).getMaxHealth() + " guard hp=" + get(s, GUARD).getMaxHealth() + " archer hp=" + a.getMaxHealth());
            player(s).teleportTo(l, O.getX() + 4.5, O.getY() + 6, O.getZ() + 3.5, java.util.Set.of(), 0f, 30f, false);
        });
        boolean[] done = {false};
        for (int i = 0; i < 12 && !done[0]; i++) {
            ctx.waitTicks(200);
            sp.getServer().runOnServer(s -> {
                Settlement st = town(s);
                ServerLevel l = s.overworld();
                log("small raid: alive=" + io.github.operagtx.hearthholm.threat.Threats.alive(l, st) + " killed=" + st.raid.killed + " deaths=" + st.raid.deaths
                        + " phase=" + st.raid.phase + " defenders hp: knight=" + String.format("%.0f", get(s, WARRIOR).getHealth()) + " guard=" + String.format("%.0f", get(s, GUARD).getHealth())
                        + " archer=" + String.format("%.0f", get(s, ARCHER).getHealth()) + " target-switches=" + st.stats.getOrDefault("arrows_used", 0));
                for (var id : st.raid.raiders) if (l.getEntity(id) instanceof io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity r)
                    log("   " + r.getType().toShortString() + " pos=" + r.blockPosition().toShortString() + " target=" + (r.getTarget() == null ? "-" : r.getTarget().getType().toShortString())
                            + " hp=" + String.format("%.0f", r.getHealth()));
                done[0] = st.raid.phase == io.github.operagtx.hearthholm.threat.RaidState.NONE;
            });
        }
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            Settlement st = town(s);
            int stragglers = 0;
            if (st.raid.phase == io.github.operagtx.hearthholm.threat.RaidState.ACTIVE) {
                st.raid.wavesLeft = 0;
                for (var id : List.copyOf(st.raid.raiders)) if (l.getEntity(id) instanceof LivingEntity a) { a.kill(l); stragglers++; }
            }
            int alive = 0;
            for (UUID id : new UUID[]{GUARD, WARRIOR, ARCHER}) if (s.overworld().getEntity(id) instanceof SettlerEntity e && e.isAlive()) alive++;
            log("SMALL RAID result: defenders alive " + alive + "/3, settler deaths=" + st.raid.deaths + " raiders killed by defenders=" + st.raid.killed
                    + " stragglers killed by test=" + stragglers + " lastResult=" + st.raid.lastResult + " salves used=" + st.stats.get("salves_used")
                    + " taunts=" + st.stats.get("taunts") + " arrows=" + st.stats.get("arrows_used"));
            if (alive < 3) throw new AssertionError("a defender died in the small raid");
        });
        ctx.waitTicks(30);
        sp.getServer().runOnServer(s -> {
            for (var e : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, new AABB(O).inflate(120))) e.discard();
            log("WAVE D OK");
        });
    }

    private static float FRONT_COST;
}
