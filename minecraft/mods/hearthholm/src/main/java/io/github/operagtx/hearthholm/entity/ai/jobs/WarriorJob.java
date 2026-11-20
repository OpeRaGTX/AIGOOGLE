package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.tasks.Sequence;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Defends the territory of a banner: roams it, holds the banner or escorts the settlement owner. */
public class WarriorJob extends SiteJob {
    public static final int PATROL = 0, FOLLOW = 1, HOLD = 2;

    public WarriorJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.TERRITORY_ID);
    }

    @Override protected boolean exclusive() { return false; }
    @Override protected int toolRetryTicks() { return io.github.operagtx.hearthholm.core.Balance.WEAPON_RETRY_TICKS; }

    @Override
    public boolean defends(SettlerEntity s, LivingEntity target) {
        WorkSiteBlockEntity w = site();
        if (w != null) {
            if (w.hasZone() && w.option("stance") != FOLLOW) {   // wave L2: a drawn zone (50 x 50): inside it, with a margin of 4 blocks
                var zb = w.zone().bounds(w.getBlockPos().getY() - 24, w.getBlockPos().getY() + 24);
                if (zb != null) return zb.inflate(4, 0, 4).contains(target.position());
            }
            int r = w.option("radius") + 4;
            if (w.option("stance") == FOLLOW) {
                ServerPlayer owner = owner(s);
                if (owner != null && owner.distanceToSqr(target) < 16 * 16) return true;
            }
            return w.getBlockPos().distSqr(target.blockPosition()) <= (double) r * r;
        }
        return false;
    }

    private static @Nullable ServerPlayer owner(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st == null || !(s.level() instanceof ServerLevel sl)) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(st.owner);
        return p != null && p.level() == s.level() && p.distanceToSqr(s) < 96 * 96 ? p : null;
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Task gear = CombatGear.trip(this, s);
        if (gear != null) return gear;
        WorkSiteBlockEntity w = claim();
        Settlement st = s.settlement();
        if (st == null) return new Wait(100);
        int stance = w == null ? PATROL : w.option("stance");
        if (stance == FOLLOW) {
            ServerPlayer owner = owner(s);
            if (owner != null) {
                s.setStatus("escorting", owner.getName());
                if (s.distanceToSqr(owner) < 9) return new Wait(20, owner.getEyePosition());
                return new MoveTo(owner.position(), 3, 0.95);
            }
        }
        Vec3 center = Vec3.atBottomCenterOf(w != null ? w.getBlockPos() : st.center);
        if (stance == HOLD && w != null) {
            s.setStatus("holding");
            return new Sequence(MoveTo.block(w.getBlockPos(), 1.8), new Wait(100));
        }
        int r = w != null ? w.option("radius") : Math.max(10, st.radius());   // wave L1: without a marker he patrols the whole settlement, with one the marked territory
        s.setStatus(w != null ? "guarding_territory" : "patrolling_hall");
        Vec3 t = center.add(s.getRandom().nextInt(2 * r + 1) - r, 0, s.getRandom().nextInt(2 * r + 1) - r);
        if (w != null && w.hasZone()) {   // wave L2: the patrol goes to random cells of the drawn zone
            var col = w.zone().randomColumn(io.github.operagtx.hearthholm.zone.WorkZone.SURFACE, s.getRandom(), w.getBlockPos().getY());
            if (col != null) t = Vec3.atBottomCenterOf(col);
        }
        return new Sequence(new MoveTo(t, 2.5, 0.6).noHop(), new Wait(40 + s.getRandom().nextInt(80)));
    }
}
