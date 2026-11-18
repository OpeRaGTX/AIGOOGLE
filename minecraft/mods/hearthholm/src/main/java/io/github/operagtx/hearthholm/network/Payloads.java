package io.github.operagtx.hearthholm.network;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
        public static final int REFRESH = 0, HIRE = 1, REROLL = 2, RESCAN = 3, RENAME = 4, OPEN_RESIDENT = 5, MINT = 6, FESTIVAL = 7, ALARM = 8;
        public static final Type<TownHallAction> TYPE = new Type<>(Hearthholm.id("town_hall_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TownHallAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TownHallAction::desk,
                ByteBufCodecs.VAR_INT, TownHallAction::action,
                ByteBufCodecs.VAR_INT, TownHallAction::arg,
                ByteBufCodecs.stringUtf8(256), TownHallAction::text,
                TownHallAction::new);
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
}
