package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;

/** The knight works like a warrior (banner territory, escort, hold); its edge comes from stats, shield and taunt. */
public final class KnightJob extends WarriorJob {
    public KnightJob(SettlerEntity settler) {
        super(settler);
    }
}
