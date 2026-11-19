package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Walks into reach and places a block, paying one item from the settler's inventory. */
public final class PlaceBlock implements Task {
    private final BlockPos pos;
    private final BlockState state;
    private final @Nullable Item cost;
    private final @Nullable Consumer<SettlerEntity> after;
    private MoveTo approach;
    private int delay;

    public PlaceBlock(BlockPos pos, BlockState state, @Nullable Item cost, @Nullable Consumer<SettlerEntity> after) {
        this(pos, state, cost, 6, after);
    }

    /** {@code delay}: ticks spent standing in reach before the block appears (the builder's pace). */
    public PlaceBlock(BlockPos pos, BlockState state, @Nullable Item cost, int delay, @Nullable Consumer<SettlerEntity> after) {
        this.delay = delay;
        this.pos = pos.immutable();
        this.state = state;
        this.cost = cost;
        this.after = after;
    }

    @Override
    public Status tick(SettlerEntity s) {
        ServerLevel level = (ServerLevel) s.level();
        if (!s.canReach(pos)) {
            if (approach == null) approach = MoveTo.block(pos, 2.0);
            Status st = approach.tick(s);
            if (st == Status.FAILED) return Status.FAILED;
            if (st == Status.RUNNING) return Status.RUNNING;
            approach = null;
            if (!s.canReach(pos) && !s.hopNear(pos)) return Status.FAILED;
        }
        s.getNavigation().stop();
        s.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (--delay > 0) return Status.RUNNING;
        BlockState current = level.getBlockState(pos);
        if (!current.canBeReplaced() && !current.isAir()) return Status.FAILED;
        if (s.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos)) && state.isCollisionShapeFullBlock(level, pos)) {
            if (!s.hopNear(pos.above())) return Status.FAILED;
        }
        if (io.github.operagtx.hearthholm.blueprint.Blueprint.needsSupport(state) && !hasSupport(level, pos, state)) return Status.FAILED;   // K1: never into the air
        if (cost != null && !s.takeItem(cost, 1)) return Status.FAILED;
        BlockState toPlace = joinChest(level, pos, state);
        level.setBlock(pos, toPlace, 3);
        if (toPlace.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {   // the other half becomes the opposite half
            net.minecraft.world.level.block.state.properties.ChestType t = toPlace.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
            if (t != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                net.minecraft.core.Direction f = toPlace.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                BlockPos other = pos.relative(t == net.minecraft.world.level.block.state.properties.ChestType.RIGHT ? f.getClockWise() : f.getCounterClockWise());
                BlockState o = level.getBlockState(other);
                if (o.is(toPlace.getBlock())) level.setBlock(other, o.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, t.getOpposite()), 3);
            }
        }
        reshapeAround(level, pos);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1f);
        s.swingArm(InteractionHand.MAIN_HAND);
        if (after != null) after.accept(s);
        return Status.DONE;
    }

    @Override
    public void stop(SettlerEntity s) {
        if (approach != null) approach.stop(s);
    }

    /**
     * Wave H3: a placed stair keeps the shape stored in the blueprint (always straight); neighbours placed later fix themselves through the
     * normal shape update, but the stair placed last would stay straight. Runs the neighbour shape update on the stair itself, so inner and
     * outer corners come out right. Returns whether the shape changed.
     */
    public static boolean reshapeStairs(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        BlockState now = level.getBlockState(pos);
        if (!net.minecraft.world.level.block.StairBlock.isStairs(now)) return false;
        BlockState fixed = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(now, level, pos);
        if (fixed == now || !net.minecraft.world.level.block.StairBlock.isStairs(fixed)) return false;
        level.setBlock(pos, fixed, 2);
        return true;
    }

    /** Wave K1: whether an attachable stands on / hangs from something (its own survival rule, trapdoors: any solid neighbour). */
    public static boolean hasSupport(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
                if (d != net.minecraft.core.Direction.UP && !level.getBlockState(pos.relative(d)).isAir()
                        && level.getBlockState(pos.relative(d)).isFaceSturdy(level, pos.relative(d), d.getOpposite())) return true;
            return false;
        }
        return state.canSurvive(level, pos);
    }

    /** Wave K1: a chest next to a single chest of the same facing becomes one double chest (the blueprint stores both as single). */
    public static BlockState joinChest(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) return state;
        net.minecraft.core.Direction f = state.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
        BlockState single = state.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.SINGLE);
        for (int k = 0; k < 2; k++) {
            net.minecraft.core.Direction d = k == 0 ? f.getClockWise() : f.getCounterClockWise();
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.is(state.getBlock()) && n.getValue(net.minecraft.world.level.block.ChestBlock.FACING) == f
                    && n.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
                return single.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, k == 0
                        ? net.minecraft.world.level.block.state.properties.ChestType.RIGHT : net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        }
        return single;
    }

    /** Wave K1: connect panes, bars, fences, walls and stairs with their neighbours after a block appears (the block itself and its 6 neighbours). */
    public static void reshapeAround(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        reshape(level, pos);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) reshape(level, pos.relative(d));
    }

    public static void reshape(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        BlockState now = level.getBlockState(pos);
        net.minecraft.world.level.block.Block b = now.getBlock();
        if (b instanceof net.minecraft.world.level.block.StairBlock) { reshapeStairs(level, pos); return; }
        if (b instanceof net.minecraft.world.level.block.IronBarsBlock || b instanceof net.minecraft.world.level.block.FenceBlock
                || b instanceof net.minecraft.world.level.block.WallBlock) {
            BlockState fixed = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(now, level, pos);
            if (fixed != now && fixed.is(b)) level.setBlock(pos, fixed, 2);
        }
    }
}
