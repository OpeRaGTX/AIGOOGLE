package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Runs tasks one after another; stops at the first failure. */
public final class Sequence implements Task {
    private final ArrayDeque<Task> queue;
    private Task current;

    public Sequence(Task... tasks) {
        this.queue = new ArrayDeque<>(Arrays.asList(tasks));
    }

    @Override
    public Status tick(SettlerEntity s) {
        if (current == null) {
            current = queue.poll();
            if (current == null) return Status.DONE;
        }
        Status st = current.tick(s);
        if (st == Status.RUNNING) return st;
        current.stop(s);
        current = null;
        if (st == Status.FAILED) return Status.FAILED;
        return queue.isEmpty() ? Status.DONE : Status.RUNNING;
    }

    @Override
    public void stop(SettlerEntity s) {
        if (current != null) current.stop(s);
    }
}
