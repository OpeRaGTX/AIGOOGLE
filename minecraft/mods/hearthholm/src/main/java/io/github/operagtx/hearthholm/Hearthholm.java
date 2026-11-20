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

    /** Rift lanterns are points of interest so the spawn check can find them cheaply. */
    public static net.minecraft.world.entity.ai.village.poi.PoiType RIFT_LANTERN_POI;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Traits.bootstrap();
        Professions.bootstrap();
        WorkSiteTypes.bootstrap();
        io.github.operagtx.hearthholm.zone.LumberjackModule.register();   // wave J2: map zone for the lumberjack
        io.github.operagtx.hearthholm.zone.MinerModule.register();        // wave J3: drawn 3D dig plan for the miner
        io.github.operagtx.hearthholm.zone.ZoneModules.register();        // wave L2: map zones for the farmer, the fisher and the warrior
        io.github.operagtx.hearthholm.api.ProfessionModules.bootstrap();   // wave J1: modules for the professions above
        ModSounds.init();
        io.github.operagtx.hearthholm.registry.ModParticles.init();   // wave L3: the mod's own particle types
        ModBlocks.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModEntities.init();
        ModItems.init();
        ModTab.init();
        BlueprintLibrary.bootstrap();
        HhNetwork.init();
        io.github.operagtx.hearthholm.crafting.Mint.load();
        io.github.operagtx.hearthholm.core.Balance.load();
        io.github.operagtx.hearthholm.threat.ThreatConfig.load();
        RIFT_LANTERN_POI = net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper.register(id("rift_lantern"), 0, 1, ModBlocks.RIFT_LANTERN);
        SettlementEvents.init();
        io.github.operagtx.hearthholm.town.Quests.init();
        io.github.operagtx.hearthholm.town.Loot.init();
        LOG.info("Hearthholm ready: {} professions, {} work sites, {} blueprints",
                Professions.REGISTRY.size(), WorkSiteTypes.REGISTRY.size(), BlueprintLibrary.all().size());
    }
}
