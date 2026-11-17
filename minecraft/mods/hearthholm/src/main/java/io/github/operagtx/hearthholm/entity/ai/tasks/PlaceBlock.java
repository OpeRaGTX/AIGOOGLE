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
    private int delay = 6;

    public PlaceBlock(BlockPos pos, BlockState state, @Nullable Item cost, @Nullable Consumer<SettlerEntity> after) {
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
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1f);
        s.swingArm(InteractionHand.MAIN_HAND);
        if (after != null) after.accept(s);
        return Status.DONE;
    }

    @Override
    public void stop(SettlerEntity s) {
        if (approach != null) approach.stop(s);
    }
}
