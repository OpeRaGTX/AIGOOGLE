package io.github.operagtx.hearthholm.test;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.*;
import io.github.operagtx.hearthholm.crafting.Recipes;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SeatEntity;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/** Wave H1: coziness. Well multiblock, seats, tabletop things, new foods; builds a decorated tavern and takes the screenshots. */
final class WaveHTest {
    private static BlockPos H;          // origin of the showcase area (east of the settlement)

    private WaveHTest() {}

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, false);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    private static BlockState f(Block b, Direction d) {
        return b.defaultBlockState().setValue(HorizontalBlock.FACING, d);
    }

    private static BlockState small(Block b, Direction d, int count, boolean onTable) {
        return f(b, d).setValue(SmallStackBlock.COUNT, count).setValue(SmallStackBlock.ON_TABLE, onTable);
    }

    private static void put(ServerLevel l, BlockPos o, int x, int y, int z, BlockState st) {
        l.setBlock(o.offset(x, y, z), st, 3);
    }

    private static void fill(ServerLevel l, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) l.setBlock(o.offset(x, y, z), st, 2);
    }

    private static int countParts(ServerLevel l, BlockPos master) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++)
            if (l.getBlockState(master.offset(dx, dy, dz)).is(ModBlocks.WELL_PART)) n++;
        return n;
    }

    private static InteractionResult place(ServerLevel l, ServerPlayer p, Block block, BlockPos ground, Direction face) {
        ItemStack st = new ItemStack(block);
        return st.useOn(new UseOnContext(l, p, InteractionHand.MAIN_HAND, st, new BlockHitResult(Vec3.atCenterOf(ground), face, ground, false)));
    }

    private static InteractionResult use(ServerLevel l, ServerPlayer p, BlockPos pos) {
        return l.getBlockState(pos).useWithoutItem(l, p, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 6000");
        sp.getServer().runCommand("weather clear");

        // ---- 0. a flat showcase area east of the town
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            H = O.offset(36, 0, -4);
            p.teleportTo(s.overworld(), H.getX() + 8.5, H.getY() + 6, H.getZ() + 8.5, Set.of(), 0f, 30f, false);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
        ctx.waitTicks(40);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            for (int dx = -2; dx <= 30; dx++)
                for (int dz = -2; dz <= 38; dz++) {
                    for (int dy = 0; dy <= 9; dy++) l.setBlock(H.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    l.setBlock(H.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int dy = 2; dy <= 4; dy++) l.setBlock(H.offset(dx, -dy, dz), Blocks.DIRT.defaultBlockState(), 2);
                }
            log("H area ready at " + H.toShortString());
        });

        // ---- 1. the well multiblock
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            BlockPos w = H.offset(3, 0, 3);
            p.teleportTo(l, w.getX() + 0.5, w.getY() + 4, w.getZ() + 8.5, Set.of(), 180f, 10f, false);   // looking north: well faces south
            InteractionResult r = place(l, p, ModBlocks.WELL, w.below(), Direction.UP);
            BlockState m = l.getBlockState(w);
            int parts = countParts(l, w);
            log("HHTEST H well placed: result=" + r + " master=" + m.is(ModBlocks.WELL) + " complete=" + (m.is(ModBlocks.WELL) && m.getValue(WellBlock.COMPLETE))
                    + " parts=" + parts + " facing=" + (m.is(ModBlocks.WELL) ? m.getValue(HorizontalBlock.FACING) : null));
            if (!m.is(ModBlocks.WELL) || parts != 20 || !m.getValue(WellBlock.COMPLETE)) throw new AssertionError("well multiblock not placed");

            // no room: a stone block in the volume refuses the placement
            BlockPos w2 = H.offset(3, 0, 9);
            l.setBlock(w2.offset(1, 2, 1), Blocks.STONE.defaultBlockState(), 3);
            InteractionResult r2 = place(l, p, ModBlocks.WELL, w2.below(), Direction.UP);
            boolean refused = !l.getBlockState(w2).is(ModBlocks.WELL) && countParts(l, w2) == 0;
            log("HHTEST H well refused without room: result=" + r2 + " nothingPlaced=" + refused);
            if (!refused) throw new AssertionError("well placed without room");
            l.setBlock(w2.offset(1, 2, 1), Blocks.AIR.defaultBlockState(), 3);

            // survival break of a roof part removes the whole well and drops exactly one item
            p.setGameMode(GameType.SURVIVAL);
            BlockPos roofPart = w.offset(1, 2, 1);
            boolean isPart = l.getBlockState(roofPart).is(ModBlocks.WELL_PART);
            p.gameMode.destroyBlock(roofPart);
            int left = countParts(l, w) + (l.getBlockState(w).is(ModBlocks.WELL) ? 1 : 0);
            int drops = 0;
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, new AABB(w).inflate(6))) if (ie.getItem().is(ModBlocks.WELL.asItem())) drops += ie.getItem().getCount();
            log("HHTEST H well broken via roof part (isPart=" + isPart + "): blocksLeft=" + left + " droppedItems=" + drops);
            if (!isPart || left != 0 || drops != 1) throw new AssertionError("well did not collapse into exactly one item");
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, new AABB(w).inflate(6))) ie.discard();

            // breaking the master (creative) removes everything and drops nothing
            p.setGameMode(GameType.CREATIVE);
            place(l, p, ModBlocks.WELL, w.below(), Direction.UP);
            p.gameMode.destroyBlock(w);
            drops = 0;
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, new AABB(w).inflate(6))) drops += ie.getItem().getCount();
            log("HHTEST H well broken via master in creative: blocksLeft=" + (countParts(l, w) + (l.getBlockState(w).is(ModBlocks.WELL) ? 1 : 0)) + " drops=" + drops);
            if (countParts(l, w) != 0 || l.getBlockState(w).is(ModBlocks.WELL) || drops != 0) throw new AssertionError("master break");
            // the final well for the screenshot, plus the decor list still knows the master
            place(l, p, ModBlocks.WELL, w.below(), Direction.UP);
            log("HHTEST H well re-placed: parts=" + countParts(l, w));
        });

        // ---- 2. build the tavern interior (furniture, tabletop things, lights)
        sp.getServer().runOnServer(s -> buildTavern(s.overworld(), H.offset(10, 0, 2)));
        ctx.waitTicks(10);

        // ---- 3. seats: the player sits on a chair; occupied/sneak/break rules
        BlockPos T = H.offset(10, 0, 2);
        BlockPos chair = T.offset(2, 0, 4), stool = T.offset(6, 0, 4);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5, Set.of(), 0f, 0f, false);
            InteractionResult r = use(l, p, chair);
            log("HHTEST H sit on chair: result=" + r + " riding=" + (p.getVehicle() instanceof SeatEntity) + " chair=" + l.getBlockState(chair).getBlock().getDescriptionId());
            if (!(p.getVehicle() instanceof SeatEntity)) throw new AssertionError("player did not sit");
        });
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            boolean still = p.getVehicle() instanceof SeatEntity;
            log("HHTEST H seated 10 ticks later: riding=" + still + " feetAboveBlockFloor=" + String.format("%.2f", p.getY() - chair.getY()) + " (seat surface 0.63)");
            if (!still) throw new AssertionError("player got off by itself");
            p.stopRiding();   // what sneaking does
        });
        ctx.waitTicks(5);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            int seats = l.getEntitiesOfClass(SeatEntity.class, new AABB(chair).inflate(3)).size();
            log("HHTEST H dismounted (sneak): riding=" + (p.getVehicle() != null) + " seatEntitiesLeft=" + seats);
            if (p.getVehicle() != null || seats != 0) throw new AssertionError("dismount failed");
            // break the stool under a sitter: the seat disappears and the sitter is released
            use(l, p, stool);
            boolean sat = p.getVehicle() instanceof SeatEntity;
            l.setBlock(stool, Blocks.AIR.defaultBlockState(), 3);
            log("HHTEST H sat on stool=" + sat + ", stool removed");
        });
        ctx.waitTicks(5);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            log("HHTEST H after breaking the stool: riding=" + (p.getVehicle() != null) + " seatEntitiesLeft=" + l.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(3)).size());
            if (p.getVehicle() != null) throw new AssertionError("still riding a broken stool");
            l.setBlock(stool, f(ModBlocks.STOOL, Direction.WEST), 3);
        });

        // ---- 4. tabletop placement through the real item path: on_table + stacking bottles
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            BlockPos tbl = T.offset(11, 0, 4);                     // round table C
            BlockPos top = tbl.above();
            l.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
            InteractionResult r1 = place(l, p, ModBlocks.BOTTLES, tbl, Direction.UP);
            BlockState b1 = l.getBlockState(top);
            place(l, p, ModBlocks.BOTTLES, tbl, Direction.UP);
            BlockState b2 = l.getBlockState(top);
            log("HHTEST H bottles on a table: result=" + r1 + " first=" + (b1.is(ModBlocks.BOTTLES) ? b1.getValue(SmallStackBlock.COUNT) + "/onTable=" + b1.getValue(SmallStackBlock.ON_TABLE) : "none")
                    + " second=" + (b2.is(ModBlocks.BOTTLES) ? b2.getValue(SmallStackBlock.COUNT) : -1));
            if (!b1.is(ModBlocks.BOTTLES) || !b1.getValue(SmallStackBlock.ON_TABLE) || !b2.is(ModBlocks.BOTTLES) || b2.getValue(SmallStackBlock.COUNT) != 2)
                throw new AssertionError("bottle placement");
            l.setBlock(top, small(ModBlocks.CANDLES, Direction.NORTH, 3, true), 3);
            // a long table joins with its neighbour
            BlockState lt = l.getBlockState(T.offset(4, 0, 7));
            log("HHTEST H long table links: a=" + lt.getValue(LongTableBlock.A) + " b=" + lt.getValue(LongTableBlock.B) + " (middle of 4 should be a=true b=true)");
            if (!lt.getValue(LongTableBlock.A) || !lt.getValue(LongTableBlock.B)) throw new AssertionError("long table links");
        });

        // ---- 5. foods: registered, named in both languages, craftable, in the cook's catalogue, edible, drinks buff
        ctx.runOnClient(mc -> {
            int missing = 0;
            for (String n : ModItems.FOODS.keySet()) if (!net.minecraft.locale.Language.getInstance().has("item.hearthholm." + n)) missing++;
            String[] blocks = {"round_table", "long_table", "chair", "stool", "bottles", "mug", "plate_bread", "plate_roast", "plate_fish", "candles", "candlestick", "wall_shelf",
                    "hanging_herbs", "barrel_rack", "rug_red", "rug_blue", "crate", "firewood_pile", "hanging_lantern", "well_part"};
            for (String n : blocks) if (!net.minecraft.locale.Language.getInstance().has("block.hearthholm." + n)) missing++;
            log("HHTEST H foods registered=" + ModItems.FOODS.size() + " new decor blocks=" + (blocks.length - 1) + " missingTranslations=" + missing);
            if (ModItems.FOODS.size() < 15 || missing > 0) throw new AssertionError("foods or translations missing");
        });
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            int craftable = 0, inKitchen = 0, meals = 0;
            List<String> noRecipe = new ArrayList<>();
            for (var e : ModItems.FOODS.entrySet()) {
                Item it = e.getValue();
                if (!Recipes.options(l, it).isEmpty()) craftable++; else noRecipe.add(e.getKey());
                if (Workshop.KITCHEN.catalog().contains(it)) inKitchen++;
                if (Workshop.MEALS.contains(it)) meals++;
            }
            log("HHTEST H foods: withRecipe=" + craftable + "/" + ModItems.FOODS.size() + " noRecipe=" + noRecipe + " inKitchenCatalog=" + inKitchen + " counted as meals=" + meals);
            if (craftable != ModItems.FOODS.size() || inKitchen != ModItems.FOODS.size()) throw new AssertionError("food recipes/catalog");
            int decorInCatalogs = 0;
            for (Block b : List.of(ModBlocks.ROUND_TABLE, ModBlocks.LONG_TABLE, ModBlocks.CHAIR, ModBlocks.STOOL, ModBlocks.BOTTLES, ModBlocks.MUG, ModBlocks.CANDLES, ModBlocks.CANDLESTICK,
                    ModBlocks.HANGING_LANTERN, ModBlocks.WALL_SHELF, ModBlocks.CRATE, ModBlocks.BARREL_RACK))
                if (Workshop.forItem(b.asItem()) != null && !Recipes.options(l, b.asItem()).isEmpty()) decorInCatalogs++;
            log("HHTEST H decor orderable (catalog + recipe): " + decorInCatalogs + "/12 in carpenter/mason/smith catalogues");
            if (decorInCatalogs != 12) throw new AssertionError("decor not orderable");
            // eating in survival
            p.setGameMode(GameType.SURVIVAL);
            StringBuilder sb = new StringBuilder();
            int ok = 0;
            for (var e : ModItems.FOODS.entrySet()) {
                p.getFoodData().setFoodLevel(2);
                ItemStack st = new ItemStack(e.getValue(), 2);
                ItemStack rest = st.finishUsingItem(l, p);
                int lvl = p.getFoodData().getFoodLevel();
                if (lvl > 2) ok++;
                if (e.getKey().equals("ale") || e.getKey().equals("herbal_tea") || e.getKey().equals("fish_soup"))
                    sb.append(" ").append(e.getKey()).append("->").append(lvl).append(" left=").append(rest.getItem() == Items.AIR ? "none" : BuiltInRegistries.ITEM.getKey(rest.getItem()).getPath());
            }
            log("HHTEST H foods eaten: " + ok + "/" + ModItems.FOODS.size() + " raised hunger;" + sb + " ale resistance=" + p.hasEffect(MobEffects.RESISTANCE)
                    + " cider speed=" + p.hasEffect(MobEffects.SPEED) + " tea regen=" + p.hasEffect(MobEffects.REGENERATION));
            if (ok != ModItems.FOODS.size() || !p.hasEffect(MobEffects.RESISTANCE) || !p.hasEffect(MobEffects.SPEED)) throw new AssertionError("eating foods");
            p.setGameMode(GameType.CREATIVE);
            p.removeAllEffects();
        });

        // ---- 6. screenshots
        // 6a. the well close-up
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos w = H.offset(3, 0, 3);
            look(p, w.getX() + 4.5, w.getY() + 2.2, w.getZ() + 5.0, w.getX() + 0.5, w.getY() + 1.6, w.getZ() + 0.5);
            p.getInventory().setSelectedSlot(8);
        });
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h1_well");
        // 6b. the tavern interior
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            look(p, T.getX() + 14.2, T.getY() + 1.4, T.getZ() + 8.4, T.getX() + 5.5, T.getY() + 1.0, T.getZ() + 3.0);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h2_tavern");
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            look(p, T.getX() + 1.6, T.getY() + 1.5, T.getZ() + 7.6, T.getX() + 9.5, T.getY() + 0.6, T.getZ() + 2.0);
        });
        ctx.waitTicks(6);
        ctx.takeScreenshot("hh_h2b_tavern_counter");
        // 6c. rows of blocks
        sp.getServer().runOnServer(s -> buildRows(s.overworld()));
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos g = H.offset(2, 0, 14);
            look(p, g.getX() + 11, g.getY() + 5.5, g.getZ() - 5.5, g.getX() + 11, g.getY() + 0.5, g.getZ() + 3.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h3_new_blocks");
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos g = H.offset(2, 0, 24);
            look(p, g.getX() + 11, g.getY() + 5.5, g.getZ() - 5.5, g.getX() + 11, g.getY() + 0.5, g.getZ() + 3.5);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h4_reworked_blocks");
        // 6d. food and item icons on display
        sp.getServer().runOnServer(s -> buildGallery(s));
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockPos g = H.offset(2, 0, 33);
            look(p, g.getX() + 8.2, g.getY() + 3.2, g.getZ() - 6.5, g.getX() + 8.2, g.getY() + 2.8, g.getZ() + 1.0);
        });
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_h5_foods");
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        log("HHTEST H screenshots: hh_h1_well hh_h2_tavern hh_h2b_tavern_counter hh_h3_new_blocks hh_h4_reworked_blocks hh_h5_foods");
        log("WAVE H OK");
    }

    // ------------------------------------------------------------------------------------------------ builders
    private static void buildTavern(ServerLevel l, BlockPos T) {
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), floor = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState post = Blocks.OAK_LOG.defaultBlockState(), beam = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X);
        fill(l, T, 0, -1, 0, 15, -1, 9, floor);
        fill(l, T, 0, 0, 0, 15, 3, 0, planks); fill(l, T, 0, 0, 9, 15, 3, 9, planks);
        fill(l, T, 0, 0, 0, 0, 3, 9, planks); fill(l, T, 15, 0, 0, 15, 3, 9, planks);
        fill(l, T, 1, 0, 1, 14, 3, 8, Blocks.AIR.defaultBlockState());
        fill(l, T, 0, 4, 0, 15, 4, 9, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int x : new int[]{0, 15}) for (int z : new int[]{0, 9}) fill(l, T, x, 0, z, x, 3, z, post);
        for (int x : new int[]{5, 10}) { fill(l, T, x, 0, 0, x, 3, 0, post); fill(l, T, x, 0, 9, x, 3, 9, post); }
        for (int x : new int[]{2, 3, 7, 8, 12, 13}) { fill(l, T, x, 1, 0, x, 2, 0, Blocks.GLASS_PANE.defaultBlockState()); }
        for (int x : new int[]{2, 3, 12, 13}) { fill(l, T, x, 1, 9, x, 2, 9, Blocks.GLASS_PANE.defaultBlockState()); }
        fill(l, T, 6, 0, 9, 9, 2, 9, Blocks.AIR.defaultBlockState());                 // doorway
        fill(l, T, 1, 3, 3, 14, 3, 3, beam); fill(l, T, 1, 3, 6, 14, 3, 6, beam);
        fill(l, T, 1, 0, 1, 14, 0, 8, Blocks.AIR.defaultBlockState());
        // counter, shelves, kegs along the north wall
        for (int x = 2; x <= 6; x++) put(l, T, x, 0, 1, f(ModBlocks.COUNTER, Direction.SOUTH));
        put(l, T, 2, 1, 1, small(ModBlocks.BOTTLES, Direction.SOUTH, 3, true)); put(l, T, 3, 1, 1, small(ModBlocks.MUG, Direction.SOUTH, 2, true));
        put(l, T, 4, 1, 1, small(ModBlocks.PLATE_BREAD, Direction.SOUTH, 1, true)); put(l, T, 5, 1, 1, small(ModBlocks.MUG, Direction.EAST, 3, true));
        put(l, T, 6, 1, 1, small(ModBlocks.CANDLESTICK, Direction.SOUTH, 1, true));
        for (int x : new int[]{2, 4, 6}) put(l, T, x, 2, 1, f(ModBlocks.WALL_SHELF, Direction.SOUTH));
        put(l, T, 8, 0, 1, f(ModBlocks.BARREL_RACK, Direction.SOUTH)); put(l, T, 9, 0, 1, f(ModBlocks.BARREL_RACK, Direction.SOUTH));
        put(l, T, 11, 0, 1, f(ModBlocks.FIREWOOD_PILE, Direction.SOUTH)); put(l, T, 12, 0, 1, f(ModBlocks.FIREWOOD_PILE, Direction.SOUTH));
        put(l, T, 14, 0, 1, f(ModBlocks.CRATE, Direction.SOUTH)); put(l, T, 14, 0, 2, f(ModBlocks.CRATE, Direction.SOUTH)); put(l, T, 14, 1, 1, f(ModBlocks.CRATE, Direction.EAST));
        put(l, T, 8, 2, 1, f(ModBlocks.WALL_SHELF, Direction.SOUTH)); put(l, T, 10, 2, 1, f(ModBlocks.WALL_SHELF, Direction.SOUTH));
        // round tables with seats and things on them
        Block[] seat = {ModBlocks.CHAIR, ModBlocks.STOOL};
        int[][] tables = {{3, 4}, {7, 4}, {11, 4}};
        for (int i = 0; i < tables.length; i++) {
            int x = tables[i][0], z = tables[i][1];
            put(l, T, x, 0, z, ModBlocks.ROUND_TABLE.defaultBlockState());
            put(l, T, x - 1, 0, z, f(seat[i % 2], Direction.EAST)); put(l, T, x + 1, 0, z, f(seat[(i + 1) % 2], Direction.WEST));
            put(l, T, x, 0, z - 1, f(seat[i % 2], Direction.SOUTH)); if (i != 1) put(l, T, x, 0, z + 1, f(seat[(i + 1) % 2], Direction.NORTH));
        }
        put(l, T, 3, 1, 4, small(ModBlocks.BOTTLES, Direction.NORTH, 3, true));
        put(l, T, 7, 1, 4, small(ModBlocks.PLATE_ROAST, Direction.WEST, 1, true));
        put(l, T, 11, 1, 4, small(ModBlocks.BOTTLES, Direction.EAST, 1, true));
        // long tables
        for (int x = 3; x <= 6; x++) put(l, T, x, 0, 7, ModBlocks.LONG_TABLE.defaultBlockState().setValue(LongTableBlock.AXIS, Direction.Axis.X));
        for (int x = 9; x <= 12; x++) put(l, T, x, 0, 7, ModBlocks.LONG_TABLE.defaultBlockState().setValue(LongTableBlock.AXIS, Direction.Axis.X));
        put(l, T, 3, 1, 7, small(ModBlocks.MUG, Direction.NORTH, 3, true)); put(l, T, 4, 1, 7, small(ModBlocks.PLATE_BREAD, Direction.NORTH, 1, true));
        put(l, T, 5, 1, 7, small(ModBlocks.BOTTLES, Direction.WEST, 2, true)); put(l, T, 6, 1, 7, small(ModBlocks.PLATE_FISH, Direction.NORTH, 1, true));
        put(l, T, 9, 1, 7, small(ModBlocks.CANDLESTICK, Direction.NORTH, 1, true)); put(l, T, 10, 1, 7, small(ModBlocks.PLATE_ROAST, Direction.NORTH, 1, true));
        put(l, T, 11, 1, 7, small(ModBlocks.MUG, Direction.EAST, 1, true)); put(l, T, 12, 1, 7, small(ModBlocks.CANDLES, Direction.NORTH, 3, true));
        for (int x = 3; x <= 12; x++) if (x < 7 || x > 8) put(l, T, x, 0, 8, f(x % 2 == 0 ? ModBlocks.CHAIR : ModBlocks.STOOL, Direction.NORTH));
        // rugs
        for (int z = 3; z <= 5; z++) put(l, T, 5, 0, z, f(ModBlocks.RUG_RED, Direction.NORTH));
        for (int z = 3; z <= 5; z++) put(l, T, 9, 0, z, f(ModBlocks.RUG_BLUE, Direction.NORTH));
        // lights and herbs hanging from the beams
        for (int[] p : new int[][]{{3, 3}, {7, 3}, {11, 3}, {5, 6}, {9, 6}, {13, 6}})
            put(l, T, p[0], 2, p[1], ModBlocks.HANGING_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        for (int x : new int[]{1, 13}) put(l, T, x, 2, 3, f(ModBlocks.HANGING_HERBS, Direction.NORTH));
        put(l, T, 5, 2, 3, f(ModBlocks.HANGING_HERBS, Direction.NORTH)); put(l, T, 9, 2, 3, f(ModBlocks.HANGING_HERBS, Direction.NORTH));
        put(l, T, 1, 0, 8, f(ModBlocks.CRATE, Direction.NORTH)); put(l, T, 14, 0, 8, f(ModBlocks.FIREWOOD_PILE, Direction.WEST));
        log("HHTEST H tavern interior built at " + T.toShortString());
    }

    private static void buildRows(ServerLevel l) {
        BlockPos g = H.offset(2, 0, 14), g2 = H.offset(2, 0, 24);
        for (BlockPos o : new BlockPos[]{g, g2}) {
            fill(l, o, -1, -1, -1, 23, -1, 8, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        Direction N = Direction.NORTH;
        Block[] a = {ModBlocks.ROUND_TABLE, ModBlocks.LONG_TABLE, ModBlocks.CHAIR, ModBlocks.STOOL, ModBlocks.BOTTLES, ModBlocks.MUG, ModBlocks.PLATE_BREAD,
                ModBlocks.PLATE_ROAST, ModBlocks.PLATE_FISH, ModBlocks.CANDLES, ModBlocks.CANDLESTICK};
        Block[] b = {ModBlocks.WALL_SHELF, ModBlocks.HANGING_HERBS, ModBlocks.BARREL_RACK, ModBlocks.RUG_RED, ModBlocks.RUG_BLUE, ModBlocks.CRATE, ModBlocks.FIREWOOD_PILE,
                ModBlocks.HANGING_LANTERN, ModBlocks.BENCH, ModBlocks.TAVERN_KEG, ModBlocks.COUNTER};
        row(l, g, 0, a, N);
        row(l, g, 4, b, N);
        Block[] c = {ModBlocks.TOWN_HALL_DESK, ModBlocks.CARPENTER_BENCH, ModBlocks.MASON_TABLE, ModBlocks.SMITH_ANVIL, ModBlocks.COOK_STOVE, ModBlocks.HERBALIST_TABLE,
                ModBlocks.WEAPON_RACK, ModBlocks.TAVERN_KEG, ModBlocks.ORDER_BOARD, ModBlocks.BULLETIN_BOARD, ModBlocks.ALARM_BELL};
        Block[] d = {ModBlocks.LAMP_POST, ModBlocks.SIGNPOST, ModBlocks.SIGNS.get(0), ModBlocks.SIGNS.get(1), ModBlocks.SIGNS.get(2), ModBlocks.SIGNS.get(3), ModBlocks.SIGNS.get(4),
                ModBlocks.FLOWER_BOX, ModBlocks.BENCH, ModBlocks.CHIEFTAIN_BANNER, ModBlocks.SETTLEMENT_BANNER};
        row(l, g2, 0, c, N);
        row(l, g2, 4, d, N);
        log("HHTEST H block rows built");
    }

    private static void row(ServerLevel l, BlockPos o, int z, Block[] blocks, Direction facing) {
        for (int i = 0; i < blocks.length; i++) {
            Block bl = blocks[i];
            BlockState st = bl.defaultBlockState();
            if (st.hasProperty(HorizontalBlock.FACING)) st = st.setValue(HorizontalBlock.FACING, facing);
            if (bl == ModBlocks.BOTTLES || bl == ModBlocks.MUG || bl == ModBlocks.CANDLES) st = st.setValue(SmallStackBlock.COUNT, 3);
            if (bl == ModBlocks.HANGING_LANTERN) st = st.setValue(LanternBlock.HANGING, false);
            if (bl == ModBlocks.LONG_TABLE) { l.setBlock(o.offset(i * 2 + 1, 0, z), st, 3); }
            l.setBlock(o.offset(i * 2, 0, z), st, 3);
        }
    }

    private static void buildGallery(net.minecraft.server.MinecraftServer s) {
        ServerLevel l = s.overworld();
        BlockPos g = H.offset(2, 0, 33);
        fill(l, g, -1, -1, -2, 18, -1, 4, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        fill(l, g, -1, 0, 3, 18, 5, 3, Blocks.SMOOTH_STONE.defaultBlockState());
        List<String> ids = new ArrayList<>(ModItems.FOODS.keySet());
        ids.addAll(List.of("hearth_mark", "war_horn", "healing_salve", "founders_chronicle"));
        int per = 11;
        for (int i = 0; i < ids.size(); i++) {
            int col = i % per, rowi = i / per;
            double x = g.getX() + 0.8 + col * 1.55, y = g.getY() + 3.7 - rowi * 1.7, z = g.getZ() + 2.4;
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(),
                    "summon minecraft:item_display " + x + " " + y + " " + z + " {item:{id:\"hearthholm:" + ids.get(i)
                            + "\",count:1},billboard:\"center\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1.35f,1.35f,1.35f],translation:[0f,0f,0f]}}");
        }
        log("HHTEST H item gallery: " + ids.size() + " item displays");
    }
}
