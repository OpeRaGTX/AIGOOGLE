package io.github.operagtx.hearthholm.zone;

import io.github.operagtx.hearthholm.api.HearthholmApi;
import io.github.operagtx.hearthholm.api.ProfessionModule;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinerJob;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;

/** Wave J3: the miner as a map-zone profession - right-click the stone with the pickaxe to draw the 3D dig plan. */
public final class MinerModule {
    private MinerModule() {}

    /** Call after {@code WorkSiteTypes.bootstrap()} and before {@code ProfessionModules.bootstrap()}. */
    public static void register() {
        HearthholmApi.registerModule(ProfessionModule.of(Professions.MINER, WorkSiteTypes.REGISTRY.get(WorkSiteTypes.MINE_ID).orElseThrow(),
                MinerJob::new, ZoneMapService::open,
                new ProfessionModule.ZoneData(ProfessionModule.ZoneData.Kind.MAP, be -> MinePlanner.previewBox(be))));
    }
}
