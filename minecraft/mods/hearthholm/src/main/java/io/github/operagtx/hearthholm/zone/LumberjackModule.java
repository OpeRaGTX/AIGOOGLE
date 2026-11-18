package io.github.operagtx.hearthholm.zone;

import io.github.operagtx.hearthholm.api.HearthholmApi;
import io.github.operagtx.hearthholm.api.ProfessionModule;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.ai.jobs.LumberjackJob;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;

/** Wave J2: the lumberjack as the first map-zone profession (right-click the chopping block to draw the zone). */
public final class LumberjackModule {
    private LumberjackModule() {}

    /** Call after {@code WorkSiteTypes.bootstrap()} and before {@code ProfessionModules.bootstrap()}. */
    public static void register() {
        HearthholmApi.registerModule(ProfessionModule.of(Professions.LUMBERJACK, WorkSiteTypes.REGISTRY.get(WorkSiteTypes.FOREST_ID).orElseThrow(),
                LumberjackJob::new, ZoneMapService::open,
                new ProfessionModule.ZoneData(ProfessionModule.ZoneData.Kind.MAP,
                        be -> be.hasZone() ? be.zone().bounds(be.getBlockPos().getY() - 1, be.getBlockPos().getY() + 10) : null)));
    }
}
