package io.github.operagtx.hearthholm.entity.ai;

/** A top-level need-driven behaviour (sleep, eat, work, idle). Order in the brain = priority. */
public interface Activity {
    boolean wants();

    default void start() {}

    /** @return false when finished */
    boolean tick();

    default void stop() {}
}
