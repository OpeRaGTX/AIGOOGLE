package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import org.jetbrains.annotations.Nullable;

/**
 * Wave K2: when the shift is over the settler hands in everything he carries except his tool or weapon, armour and a couple of food
 * items: into the chest by his work site, else the warehouse (see {@code BaseJob.endOfShift}). Runs once per workday; a full chest means
 * one more trip to the next container, at most {@link #MAX_TRIPS} in a row, so nobody paces forever.
 */
public final class ShiftEndActivity implements Activity {
    private static final int MAX_TRIPS = 4;
    private final SettlerEntity s;
    private @Nullable Task task;
    private int trips;

    public ShiftEndActivity(SettlerEntity s) { this.s = s; }

    @Override
    public boolean wants() {
        return !s.isLeaving() && s.hasArrived() && s.shiftUnloadDue();
    }

    @Override
    public void start() {
        task = null;
        trips = 0;
    }

    @Override
    public boolean tick() {
        if (s.isWorkTime()) return false;   // the clock moved back: back to work
        if (task == null) {
            if (trips >= MAX_TRIPS) { s.markUnloaded(); return false; }
            task = s.brain().job().endOfShift(s);
            if (task == null) { s.markUnloaded(); return false; }   // nothing to hand in, or nowhere to put it
            trips++;
        }
        Task.Status r = task.tick(s);
        if (r != Task.Status.RUNNING) {
            task.stop(s);
            task = null;   // the next tick asks the job again: it returns null once the pockets are light
        }
        return true;
    }

    @Override
    public void stop() {
        if (task != null) task.stop(s);
        task = null;
    }
}
