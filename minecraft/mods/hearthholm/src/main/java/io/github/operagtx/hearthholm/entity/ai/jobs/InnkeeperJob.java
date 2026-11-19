package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.town.Tavern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** By day the innkeeper keeps the tavern tidy behind the keg; the real work (serving) happens in the evening ({@code TavernActivity}). */
public final class InnkeeperJob extends BaseJob {
    public InnkeeperJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        BlockPos keg = Tavern.keg(s.settlement());
        if (keg == null) {
            s.setStatus("need_workshop", io.github.operagtx.hearthholm.crafting.Workshop.TAVERN.label());
            return new Wait(200);
        }
        s.setStatus("tavern_prep");
        if (s.blockPosition().distSqr(keg) > 25) return io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo.block(keg, 3);
        return new Wait(200, Vec3.atCenterOf(keg));
    }
}
