package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;

/** Wave L1: the archer's promotion. Same duties (posts, arrows, loot); the crossbow itself is handled by the shooting goal and the profession's tool. */
public final class CrossbowmanJob extends ArcherJob {
    public CrossbowmanJob(SettlerEntity settler) {
        super(settler);
    }
}
