package io.github.operagtx.hearthholm.client;

import io.github.operagtx.hearthholm.client.render.SettlerRenderer;
import io.github.operagtx.hearthholm.client.screen.OrdersScreen;
import io.github.operagtx.hearthholm.client.screen.SettlerScreen;
import io.github.operagtx.hearthholm.client.screen.TownHallScreen;
import io.github.operagtx.hearthholm.client.screen.WorkSiteScreen;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class HearthholmClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SETTLER, SettlerRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASH_RAIDER, io.github.operagtx.hearthholm.client.render.AshbornRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASH_ARSONIST, io.github.operagtx.hearthholm.client.render.AshbornRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASH_ARCHER, io.github.operagtx.hearthholm.client.render.AshbornRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASH_BRUTE, io.github.operagtx.hearthholm.client.render.AshbornRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASH_CHIEFTAIN, io.github.operagtx.hearthholm.client.render.AshbornRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
                io.github.operagtx.hearthholm.client.render.RiftWispModel.LAYER, io.github.operagtx.hearthholm.client.render.RiftWispModel::createLayer);
        EntityRendererRegistry.register(ModEntities.RIFT_WISP, io.github.operagtx.hearthholm.client.render.RiftWispRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEAT, io.github.operagtx.hearthholm.client.render.SeatRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRADER, io.github.operagtx.hearthholm.client.render.TraderRenderer::new);
        net.minecraft.client.gui.screens.MenuScreens.register(io.github.operagtx.hearthholm.registry.ModMenus.SETTLER,
                io.github.operagtx.hearthholm.client.screen.SettlerInventoryScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenTrade.TYPE,
                (p, ctx) -> ctx.client().execute(() -> io.github.operagtx.hearthholm.client.screen.TradeScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenBoard.TYPE,
                (p, ctx) -> ctx.client().execute(() -> io.github.operagtx.hearthholm.client.screen.BoardScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenTownHall.TYPE,
                (p, ctx) -> ctx.client().execute(() -> TownHallScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OwnersView.TYPE,
                (p, ctx) -> ctx.client().execute(() -> TownHallScreen.showOwners(p)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenSettler.TYPE,
                (p, ctx) -> ctx.client().execute(() -> SettlerScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenWorkSite.TYPE,
                (p, ctx) -> ctx.client().execute(() -> WorkSiteScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.ZoneMapData.TYPE,
                (p, ctx) -> ctx.client().execute(() -> io.github.operagtx.hearthholm.client.screen.ZoneMapScreen.show(p)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenOrders.TYPE,
                (p, ctx) -> ctx.client().execute(() -> OrdersScreen.show(p.view())));
        io.github.operagtx.hearthholm.item.ChronicleItem.OPENER = io.github.operagtx.hearthholm.client.screen.ChronicleScreen::open;
        io.github.operagtx.hearthholm.client.particle.HearthParticles.register();   // wave L3
        ClientTickEvents.END_CLIENT_TICK.register(ZonePreview::tick);
    }
}
