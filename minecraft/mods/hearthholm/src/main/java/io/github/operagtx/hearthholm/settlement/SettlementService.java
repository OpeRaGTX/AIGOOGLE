package io.github.operagtx.hearthholm.settlement;

import io.github.operagtx.hearthholm.block.TownHallDeskBlock;
import io.github.operagtx.hearthholm.block.TownHallDeskBlockEntity;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.crafting.CraftPlanner;
import io.github.operagtx.hearthholm.crafting.Mint;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModEntities;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Glue between blocks, players, settlers and settlement data. Every gameplay rule that needs
 * more than one of those lives here, so blocks and entities stay thin.
 */
public final class SettlementService {
    private SettlementService() {}

    public static SettlementManager manager(ServerLevel level) {
        return SettlementManager.get(level.getServer());
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable("message.hearthholm." + key, args));
    }

    private static boolean canManage(ServerPlayer p, Settlement s) {
        return p.getUUID().equals(s.owner) || p.isCreative();
    }

    public static void markDirty(SettlerEntity s) {
        if (s.level() instanceof ServerLevel sl) manager(sl).setDirty();
    }

    public static void notifyOwner(SettlerEntity s, Component msg) {
        Settlement st = s.settlement();
        if (st != null && s.level() instanceof ServerLevel sl) notify(sl, st, msg);
    }

    public static void notify(ServerLevel level, Settlement st, Component msg) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(st.owner);
        if (p != null) p.sendSystemMessage(Component.empty().append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(st.displayName().copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY)).append(msg));
    }

    // ---- town hall desk -------------------------------------------------------------------

    public static void onDeskPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        SettlementManager m = manager(level);
        Settlement near = m.near(level.dimension(), pos, 24);
        if (!(level.getBlockEntity(pos) instanceof TownHallDeskBlockEntity desk)) return;
        if (near != null) {
            if (near.owner.equals(player.getUUID()) && !near.deskPresent && near.contains(level.dimension(), pos)) {
                near.center = pos.immutable();
                near.deskPresent = true;
                desk.setSettlementId(near.id);
                near.rescan(level);
                m.setDirty();
                say(player, "desk_restored", near.displayName());
                return;
            }
            say(player, near.owner.equals(player.getUUID()) ? "already_has_desk" : "too_close", near.displayName());
            level.destroyBlock(pos, true);
            return;
        }
        Settlement s = m.create(player.getUUID(), player.getName().getString(), level.dimension(), pos);
        desk.setSettlementId(s.id);
        s.rescan(level);
        level.playSound(null, pos, ModSounds.BELL, SoundSource.BLOCKS, 2f, 1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.6, 0.4, 0.6, 0.1);
        player.sendSystemMessage(Component.translatable("message.hearthholm.founded").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.hearthholm.founded_hint").withStyle(ChatFormatting.GRAY));
    }

    public static void onDeskRemoved(ServerLevel level, BlockPos pos, UUID id) {
        Settlement s = manager(level).byId(id);
        if (s == null || !s.center.equals(pos)) return;
        s.deskPresent = false;
        s.buildings.removeIf(b -> b.is(BuildingRecord.HALL));
        manager(level).setDirty();
        notify(level, s, Component.translatable("message.hearthholm.desk_removed").withStyle(ChatFormatting.RED));
    }

    /** Called by a builder finishing a town hall blueprint: the desk moves into the new hall. */
    public static void relocateDesk(ServerLevel level, SettlerEntity builder, BlockPos to, BlockState state) {
        Settlement s = builder.settlement();
        if (s == null) return;
        if (s.deskPresent && level.isLoaded(s.center) && level.getBlockEntity(s.center) instanceof TownHallDeskBlockEntity old) {
            old.relocating = true;
            level.removeBlock(s.center, false);
        }
        level.setBlock(to, state, 3);
        if (level.getBlockEntity(to) instanceof TownHallDeskBlockEntity desk) desk.setSettlementId(s.id);
        s.center = to.immutable();
        s.deskPresent = true;
        manager(level).setDirty();
        notify(level, s, Component.translatable("message.hearthholm.desk_moved").withStyle(ChatFormatting.GREEN));
    }

    public static @Nullable Settlement deskSettlement(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TownHallDeskBlockEntity desk)) return null;
        return manager(level).byId(desk.settlementId());
    }

    public static void openTownHall(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        Settlement s = deskSettlement(level, pos);
        if (s == null) {
            say(player, "desk_inactive");
            return;
        }
        if (!canManage(player, s)) {
            say(player, "not_owner", s.ownerName);
            return;
        }
        if (level.getGameTime() - s.lastScan > 100) {
            for (Component c : s.rescan(level)) notify(level, s, c);
            manager(level).setDirty();
        }
        s.ensureCandidates(day(level), level.getRandom());
        ServerPlayNetworking.send(player, new Payloads.OpenTownHall(view(level, s)));
    }

    public static long day(ServerLevel level) {
        return Math.floorDiv(level.getOverworldClockTime(), 24000L);
    }

    private static Views.Settlement view(ServerLevel level, Settlement s) {
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        List<Views.Resident> people = new ArrayList<>();
        for (ResidentRecord r : s.residents) {
            people.add(new Views.Resident(r.uuid, r.profile, r.profession, r.mood, r.food, r.status, r.bed != null, r.level));
        }
        List<Views.Building> buildings = new ArrayList<>();
        for (BuildingRecord b : s.buildings) {
            buildings.add(new Views.Building(b.roles(), b.anchor(), b.beds().size(), b.containers().size(), b.comfort(), b.problem(), b.features()));
        }
        SettlementTier tier = s.tier();
        SettlementTier next = s.nextTier();
        return new Views.Settlement(s.center, s.displayName(), tier.key(), s.renown, next == null ? -1 : next.renown(),
                next == null ? "" : next.key(), s.residents.size(), s.popCap(), s.housingBeds().size(), s.freeBeds(),
                Storage.totalNutrition(storage), s.hireCost(), s.radius(), s.hallValid(), s.warehouseValid(),
                people, buildings, List.copyOf(s.candidates), hints(s, storage), new LinkedHashMap<>(s.stats),
                Storage.count(storage, st -> st.is(ModItems.HEARTH_MARK)), Mint.describe(),
                Festival.daysUntil(level, s), Festival.cost(s));
    }

    private static List<Component> hints(Settlement s, List<Container> storage) {
        List<Component> out = new ArrayList<>();
        if (!s.hallValid()) out.add(Component.translatable("hint.hearthholm.build_hall"));
        if (s.housingBeds().isEmpty()) out.add(Component.translatable("hint.hearthholm.build_house"));
        if (!s.warehouseValid()) out.add(Component.translatable("hint.hearthholm.build_warehouse"));
        if (s.residents.isEmpty()) out.add(Component.translatable("hint.hearthholm.hire_first"));
        if (s.residents.stream().anyMatch(r -> r.profession.equals(Professions.UNEMPLOYED_ID)))
            out.add(Component.translatable("hint.hearthholm.assign_jobs"));
        if (!s.residents.isEmpty() && s.sites.isEmpty()) out.add(Component.translatable("hint.hearthholm.place_markers"));
        if (s.warehouseValid() && Storage.totalNutrition(storage) < 20) out.add(Component.translatable("hint.hearthholm.stock_food"));
        for (Workshop w : Workshop.values()) {
            if (CraftPlanner.hasCrafter(s, w) && CraftPlanner.workshop(s, w) == null)
                out.add(Component.translatable("hint.hearthholm.build_workshop", w.label()));
        }
        SettlementTier next = s.nextTier();
        if (next != null && out.size() < 4) out.add(Component.translatable("hint.hearthholm.next_tier", next.label(), next.renown()));
        return out;
    }

    public static void handleTownHall(ServerPlayer player, Payloads.TownHallAction a) {
        ServerLevel level = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.desk())) > 100) return;
        Settlement s = deskSettlement(level, a.desk());
        if (s == null || !canManage(player, s)) return;
        switch (a.action()) {
            case Payloads.TownHallAction.HIRE -> hire(player, level, s, a.arg());
            case Payloads.TownHallAction.REROLL -> {
                if (pay(player, level, s, 4)) {
                    s.candidatesDay = -1;
                    s.ensureCandidates(day(level), level.getRandom());
                } else say(player, "not_enough_food", 4);
            }
            case Payloads.TownHallAction.RESCAN -> {
                List<Component> news = s.rescan(level);
                for (Component c : news) notify(level, s, c);
                say(player, "rescanned", s.buildings.size());
            }
            case Payloads.TownHallAction.MINT -> mint(player, level, s);
            case Payloads.TownHallAction.FESTIVAL -> {
                if (Festival.active(level, s)) say(player, "festival_running");
                else if (!Festival.start(level, s, true)) say(player, "festival_denied", Festival.cost(s));
            }
            case Payloads.TownHallAction.RENAME -> {
                String name = a.text().strip();
                if (name.length() <= 32) s.name = name;
            }
            case Payloads.TownHallAction.OPEN_RESIDENT -> {
                if (a.arg() >= 0 && a.arg() < s.residents.size()) {
                    UUID id = s.residents.get(a.arg()).uuid;
                    if (level.getEntity(id) instanceof SettlerEntity e) {
                        openSettler(player, e);
                        return;
                    }
                    say(player, "resident_far");
                }
            }
            default -> {}
        }
        manager(level).setDirty();
        ServerPlayNetworking.send(player, new Payloads.OpenTownHall(view(level, s)));
    }

    private static int playerNutrition(ServerPlayer p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) n += Storage.nutrition(inv.getItem(i)) * inv.getItem(i).getCount();
        return n;
    }

    /** Pays food: warehouse first, then the player's own pockets. */
    private static boolean pay(ServerPlayer player, ServerLevel level, Settlement s, int cost) {
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        if (Storage.totalNutrition(storage) + playerNutrition(player) < cost) return false;
        int paid = Storage.consumeNutrition(storage, cost);
        if (paid < cost) Storage.consumeNutrition(List.of(player.getInventory()), cost - paid);
        return true;
    }

    private static void hire(ServerPlayer player, ServerLevel level, Settlement s, int index) {
        if (index < 0 || index >= s.candidates.size()) return;
        if (s.residents.size() >= s.popCap()) { say(player, "pop_cap", s.popCap()); return; }
        if (s.freeBeds() <= 0) { say(player, "no_free_bed"); return; }
        int cost = s.hireCost();
        if (!pay(player, level, s, cost)) { say(player, "not_enough_food", cost); return; }
        SettlerProfile profile = s.candidates.remove(index);
        SettlerEntity e = ModEntities.SETTLER.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (e == null) return;
        e.setup(profile, s.id);
        BlockPos spawn = arrivalSpot(level, s);
        e.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        level.addFreshEntity(e);
        s.residents.add(new ResidentRecord(e.getUUID(), profile));
        s.assignBeds();
        manager(level).setDirty();
        level.playSound(null, s.center, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        level.playSound(null, s.center, ModSounds.BELL, SoundSource.NEUTRAL, 1.2f, 1.3f);
        if (profile.legendary()) player.sendSystemMessage(Component.translatable("message.hearthholm.legend_hired", profile.fullName(), s.displayName()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.hearthholm.hired", profile.name()).withStyle(ChatFormatting.GREEN));
    }

    private static BlockPos arrivalSpot(ServerLevel level, Settlement s) {
        int dist = Math.max(8, Math.min(28, s.radius() - 6));
        for (int i = 0; i < 12; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            int x = s.center.getX() + (int) (Math.cos(a) * dist), z = s.center.getZ() + (int) (Math.sin(a) * dist);
            if (!level.isLoaded(new BlockPos(x, s.center.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (level.getFluidState(p.below()).isEmpty() && Math.abs(y - s.center.getY()) < 24) return p;
        }
        return s.center.offset(2, 0, 2);
    }

    // ---- mint -----------------------------------------------------------------------------

    private static void mint(ServerPlayer player, ServerLevel level, Settlement s) {
        if (!s.warehouseValid()) { say(player, "mint_no_warehouse"); return; }
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        List<Component> spent = new ArrayList<>();
        int marks = Mint.mint(storage, spent);
        if (marks <= 0) { say(player, "mint_nothing"); return; }
        int left = marks;
        while (left > 0) {
            int n = Math.min(64, left);
            ItemStack rest = Storage.insert(storage, new ItemStack(ModItems.HEARTH_MARK, n));
            if (!rest.isEmpty() && !player.getInventory().add(rest)) player.spawnAtLocation(level, rest);
            left -= n;
        }
        s.marksMinted += marks;
        s.count("marks", marks);
        level.playSound(null, s.center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
        net.minecraft.network.chat.MutableComponent list = Component.empty();
        for (int i = 0; i < spent.size(); i++) list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(spent.get(i));
        player.sendSystemMessage(Component.translatable("message.hearthholm.minted", marks, list).withStyle(ChatFormatting.GOLD));
    }

    // ---- workshops and orders -------------------------------------------------------------

    public static void onWorkshopPlaced(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Settlement s = manager(level).at(level.dimension(), pos);
        if (s == null) {
            if (player != null) say(player, "outside");
            return;
        }
        if (!s.workshops.contains(pos)) s.workshops.add(pos.immutable());
        for (Component c : s.rescan(level)) notify(level, s, c);
        manager(level).setDirty();
        if (player != null) describeWorkshop(player, pos);
    }

    public static void describeWorkshop(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        Settlement s = manager(level).at(level.dimension(), pos);
        if (s == null || !(level.getBlockState(pos).getBlock() instanceof io.github.operagtx.hearthholm.block.WorkshopBlock wb)) {
            say(player, "outside");
            return;
        }
        if (!s.workshops.contains(pos)) {
            s.workshops.add(pos.immutable());
            s.rescan(level);
            manager(level).setDirty();
        }
        Workshop kind = wb.kind();
        BuildingRecord b = null;
        for (BuildingRecord r : s.buildings) if (r.stations().contains(pos)) b = r;
        if (b == null) { say(player, "warehouse_unknown"); return; }
        if (!b.valid()) {
            player.sendSystemMessage(Component.translatable("message.hearthholm.workshop_problem", kind.label(),
                    Component.translatable("problem.hearthholm." + b.problem())).withStyle(ChatFormatting.RED));
            return;
        }
        Component worker = Component.translatable("gui.hearthholm.nobody");
        for (ResidentRecord r : s.residents) if (r.profession.equals(kind.profession())) { worker = r.profile.name(); break; }
        boolean heat = CraftPlanner.heat(kind, b);
        player.sendSystemMessage(Component.translatable("message.hearthholm.workshop_ok", kind.label(), worker,
                Component.translatable(heat ? "gui.hearthholm.heat_yes" : "gui.hearthholm.heat_no")).withStyle(ChatFormatting.GREEN));
    }

    public static void openOrders(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        Settlement s = manager(level).at(level.dimension(), pos);
        if (s == null) { say(player, "outside"); return; }
        if (!canManage(player, s)) { say(player, "not_owner", s.ownerName); return; }
        if (level.getGameTime() - s.lastScan > 100) {
            for (Component c : s.rescan(level)) notify(level, s, c);
            manager(level).setDirty();
        }
        ServerPlayNetworking.send(player, new Payloads.OpenOrders(ordersView(level, s, pos)));
    }

    public static Views.Orders ordersView(ServerLevel level, Settlement s, BlockPos board) {
        Map<Item, Integer> stock = CraftPlanner.stock(Storage.resolve(level, s.storagePositions()));
        List<Views.OrderLine> lines = new ArrayList<>();
        for (Settlement.Order o : s.orders) {
            Workshop kind = Workshop.forItem(o.item());
            if (kind == null) continue;
            BuildingRecord shop = CraftPlanner.workshop(s, kind);
            CraftPlanner.Verdict v = CraftPlanner.evaluate(level, s, kind, o, stock, shop, CraftPlanner.hasCrafter(s, kind));
            lines.add(new Views.OrderLine(o.id(), o.amount(), v.stock(), v.status(), v.color()));
        }
        List<Views.ShopInfo> shops = new ArrayList<>();
        for (Workshop w : Workshop.values()) {
            BuildingRecord shop = CraftPlanner.workshop(s, w);
            int crafters = 0;
            for (ResidentRecord r : s.residents) if (r.profession.equals(w.profession())) crafters++;
            shops.add(new Views.ShopInfo(w.key(), shop != null, CraftPlanner.heat(w, shop), crafters));
        }
        return new Views.Orders(board, s.displayName(), lines, shops);
    }

    public static void handleOrders(ServerPlayer player, Payloads.OrderAction a) {
        ServerLevel level = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.board())) > 100) return;
        Settlement s = manager(level).at(level.dimension(), a.board());
        if (s == null || !canManage(player, s)) return;
        Identifier id = Identifier.tryParse(a.item());
        if (a.amount() >= 0 && id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (Workshop.forItem(item) != null) setOrder(s, item, a.amount());
            manager(level).setDirty();
        }
        ServerPlayNetworking.send(player, new Payloads.OpenOrders(ordersView(level, s, a.board())));
    }

    /** Sets (amount &gt; 0) or removes (0) the order for an item, keeping the list order. */
    public static void setOrder(Settlement s, Item item, int amount) {
        amount = Math.max(0, Math.min(999, amount));
        for (int i = 0; i < s.orders.size(); i++) {
            if (s.orders.get(i).item() == item) {
                if (amount == 0) s.orders.remove(i);
                else s.orders.set(i, new Settlement.Order(item, amount));
                return;
            }
        }
        if (amount > 0 && s.orders.size() < 24) s.orders.add(new Settlement.Order(item, amount));
    }

    // ---- warehouse ledger -----------------------------------------------------------------

    public static void onLedgerPlaced(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Settlement s = manager(level).at(level.dimension(), pos);
        if (s == null) {
            if (player != null) say(player, "outside");
            return;
        }
        if (!s.ledgers.contains(pos)) s.ledgers.add(pos.immutable());
        for (Component c : s.rescan(level)) notify(level, s, c);
        manager(level).setDirty();
        if (player != null) describeLedger(player, pos);
    }

    public static void describeLedger(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        Settlement s = manager(level).at(level.dimension(), pos);
        if (s == null) { say(player, "outside"); return; }
        BuildingRecord b = null;
        for (BuildingRecord r : s.buildings) if (r.is(BuildingRecord.WAREHOUSE) && r.anchor().equals(pos)) b = r;
        if (b == null) for (BuildingRecord r : s.buildings) if (r.is(BuildingRecord.WAREHOUSE)) b = r;
        if (b == null) { say(player, "warehouse_unknown"); return; }
        if (!b.valid()) {
            player.sendSystemMessage(Component.translatable("message.hearthholm.warehouse_problem",
                    Component.translatable("problem.hearthholm." + b.problem())).withStyle(ChatFormatting.RED));
            return;
        }
        List<Container> c = Storage.resolve(level, b.containers());
        say(player, "warehouse_ok", b.containers().size(), Storage.totalNutrition(c));
    }

    // ---- work sites -----------------------------------------------------------------------

    public static void onWorkSitePlaced(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof WorkSiteBlockEntity w)) return;
        Settlement s = manager(level).at(level.dimension(), pos);
        if (s == null) {
            if (player != null) say(player, "site_outside");
            return;
        }
        s.sites.removeIf(r -> r.pos().equals(pos));
        s.sites.add(new Settlement.SiteRecord(pos.immutable(), w.type().id()));
        w.setSettlementId(s.id);
        manager(level).setDirty();
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.hearthholm.site_added", w.type().label()).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(w.type().hint().copy().withStyle(ChatFormatting.GRAY));
        }
    }

    public static void onWorkSiteRemoved(ServerLevel level, BlockPos pos, UUID id) {
        Settlement s = manager(level).byId(id);
        if (s != null && s.sites.removeIf(r -> r.pos().equals(pos))) manager(level).setDirty();
    }

    public static void openWorkSite(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!(level.getBlockEntity(pos) instanceof WorkSiteBlockEntity w)) return;
        Settlement s = manager(level).byId(w.settlementId());
        if (s != null && !canManage(player, s)) { say(player, "not_owner", s.ownerName); return; }
        ServerPlayNetworking.send(player, new Payloads.OpenWorkSite(siteView(level, w, s)));
    }

    private static Views.WorkSite siteView(ServerLevel level, WorkSiteBlockEntity w, @Nullable Settlement s) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (var o : w.type().options()) values.put(o.key(), w.option(o.key()));
        Component worker = Component.translatable("gui.hearthholm.nobody");
        Component status = s == null ? Component.translatable("message.hearthholm.site_outside") : Component.empty();
        if (w.workerId() != null && level.getEntity(w.workerId()) instanceof SettlerEntity e) {
            worker = e.getName();
            status = e.status();
        } else if (s != null) {
            Identifier prof = w.type().profession();
            for (ResidentRecord r : s.residents) {
                if (r.profession.equals(prof)) { worker = r.profile.name(); status = r.status; break; }
            }
        }
        int total = 0;
        if (w.type().id().equals(WorkSiteTypes.MINE_ID)) total = io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner.plan(w).steps().size();
        if (w.type().id().equals(WorkSiteTypes.CONSTRUCTION_ID)) {
            Blueprint bp = io.github.operagtx.hearthholm.blueprint.BlueprintLibrary.byIndex(w.option("blueprint"));
            if (bp != null) total = bp.ops(w.getBlockPos(), w.facing()).size();
        }
        return new Views.WorkSite(w.getBlockPos(), w.type().id(), values, status, worker, w.progress(), total);
    }

    public static void handleWorkSite(ServerPlayer player, Payloads.WorkSiteAction a) {
        ServerLevel level = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.pos())) > 100) return;
        if (!(level.getBlockEntity(a.pos()) instanceof WorkSiteBlockEntity w)) return;
        Settlement s = manager(level).byId(w.settlementId());
        if (s != null && !canManage(player, s)) return;
        if (Payloads.WorkSiteAction.RESET.equals(a.key())) w.setProgress(0);
        else w.setOption(a.key(), a.value());
        ServerPlayNetworking.send(player, new Payloads.OpenWorkSite(siteView(level, w, s)));
    }

    public static void finishConstruction(ServerLevel level, WorkSiteBlockEntity w, Blueprint bp, SettlerEntity builder) {
        Settlement s = builder.settlement();
        BlockPos pos = w.getBlockPos();
        w.setWorkerId(null);
        level.removeBlock(pos, false);
        net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(ModBlocks.CONSTRUCTION_STAKE));
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 0.9f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 25, 2, 1, 2, 0.1);
        if (s != null) {
            s.addRenown(20);
            notify(level, s, Component.translatable("message.hearthholm.construction_done", builder.getName(), bp.label()).withStyle(ChatFormatting.GREEN));
            for (Component c : s.rescan(level)) notify(level, s, c);
            manager(level).setDirty();
        }
    }

    // ---- settlers -------------------------------------------------------------------------

    public static void interactSettler(ServerPlayer player, SettlerEntity e, InteractionHand hand) {
        Settlement s = e.settlement();
        if (s == null) return;
        if (!canManage(player, s)) { say(player, "not_owner", s.ownerName); return; }
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !held.isEmpty()) {
            give(player, e, hand, held);
            return;
        }
        openSettler(player, e);
    }

    private static void give(ServerPlayer player, SettlerEntity e, InteractionHand hand, ItemStack held) {
        ItemStack one = held.copy();
        Equippable eq = held.get(DataComponents.EQUIPPABLE);
        if (e.profession().isTool(held)) {
            ItemStack old = e.getMainHandItem();
            e.setItemSlot(EquipmentSlot.MAINHAND, one.copyWithCount(1));
            held.shrink(1);
            if (!old.isEmpty() && !player.getInventory().add(old)) e.spawnAtLocation(player.level(), old);
        } else if (eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            ItemStack old = e.getItemBySlot(eq.slot());
            e.setItemSlot(eq.slot(), one.copyWithCount(1));
            held.shrink(1);
            if (!old.isEmpty() && !player.getInventory().add(old)) e.spawnAtLocation(player.level(), old);
        } else {
            ItemStack rest = e.inventory().addItem(one);
            held.setCount(rest.getCount());
        }
        player.setItemInHand(hand, held);
        e.level().playSound(null, e.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5f, 1f);
        say(player, "gave", one.getHoverName(), e.getName());
    }

    private static Component relationName(@Nullable Settlement s, @Nullable java.util.Map.Entry<UUID, Integer> rel) {
        ResidentRecord r = rel == null || s == null ? null : s.resident(rel.getKey());
        return r == null ? Component.empty() : r.profile.listName();
    }

    public static void openSettler(ServerPlayer player, SettlerEntity e) {
        Settlement s = e.settlement();
        Identifier prof = e.professionId();
        int lvl = e.level(prof);
        int next = lvl < SettlerEntity.LEVEL_XP.length ? SettlerEntity.LEVEL_XP[lvl] : -1;
        List<ItemStack> equipment = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            equipment.add(e.getItemBySlot(slot).copy());
        }
        List<ItemStack> inv = new ArrayList<>();
        for (ItemStack st : e.inventory().getItems()) inv.add(st.copy());
        var friend = e.bestRelation(true);
        var rival = e.bestRelation(false);
        Views.Settler v = new Views.Settler(e.getId(), e.profile(), prof, Math.round(e.food()), Math.round(e.mood()),
                e.moodEntries(), e.status(), lvl, e.xp(prof), next, inv, equipment, e.bed() != null,
                s == null ? Component.empty() : s.displayName(),
                relationName(s, friend), friend == null ? 0 : friend.getValue(), relationName(s, rival), rival == null ? 0 : rival.getValue());
        ServerPlayNetworking.send(player, new Payloads.OpenSettler(v));
    }

    public static void handleSettler(ServerPlayer player, Payloads.SettlerAction a) {
        if (!(player.level().getEntity(a.entityId()) instanceof SettlerEntity e)) return;
        if (player.distanceToSqr(e) > 32 * 32) return;
        Settlement s = e.settlement();
        if (s == null || !canManage(player, s)) return;
        switch (a.action()) {
            case Payloads.SettlerAction.SET_PROFESSION -> {
                Identifier id = Identifier.tryParse(a.arg());
                Profession p = id == null ? null : Professions.REGISTRY.get(id).orElse(null);
                if (p != null) {
                    e.setProfession(p);
                    say(player, "profession_set", e.getName(), p.label());
                }
            }
            case Payloads.SettlerAction.DISMISS -> {
                e.leave(Component.translatable("message.hearthholm.dismissed", e.getName()));
                return;
            }
            default -> {}
        }
        openSettler(player, e);
    }

    /** A settler was attacked: nearby combatants take on the attacker. */
    public static void callGuards(SettlerEntity victim, LivingEntity attacker) {
        for (SettlerEntity g : victim.level().getEntitiesOfClass(SettlerEntity.class, victim.getBoundingBox().inflate(32),
                x -> x.isCombatant() && x.getTarget() == null && !x.isSleeping())) {
            g.setTarget(attacker);
        }
    }
}
