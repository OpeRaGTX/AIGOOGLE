package io.github.operagtx.hearthholm.network;

import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class HhNetwork {
    private HhNetwork() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenTownHall.TYPE, Payloads.OpenTownHall.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenSettler.TYPE, Payloads.OpenSettler.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenWorkSite.TYPE, Payloads.OpenWorkSite.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenOrders.TYPE, Payloads.OpenOrders.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.OrderAction.TYPE, Payloads.OrderAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.TownHallAction.TYPE, Payloads.TownHallAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.SettlerAction.TYPE, Payloads.SettlerAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.WorkSiteAction.TYPE, Payloads.WorkSiteAction.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.TownHallAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleTownHall(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SettlerAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleSettler(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.OrderAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleOrders(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.WorkSiteAction.TYPE,
                (p, ctx) -> ctx.server().execute(() -> SettlementService.handleWorkSite(ctx.player(), p)));
    }
}
