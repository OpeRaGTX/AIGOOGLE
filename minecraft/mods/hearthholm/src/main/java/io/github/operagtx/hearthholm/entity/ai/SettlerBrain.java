package io.github.operagtx.hearthholm.entity.ai;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.activity.*;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Picks the most important activity every second and ticks it. Activities are listed by
 * priority; a higher one interrupts a lower one, which simply restarts later.
 */
public final class SettlerBrain {
    private final SettlerEntity settler;
    private final List<Activity> activities;
    private Activity current;
    private int evalCooldown;
    private Job job;
    private Identifier jobFor;

    public SettlerBrain(SettlerEntity settler) {
        this.settler = settler;
        this.activities = List.of(
                new LeaveActivity(settler),
                new ArriveActivity(settler),
                new AlarmActivity(settler),
                new SleepActivity(settler),
                new EatActivity(settler),
                new FestivalActivity(settler),
                new ChatActivity(settler),
                new WorkActivity(settler),
                new IdleActivity(settler));
    }

    public Job job() {
        Identifier p = settler.professionId();
        if (job == null || !p.equals(jobFor)) {
            if (job != null) job.release(settler);
            job = settler.profession().createJob(settler);
            jobFor = p;
        }
        return job;
    }

    public void onProfessionChanged() {
        pause();
        if (job != null) {
            job.release(settler);
            job = null;
        }
    }

    public void pause() {
        if (current != null) {
            current.stop();
            current = null;
        }
        evalCooldown = 0;
    }

    public void tick() {
        if (--evalCooldown <= 0) {
            evalCooldown = 20;
            for (Activity a : activities) {
                if (a == current) break;
                if (a.wants()) {
                    if (current != null) current.stop();
                    current = a;
                    current.start();
                    break;
                }
            }
        }
        if (current != null && !current.tick()) {
            current.stop();
            current = null;
            evalCooldown = 0;
        }
    }

    public String currentActivity() {
        return current == null ? "none" : current.getClass().getSimpleName();
    }
}
