package io.github.operagtx.hearthholm.api;

import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.core.Trait;
import io.github.operagtx.hearthholm.core.Traits;
import io.github.operagtx.hearthholm.worksite.WorkSiteType;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;

/**
 * Public extension points. Add-ons (or later versions of this mod) register new content here
 * during {@code onInitialize}; existing content and saves stay untouched.
 *
 * <ul>
 *   <li>A new profession = {@link Profession.Builder} + a {@code Job} implementation.</li>
 *   <li>A new work zone = {@link WorkSiteType} (its options get a config screen for free)
 *       + a block registered with {@code ModBlocks.workSite(...)}.</li>
 *   <li>A new trait = {@link Trait} record with modifiers.</li>
 *   <li>A new blueprint = JSON in {@code data/<ns>/blueprints/} listed in its {@code index.json},
 *       or {@link #registerBlueprint}.</li>
 * </ul>
 */
public final class HearthholmApi {
    private HearthholmApi() {}

    public static Profession registerProfession(Profession profession) {
        return Professions.REGISTRY.register(profession.id(), profession);
    }

    public static WorkSiteType registerWorkSite(WorkSiteType type) {
        return WorkSiteTypes.REGISTRY.register(type.id(), type);
    }

    /** Wave J1: replaces the default adapter of a profession (job, site screen, zone) - register before {@code ProfessionModules.bootstrap}. */
    public static ProfessionModule registerModule(ProfessionModule module) {
        return ProfessionModules.register(module);
    }

    public static Trait registerTrait(Trait trait) {
        return Traits.REGISTRY.register(trait.id(), trait);
    }

    public static void registerBlueprint(Blueprint blueprint) {
        BlueprintLibrary.register(blueprint);
    }
}
