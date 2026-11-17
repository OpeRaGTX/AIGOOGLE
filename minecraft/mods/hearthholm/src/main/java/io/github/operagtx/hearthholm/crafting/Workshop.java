package io.github.operagtx.hearthholm.crafting;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The kinds of workshop (plus the herbalist table and the barracks rack). Each one is a building role (bit flag in {@code BuildingRecord}),
 * an anchor block, a crafter profession, a curated list of things the crafter may make (also the
 * order-board catalogue) and the vanilla recipe types it may use. Smelting-type recipes need a
 * furnace in the room, except in the kitchen where the stove itself is the heat source.
 */
public enum Workshop {
    CARPENTRY("carpentry", 8, "carpenter", false, List.of(RecipeType.CRAFTING),
            List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.STICK, Items.CHEST, Items.BARREL,
                    Items.LADDER, Items.CRAFTING_TABLE, Items.OAK_DOOR, Items.OAK_TRAPDOOR, Items.OAK_FENCE, Items.BOWL,
                    Items.BED.red(), Items.TORCH)),
    MASONRY("masonry", 16, "mason", false, List.of(RecipeType.CRAFTING, RecipeType.STONECUTTING, RecipeType.SMELTING),
            List.of(Items.STONE_BRICKS, Items.STONE, Items.SMOOTH_STONE, Items.STONE_BRICK_STAIRS, Items.STONE_BRICK_SLAB,
                    Items.COBBLESTONE_STAIRS, Items.COBBLESTONE_SLAB, Items.COBBLESTONE_WALL, Items.STONE_STAIRS,
                    Items.STONE_SLAB, Items.FURNACE)),
    SMITHY("smithy", 32, "smith", false, List.of(RecipeType.CRAFTING, RecipeType.BLASTING, RecipeType.SMELTING),
            List.of(Items.IRON_INGOT, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SWORD,
                    Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.SHEARS,
                    Items.BUCKET, Items.LANTERN, Items.IRON_BARS)),
    KITCHEN("kitchen", 64, "cook", true, List.of(RecipeType.CRAFTING, RecipeType.SMOKING, RecipeType.SMELTING, RecipeType.CAMPFIRE_COOKING),
            List.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
                    Items.COOKED_MUTTON, Items.COOKED_COD, Items.COOKED_SALMON, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP,
                    Items.PUMPKIN_PIE, Items.COOKIE)),
    /** Wave D: the healer's herbalist table. No orders: the healer brews salves on its own (see HealerJob). */
    HERBALISM("herbalism", 128, "healer", false, List.of(), List.of()),
    /** Wave D: a barracks is a room with a weapon rack; combat settlers rest there. Has no crafter. */
    BARRACKS("barracks", 256, "barracks_none", false, List.of(), List.of()),
    /** Wave E: a tavern is a room with a keg; the innkeeper serves food from storage, off-duty settlers gather there in the evening. */
    TAVERN("tavern", 512, "innkeeper", false, List.of(), List.of());

    /** Without any kitchen order, cooks keep this many meals in the warehouse. */
    public static final int DEFAULT_MEALS = 32;

    private final String key;
    private final int role;
    private final Identifier profession;
    private final boolean ownHeat;
    private final List<RecipeType<?>> types;
    private final List<Item> catalog;

    Workshop(String key, int role, String profession, boolean ownHeat, List<RecipeType<?>> types, List<Item> catalog) {
        this.key = key;
        this.role = role;
        this.profession = Hearthholm.id(profession);
        this.ownHeat = ownHeat;
        this.types = types;
        this.catalog = catalog;
    }

    public String key() { return key; }
    public int role() { return role; }
    public Identifier profession() { return profession; }
    public List<Item> catalog() { return catalog; }
    public List<RecipeType<?>> recipeTypes() { return types; }

    /** Whether cooking-type recipes work here without a furnace in the room. */
    public boolean ownHeat() { return ownHeat; }

    /** Whether orders can be placed for this kind (it has a catalogue). */
    public boolean hasCatalog() { return !catalog.isEmpty(); }

    public Component label() {
        return Component.translatable("workshop.hearthholm." + key);
    }

    public net.minecraft.sounds.SoundEvent sound() {
        return switch (this) {
            case CARPENTRY -> io.github.operagtx.hearthholm.registry.ModSounds.SAW;
            case MASONRY -> io.github.operagtx.hearthholm.registry.ModSounds.CHISEL;
            case SMITHY -> io.github.operagtx.hearthholm.registry.ModSounds.HAMMER;
            case KITCHEN -> io.github.operagtx.hearthholm.registry.ModSounds.SIZZLE;
            case HERBALISM, BARRACKS, TAVERN -> io.github.operagtx.hearthholm.registry.ModSounds.SIZZLE;
        };
    }

    public static @Nullable Workshop forProfession(Identifier profession) {
        for (Workshop w : values()) if (w.profession.equals(profession)) return w;
        return null;
    }

    public static @Nullable Workshop forItem(Item item) {
        for (Workshop w : values()) if (w.catalog.contains(item)) return w;
        return null;
    }

    public static boolean cooking(RecipeType<?> type) {
        return type != RecipeType.CRAFTING && type != RecipeType.STONECUTTING;
    }

    /** Ready meals: they give the "Tasty meal" mood bonus when eaten. */
    public static final Set<Item> MEALS = Set.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_COD, Items.COOKED_SALMON, Items.MUSHROOM_STEW,
            Items.BEETROOT_SOUP, Items.RABBIT_STEW, Items.PUMPKIN_PIE, Items.COOKIE, Items.CAKE);
}
