package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Wait implements Task {
    private int ticks;
    private final @Nullable Vec3 look;

    public Wait(int ticks) { this(ticks, null); }

    public Wait(int ticks, @Nullable Vec3 look) {
        this.ticks = ticks;
        this.look = look;
    }

    @Override
    public Status tick(SettlerEntity s) {
        if (look != null) s.getLookControl().setLookAt(look.x, look.y, look.z);
        return --ticks <= 0 ? Status.DONE : Status.RUNNING;
    }
}
