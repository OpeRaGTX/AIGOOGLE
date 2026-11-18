package io.github.operagtx.hearthholm.api;

import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteType;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Wave J1: everything one profession plugs into the mod, in one place - its work-site type, the screen that opens from the
 * site block, its job factory and its optional zone data. The professions registered so far get thin adapters
 * ({@link #adapt}); the lumberjack and miner map zones (J2/J3) register a module with {@link ZoneData.Kind#MAP} and their
 * own {@link SiteScreenOpener}. Saves are untouched: nothing here is persisted.
 */
public interface ProfessionModule {
    Profession profession();

    /** The work-site block type of this profession, or null (workshop and town professions have none). */
    @Nullable WorkSiteType workSite();

    Function<SettlerEntity, Job> jobFactory();

    /** What opens when a player uses the site block. */
    SiteScreenOpener screenOpener();

    /** The zone the worker operates in, or null when the profession has none. */
    @Nullable ZoneData zone();

    /** Opens the configuration screen of a work site for a player (permission is checked before). */
    @FunctionalInterface
    interface SiteScreenOpener {
        /** The generic option screen every work-site type gets for free. */
        SiteScreenOpener GENERIC = (player, site, settlement) -> SettlementService.sendWorkSiteScreen(player, site, settlement);

        void open(ServerPlayer player, WorkSiteBlockEntity site, @Nullable Settlement settlement);
    }

    /** Zone of a profession: a simple box around the block today, a hand-drawn map later (J2/J3). */
    record ZoneData(Kind kind, Function<WorkSiteBlockEntity, @Nullable AABB> bounds) {
        public enum Kind { BOX, MAP }
    }

    /** A module made from what a profession already declares (job from its builder, site type from the registry). */
    static ProfessionModule adapt(Profession p) {
        return of(p, p.siteType() == null ? null : WorkSiteTypes.REGISTRY.get(p.siteType()).orElse(null), p::createJob, SiteScreenOpener.GENERIC, null);
    }

    static ProfessionModule of(Profession p, @Nullable WorkSiteType site, Function<SettlerEntity, Job> job, SiteScreenOpener opener, @Nullable ZoneData zone) {
        ZoneData z = zone != null || site == null ? zone : new ZoneData(ZoneData.Kind.BOX, site.preview());
        return new ProfessionModule() {
            @Override public Profession profession() { return p; }
            @Override public @Nullable WorkSiteType workSite() { return site; }
            @Override public Function<SettlerEntity, Job> jobFactory() { return job; }
            @Override public SiteScreenOpener screenOpener() { return opener; }
            @Override public @Nullable ZoneData zone() { return z; }
        };
    }
}
