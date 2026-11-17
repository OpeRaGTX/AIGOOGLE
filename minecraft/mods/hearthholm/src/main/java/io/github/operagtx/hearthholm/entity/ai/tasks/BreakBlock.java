package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Walks into reach, mines the block over time (crack animation, swings) and keeps the drops. */
public final class BreakBlock implements Task {
    private final BlockPos pos;
    private final @Nullable Consumer<List<ItemStack>> onBroken;
    private MoveTo approach;
    private int ticks;
    private int needed = -1;

    public BreakBlock(BlockPos pos, @Nullable Consumer<List<ItemStack>> onBroken) {
        this.pos = pos.immutable();
        this.onBroken = onBroken;
    }

    @Override
    public Status tick(SettlerEntity s) {
        ServerLevel level = (ServerLevel) s.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return Status.DONE;
        if (state.getCollisionShape(level, pos).isEmpty() && !state.getFluidState().isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return Status.DONE;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return Status.FAILED;
        if (!s.canReach(pos)) {
            if (approach == null) approach = MoveTo.block(pos, 2.0);
            Status st = approach.tick(s);
            if (st == Status.FAILED) return Status.FAILED;
            if (st == Status.RUNNING || !s.canReach(pos)) {
                if (st == Status.DONE) approach = null;
                return Status.RUNNING;
            }
        }
        s.getNavigation().stop();
        s.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (needed < 0) needed = s.breakTicks(state, pos);
        ticks++;
        if (ticks % 5 == 1) {
            s.swingArm(InteractionHand.MAIN_HAND);
            io.github.operagtx.hearthholm.town.Fx.mine(level, pos, state, hardness >= 1.5f);
        }
        level.destroyBlockProgress(s.getId(), pos, Math.min(9, ticks * 10 / Math.max(1, needed)));
        if (ticks < needed) return Status.RUNNING;

        ItemStack tool = s.getMainHandItem();
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), s, tool);
        level.destroyBlock(pos, false, s);
        level.destroyBlockProgress(s.getId(), pos, -1);
        if (!tool.isEmpty() && tool.isDamageableItem()) tool.hurtAndBreak(1, s, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        s.store(drops);
        if (onBroken != null) onBroken.accept(drops);
        return Status.DONE;
    }

    @Override
    public void stop(SettlerEntity s) {
        s.level().destroyBlockProgress(s.getId(), pos, -1);
        if (approach != null) approach.stop(s);
    }
}
