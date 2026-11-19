package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/** Walks to a container and does something with it (deposit, take food, take a tool...). */
public final class ContainerTrip implements Task {
    private final BlockPos pos;
    private final BiConsumer<SettlerEntity, Container> action;
    private final MoveTo approach;

    public ContainerTrip(BlockPos pos, BiConsumer<SettlerEntity, Container> action) {
        this.pos = pos.immutable();
        this.action = action;
        this.approach = MoveTo.block(pos, 2.3);
    }

    @Override
    public Status tick(SettlerEntity s) {
        Status st = approach.tick(s);
        if (st != Status.DONE) return st;
        if (!(s.level().getBlockEntity(pos) instanceof Container c)) return Status.FAILED;
        s.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        s.swingArm(InteractionHand.MAIN_HAND);
        s.level().playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
        action.accept(s, c);
        return Status.DONE;
    }

    @Override
    public void stop(SettlerEntity s) {
        approach.stop(s);
    }
}
