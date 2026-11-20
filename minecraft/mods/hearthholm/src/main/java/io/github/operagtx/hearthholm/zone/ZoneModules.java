package io.github.operagtx.hearthholm.zone;

import io.github.operagtx.hearthholm.api.HearthholmApi;
import io.github.operagtx.hearthholm.api.ProfessionModule;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.entity.ai.jobs.FarmerJob;
import io.github.operagtx.hearthholm.entity.ai.jobs.FisherJob;
import io.github.operagtx.hearthholm.entity.ai.jobs.WarriorJob;
import io.github.operagtx.hearthholm.worksite.WorkSiteType;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Wave L2: the professions that draw their work area on the zone map like the lumberjack (J2) and the miner (J3): the farmer (30 x 30 blocks, the barrel of fertilizer),
 * the fisher (10 x 10, the bucket with a fish) and the warrior (50 x 50, the stone with a sword). One table, one line per profession: a new zone profession adds a
 * line here, a {@code WorkZone.Spec} in {@code WorkSiteTypes.bootstrap} and the job's use of {@code WorkSiteBlockEntity.zone()}.
 * Call after {@code WorkSiteTypes.bootstrap()} and before {@code ProfessionModules.bootstrap()}.
 */
public final class ZoneModules {
    private ZoneModules() {}

    public static void register() {
        add(Professions.FARMER, WorkSiteTypes.FARM_ID, FarmerJob::new);
        add(Professions.FISHER, WorkSiteTypes.FISHING_ID, FisherJob::new);
        add(Professions.WARRIOR, WorkSiteTypes.TERRITORY_ID, WarriorJob::new);
    }

    private static void add(Profession profession, Identifier siteType, Function<SettlerEntity, Job> job) {
        WorkSiteType type = WorkSiteTypes.REGISTRY.get(siteType).orElseThrow();
        HearthholmApi.registerModule(ProfessionModule.of(profession, type, job, ZoneMapService::open,
                new ProfessionModule.ZoneData(ProfessionModule.ZoneData.Kind.MAP, type.preview())));
    }
}
