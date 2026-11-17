package io.github.operagtx.hearthholm.town;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.entity.trader.TraderEntity;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.registry.ModEntities;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The trader caravan: every {@link #PERIOD} in-game days (from hamlet on) a trader with a llama walks to the market,
 * stays a day, buys the warehouse's surplus for hearth marks and sells seeds, exotic saplings, blueprints and a rare
 * hire. Goods and marks live in the warehouse; offers are derived from a saved seed so they survive reloads.
 */
public final class Caravan {
    public static final int PERIOD = 7, STAY = 24000, TRADE_RANGE_SQR = 20 * 20;

    /** What the trader pays for: item, items per mark, the stock the settlement keeps back. */
    public record Rate(Item item, int perMark, int reserve) {}

    public static final List<Rate> BUY = List.of(
            new Rate(Items.COBBLESTONE, 16, 128), new Rate(Items.OAK_LOG, 8, 64), new Rate(Items.SPRUCE_LOG, 8, 64),
            new Rate(Items.BIRCH_LOG, 8, 64), new Rate(Items.OAK_PLANKS, 12, 96), new Rate(Items.COAL, 4, 32),
            new Rate(Items.IRON_INGOT, 1, 32), new Rate(Items.WHEAT, 8, 48), new Rate(Items.CARROT, 8, 48),
            new Rate(Items.POTATO, 8, 48), new Rate(Items.BREAD, 4, 32), new Rate(Items.SAND, 16, 64),
            new Rate(Items.COOKED_COD, 4, 24), new Rate(Items.STRING, 8, 16));

    public record Offer(String kind, ItemStack stack, int price, String ref) {}

    private Caravan() {}

    // ---- offers ----------------------------------------------------------------------------------------

    private static final List<Item> SAPLINGS = List.of(Items.CHERRY_SAPLING, Items.ACACIA_SAPLING, Items.JUNGLE_SAPLING,
            Items.DARK_OAK_SAPLING, Items.MANGROVE_PROPAGULE);

    /** The caravan's goods for this visit: indices are stable for the whole visit. */
    public static List<Offer> offers(Settlement s) {
        Random r = new Random(s.civic.caravanSeed);
        List<Offer> out = new ArrayList<>();
        out.add(new Offer("item", new ItemStack(Items.WHEAT_SEEDS, 16), io.github.operagtx.hearthholm.core.Balance.PRICE_SEEDS, ""));
        out.add(new Offer("item", new ItemStack(Items.BEETROOT_SEEDS, 16), io.github.operagtx.hearthholm.core.Balance.PRICE_SEEDS, ""));
        out.add(new Offer("item", new ItemStack(r.nextBoolean() ? Items.MELON_SEEDS : Items.PUMPKIN_SEEDS, 8), io.github.operagtx.hearthholm.core.Balance.PRICE_SEEDS_RARE, ""));
        int a = r.nextInt(SAPLINGS.size()), b = (a + 1 + r.nextInt(SAPLINGS.size() - 1)) % SAPLINGS.size();
        out.add(new Offer("item", new ItemStack(SAPLINGS.get(a), 4), io.github.operagtx.hearthholm.core.Balance.PRICE_SAPLING, ""));
        out.add(new Offer("item", new ItemStack(SAPLINGS.get(b), 4), io.github.operagtx.hearthholm.core.Balance.PRICE_SAPLING, ""));
        List<String> locked = new ArrayList<>();
        for (Identifier id : BlueprintLibrary.ids()) if (BlueprintLibrary.requiredTier(id) > 0) locked.add(id.getPath());
        if (!locked.isEmpty()) {
            String bp = locked.get(r.nextInt(locked.size()));
            out.add(new Offer("blueprint", new ItemStack(Items.PAPER), io.github.operagtx.hearthholm.core.Balance.PRICE_BLUEPRINT_BASE + io.github.operagtx.hearthholm.core.Balance.PRICE_BLUEPRINT_PER_TIER * BlueprintLibrary.requiredTier(Hearthholm.id(bp)), bp));
        }
        out.add(new Offer("hire", new ItemStack(Items.NAME_TAG), io.github.operagtx.hearthholm.core.Balance.PRICE_RARE_HIRE, ""));
        return out;
    }

    private static Component label(Offer o) {
        return switch (o.kind()) {
            case "blueprint" -> Component.translatable("trade.hearthholm.blueprint", Component.translatable("blueprint.hearthholm." + o.ref()));
            case "hire" -> Component.translatable("trade.hearthholm.hire");
            default -> Component.translatable("trade.hearthholm.item", o.stack().getCount(), o.stack().getHoverName());
        };
    }

    private static boolean soldOut(Settlement s, int index, Offer o) {
        if (s.civic.soldOut.contains(index)) return true;
        return o.kind().equals("blueprint") && s.civic.unlocked.contains(o.ref());
    }

    public static List<Views.SellLine> sells(ServerLevel level, Settlement s) {
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        List<Views.SellLine> out = new ArrayList<>();
        for (Rate r : BUY) {
            int spare = Storage.count(storage, st -> st.is(r.item())) - r.reserve();
            if (spare >= r.perMark()) out.add(new Views.SellLine(new ItemStack(r.item()), spare, r.perMark()));
        }
        return out;
    }

    public static int marks(ServerLevel level, Settlement s) {
        return Storage.count(Storage.resolve(level, s.storagePositions()), st -> st.is(ModItems.HEARTH_MARK));
    }

    public static Views.Trade view(ServerLevel level, Settlement s, BlockPos pos) {
        List<Views.TradeOffer> offers = new ArrayList<>();
        List<Offer> all = offers(s);
        for (int i = 0; i < all.size(); i++) {
            Offer o = all.get(i);
            offers.add(new Views.TradeOffer(o.stack(), o.price(), label(o), soldOut(s, i, o)));
        }
        return new Views.Trade(pos, Component.translatable("entity.hearthholm.trader"), marks(level, s), offers, sells(level, s));
    }

    // ---- arrival and departure ---------------------------------------------------------------------------

    public static @Nullable BlockPos market(ServerLevel level, Settlement s) {
        for (BlockPos p : s.civic.markets) if (level.isLoaded(p)) return p;
        return null;
    }

    /** Where the caravan sets up: a market stall, else the square. */
    public static BlockPos spot(ServerLevel level, Settlement s) {
        BlockPos m = market(level, s);
        return m != null ? m : Festival.square(level, s);
    }

    /** Once a second per settlement. */
    public static void tick(ServerLevel level, Settlement s) {
        Civic c = s.civic;
        long now = level.getGameTime();
        if (c.caravanEnd > 0 && now >= c.caravanEnd) {
            depart(level, s);
            return;
        }
        if (c.caravanHere(now) || s.residents.isEmpty() || s.tier().index() < 1) return;
        long day = SettlementService.day(level);
        if (c.lastCaravan == -1) {
            c.lastCaravan = day;
            SettlementService.manager(level).setDirty();
            return;
        }
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (day >= c.lastCaravan + PERIOD && tod >= 1000 && tod < 3000) arrive(level, s);
    }

    /** The caravan comes (also called by tests). Returns the trader, or null when it could not be placed. */
    public static @Nullable TraderEntity arrive(ServerLevel level, Settlement s) {
        Civic c = s.civic;
        BlockPos spot = spot(level, s);
        BlockPos from = spawnPoint(level, spot);
        TraderEntity trader = ModEntities.TRADER.create(level, EntitySpawnReason.EVENT);
        if (trader == null) return null;
        trader.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        trader.setCaravan(s.id, spot);
        level.addFreshEntity(trader);
        TraderLlama llama = EntityTypes.TRADER_LLAMA.create(level, EntitySpawnReason.EVENT);
        if (llama != null) {
            llama.setPos(from.getX() + 1.5, from.getY(), from.getZ() + 0.5);
            llama.setPersistenceRequired();
            level.addFreshEntity(llama);
            llama.setLeashedTo(trader, true);
            c.llama = llama.getUUID();
        }
        c.trader = trader.getUUID();
        c.lastCaravan = SettlementService.day(level);
        c.caravanEnd = level.getGameTime() + STAY;
        c.caravanSeed = level.getRandom().nextInt();
        c.soldOut.clear();
        s.count("caravans", 1);
        level.playSound(null, spot, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.2f, 1.4f);
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.caravan_arrived").withStyle(ChatFormatting.GOLD));
        SettlementService.manager(level).setDirty();
        return trader;
    }

    private static BlockPos spawnPoint(ServerLevel level, BlockPos spot) {
        for (int i = 0; i < 12; i++) {
            double ang = (i * 0.9) + 0.4;
            int x = spot.getX() + (int) Math.round(Math.cos(ang) * 14), z = spot.getZ() + (int) Math.round(Math.sin(ang) * 14);
            if (!level.isLoaded(new BlockPos(x, spot.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - spot.getY()) <= 3 && level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) return new BlockPos(x, y, z);
        }
        return spot;
    }

    /** The caravan packs up (end of the day, or a test). */
    public static void depart(ServerLevel level, Settlement s) {
        Civic c = s.civic;
        for (java.util.UUID id : new java.util.UUID[]{c.trader, c.llama}) {
            if (id == null) continue;
            Entity e = level.getEntity(id);
            if (e != null) {
                level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                e.discard();
            }
        }
        c.trader = null;
        c.llama = null;
        c.caravanEnd = -1;
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.caravan_left").withStyle(ChatFormatting.GRAY));
        SettlementService.manager(level).setDirty();
    }

    /** A trader that outlived its caravan (or its settlement): remove him and the llama on his lead. */
    public static void discardWithLlama(ServerLevel level, TraderEntity t) {
        for (TraderLlama l : level.getEntitiesOfClass(TraderLlama.class, t.getBoundingBox().inflate(16), x -> x.getLeashHolder() == t)) l.discard();
        t.discard();
    }

    public static void registerMarket(ServerLevel level, BlockPos pos) {
        Settlement s = SettlementService.manager(level).at(level.dimension(), pos);
        if (s == null || s.civic.markets.contains(pos)) return;
        s.civic.markets.add(pos.immutable());
        SettlementService.manager(level).setDirty();
    }

    // ---- trading -----------------------------------------------------------------------------------------

    public static void openFromBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        Settlement s = SettlementService.manager(level).at(level.dimension(), pos);
        if (s == null) { SettlementService.say(player, "outside"); return; }
        open(player, level, s, pos);
    }

    public static void openFromTrader(ServerPlayer player, TraderEntity trader) {
        ServerLevel level = player.level();
        for (Settlement s : SettlementService.manager(level).all()) {
            if (trader.getUUID().equals(s.civic.trader)) { open(player, level, s, trader.blockPosition()); return; }
        }
    }

    private static void open(ServerPlayer player, ServerLevel level, Settlement s, BlockPos pos) {
        if (!SettlementService.canManage(player, s)) { SettlementService.say(player, "not_owner", s.ownerName); return; }
        if (!s.civic.caravanHere(level.getGameTime())) {
            long days = Math.max(0, s.civic.lastCaravan + PERIOD - SettlementService.day(level));
            SettlementService.say(player, "caravan_none", days);
            return;
        }
        ServerPlayNetworking.send(player, new Payloads.OpenTrade(view(level, s, pos)));
    }

    public static void handle(ServerPlayer player, Payloads.TradeAction a) {
        ServerLevel level = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.pos())) > TRADE_RANGE_SQR) return;
        Settlement s = SettlementService.manager(level).at(level.dimension(), a.pos());
        if (s == null || !SettlementService.canManage(player, s) || !s.civic.caravanHere(level.getGameTime())) return;
        if (a.action() == Payloads.TradeAction.BUY) buy(player, level, s, a.index());
        else if (a.action() == Payloads.TradeAction.SELL) sell(player, level, s, a.index());
        ServerPlayNetworking.send(player, new Payloads.OpenTrade(view(level, s, a.pos())));
    }

    private static void drop(ServerLevel level, Settlement s, ItemStack rest) {
        if (rest.isEmpty()) return;
        BlockPos p = spot(level, s);
        level.addFreshEntity(new ItemEntity(level, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, rest));
    }

    /** Buys offer {@code index}; returns false (nothing changes) when it is gone or the warehouse lacks the marks. */
    public static boolean buy(ServerPlayer player, ServerLevel level, Settlement s, int index) {
        List<Offer> all = offers(s);
        if (index < 0 || index >= all.size()) return false;
        Offer o = all.get(index);
        if (soldOut(s, index, o)) { SettlementService.say(player, "trade_sold_out"); return false; }
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        if (Storage.count(storage, st -> st.is(ModItems.HEARTH_MARK)) < o.price()) {
            SettlementService.say(player, "trade_no_marks", o.price());
            return false;
        }
        int left = o.price();
        while (left > 0) {
            ItemStack got = Storage.take(storage, st -> st.is(ModItems.HEARTH_MARK), left);
            if (got.isEmpty()) break;
            left -= got.getCount();
        }
        switch (o.kind()) {
            case "blueprint" -> {
                s.civic.unlocked.add(o.ref());
                s.civic.soldOut.add(index);
            }
            case "hire" -> {
                SettlerProfile p = SettlerProfile.random(level.getRandom(), true);
                s.candidates.add(0, p);
                s.civic.soldOut.add(index);
                player.sendSystemMessage(Component.translatable("message.hearthholm.trade_hire", p.name()).withStyle(ChatFormatting.GREEN));
            }
            default -> drop(level, s, Storage.insert(storage, o.stack().copy()));
        }
        s.count("caravan_bought", o.price());
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
        SettlementService.manager(level).setDirty();
        return true;
    }

    /** Sells the spare stock of line {@code index} (as listed by {@link #sells}); returns the marks earned. */
    public static int sell(ServerPlayer player, ServerLevel level, Settlement s, int index) {
        List<Views.SellLine> lines = sells(level, s);
        if (index < 0 || index >= lines.size()) return 0;
        Views.SellLine l = lines.get(index);
        int n = Math.min(l.spare(), 256) / l.perMark() * l.perMark();
        if (n <= 0) return 0;
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        int left = n;
        while (left > 0) {
            ItemStack got = Storage.take(storage, st -> st.is(l.stack().getItem()), left);
            if (got.isEmpty()) break;
            left -= got.getCount();
        }
        int sold = n - left;
        int marks = sold / l.perMark();
        int rest = marks;
        while (rest > 0) {
            int part = Math.min(rest, 64);
            drop(level, s, Storage.insert(storage, new ItemStack(ModItems.HEARTH_MARK, part)));
            rest -= part;
        }
        s.count("caravan_sold", marks);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8f, 0.9f);
        SettlementService.manager(level).setDirty();
        return marks;
    }
}
