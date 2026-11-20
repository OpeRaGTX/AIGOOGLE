package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.combat.Cavalry;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.tasks.Run;
import io.github.operagtx.hearthholm.entity.ai.tasks.Sequence;
import io.github.operagtx.hearthholm.settlement.Settlement;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Wave L1: the warrior's promotion. With a horse in the settlement territory and a saddle (on the horse, in his pockets or in a chest) he saddles the horse, mounts
 * it and patrols like a warrior on horseback; in a fight {@code CavalryChargeGoal} rams, rides past, turns and rams again. Without a horse or a saddle he is
 * simply a warrior on foot. He gets off when the shift ends ({@code SettlerEntity.tickMilitary}) and when the job is released.
 */
public final class CavalrymanJob extends WarriorJob {
    public CavalrymanJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st != null && !s.isPassenger() && s.getTarget() == null) {
            Horse horse = Cavalry.findHorse(s, st);
            if (horse != null) {
                if (!Cavalry.saddled(horse) && !s.has(Items.SADDLE)) {
                    Task fetch = fetch(x -> x.is(Items.SADDLE), 1, "fetching_saddle");   // from a chest by the post or the warehouse
                    if (fetch != null) return fetch;
                } else {
                    s.setStatus("mounting");
                    return new Sequence(new MoveTo(horse.position(), 2.2, 0.9), new Run(who -> {
                        if (horse.isAlive() && Cavalry.saddleFromPockets(who, horse)) Cavalry.mount(who, horse);
                    }));
                }
            }
        }
        return super.next(s);
    }

    /** Profession change or leaving: get off. (Not {@code pause}: the brain pauses the job between every two tasks, which unseated him right after boarding.) */
    @Override
    public void release(SettlerEntity settler) {
        Cavalry.dismount(settler);
        super.release(settler);
    }
}
