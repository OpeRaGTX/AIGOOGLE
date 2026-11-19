package io.github.operagtx.hearthholm.crafting;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.Set;

/**
 * The kinds of workshop (plus the herbalist table and the barracks rack). Each one is a building role (bit flag in {@code BuildingRecord}),
 * an anchor block, a crafter profession, a curated list of things the crafter may make (also the
 * order-board catalogue) and the vanilla recipe types it may use. Smelting-type recipes need a
 * furnace in the room, except in the kitchen where the stove itself is the heat source.
 */
public enum Workshop {
    CARPENTRY("carpentry", 8, "carpenter", false, List.of(RecipeType.CRAFTING),
            () -> List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.STICK, Items.CHEST, Items.BARREL,
                    Items.LADDER, Items.CRAFTING_TABLE, Items.OAK_DOOR, Items.OAK_TRAPDOOR, Items.OAK_FENCE, Items.BOWL,
                    Items.BED.red(), Items.BED.blue(), Items.BED.green(), Items.TORCH,
                    // wave H2: roof stairs/slabs, awning wool
                    Items.OAK_STAIRS, Items.OAK_SLAB, Items.SPRUCE_STAIRS, Items.SPRUCE_SLAB, Items.WOOL.red(), Items.WOOL.white(),
                    deco(ModBlocks.CARPENTER_BENCH), deco(ModBlocks.WEAPON_RACK), deco(ModBlocks.MARKET_STALL), deco(ModBlocks.ALARM_BELL), deco(ModBlocks.TAVERN_KEG), deco(ModBlocks.BENCH), deco(ModBlocks.COUNTER),
                    deco(ModBlocks.FLOWER_BOX), deco(ModBlocks.SIGNPOST), deco(ModBlocks.SIGNS.get(0)), deco(ModBlocks.SIGNS.get(4)),
                    // wave H1: furniture and table things (data only)
                    deco(ModBlocks.ROUND_TABLE), deco(ModBlocks.LONG_TABLE), deco(ModBlocks.CHAIR), deco(ModBlocks.STOOL), deco(ModBlocks.BARREL_RACK),
                    deco(ModBlocks.CRATE), deco(ModBlocks.FIREWOOD_PILE), deco(ModBlocks.WALL_SHELF), deco(ModBlocks.HANGING_HERBS), deco(ModBlocks.RUG_RED),
                    deco(ModBlocks.RUG_BLUE), deco(ModBlocks.MUG), deco(ModBlocks.PLATE_BREAD), deco(ModBlocks.PLATE_ROAST), deco(ModBlocks.PLATE_FISH))),
    MASONRY("masonry", 16, "mason", false, List.of(RecipeType.CRAFTING, RecipeType.STONECUTTING, RecipeType.SMELTING),
            () -> List.of(Items.STONE_BRICKS, Items.STONE, Items.SMOOTH_STONE, Items.STONE_BRICK_STAIRS, Items.STONE_BRICK_SLAB,
                    Items.COBBLESTONE_STAIRS, Items.COBBLESTONE_SLAB, Items.COBBLESTONE_WALL, Items.STONE_STAIRS,
                    Items.STONE_SLAB, Items.FURNACE, Items.TERRACOTTA, Items.DYED_TERRACOTTA.white(), Items.GLASS_PANE, deco(ModBlocks.WELL), deco(ModBlocks.SIGNS.get(1)),
                    deco(ModBlocks.BOTTLES), deco(ModBlocks.CANDLES), deco(ModBlocks.CHIMNEY_CAP))),
    SMITHY("smithy", 32, "smith", false, List.of(RecipeType.CRAFTING, RecipeType.BLASTING, RecipeType.SMELTING),
            () -> List.of(Items.IRON_INGOT, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SWORD,
                    Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.SHEARS,
                    Items.BUCKET, Items.LANTERN, Items.IRON_BARS, deco(ModBlocks.LAMP_POST), deco(ModBlocks.SIGNS.get(2)),
                    deco(ModBlocks.CANDLESTICK), deco(ModBlocks.HANGING_LANTERN))),
    KITCHEN("kitchen", 64, "cook", true, List.of(RecipeType.CRAFTING, RecipeType.SMOKING, RecipeType.SMELTING, RecipeType.CAMPFIRE_COOKING),
            () -> {
                List<Item> l = new ArrayList<>(List.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
                        Items.COOKED_MUTTON, Items.COOKED_COD, Items.COOKED_SALMON, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP,
                        Items.PUMPKIN_PIE, Items.COOKIE));
                l.addAll(io.github.operagtx.hearthholm.registry.ModItems.FOODS.values());   // wave H1: the new dishes
                l.add(deco(ModBlocks.SIGNS.get(3)));
                return l;
            }),
    /** Wave D: the healer's herbalist table. No orders: the healer brews salves on its own (see HealerJob). */
    HERBALISM("herbalism", 128, "healer", false, List.of(), List::of),
    /** Wave D: a barracks is a room with a weapon rack; combat settlers rest there. Has no crafter. */
    BARRACKS("barracks", 256, "barracks_none", false, List.of(), List::of),
    /** Wave E: a tavern is a room with a keg; the innkeeper serves food from storage, off-duty settlers gather there in the evening. */
    TAVERN("tavern", 512, "innkeeper", false, List.of(), List::of);

    /** Without any kitchen order, cooks keep this many meals in the warehouse. */
    public static final int DEFAULT_MEALS = 32;

    private final String key;
    private final int role;
    private final Identifier profession;
    private final boolean ownHeat;
    private final List<RecipeType<?>> types;
    private final Supplier<List<Item>> catalogSupplier;
    private List<Item> catalog;

    Workshop(String key, int role, String profession, boolean ownHeat, List<RecipeType<?>> types, Supplier<List<Item>> catalog) {
        this.key = key;
        this.role = role;
        this.profession = Hearthholm.id(profession);
        this.ownHeat = ownHeat;
        this.types = types;
        this.catalogSupplier = catalog;
    }

    public String key() { return key; }
    public int role() { return role; }
    public Identifier profession() { return profession; }
    /** Resolved lazily: decor blocks (wave F) are registered after this enum loads. */
    public synchronized List<Item> catalog() {
        if (catalog == null) catalog = List.copyOf(new ArrayList<>(catalogSupplier.get()));
        return catalog;
    }

    private static Item deco(net.minecraft.world.level.block.Block b) { return b.asItem(); }
    public List<RecipeType<?>> recipeTypes() { return types; }

    /** Whether cooking-type recipes work here without a furnace in the room. */
    public boolean ownHeat() { return ownHeat; }

    /**
     * Wave M1: whether this workshop uses cooking-type recipes (smelting, blasting, smoking...) at all. Only those can be blocked by a missing
     * furnace, so only those workshops get the "furnace in the room" hint; the carpenter (crafting only) never does.
     */
    public boolean smelts() {
        for (RecipeType<?> t : types) if (cooking(t)) return true;
        return false;
    }

    /** Whether orders can be placed for this kind (it has a catalogue). */
    public boolean hasCatalog() { return this != HERBALISM && this != BARRACKS && this != TAVERN; }

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
        for (Workshop w : values()) if (w.catalog().contains(item)) return w;
        return null;
    }

    public static boolean cooking(RecipeType<?> type) {
        return type != RecipeType.CRAFTING && type != RecipeType.STONECUTTING;
    }

    /** Ready meals: they give the "Tasty meal" mood bonus when eaten. */
    public static final Set<Item> MEALS = new java.util.HashSet<>(Set.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_COD, Items.COOKED_SALMON, Items.MUSHROOM_STEW,
            Items.BEETROOT_SOUP, Items.RABBIT_STEW, Items.PUMPKIN_PIE, Items.COOKIE, Items.CAKE));   // wave H1 adds the new foods (ModItems)
}
