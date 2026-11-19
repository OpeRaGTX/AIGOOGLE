package io.github.operagtx.hearthholm.api;

import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Wave J1: registry of {@link ProfessionModule}s, keyed by profession id. Lookups fall back to an adapter of the profession. */
public final class ProfessionModules {
    public static final KeyedRegistry<ProfessionModule> REGISTRY = new KeyedRegistry<>("profession module");

    private ProfessionModules() {}

    /** Registers an adapter for every profession declared so far (after {@code Professions} and {@code WorkSiteTypes}). */
    public static void bootstrap() {
        for (Profession p : Professions.REGISTRY.values()) {
            if (REGISTRY.get(p.id()).isEmpty()) REGISTRY.register(p.id(), ProfessionModule.adapt(p));
        }
    }

    public static ProfessionModule register(ProfessionModule m) {
        return REGISTRY.register(m.profession().id(), m);
    }

    public static ProfessionModule of(Profession p) {
        return REGISTRY.get(p.id()).orElseGet(() -> ProfessionModule.adapt(p));
    }

    /** The module that owns a work-site type (matched by the profession of the type), or null. */
    public static @Nullable ProfessionModule forSite(Identifier professionId) {
        return REGISTRY.get(professionId).orElse(null);
    }

    public static Job createJob(SettlerEntity s) {
        return of(s.profession()).jobFactory().apply(s);
    }

    public static void openSite(net.minecraft.server.level.ServerPlayer player, WorkSiteBlockEntity site, @Nullable io.github.operagtx.hearthholm.settlement.Settlement settlement) {
        ProfessionModule m = forSite(site.type().profession());
        (m != null ? m.screenOpener() : ProfessionModule.SiteScreenOpener.GENERIC).open(player, site, settlement);
    }
}
