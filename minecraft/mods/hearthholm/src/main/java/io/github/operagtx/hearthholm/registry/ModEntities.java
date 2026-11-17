package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static EntityType<SettlerEntity> SETTLER;

    private ModEntities() {}

    public static void init() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hearthholm.id("settler"));
        SETTLER = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<SettlerEntity>of(SettlerEntity::new, MobCategory.MISC)
                        .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build(key));
        FabricDefaultAttributeRegistry.register(SETTLER, SettlerEntity.createAttributes());
    }
}
