package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Walks the patrol posts like a guard, with a bow; restocks arrows from the warehouse when low. */
public final class ArcherJob extends GuardJob {
    public static final int ARROWS_LOW = 12, ARROWS_FETCH = 48;
    private long nextArrowTrip;

    public ArcherJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        if (s.count(st -> st.is(Items.ARROW)) < ARROWS_LOW && s.level().getGameTime() >= nextArrowTrip) {
            nextArrowTrip = s.level().getGameTime() + 1200;
            BlockPos p = findWith(storage(), st -> st.is(Items.ARROW));
            if (p != null) {
                s.setStatus("fetching_arrows");
                return new ContainerTrip(p, (who, c) -> who.store(Storage.take(List.of(c), st -> st.is(Items.ARROW), ARROWS_FETCH)));
            }
        }
        return super.next(s);
    }
}
