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
    }
}
