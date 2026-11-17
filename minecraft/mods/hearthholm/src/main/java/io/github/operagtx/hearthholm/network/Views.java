package io.github.operagtx.hearthholm.network;

import io.github.operagtx.hearthholm.entity.MoodEntry;
import io.github.operagtx.hearthholm.settlement.SettlerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Read-only snapshots sent to the client to draw the screens. */
public final class Views {
    private Views() {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Component> COMP = ComponentSerialization.TRUSTED_STREAM_CODEC;

    public record Resident(UUID id, SettlerProfile profile, Identifier profession, int mood, int food,
                           Component status, boolean bed, int level) {
        static void write(RegistryFriendlyByteBuf b, Resident r) {
            UUIDUtil.STREAM_CODEC.encode(b, r.id);
            SettlerProfile.STREAM_CODEC.encode(b, r.profile);
            Identifier.STREAM_CODEC.encode(b, r.profession);
            b.writeVarInt(r.mood);
            b.writeVarInt(r.food);
            COMP.encode(b, r.status);
            b.writeBoolean(r.bed);
            b.writeVarInt(r.level);
        }

        static Resident read(RegistryFriendlyByteBuf b) {
            return new Resident(UUIDUtil.STREAM_CODEC.decode(b), SettlerProfile.STREAM_CODEC.decode(b),
                    Identifier.STREAM_CODEC.decode(b), b.readVarInt(), b.readVarInt(), COMP.decode(b), b.readBoolean(), b.readVarInt());
        }
    }

    public record Building(int roles, BlockPos anchor, int beds, int containers, int comfort, String problem, int features) {}

    public record Settlement(BlockPos desk, Component name, String tier, int renown, int nextRenown, String nextTier,
                             int residents, int cap, int beds, int freeBeds, int food, int hireCost, int radius,
                             boolean hall, boolean warehouse, List<Resident> people, List<Building> buildings,
                             List<SettlerProfile> candidates, List<Component> hints, Map<String, Integer> stats,
                             int marks, Component mintInfo, int festivalDays, int festivalCost) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Settlement> CODEC = StreamCodec.of(Settlement::write, Settlement::read);

        static void write(RegistryFriendlyByteBuf b, Settlement v) {
            b.writeBlockPos(v.desk);
            COMP.encode(b, v.name);
            b.writeUtf(v.tier);
            b.writeVarInt(v.renown);
            b.writeVarInt(v.nextRenown);
            b.writeUtf(v.nextTier);
            b.writeVarInt(v.residents);
            b.writeVarInt(v.cap);
            b.writeVarInt(v.beds);
            b.writeVarInt(v.freeBeds);
            b.writeVarInt(v.food);
            b.writeVarInt(v.hireCost);
            b.writeVarInt(v.radius);
            b.writeBoolean(v.hall);
            b.writeBoolean(v.warehouse);
            b.writeVarInt(v.people.size());
            for (Resident r : v.people) Resident.write(b, r);
            b.writeVarInt(v.buildings.size());
            for (Building x : v.buildings) {
                b.writeVarInt(x.roles);
                b.writeBlockPos(x.anchor);
                b.writeVarInt(x.beds);
                b.writeVarInt(x.containers);
                b.writeVarInt(x.comfort);
                b.writeUtf(x.problem);
                b.writeVarInt(x.features);
            }
            b.writeVarInt(v.candidates.size());
            for (SettlerProfile p : v.candidates) SettlerProfile.STREAM_CODEC.encode(b, p);
            b.writeVarInt(v.hints.size());
            for (Component c : v.hints) COMP.encode(b, c);
            b.writeVarInt(v.stats.size());
            v.stats.forEach((k, n) -> { b.writeUtf(k); b.writeVarInt(n); });
            b.writeVarInt(v.marks);
            COMP.encode(b, v.mintInfo);
            b.writeVarInt(v.festivalDays + 1);
            b.writeVarInt(v.festivalCost);
        }

        static Settlement read(RegistryFriendlyByteBuf b) {
            BlockPos desk = b.readBlockPos();
            Component name = COMP.decode(b);
            String tier = b.readUtf();
            int renown = b.readVarInt(), next = b.readVarInt();
            String nextTier = b.readUtf();
            int residents = b.readVarInt(), cap = b.readVarInt(), beds = b.readVarInt(), free = b.readVarInt();
            int food = b.readVarInt(), cost = b.readVarInt(), radius = b.readVarInt();
            boolean hall = b.readBoolean(), wh = b.readBoolean();
            List<Resident> people = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) people.add(Resident.read(b));
            List<Building> buildings = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--)
                buildings.add(new Building(b.readVarInt(), b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), b.readVarInt()));
            List<SettlerProfile> cands = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) cands.add(SettlerProfile.STREAM_CODEC.decode(b));
            List<Component> hints = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) hints.add(COMP.decode(b));
            Map<String, Integer> stats = new LinkedHashMap<>();
            for (int i = b.readVarInt(); i > 0; i--) stats.put(b.readUtf(), b.readVarInt());
            int marks = b.readVarInt();
            Component mint = COMP.decode(b);
            int festDays = b.readVarInt() - 1, festCost = b.readVarInt();
            return new Settlement(desk, name, tier, renown, next, nextTier, residents, cap, beds, free, food, cost, radius,
                    hall, wh, people, buildings, cands, hints, stats, marks, mint, festDays, festCost);
        }
    }

    public record Settler(int entityId, SettlerProfile profile, Identifier profession, int food, int mood,
                          List<MoodEntry> moods, Component status, int level, int xp, int nextXp,
                          List<ItemStack> inventory, List<ItemStack> equipment, boolean bed, Component settlement,
                          Component friend, int friendValue, Component rival, int rivalValue, boolean promotable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Settler> CODEC = StreamCodec.of(Settler::write, Settler::read);

        static void write(RegistryFriendlyByteBuf b, Settler v) {
            b.writeVarInt(v.entityId);
            SettlerProfile.STREAM_CODEC.encode(b, v.profile);
            Identifier.STREAM_CODEC.encode(b, v.profession);
            b.writeVarInt(v.food);
            b.writeVarInt(v.mood);
            b.writeVarInt(v.moods.size());
            for (MoodEntry m : v.moods) MoodEntry.STREAM_CODEC.encode(b, m);
            COMP.encode(b, v.status);
            b.writeVarInt(v.level);
            b.writeVarInt(v.xp);
            b.writeVarInt(v.nextXp);
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(b, v.inventory);
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(b, v.equipment);
            b.writeBoolean(v.bed);
            COMP.encode(b, v.settlement);
            COMP.encode(b, v.friend);
            b.writeVarInt(v.friendValue);
            COMP.encode(b, v.rival);
            b.writeVarInt(v.rivalValue);
            b.writeBoolean(v.promotable);
        }

        static Settler read(RegistryFriendlyByteBuf b) {
            int id = b.readVarInt();
            SettlerProfile p = SettlerProfile.STREAM_CODEC.decode(b);
            Identifier prof = Identifier.STREAM_CODEC.decode(b);
            int food = b.readVarInt(), mood = b.readVarInt();
            List<MoodEntry> moods = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) moods.add(MoodEntry.STREAM_CODEC.decode(b));
            Component status = COMP.decode(b);
            int level = b.readVarInt(), xp = b.readVarInt(), next = b.readVarInt();
            List<ItemStack> inv = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(b);
            List<ItemStack> eq = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(b);
            return new Settler(id, p, prof, food, mood, moods, status, level, xp, next, inv, eq, b.readBoolean(), COMP.decode(b),
                    COMP.decode(b), b.readVarInt(), COMP.decode(b), b.readVarInt(), b.readBoolean());
        }
    }

    public record WorkSite(BlockPos pos, Identifier type, Map<String, Integer> values, Component status,
                           Component worker, int progress, int total) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkSite> CODEC = StreamCodec.of(WorkSite::write, WorkSite::read);

        static void write(RegistryFriendlyByteBuf b, WorkSite v) {
            b.writeBlockPos(v.pos);
            Identifier.STREAM_CODEC.encode(b, v.type);
            b.writeVarInt(v.values.size());
            v.values.forEach((k, n) -> { b.writeUtf(k); b.writeVarInt(n); });
            COMP.encode(b, v.status);
            COMP.encode(b, v.worker);
            b.writeVarInt(v.progress);
            b.writeVarInt(v.total);
        }

        static WorkSite read(RegistryFriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            Identifier type = Identifier.STREAM_CODEC.decode(b);
            Map<String, Integer> values = new LinkedHashMap<>();
            for (int i = b.readVarInt(); i > 0; i--) values.put(b.readUtf(), b.readVarInt());
            return new WorkSite(pos, type, values, COMP.decode(b), COMP.decode(b), b.readVarInt(), b.readVarInt());
        }
    }

    /** One order-board line: target, stock and why it is (not) being made. */
    public record OrderLine(Identifier item, int target, int stock, Component status, int color) {}

    /** Per workshop kind: is there a valid workshop, how many crafters. */
    public record ShopInfo(String kind, boolean workshop, boolean furnace, int crafters) {}

    public record Orders(BlockPos board, Component name, List<OrderLine> lines, List<ShopInfo> shops) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Orders> CODEC = StreamCodec.of(Orders::write, Orders::read);

        static void write(RegistryFriendlyByteBuf b, Orders v) {
            b.writeBlockPos(v.board);
            COMP.encode(b, v.name);
            b.writeVarInt(v.lines.size());
            for (OrderLine l : v.lines) {
                Identifier.STREAM_CODEC.encode(b, l.item);
                b.writeVarInt(l.target);
                b.writeVarInt(l.stock);
                COMP.encode(b, l.status);
                b.writeInt(l.color);
            }
            b.writeVarInt(v.shops.size());
            for (ShopInfo s : v.shops) {
                b.writeUtf(s.kind);
                b.writeBoolean(s.workshop);
                b.writeBoolean(s.furnace);
                b.writeVarInt(s.crafters);
            }
        }

        static Orders read(RegistryFriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            Component name = COMP.decode(b);
            List<OrderLine> lines = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--)
                lines.add(new OrderLine(Identifier.STREAM_CODEC.decode(b), b.readVarInt(), b.readVarInt(), COMP.decode(b), b.readInt()));
            List<ShopInfo> shops = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) shops.add(new ShopInfo(b.readUtf(), b.readBoolean(), b.readBoolean(), b.readVarInt()));
            return new Orders(pos, name, lines, shops);
        }
    }
}
