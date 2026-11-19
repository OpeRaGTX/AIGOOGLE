package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;

import java.util.function.Consumer;

/** Runs an action once. */
public record Run(Consumer<SettlerEntity> action) implements Task {
    @Override
    public Status tick(SettlerEntity s) {
        action.accept(s);
        return Status.DONE;
    }
}
