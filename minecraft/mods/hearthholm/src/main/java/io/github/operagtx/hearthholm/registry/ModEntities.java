package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ashborn.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static EntityType<SettlerEntity> SETTLER;
    public static EntityType<AshRaiderEntity> ASH_RAIDER;
    public static EntityType<AshArsonistEntity> ASH_ARSONIST;
    public static EntityType<AshArcherEntity> ASH_ARCHER;
    public static EntityType<AshBruteEntity> ASH_BRUTE;
    public static EntityType<AshChieftainEntity> ASH_CHIEFTAIN;
    public static EntityType<RiftWispEntity> RIFT_WISP;
    public static EntityType<io.github.operagtx.hearthholm.entity.trader.TraderEntity> TRADER;
    /** Wave H1: invisible seat for chairs and stools. */
    public static EntityType<io.github.operagtx.hearthholm.entity.SeatEntity> SEAT;

    private ModEntities() {}

    private static <T extends Entity> EntityType<T> reg(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hearthholm.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        SETTLER = reg("settler", EntityType.Builder.<SettlerEntity>of(SettlerEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10));
        FabricDefaultAttributeRegistry.register(SETTLER, SettlerEntity.createAttributes());
        // wave C: the Ashborn and the rift wisp
        ASH_RAIDER = reg("ash_raider", EntityType.Builder.<AshRaiderEntity>of(AshRaiderEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
        ASH_ARSONIST = reg("ash_arsonist", EntityType.Builder.<AshArsonistEntity>of(AshArsonistEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
        ASH_ARCHER = reg("ash_archer", EntityType.Builder.<AshArcherEntity>of(AshArcherEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
        ASH_BRUTE = reg("ash_brute", EntityType.Builder.<AshBruteEntity>of(AshBruteEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
        ASH_CHIEFTAIN = reg("ash_chieftain", EntityType.Builder.<AshChieftainEntity>of(AshChieftainEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).fireImmune().clientTrackingRange(12));
        RIFT_WISP = reg("rift_wisp", EntityType.Builder.<RiftWispEntity>of(RiftWispEntity::new, MobCategory.MONSTER)
                .sized(0.5f, 0.5f).eyeHeight(0.25f).clientTrackingRange(8));
        TRADER = reg("trader", EntityType.Builder.<io.github.operagtx.hearthholm.entity.trader.TraderEntity>of(io.github.operagtx.hearthholm.entity.trader.TraderEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
        FabricDefaultAttributeRegistry.register(TRADER, io.github.operagtx.hearthholm.entity.trader.TraderEntity.createAttributes());
        SEAT = reg("seat", EntityType.Builder.<io.github.operagtx.hearthholm.entity.SeatEntity>of(io.github.operagtx.hearthholm.entity.SeatEntity::new, MobCategory.MISC)
                .sized(0.1f, 0.1f).noSummon().clientTrackingRange(6));
        FabricDefaultAttributeRegistry.register(ASH_RAIDER, AshbornEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ASH_ARSONIST, AshArsonistEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ASH_ARCHER, AshArcherEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ASH_BRUTE, AshBruteEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ASH_CHIEFTAIN, AshChieftainEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIFT_WISP, RiftWispEntity.createAttributes());
    }
}
