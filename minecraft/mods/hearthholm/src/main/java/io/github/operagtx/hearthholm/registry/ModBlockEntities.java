package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.TownHallDeskBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static BlockEntityType<TownHallDeskBlockEntity> TOWN_HALL_DESK;
    public static BlockEntityType<WorkSiteBlockEntity> WORK_SITE;

    private ModBlockEntities() {}

    public static void init() {
        TOWN_HALL_DESK = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hearthholm.id("town_hall_desk"),
                FabricBlockEntityTypeBuilder.create(TownHallDeskBlockEntity::new, ModBlocks.TOWN_HALL_DESK).build());
        WORK_SITE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hearthholm.id("work_site"),
                FabricBlockEntityTypeBuilder.create(WorkSiteBlockEntity::new, ModBlocks.WORK_SITES.toArray(Block[]::new)).build());
    }
}
