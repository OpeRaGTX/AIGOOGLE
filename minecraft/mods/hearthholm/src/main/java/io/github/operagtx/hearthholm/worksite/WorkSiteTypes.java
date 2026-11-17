package io.github.operagtx.hearthholm.worksite;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.api.KeyedRegistry;
import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class WorkSiteTypes {
    public static final KeyedRegistry<WorkSiteType> REGISTRY = new KeyedRegistry<>("work site");

    public static final Identifier CONSTRUCTION_ID = Hearthholm.id("construction");
    public static final Identifier MINE_ID = Hearthholm.id("mine");
    public static final Identifier FOREST_ID = Hearthholm.id("forest");
    public static final Identifier FARM_ID = Hearthholm.id("farm");
    public static final Identifier FISHING_ID = Hearthholm.id("fishing");
    public static final Identifier PATROL_ID = Hearthholm.id("patrol");
    public static final Identifier TERRITORY_ID = Hearthholm.id("territory");

    private WorkSiteTypes() {}

    public static WorkSiteType get(Identifier id) {
        return REGISTRY.get(id).orElseThrow(() -> new IllegalStateException("Unknown work site " + id));
    }

    private static AABB around(BlockPos p, int r, int down, int up) {
        return new AABB(p.getX() - r, p.getY() - down, p.getZ() - r, p.getX() + r + 1, p.getY() + up, p.getZ() + r + 1);
    }

    public static void bootstrap() {
        REGISTRY.register(CONSTRUCTION_ID, new WorkSiteType(CONSTRUCTION_ID, Hearthholm.id("builder"), List.of(
                SiteOption.dynamic("blueprint", BlueprintLibrary::labelKeys),
                SiteOption.toggle("build", false)), 0xF2B33D, be -> {
            Blueprint bp = BlueprintLibrary.byIndex(be.option("blueprint"));
            return bp == null ? null : bp.worldBox(be.getBlockPos(), be.facing());
        }));
        REGISTRY.register(MINE_ID, new WorkSiteType(MINE_ID, Hearthholm.id("miner"), List.of(
                SiteOption.choice("mode", 0, "spiral", "ladder", "tunnel"),
                SiteOption.range("depth", 4, 96, 4, 24),
                SiteOption.range("length", 8, 96, 8, 32),
                SiteOption.toggle("branches", true),
                SiteOption.toggle("torches", true)), 0x9AA3AD, MinePlanner::previewBox));
        REGISTRY.register(FOREST_ID, new WorkSiteType(FOREST_ID, Hearthholm.id("lumberjack"), List.of(
                SiteOption.range("radius", 6, 24, 2, 12),
                SiteOption.toggle("replant", true)), 0x5FA04E, be -> around(be.getBlockPos(), be.option("radius"), 1, 10)));
        REGISTRY.register(FARM_ID, new WorkSiteType(FARM_ID, Hearthholm.id("farmer"), List.of(
                SiteOption.range("radius", 2, 8, 1, 4),
                SiteOption.choice("crop", 0, "auto", "wheat", "carrot", "potato", "beetroot"),
                SiteOption.toggle("till", true)), 0xD8C25A, be -> around(be.getBlockPos(), be.option("radius"), 1, 2)));
        REGISTRY.register(FISHING_ID, new WorkSiteType(FISHING_ID, Hearthholm.id("fisher"), List.of(
                SiteOption.toggle("keep_junk", false)), 0x4FA3C7, be -> around(be.getBlockPos(), 4, 2, 2)));
        REGISTRY.register(PATROL_ID, new WorkSiteType(PATROL_ID, Hearthholm.id("guard"), List.of(
                SiteOption.range("wait", 5, 60, 5, 15),
                SiteOption.range("radius", 4, 24, 2, 8)), 0x6C7FD8, be -> around(be.getBlockPos(), be.option("radius"), 0, 3)));
        REGISTRY.register(TERRITORY_ID, new WorkSiteType(TERRITORY_ID, Hearthholm.id("warrior"), List.of(
                SiteOption.range("radius", 8, 48, 4, 16),
                SiteOption.choice("stance", 0, "patrol", "follow", "hold")), 0xC9483C, be -> around(be.getBlockPos(), be.option("radius"), 0, 3)));
    }
}
