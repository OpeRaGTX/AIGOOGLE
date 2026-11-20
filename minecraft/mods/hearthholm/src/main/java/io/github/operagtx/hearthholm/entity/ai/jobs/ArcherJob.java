package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.social.Social;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Walks the patrol posts like a guard, with a bow. Arrows are consumed per shot; below {@code Balance.ARROWS_LOW} the archer
 * restocks from a chest next to the post or the warehouse. Without arrows (and none to fetch) it shows "Needs arrows" and
 * fights in melee.
 */
public class ArcherJob extends GuardJob {
    private long nextArrowTrip, nextArrowSay;

    public ArcherJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        long now = s.level().getGameTime();
        if (s.count(st -> st.is(Items.ARROW)) < Balance.ARROWS_LOW && now >= nextArrowTrip) {
            BlockPos p = findWith(dropOffs(), st -> st.is(Items.ARROW));
            if (p != null) {
                nextArrowTrip = now + 200;
                s.setStatus("fetching_arrows");
                return new ContainerTrip(p, (who, c) -> who.store(Storage.take(List.of(c), st -> st.is(Items.ARROW), Balance.ARROWS_FETCH)));
            }
            nextArrowTrip = now + 400;
        }
        Task t = super.next(s);
        if (s.needsArrows()) {
            s.setStatus("needs_arrows");
            s.addThought("no_arrows", -3, 2400);
            if (now >= nextArrowSay) {
                nextArrowSay = now + 600;
                Social.say(s, "need_arrows", 99);
            }
        }
        return t;
    }
}
