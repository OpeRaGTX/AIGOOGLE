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
    /** Wave D: calls up to 4 combat settlers into a party; use again to send them home. */
    public static Item WAR_HORN;
    /** Wave D: brewed by healers from herbs; heals whoever drinks it. */
    public static Item HEALING_SALVE;
    /** Wave F: the in-game guide book, given once when a settlement is founded. */
    public static Item FOUNDERS_CHRONICLE;
    public static final List<Item> SPAWN_EGGS = new ArrayList<>();
    /** Wave H1: new foods by id path (fried_fish, ale, ...), in creative-tab order. */
    public static final java.util.Map<String, Item> FOODS = new java.util.LinkedHashMap<>();

    private ModItems() {}

    private static Item reg(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearthholm.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }

    private static void egg(String name, EntityType<?> type) {
        SPAWN_EGGS.add(reg(name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type)));
    }

    public static Item food(String name) {
        return FOODS.get(name);
    }

    /** What a food does when eaten: nutrition, saturation, and for drinks/bowls what is left over and a small buff. */
    private static void food(String name, int nutrition, float saturation, Item leftover, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> buff, int buffTicks, boolean drink) {
        net.minecraft.world.food.FoodProperties.Builder fb = new net.minecraft.world.food.FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation);
        Item.Properties props = new Item.Properties().stacksTo(leftover != null ? 16 : 64);
        if (drink) fb.alwaysEdible();
        if (drink || buff != null) {
            var cb = net.minecraft.world.item.component.Consumable.builder().consumeSeconds(1.6f);
            if (drink) cb.animation(net.minecraft.world.item.ItemUseAnimation.DRINK).sound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK).hasConsumeParticles(false);
            if (buff != null) cb.onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(new net.minecraft.world.effect.MobEffectInstance(buff, buffTicks)));
            props.food(fb.build(), cb.build());
        } else {
            props.food(fb.build());
        }
        if (leftover != null) props.usingConvertsTo(leftover);
        FOODS.put(name, reg(name, Item::new, props));
    }

    private static void foods() {
        var bowl = net.minecraft.world.item.Items.BOWL;
        var bottle = net.minecraft.world.item.Items.GLASS_BOTTLE;
        food("fried_fish", 6, 0.7f, null, null, 0, false);
        food("fish_pie", 8, 0.8f, null, null, 0, false);
        food("smoked_fish", 6, 0.6f, null, null, 0, false);
        food("fish_soup", 7, 0.7f, bowl, null, 0, false);
        food("meat_stew", 10, 0.8f, bowl, null, 0, false);
        food("sausage", 5, 0.6f, null, null, 0, false);
        food("cheese_wedge", 4, 0.4f, null, null, 0, false);
        food("berry_pie", 8, 0.3f, null, null, 0, false);
        food("apple_pie", 8, 0.3f, null, null, 0, false);
        food("meat_pie", 10, 0.7f, null, null, 0, false);
        food("rye_loaf", 6, 0.7f, null, null, 0, false);
        food("honey_bun", 6, 0.4f, null, null, 0, false);
        food("porridge", 6, 0.5f, bowl, null, 0, false);
        food("roast_vegetables", 8, 0.8f, null, null, 0, false);
        food("ale", 2, 0.2f, bottle, net.minecraft.world.effect.MobEffects.RESISTANCE, 300, true);
        food("mead", 3, 0.3f, bottle, net.minecraft.world.effect.MobEffects.STRENGTH, 200, true);
        food("cider", 3, 0.3f, bottle, net.minecraft.world.effect.MobEffects.SPEED, 400, true);
        food("herbal_tea", 2, 0.4f, bottle, net.minecraft.world.effect.MobEffects.REGENERATION, 100, true);
        // cooked meals count as "tasty meals" for settlers
        io.github.operagtx.hearthholm.crafting.Workshop.MEALS.addAll(FOODS.values());
    }

    public static void init() {
        foods();
        HEARTH_MARK = reg("hearth_mark", Item::new, new Item.Properties().stacksTo(64));
        RIFT_SHARD = reg("rift_shard", Item::new, new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
        FOUNDERS_WHISTLE = reg("founders_whistle", FoundersWhistleItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        WAR_HORN = reg("war_horn", io.github.operagtx.hearthholm.item.WarHornItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        HEALING_SALVE = reg("healing_salve", io.github.operagtx.hearthholm.item.HealingSalveItem::new, new Item.Properties().stacksTo(16));
        FOUNDERS_CHRONICLE = reg("founders_chronicle", io.github.operagtx.hearthholm.item.ChronicleItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        egg("ash_raider", ModEntities.ASH_RAIDER);
        egg("ash_arsonist", ModEntities.ASH_ARSONIST);
        egg("ash_archer", ModEntities.ASH_ARCHER);
        egg("ash_brute", ModEntities.ASH_BRUTE);
        egg("ash_chieftain", ModEntities.ASH_CHIEFTAIN);
        egg("rift_wisp", ModEntities.RIFT_WISP);
    }
}
