package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.town.Loot;
import net.minecraft.world.entity.Entity;

/** Wave I2: walks to a dropped item (or a loose arrow) and picks it up. */
public final class Fetch implements Task {
    private final Entity target;
    private int ticks, repath;

    public Fetch(Entity target) {
        this.target = target;
    }

    @Override
    public Status tick(SettlerEntity s) {
        if (!target.isAlive() || ++ticks > 600) return ticks > 600 ? Status.FAILED : Status.DONE;
        if (s.distanceToSqr(target) < 2.25) {
            Loot.collect(s, target);
            s.getNavigation().stop();
            return Status.DONE;
        }
        if (--repath <= 0) {
            repath = 20;
            s.getNavigation().moveTo(target, 0.6);
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(SettlerEntity s) {
        s.getNavigation().stop();
    }
}
