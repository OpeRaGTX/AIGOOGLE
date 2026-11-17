package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.ResidentRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Base for jobs bound to a work-site marker: claiming the nearest free marker, trips to the
 * marker chest or warehouse, and fetching tools. Subclasses only describe the actual work.
 */
public abstract class SiteJob extends BaseJob {
    private final Identifier siteType;
    private @Nullable BlockPos sitePos;
    private long nextClaim;

    protected SiteJob(SettlerEntity settler, Identifier siteType) {
        super(settler);
        this.siteType = siteType;
    }

    /** Whether one marker serves only one worker (mines, fields) or many (patrol posts). */
    protected boolean exclusive() { return true; }

    protected @Nullable WorkSiteBlockEntity site() {
        if (sitePos == null) return null;
        if (settler.level().getBlockEntity(sitePos) instanceof WorkSiteBlockEntity w && w.type().id().equals(siteType)) return w;
        sitePos = null;
        return null;
    }

    protected @Nullable WorkSiteBlockEntity claim() {
        WorkSiteBlockEntity current = site();
        if (current != null) return current;
        long now = settler.level().getGameTime();
        if (now < nextClaim) return null;
        nextClaim = now + 100;
        Settlement st = settler.settlement();
        if (st == null) return null;
        WorkSiteBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Settlement.SiteRecord rec : st.sitesOf(siteType)) {
            if (!settler.level().isLoaded(rec.pos()) || !(settler.level().getBlockEntity(rec.pos()) instanceof WorkSiteBlockEntity w)) continue;
            if (!available(st, w)) continue;
            double d = rec.pos().distSqr(settler.blockPosition());
            if (d < bestDist) { bestDist = d; best = w; }
        }
        if (best == null) {
            settler.setStatus("need_site", WorkSiteTypes.get(siteType).label());
            return null;
        }
        if (exclusive()) best.setWorkerId(settler.getUUID());
        sitePos = best.getBlockPos();
        return best;
    }


    protected boolean available(Settlement st, WorkSiteBlockEntity w) {
        if (!exclusive() || w.workerId() == null || w.workerId().equals(settler.getUUID())) return true;
        ResidentRecord other = st.resident(w.workerId());
        return other == null || !other.profession.equals(settler.professionId());
    }

    @Override
    public void release(SettlerEntity s) {
        WorkSiteBlockEntity w = site();
        if (w != null && settler.getUUID().equals(w.workerId())) w.setWorkerId(null);
        sitePos = null;
    }


    /** Site chest first, then warehouse. */
    @Override
    protected List<BlockPos> dropOffs() {
        List<BlockPos> out = new ArrayList<>();
        WorkSiteBlockEntity w = site();
        if (w != null) out.addAll(chestsNear(w.getBlockPos()));
        out.addAll(storage());
        return out;
    }
}
