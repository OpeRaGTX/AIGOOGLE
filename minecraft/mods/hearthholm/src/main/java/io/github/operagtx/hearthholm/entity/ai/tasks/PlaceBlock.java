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
        if (cost != null && !s.takeItem(cost, 1)) return Status.FAILED;
        level.setBlock(pos, state, 3);
        reshapeStairs(level, pos);
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
}
