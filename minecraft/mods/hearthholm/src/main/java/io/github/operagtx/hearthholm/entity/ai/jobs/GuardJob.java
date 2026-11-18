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

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.ai.tasks.Fetch;
import io.github.operagtx.hearthholm.town.Loot;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Walks the route of all patrol posts (in placement order) day and night. */
public class GuardJob extends SiteJob {
    private int postIndex;

    public GuardJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.PATROL_ID);
    }

    @Override protected boolean exclusive() { return false; }
    @Override protected int toolRetryTicks() { return Balance.WEAPON_RETRY_TICKS; }

    /** Patrol posts of the settlement, in route order. */
    private List<Settlement.SiteRecord> posts(Settlement st) {
        List<Settlement.SiteRecord> posts = st.sitesOf(WorkSiteTypes.PATROL_ID);
        posts.sort(Comparator.comparingLong(r -> r.pos().asLong()));
        return posts;
    }

    /** Chests next to the post nearest to the guard come first (weapons, arrows), then the warehouse. */
    @Override
    protected List<BlockPos> dropOffs() {
        List<BlockPos> out = new ArrayList<>();
        Settlement st = settler.settlement();
        if (st != null) {
            BlockPos near = null;
            double best = Double.MAX_VALUE;
            for (Settlement.SiteRecord r : st.sitesOf(WorkSiteTypes.PATROL_ID)) {
                double d = r.pos().distSqr(settler.blockPosition());
                if (d < best) { best = d; near = r.pos(); }
            }
            if (near != null) out.addAll(chestsNear(near, 3));
        }
        out.addAll(storage());
        return out;
    }

    /** Loot of the fallen: walk to a claimable drop inside the patrol radius, and carry the haul home when pockets fill or the shift ends. */
    private @Nullable Task lootTask(SettlerEntity s, Settlement st, List<Settlement.SiteRecord> posts) {
        boolean carrying = s.inventory().getItems().stream().anyMatch(x -> !x.isEmpty() && !CombatGear.keep(s, x));
        if (carrying && (pocketsFull() || s.shiftEndsWithin(900))) {
            Task t = depositTrip(x -> CombatGear.keep(s, x));
            if (t != null) return t;
        }
        if (pocketsFull() || s.getTarget() != null) return null;
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        if (posts.isEmpty()) {
            best = Loot.nearest(s, Vec3.atBottomCenterOf(st.center), 12 + Balance.LOOT_EXTRA_RADIUS);
        } else {
            for (Settlement.SiteRecord r : posts) {
                if (!s.level().isLoaded(r.pos())) continue;
                int radius = s.level().getBlockEntity(r.pos()) instanceof WorkSiteBlockEntity w ? w.option("radius") : 8;
                Entity e = Loot.nearest(s, Vec3.atBottomCenterOf(r.pos()), radius + Balance.LOOT_EXTRA_RADIUS);
                if (e != null && s.distanceToSqr(e) < bestD) { best = e; bestD = s.distanceToSqr(e); }
            }
        }
        if (best == null) return null;
        s.setStatus("collecting_loot");
        return new Fetch(best);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Task gear = CombatGear.trip(this, s);
        if (gear != null) return gear;
        Settlement st = s.settlement();
        if (st == null) return new Wait(100);
        List<Settlement.SiteRecord> posts = posts(st);
        Task loot = lootTask(s, st, posts);
        if (loot != null) return loot;
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
