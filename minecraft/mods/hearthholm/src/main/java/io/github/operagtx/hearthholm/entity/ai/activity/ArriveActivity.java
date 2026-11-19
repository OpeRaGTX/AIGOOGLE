package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** A freshly hired settler walks in from the edge of the settlement to the town hall. */
public final class ArriveActivity implements Activity {
    private final SettlerEntity s;
    private MoveTo walk;

    public ArriveActivity(SettlerEntity s) { this.s = s; }

    @Override public boolean wants() { return !s.hasArrived() && !s.isLeaving(); }

    @Override
    public boolean tick() {
        Settlement st = s.settlement();
        if (st == null) { s.markArrived(); return false; }
        s.setStatus("arriving");
        if (walk == null) walk = new MoveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(st.center), 4, 0.7);
        Task.Status r = walk.tick(s);
        if (r == Task.Status.RUNNING) return true;
        s.markArrived();
        SettlementService.notifyOwner(s, Component.translatable("message.hearthholm.arrived", s.getName(), st.displayName()).withStyle(ChatFormatting.GREEN));
        return false;
    }

    @Override public void stop() { if (walk != null) walk.stop(s); walk = null; }
}
