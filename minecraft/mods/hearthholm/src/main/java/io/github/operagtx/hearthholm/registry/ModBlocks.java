package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.*;
import io.github.operagtx.hearthholm.block.OrderBoardBlock;
import io.github.operagtx.hearthholm.block.WarehouseLedgerBlock;
import io.github.operagtx.hearthholm.block.WorkshopBlock;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlock;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModBlocks {
    /** Blocks shown in the creative tab, in order. */
    public static final List<Block> TAB_ORDER = new ArrayList<>();
    public static final List<WorkSiteBlock> WORK_SITES = new ArrayList<>();

    public static Block TOWN_HALL_DESK;
    public static Block WAREHOUSE_LEDGER;
    public static WorkshopBlock CARPENTER_BENCH;
    public static WorkshopBlock MASON_TABLE;
    public static WorkshopBlock SMITH_ANVIL;
    public static WorkshopBlock COOK_STOVE;
    public static Block ORDER_BOARD;
    public static WorkSiteBlock CONSTRUCTION_STAKE;
    public static WorkSiteBlock MINE_MARKER;
    public static WorkSiteBlock FOREST_MARKER;
    public static WorkSiteBlock FARM_MARKER;
    public static WorkSiteBlock FISHING_MARKER;
    public static WorkSiteBlock PATROL_POST;
    public static WorkSiteBlock TERRITORY_BANNER;
    public static WorkshopBlock HERBALIST_TABLE, WEAPON_RACK;
    public static WorkshopBlock TAVERN_KEG;
    public static Block LAMP_POST, SIGNPOST, BENCH, WELL, COUNTER, FLOWER_BOX, SETTLEMENT_BANNER, BULLETIN_BOARD, MARKET_STALL;
    public static final List<Block> SIGNS = new ArrayList<>();
    /** Wave H1: coziness. */
    public static Block WELL_PART, ROUND_TABLE, LONG_TABLE, CHAIR, STOOL, BOTTLES, MUG, PLATE_BREAD, PLATE_ROAST, PLATE_FISH, CANDLES, CANDLESTICK,
            WALL_SHELF, HANGING_HERBS, BARREL_RACK, RUG_RED, RUG_BLUE, CRATE, FIREWOOD_PILE, HANGING_LANTERN, CHIMNEY_CAP;
    public static Block ALARM_BELL, PALISADE, PALISADE_GATE, RIFT_CORE, RIFT_LANTERN, CHIEFTAIN_BANNER;

    private ModBlocks() {}

    public static <B extends Block> B register(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        Identifier id = Hearthholm.id(name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        TAB_ORDER.add(block);
        return block;
    }

    /** Registers a marker block for a work-site type (used by add-ons too). */
    public static WorkSiteBlock workSite(String name, Identifier type, MapColor color) {
        WorkSiteBlock b = register(name, p -> new WorkSiteBlock(p, type), BlockBehaviour.Properties.of()
                .mapColor(color).strength(0.8f).sound(SoundType.WOOD).noOcclusion());
        WORK_SITES.add(b);
        return b;
    }

    public static void init() {
        TOWN_HALL_DESK = register("town_hall_desk", TownHallDeskBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
        WAREHOUSE_LEDGER = register("warehouse_ledger", WarehouseLedgerBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
        CARPENTER_BENCH = register("carpenter_bench", p -> new WorkshopBlock(p, Workshop.CARPENTRY, Block.box(0, 0, 0, 16, 14, 16)),
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion());
        MASON_TABLE = register("mason_table", p -> new WorkshopBlock(p, Workshop.MASONRY, Block.box(0, 0, 0, 16, 14, 16)),
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f).sound(SoundType.STONE).noOcclusion());
        SMITH_ANVIL = register("smith_anvil", p -> new WorkshopBlock(p, Workshop.SMITHY, Block.box(1, 0, 2, 15, 12, 14)),
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f).sound(SoundType.ANVIL).noOcclusion());
        COOK_STOVE = register("cook_stove", p -> new WorkshopBlock(p, Workshop.KITCHEN, Block.box(0, 0, 0, 16, 16, 16)),
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f).sound(SoundType.STONE).lightLevel(st -> 11));
        ORDER_BOARD = register("order_board", OrderBoardBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
        CONSTRUCTION_STAKE = workSite("construction_stake", WorkSiteTypes.CONSTRUCTION_ID, MapColor.COLOR_YELLOW);
        MINE_MARKER = workSite("mine_marker", WorkSiteTypes.MINE_ID, MapColor.STONE);
        FOREST_MARKER = workSite("forest_marker", WorkSiteTypes.FOREST_ID, MapColor.PLANT);
        FARM_MARKER = workSite("farm_marker", WorkSiteTypes.FARM_ID, MapColor.COLOR_YELLOW);
        FISHING_MARKER = workSite("fishing_marker", WorkSiteTypes.FISHING_ID, MapColor.WATER);
        PATROL_POST = workSite("patrol_post", WorkSiteTypes.PATROL_ID, MapColor.COLOR_BLUE);
        TERRITORY_BANNER = workSite("territory_banner", WorkSiteTypes.TERRITORY_ID, MapColor.COLOR_RED);
        // wave C: defence and threats
        ALARM_BELL = register("alarm_bell", AlarmBellBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.GOLD).strength(2f).sound(SoundType.WOOD).noOcclusion());
        PALISADE = register("palisade", PalisadeBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(3f, 12f).sound(SoundType.WOOD));
        PALISADE_GATE = register("palisade_gate", PalisadeGateBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(3f, 12f).sound(SoundType.WOOD).noOcclusion());
        RIFT_CORE = register("rift_core", RiftCoreBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE).strength(6f, 1200f).sound(SoundType.AMETHYST).lightLevel(st -> 15).noLootTable());
        RIFT_LANTERN = register("rift_lantern", RiftLanternBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE).strength(1.5f).sound(SoundType.LANTERN).lightLevel(st -> 15).noOcclusion());
        CHIEFTAIN_BANNER = register("chieftain_banner", ChieftainBannerBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
        // wave D: party and military
        HERBALIST_TABLE = register("herbalist_table", p -> new WorkshopBlock(p, Workshop.HERBALISM, Block.box(0, 0, 0, 16, 14, 16)),
                BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(2f).sound(SoundType.WOOD).noOcclusion());
        WEAPON_RACK = register("weapon_rack", p -> new WorkshopBlock(p, Workshop.BARRACKS, Block.box(0, 0, 0, 16, 16, 16)),
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion());
        // wave E: square and market
        TAVERN_KEG = register("tavern_keg", p -> new WorkshopBlock(p, Workshop.TAVERN, Block.box(1, 0, 1, 15, 14, 15)),
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion());
        BlockBehaviour.Properties wood = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion();
        LAMP_POST = register("lamp_post", p -> new DecorBlock(p, Block.box(5, 0, 5, 11, 16, 11)), BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.LANTERN).lightLevel(st -> 15).noOcclusion());
        SIGNPOST = register("signpost", p -> new DecorBlock(p, Block.box(5, 0, 5, 11, 16, 11)), wood);
        BENCH = register("bench", p -> new DecorBlock(p, Block.box(1, 0, 3, 15, 9, 13)), wood);
        WELL = register("well", WellBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(2f).sound(SoundType.STONE).noOcclusion().noLootTable());
        COUNTER = register("counter", p -> new DecorBlock(p, Block.box(0, 0, 0, 16, 14, 16)), wood);
        FLOWER_BOX = register("flower_box", p -> new DecorBlock(p, Block.box(0, 0, 3, 16, 10, 13)), wood);
        for (String kind : List.of("carpenter", "mason", "smith", "cook", "tavern")) {
            SIGNS.add(register("sign_" + kind, p -> new DecorBlock(p, Block.box(1, 2, 6, 15, 15, 10)), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(1f).sound(SoundType.WOOD).noOcclusion().noCollision()));
        }
        SETTLEMENT_BANNER = register("settlement_banner", SettlementBannerBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
        BULLETIN_BOARD = register("bulletin_board", BulletinBoardBlock::new, wood);
        MARKET_STALL = register("market_stall", MarketStallBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion());
        initCozy();
    }

    /** Wave H1: furniture and table things, the well parts. */
    private static void initCozy() {
        Identifier wpId = Hearthholm.id("well_part");
        ResourceKey<Block> wpKey = ResourceKey.create(Registries.BLOCK, wpId);
        WELL_PART = Registry.register(BuiltInRegistries.BLOCK, wpKey, new WellPartBlock(BlockBehaviour.Properties.of().setId(wpKey)
                .mapColor(MapColor.STONE).strength(2f).sound(SoundType.STONE).noOcclusion().noLootTable()));
        java.util.function.Supplier<BlockBehaviour.Properties> wood = () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion();
        BlockBehaviour.Properties glass = BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion().instabreak();
        ROUND_TABLE = register("round_table", p -> new FacingShapeBlock(p, new double[]{0, 12, 0, 16, 14, 16}, new double[]{4, 0, 4, 12, 12, 12}), wood.get());
        LONG_TABLE = register("long_table", LongTableBlock::new, wood.get());
        CHAIR = register("chair", p -> new SeatBlock(p, 10, new double[]{3, 0, 3, 13, 10, 13}, new double[]{3, 10, 11, 13, 16, 13}), wood.get());
        STOOL = register("stool", p -> new SeatBlock(p, 11, new double[]{3, 0, 3, 13, 11, 13}), wood.get());
        BOTTLES = register("bottles", p -> new SmallStackBlock(p, 3, 11), glass);
        MUG = register("mug", p -> new SmallStackBlock(p, 3, 6), wood.get().instabreak());
        BlockBehaviour.Properties plate = BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.3f).sound(SoundType.DECORATED_POT).noOcclusion().instabreak();
        PLATE_BREAD = register("plate_bread", p -> new SmallStackBlock(p, 1, 4), plate);
        PLATE_ROAST = register("plate_roast", p -> new SmallStackBlock(p, 1, 5), plate);
        PLATE_FISH = register("plate_fish", p -> new SmallStackBlock(p, 1, 3), plate);
        CANDLES = register("candles", p -> new SmallStackBlock(p, 3, 11), BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.1f).sound(SoundType.CANDLE)
                .noOcclusion().instabreak().lightLevel(st -> 4 + 2 * st.getValue(SmallStackBlock.COUNT)));
        CANDLESTICK = register("candlestick", p -> new SmallStackBlock(p, 1, 16), BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.3f)
                .sound(SoundType.LANTERN).noOcclusion().instabreak().lightLevel(st -> 12));
        WALL_SHELF = register("wall_shelf", p -> new WallDecorBlock(p, new double[]{0, 5, 10, 16, 14, 16}), wood.get());
        HANGING_HERBS = register("hanging_herbs", p -> new WallDecorBlock(p, new double[]{0, 4, 5, 16, 16, 11}), wood.get().noCollision().instabreak());
        BARREL_RACK = register("barrel_rack", p -> new FacingShapeBlock(p, new double[]{0, 0, 1, 16, 16, 15}), wood.get());
        BlockBehaviour.Properties rug = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2f).sound(SoundType.WOOL).noOcclusion();
        RUG_RED = register("rug_red", p -> new FacingShapeBlock(p, new double[]{0, 0, 0, 16, 1, 16}), rug);
        RUG_BLUE = register("rug_blue", p -> new FacingShapeBlock(p, new double[]{0, 0, 0, 16, 1, 16}), rug);
        CRATE = register("crate", p -> new FacingShapeBlock(p, new double[]{0, 0, 0, 16, 16, 16}), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD));
        FIREWOOD_PILE = register("firewood_pile", p -> new FacingShapeBlock(p, new double[]{0, 0, 0, 16, 12, 16}), wood.get());
        CHIMNEY_CAP = register("chimney_cap", io.github.operagtx.hearthholm.block.ChimneyCapBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                .strength(1.5f, 6f).sound(SoundType.STONE).noOcclusion());
        HANGING_LANTERN = register("hanging_lantern", LanternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f)
                .sound(SoundType.LANTERN).lightLevel(st -> 13).noOcclusion());
    }
}
