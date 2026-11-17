package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.item.FoundersWhistleItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Plain items (block items are registered by {@link ModBlocks}). */
public final class ModItems {
    /** Settlement currency, minted in the town hall from surplus goods. */
    public static Item HEARTH_MARK;
    /** Dropped by closed rifts and rift wisps; crafted into the rift lantern. */
    public static Item RIFT_SHARD;
    /** Creative-only debug tool: forces a raid, a rift or the chieftain. */
    public static Item FOUNDERS_WHISTLE;
    public static final List<Item> SPAWN_EGGS = new ArrayList<>();

    private ModItems() {}

    private static Item reg(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearthholm.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }

    private static void egg(String name, EntityType<?> type) {
        SPAWN_EGGS.add(reg(name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type)));
    }

    public static void init() {
        HEARTH_MARK = reg("hearth_mark", Item::new, new Item.Properties().stacksTo(64));
        RIFT_SHARD = reg("rift_shard", Item::new, new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
        FOUNDERS_WHISTLE = reg("founders_whistle", FoundersWhistleItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        egg("ash_raider", ModEntities.ASH_RAIDER);
        egg("ash_arsonist", ModEntities.ASH_ARSONIST);
        egg("ash_archer", ModEntities.ASH_ARCHER);
        egg("ash_brute", ModEntities.ASH_BRUTE);
        egg("ash_chieftain", ModEntities.ASH_CHIEFTAIN);
        egg("rift_wisp", ModEntities.RIFT_WISP);
    }
}
