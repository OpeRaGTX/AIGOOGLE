package io.github.operagtx.hearthholm.network;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** All Hearthholm packets. Server → client open/refresh screens; client → server actions. */
public final class Payloads {
    private Payloads() {}

    public record OpenTownHall(Views.Settlement view) implements CustomPacketPayload {
        public static final Type<OpenTownHall> TYPE = new Type<>(Hearthholm.id("open_town_hall"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTownHall> CODEC =
                Views.Settlement.CODEC.map(OpenTownHall::new, OpenTownHall::view);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenSettler(Views.Settler view) implements CustomPacketPayload {
        public static final Type<OpenSettler> TYPE = new Type<>(Hearthholm.id("open_settler"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenSettler> CODEC =
                Views.Settler.CODEC.map(OpenSettler::new, OpenSettler::view);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenWorkSite(Views.WorkSite view) implements CustomPacketPayload {
        public static final Type<OpenWorkSite> TYPE = new Type<>(Hearthholm.id("open_work_site"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWorkSite> CODEC =
                Views.WorkSite.CODEC.map(OpenWorkSite::new, OpenWorkSite::view);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Town hall actions; see constants. */
    public record TownHallAction(BlockPos desk, int action, int arg, String text) implements CustomPacketPayload {
        public static final int REFRESH = 0, HIRE = 1, REROLL = 2, RESCAN = 3, RENAME = 4, OPEN_RESIDENT = 5, MINT = 6, FESTIVAL = 7, ALARM = 8,
                OWNERS = 9, ADD_OWNER = 10, REMOVE_OWNER = 11;   // wave K1: co-owners (text = player uuid)
        public static final Type<TownHallAction> TYPE = new Type<>(Hearthholm.id("town_hall_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TownHallAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TownHallAction::desk,
                ByteBufCodecs.VAR_INT, TownHallAction::action,
                ByteBufCodecs.VAR_INT, TownHallAction::arg,
                ByteBufCodecs.stringUtf8(256), TownHallAction::text,
                TownHallAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Wave K1: the "Owners" tab of the town hall: the founder, co-owners and the online players that can be added. */
    public record OwnerEntry(java.util.UUID id, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OwnerEntry> CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, OwnerEntry::id,
                ByteBufCodecs.stringUtf8(64), OwnerEntry::name,
                OwnerEntry::new);
    }

    public record OwnersView(BlockPos desk, boolean founder, String founderName, List<OwnerEntry> coOwners, List<OwnerEntry> online) implements CustomPacketPayload {
        public static final Type<OwnersView> TYPE = new Type<>(Hearthholm.id("owners_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OwnersView> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OwnersView::desk,
                ByteBufCodecs.BOOL, OwnersView::founder,
                ByteBufCodecs.stringUtf8(64), OwnersView::founderName,
                OwnerEntry.CODEC.apply(ByteBufCodecs.list(64)), OwnersView::coOwners,
                OwnerEntry.CODEC.apply(ByteBufCodecs.list(64)), OwnersView::online,
                OwnersView::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SettlerAction(int entityId, int action, String arg) implements CustomPacketPayload {
        public static final int REFRESH = 0, SET_PROFESSION = 1, DISMISS = 2, PROMOTE = 3, SET_SHIFT = 4, INVENTORY = 5;
        public static final Type<SettlerAction> TYPE = new Type<>(Hearthholm.id("settler_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SettlerAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SettlerAction::entityId,
                ByteBufCodecs.VAR_INT, SettlerAction::action,
                ByteBufCodecs.stringUtf8(256), SettlerAction::arg,
                SettlerAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WorkSiteAction(BlockPos pos, String key, int value) implements CustomPacketPayload {
        public static final String RESET = "__reset";
        public static final Type<WorkSiteAction> TYPE = new Type<>(Hearthholm.id("work_site_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkSiteAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, WorkSiteAction::pos,
                ByteBufCodecs.stringUtf8(64), WorkSiteAction::key,
                ByteBufCodecs.VAR_INT, WorkSiteAction::value,
                WorkSiteAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenOrders(Views.Orders view) implements CustomPacketPayload {
        public static final Type<OpenOrders> TYPE = new Type<>(Hearthholm.id("open_orders"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenOrders> CODEC =
                Views.Orders.CODEC.map(OpenOrders::new, OpenOrders::view);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sets an order ("keep {@code amount} of {@code item}"); amount 0 removes it, -1 only refreshes. */
    public record OrderAction(BlockPos board, String item, int amount) implements CustomPacketPayload {
        public static final Type<OrderAction> TYPE = new Type<>(Hearthholm.id("order_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OrderAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OrderAction::board,
                ByteBufCodecs.stringUtf8(128), OrderAction::item,
                ByteBufCodecs.VAR_INT, OrderAction::amount,
                OrderAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenTrade(Views.Trade view) implements CustomPacketPayload {
        public static final Type<OpenTrade> TYPE = new Type<>(Hearthholm.id("open_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTrade> CODEC =
                Views.Trade.CODEC.map(OpenTrade::new, OpenTrade::view);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Trade with the caravan at {@code pos} (market stall or trader): buy offer {@code index}, or sell spare item {@code index}. */
    public record TradeAction(BlockPos pos, int action, int index) implements CustomPacketPayload {
        public static final int REFRESH = 0, BUY = 1, SELL = 2;
        public static final Type<TradeAction> TYPE = new Type<>(Hearthholm.id("trade_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TradeAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TradeAction::pos,
                ByteBufCodecs.VAR_INT, TradeAction::action,
                ByteBufCodecs.VAR_INT, TradeAction::index,
                TradeAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenBoard(Views.Board view) implements CustomPacketPayload {
        public static final Type<OpenBoard> TYPE = new Type<>(Hearthholm.id("open_board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenBoard> CODEC =
                Views.Board.CODEC.map(OpenBoard::new, OpenBoard::view);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Bulletin board: hand in the items of quest {@code quest} or claim its reward. */
    public record BoardAction(BlockPos pos, int action, int quest) implements CustomPacketPayload {
        public static final int REFRESH = 0, DELIVER = 1, CLAIM = 2;
        public static final Type<BoardAction> TYPE = new Type<>(Hearthholm.id("board_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BoardAction::pos,
                ByteBufCodecs.VAR_INT, BoardAction::action,
                ByteBufCodecs.VAR_INT, BoardAction::quest,
                BoardAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---- wave J2: map zones (generic; the miner of J3 reuses them with a layer Y) ----------------------------

    /** A rectangle of zone cells, inclusive corners. */
    public record Rect(int x0, int z0, int x1, int z1) {}

    /** Client asks for a fresh map snapshot of the zone of the work site at {@code pos} (throttled per player). */
    public record ZoneMapRequest(BlockPos pos, int layerY) implements CustomPacketPayload {
        public static final Type<ZoneMapRequest> TYPE = new Type<>(Hearthholm.id("zone_map_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneMapRequest> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ZoneMapRequest::pos,
                ByteBufCodecs.INT, ZoneMapRequest::layerY,
                ZoneMapRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One marker on the map: kind {@link #SITE}, {@link #CHEST} or {@link #WAREHOUSE} at a block column. */
    public record MapMarker(int kind, int x, int z) {
        public static final int SITE = 0, CHEST = 1, WAREHOUSE = 2;
    }

    /**
     * The map of a zone: one byte per cell (vanilla map colour id * 4 + brightness, 0 = unknown), two flag bits per cell
     * ({@link #LOG}, {@link #LEAF}, packed four to a byte), the layer's zone bits, markers and the work-site view (options).
     */
    public record ZoneMapData(Views.WorkSite view, int layerY, int size, int cell, int originX, int originZ,
                              byte[] colors, byte[] flags, long[] zoneBits, List<MapMarker> markers,
                              long[] ghostUp, long[] ghostDown, MineInfo info) implements CustomPacketPayload {
        public static final int LOG = 1, LEAF = 2;
        /** Layer-mode flags (wave J3), same two bits: an ore block at the layer; a painted cell the miner may not dig (red). */
        public static final int ORE = 1, BLOCKED = 2;
        public static final Type<ZoneMapData> TYPE = new Type<>(Hearthholm.id("zone_map_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneMapData> CODEC = StreamCodec.of(ZoneMapData::write, ZoneMapData::read);

        static void write(RegistryFriendlyByteBuf b, ZoneMapData d) {
            Views.WorkSite.CODEC.encode(b, d.view);
            b.writeInt(d.layerY);
            b.writeVarInt(d.size);
            b.writeVarInt(d.cell);
            b.writeInt(d.originX);
            b.writeInt(d.originZ);
            b.writeByteArray(d.colors);
            b.writeByteArray(d.flags);
            b.writeLongArray(d.zoneBits);
            b.writeVarInt(d.markers.size());
            for (MapMarker m : d.markers) { b.writeVarInt(m.kind); b.writeInt(m.x); b.writeInt(m.z); }
            b.writeLongArray(d.ghostUp);
            b.writeLongArray(d.ghostDown);
            b.writeInt(d.info.markerY); b.writeInt(d.info.minY); b.writeInt(d.info.maxY);
            b.writeVarInt(d.info.cells); b.writeVarInt(d.info.blocks); b.writeVarInt(d.info.seconds);
        }

        static ZoneMapData read(RegistryFriendlyByteBuf b) {
            Views.WorkSite view = Views.WorkSite.CODEC.decode(b);
            int layer = b.readInt(), size = b.readVarInt(), cell = b.readVarInt(), ox = b.readInt(), oz = b.readInt();
            byte[] colors = b.readByteArray(), flags = b.readByteArray();
            long[] bits = b.readLongArray();
            List<MapMarker> markers = new java.util.ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) markers.add(new MapMarker(b.readVarInt(), b.readInt(), b.readInt()));
            long[] up = b.readLongArray(), down = b.readLongArray();
            MineInfo info = new MineInfo(b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
            return new ZoneMapData(view, layer, size, cell, ox, oz, colors, flags, bits, markers, up, down, info);
        }

        /** The flag bits of cell {@code i}. */
        public int flag(int i) { return (flags[i >> 2] >> ((i & 3) * 2)) & 3; }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Wave J3: what the miner's map shows besides the layer: marker Y, world Y range and the plan summary (cells, blocks to dig, estimated seconds). */
    public record MineInfo(int markerY, int minY, int maxY, int cells, int blocks, int seconds) {
        public static final MineInfo NONE = new MineInfo(0, 0, 0, 0, 0, 0);
    }

    /**
     * A zone edit as a diff: {@link #SET} / {@link #CLEAR} the cell rectangles, {@link #CLEAR_ALL} cells of the layer or
     * {@link #CLEAR_EVERYTHING} (every layer, wave J3). {@code height} (1..4, 0 = default) is the dig height of painted cells.
     */
    public record ZoneEdit(BlockPos pos, int layerY, int op, List<Rect> rects, int height) implements CustomPacketPayload {
        public static final int SET = 0, CLEAR = 1, CLEAR_ALL = 2, CLEAR_EVERYTHING = 3;
        public static final int MAX_RECTS = 512;
        public static final Type<ZoneEdit> TYPE = new Type<>(Hearthholm.id("zone_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneEdit> CODEC = StreamCodec.of(ZoneEdit::write, ZoneEdit::read);

        public ZoneEdit(BlockPos pos, int layerY, int op, List<Rect> rects) { this(pos, layerY, op, rects, 0); }

        static void write(RegistryFriendlyByteBuf b, ZoneEdit e) {
            b.writeBlockPos(e.pos);
            b.writeInt(e.layerY);
            b.writeVarInt(e.op);
            b.writeVarInt(e.rects.size());
            for (Rect r : e.rects) { b.writeVarInt(r.x0); b.writeVarInt(r.z0); b.writeVarInt(r.x1); b.writeVarInt(r.z1); }
            b.writeVarInt(e.height);
        }

        static ZoneEdit read(RegistryFriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            int layer = b.readInt(), op = b.readVarInt();
            int n = Math.min(b.readVarInt(), MAX_RECTS + 1);
            List<Rect> rects = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) rects.add(new Rect(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
            return new ZoneEdit(pos, layer, op, rects, b.readVarInt());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
