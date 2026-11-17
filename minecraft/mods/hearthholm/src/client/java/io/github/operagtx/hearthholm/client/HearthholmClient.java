package io.github.operagtx.hearthholm.client;

import io.github.operagtx.hearthholm.client.render.SettlerRenderer;
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
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenTownHall.TYPE,
                (p, ctx) -> ctx.client().execute(() -> TownHallScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenSettler.TYPE,
                (p, ctx) -> ctx.client().execute(() -> SettlerScreen.show(p.view())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenWorkSite.TYPE,
                (p, ctx) -> ctx.client().execute(() -> WorkSiteScreen.show(p.view())));
        ClientTickEvents.END_CLIENT_TICK.register(ZonePreview::tick);
    }
}
