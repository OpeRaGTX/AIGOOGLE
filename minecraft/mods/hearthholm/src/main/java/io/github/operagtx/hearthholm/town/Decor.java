package io.github.operagtx.hearthholm.town;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Street decor: blocks register with their settlement; settlers walking near enough of them get "Beautiful street". */
public final class Decor {
    public static final int RADIUS = 8, NEEDED = 3;

    private Decor() {}

    public static void register(ServerLevel level, BlockPos pos) {
        Settlement s = SettlementService.manager(level).at(level.dimension(), pos);
        if (s == null || s.civic.decor.contains(pos)) return;
        s.civic.decor.add(pos.immutable());
        SettlementService.manager(level).setDirty();
    }

    /** Decor blocks within {@link #RADIUS} blocks (and 4 up or down) of a position. */
    public static int near(Settlement s, BlockPos at) {
        int n = 0;
        for (BlockPos p : s.civic.decor) {
            if (Math.abs(p.getY() - at.getY()) <= 4 && Math.abs(p.getX() - at.getX()) <= RADIUS && Math.abs(p.getZ() - at.getZ()) <= RADIUS) n++;
        }
        return n;
    }

    /** Every 5 seconds per settler: enough decor around gives a day-long good mood. */
    public static void tick(SettlerEntity e) {
        Settlement s = e.settlement();
        if (s == null || !e.hasArrived() || e.isSleeping() || s.civic.decor.size() < NEEDED) return;
        if (near(s, e.blockPosition()) >= NEEDED) {
            if (!e.hasThought("beautiful_street")) io.github.operagtx.hearthholm.social.Social.say(e, "street", 3);
            e.addThought("beautiful_street", io.github.operagtx.hearthholm.core.Balance.MOOD_STREET, 12000);
        }
    }
}
