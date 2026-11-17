package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.tasks.Sequence;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Walks the route of all patrol posts (in placement order) day and night. */
public final class GuardJob extends SiteJob {
    private int postIndex;

    public GuardJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.PATROL_ID);
    }

    @Override protected boolean exclusive() { return false; }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Task gear = CombatGear.trip(this, s);
        if (gear != null) return gear;
        Settlement st = s.settlement();
        if (st == null) return new Wait(100);
        List<Settlement.SiteRecord> posts = st.sitesOf(WorkSiteTypes.PATROL_ID);
        posts.sort(Comparator.comparingLong(r -> r.pos().asLong()));
        if (posts.isEmpty()) {
            s.setStatus("patrolling_hall");
            Vec3 t = Vec3.atBottomCenterOf(st.center).add(s.getRandom().nextInt(21) - 10, 0, s.getRandom().nextInt(21) - 10);
            return new Sequence(new MoveTo(t, 2, 0.55), new Wait(60));
        }
        BlockPos post = posts.get(postIndex++ % posts.size()).pos();
        int wait = 15 * 20;
        int radius = 6;
        if (s.level().getBlockEntity(post) instanceof WorkSiteBlockEntity w) {
            wait = w.option("wait") * 20;
            radius = w.option("radius");
        }
        s.setStatus("patrolling");
        Vec3 look = Vec3.atCenterOf(post).add(s.getRandom().nextInt(2 * radius + 1) - radius, 1, s.getRandom().nextInt(2 * radius + 1) - radius);
        return new Sequence(MoveTo.block(post, 2.2), new Wait(wait, look));
    }


}
