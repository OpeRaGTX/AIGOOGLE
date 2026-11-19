package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.entity.ai.Task;
import org.jetbrains.annotations.Nullable;

/** No job: the idle activity takes over. */
public final class UnemployedJob implements Job {
    public UnemployedJob(SettlerEntity settler) {}

    @Override
    public @Nullable Task next(SettlerEntity settler) {
        return null;
    }
}
