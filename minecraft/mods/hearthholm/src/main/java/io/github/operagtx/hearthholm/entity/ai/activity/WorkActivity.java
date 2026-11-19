package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;

/** Runs the profession's job: asks it for tasks one by one. */
public final class WorkActivity implements Activity {
    private final SettlerEntity s;
    private Task task;
    private int cooldown;

    public WorkActivity(SettlerEntity s) { this.s = s; }

    @Override
    public boolean wants() {
        return !s.isLeaving() && s.hasArrived() && s.isWorkTime() && s.profession() != Professions.UNEMPLOYED;
    }

    @Override
    public boolean tick() {
        if (!s.isWorkTime()) return false;
        s.markWorked();
        if (task == null) {
            if (cooldown > 0) { cooldown--; return true; }
            task = s.brain().job().next(s);
            if (task == null) { cooldown = 40; return true; }
        }
        if (!(task instanceof io.github.operagtx.hearthholm.entity.ai.tasks.Wait)) s.workTick();
        Task.Status r = task.tick(s);
        if (r != Task.Status.RUNNING) {
            task.stop(s);
            task = null;
            if (r == Task.Status.FAILED) cooldown = 20;
        }
        return true;
    }

    @Override
    public void stop() {
        if (task != null) task.stop(s);
        task = null;
        s.brain().job().pause(s);
    }
}
