package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.TownHallDeskBlock;
import io.github.operagtx.hearthholm.block.WarehouseLedgerBlock;
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
    public static WorkSiteBlock CONSTRUCTION_STAKE;
    public static WorkSiteBlock MINE_MARKER;
    public static WorkSiteBlock FOREST_MARKER;
    public static WorkSiteBlock FARM_MARKER;
    public static WorkSiteBlock FISHING_MARKER;
    public static WorkSiteBlock PATROL_POST;
    public static WorkSiteBlock TERRITORY_BANNER;

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
        CONSTRUCTION_STAKE = workSite("construction_stake", WorkSiteTypes.CONSTRUCTION_ID, MapColor.COLOR_YELLOW);
        MINE_MARKER = workSite("mine_marker", WorkSiteTypes.MINE_ID, MapColor.STONE);
        FOREST_MARKER = workSite("forest_marker", WorkSiteTypes.FOREST_ID, MapColor.PLANT);
        FARM_MARKER = workSite("farm_marker", WorkSiteTypes.FARM_ID, MapColor.COLOR_YELLOW);
        FISHING_MARKER = workSite("fishing_marker", WorkSiteTypes.FISHING_ID, MapColor.WATER);
        PATROL_POST = workSite("patrol_post", WorkSiteTypes.PATROL_ID, MapColor.COLOR_BLUE);
        TERRITORY_BANNER = workSite("territory_banner", WorkSiteTypes.TERRITORY_ID, MapColor.COLOR_RED);
    }
}
