package io.github.operagtx.hearthholm.network;

import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class HhNetwork {
    private HhNetwork() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenTownHall.TYPE, Payloads.OpenTownHall.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OwnersView.TYPE, Payloads.OwnersView.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenSettler.TYPE, Payloads.OpenSettler.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenWorkSite.TYPE, Payloads.OpenWorkSite.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenOrders.TYPE, Payloads.OpenOrders.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenTrade.TYPE, Payloads.OpenTrade.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenBoard.TYPE, Payloads.OpenBoard.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.TradeAction.TYPE, Payloads.TradeAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.BoardAction.TYPE, Payloads.BoardAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.OrderAction.TYPE, Payloads.OrderAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.TownHallAction.TYPE, Payloads.TownHallAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.SettlerAction.TYPE, Payloads.SettlerAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.WorkSiteAction.TYPE, Payloads.WorkSiteAction.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(Payloads.ZoneMapData.TYPE, Payloads.ZoneMapData.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.ZoneMapRequest.TYPE, Payloads.ZoneMapRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.ZoneEdit.TYPE, Payloads.ZoneEdit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ZoneMapRequest.TYPE,
                (p, ctx) -> ctx.server().execute(() -> io.github.operagtx.hearthholm.zone.ZoneMapService.handleRequest(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ZoneEdit.TYPE,
                (p, ctx) -> ctx.server().execute(() -> io.github.operagtx.hearthholm.zone.ZoneMapService.handleEdit(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TownHallAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleTownHall(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SettlerAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleSettler(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.OrderAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleOrders(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.WorkSiteAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleWorkSite(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TradeAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> io.github.operagtx.hearthholm.town.Caravan.handle(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.BoardAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> io.github.operagtx.hearthholm.town.Quests.handle(ctx.player(), p)));
    }
}
