package io.github.operagtx.hearthholm.blueprint;

import io.github.operagtx.hearthholm.block.TownHallDeskBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A building template authored facing south: x runs west to east, z from the front row
 * (next to the stake) backwards, y from the ground layer up. {@code null} cells are kept as-is.
 * In the world the building stands in front of the stake, rotated to the stake's facing.
 */
public final class Blueprint {
    /** {@code clear} ops make room for {@code state} (the final block, possibly air); place ops put it. */
    public record BuildOp(BlockPos pos, BlockState state, @Nullable BlockPos companionPos,
                          @Nullable BlockState companionState, boolean clear) {
        public boolean desk() { return !clear && state.getBlock() instanceof TownHallDeskBlock; }
    }

    private final Identifier id;
    private final int width, height, depth;
    private final BlockState[] cells; // null = keep
    private final Map<String, List<BuildOp>> opCache = new HashMap<>();

    public Blueprint(Identifier id, int width, int height, int depth, BlockState[] cells) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.cells = cells;
    }

    public Identifier id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }

    public String labelKey() {
        return "blueprint." + id.getNamespace() + "." + id.getPath();
    }

    public Component label() {
        return Component.translatable(labelKey());
    }

    public @Nullable BlockState at(int x, int y, int z) {
        return cells[(y * depth + z) * width + x];
    }

    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public BlockPos toWorld(BlockPos stake, Direction facing, int x, int y, int z) {
        Direction right = facing.getCounterClockWise();
        return stake.relative(facing, z + 1).relative(right, x - width / 2).above(y - 1);
    }

    public AABB worldBox(BlockPos stake, Direction facing) {
        BlockPos a = toWorld(stake, facing, 0, 0, 0);
        BlockPos b = toWorld(stake, facing, width - 1, height - 1, depth - 1);
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    private static boolean attached(BlockState s) {
        Block b = s.getBlock();
        return b instanceof TorchBlock || b instanceof LanternBlock || b instanceof DoorBlock || b instanceof BedBlock
                || b instanceof LadderBlock || b instanceof CarpetBlock || b instanceof SignBlock || b instanceof BannerBlock
                || b instanceof WallBannerBlock || b instanceof FlowerPotBlock || b instanceof ButtonBlock
                || b instanceof LeverBlock || b instanceof TrapDoorBlock || b instanceof TownHallDeskBlock;
    }

    private static boolean companionHalf(BlockState s) {
        if (s.getBlock() instanceof BedBlock) return s.getValue(BedBlock.PART) == BedPart.HEAD;
        if (s.getBlock() instanceof DoorBlock) return s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        return false;
    }

    /** Clear pass (top-down) then place pass (bottom-up, attachments last). Cached per placement. */
    public synchronized List<BuildOp> ops(BlockPos stake, Direction facing) {
        String key = stake.asLong() + "/" + facing;
        List<BuildOp> cached = opCache.get(key);
        if (cached != null) return cached;
        Rotation rot = rotation(facing);
        List<BuildOp> clear = new ArrayList<>();
        List<BuildOp> solid = new ArrayList<>();
        List<BuildOp> late = new ArrayList<>();
        for (int y = height - 1; y >= 0; y--)
            for (int z = 0; z < depth; z++)
                for (int x = 0; x < width; x++) {
                    BlockState target = at(x, y, z);
                    if (target != null) clear.add(new BuildOp(toWorld(stake, facing, x, y, z), target.rotate(rot), null, null, true));
                }
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                for (int x = 0; x < width; x++) {
                    BlockState s = at(x, y, z);
                    if (s == null || s.isAir() || companionHalf(s)) continue;
                    BlockState r = s.rotate(rot);
                    BlockPos pos = toWorld(stake, facing, x, y, z);
                    BlockPos cPos = null;
                    BlockState cState = null;
                    if (s.getBlock() instanceof BedBlock) {
                        cPos = pos.relative(r.getValue(BedBlock.FACING));
                        cState = r.setValue(BedBlock.PART, BedPart.HEAD);
                    } else if (s.getBlock() instanceof DoorBlock) {
                        cPos = pos.above();
                        cState = r.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                    }
                    (attached(s) ? late : solid).add(new BuildOp(pos, r, cPos, cState, false));
                }
        List<BuildOp> all = new ArrayList<>(clear.size() + solid.size() + late.size());
        all.addAll(clear);
        all.addAll(solid);
        all.addAll(late);
        List<BuildOp> out = List.copyOf(all);
        opCache.put(key, out);
        return out;
    }

    /** Item paid for placing this state, or null when free (air, desk relocation). */
    public static @Nullable Item cost(BlockState state) {
        if (state.getBlock() instanceof TownHallDeskBlock) return null;
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    public Map<Item, Integer> materials() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (BlockState s : cells) {
            if (s == null || s.isAir() || companionHalf(s)) continue;
            Item it = cost(s);
            if (it != null) out.merge(it, 1, Integer::sum);
        }
        return out;
    }
}
