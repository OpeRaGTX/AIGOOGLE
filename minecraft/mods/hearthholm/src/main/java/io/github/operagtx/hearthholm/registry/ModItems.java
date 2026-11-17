package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Plain items (block items are registered by {@link ModBlocks}). */
public final class ModItems {
    /** Settlement currency, minted in the town hall from surplus goods. */
    public static Item HEARTH_MARK;

    private ModItems() {}

    public static void init() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearthholm.id("hearth_mark"));
        HEARTH_MARK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(64)));
    }
}
