package io.github.operagtx.hearthholm;

import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.core.Traits;
import io.github.operagtx.hearthholm.network.HhNetwork;
import io.github.operagtx.hearthholm.registry.*;
import io.github.operagtx.hearthholm.settlement.SettlementEvents;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hearthholm entry point. Order matters: content registries (traits, professions, work sites)
 * are filled before vanilla registries so blocks and entities can reference them.
 */
public final class Hearthholm implements ModInitializer {
    public static final String MOD_ID = "hearthholm";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Traits.bootstrap();
        Professions.bootstrap();
        WorkSiteTypes.bootstrap();
        ModBlocks.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModItems.init();
        ModTab.init();
        BlueprintLibrary.bootstrap();
        HhNetwork.init();
        SettlementEvents.init();
        LOG.info("Hearthholm ready: {} professions, {} work sites, {} blueprints",
                Professions.REGISTRY.size(), WorkSiteTypes.REGISTRY.size(), BlueprintLibrary.all().size());
    }
}
