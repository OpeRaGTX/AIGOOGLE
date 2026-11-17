package io.github.operagtx.hearthholm.entity.ai;

import io.github.operagtx.hearthholm.entity.SettlerEntity;

/** A small step of work (walk, break, place, wait...). Ticked until it is not RUNNING. */
public interface Task {
    enum Status { RUNNING, DONE, FAILED }

    Status tick(SettlerEntity settler);

    /** Called once when the task ends or is interrupted. */
    default void stop(SettlerEntity settler) {}
}
