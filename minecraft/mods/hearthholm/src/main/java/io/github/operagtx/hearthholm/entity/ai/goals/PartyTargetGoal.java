package io.github.operagtx.hearthholm.entity.ai.goals;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Party members fight what attacks their leader and what the leader attacks. */
public final class PartyTargetGoal extends Goal {
    private final SettlerEntity settler;
    private LivingEntity pick;

    public PartyTargetGoal(SettlerEntity settler) {
        this.settler = settler;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    private static boolean valid(SettlerEntity s, LivingEntity t, ServerPlayer owner) {
        return t != null && t.isAlive() && t != owner && !(t instanceof Player) && !(t instanceof SettlerEntity) && !(t instanceof IronGolem)
                && !(t instanceof TamableAnimal ta && ta.isTame()) && s.distanceToSqr(t) < 28 * 28 && s.canAttack(t);
    }

    @Override
    public boolean canUse() {
        if (!settler.inParty() || !settler.isCombatant() || settler.isRetreating() || settler.tickCount % 5 != 0) return false;
        if (!(settler.level() instanceof ServerLevel sl)) return false;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(settler.partyOwner());
        if (owner == null || owner.level() != settler.level()) return false;
        int now = owner.tickCount;
        pick = null;
        LivingEntity hurtBy = owner.getLastHurtByMob();
        if (now - owner.getLastHurtByMobTimestamp() < 100 && valid(settler, hurtBy, owner)) pick = hurtBy;
        else {
            LivingEntity hit = owner.getLastHurtMob();
            if (now - owner.getLastHurtMobTimestamp() < 100 && valid(settler, hit, owner)) pick = hit;
        }
        return pick != null && pick != settler.getTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        settler.setTarget(pick);
    }
}
