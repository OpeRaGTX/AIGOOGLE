package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.party.Party;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * War-horn party: follow the leader (teleporting when more than 24 blocks behind or in another dimension), eat from
 * the own pockets or the leader's off hand when hungry, no settlement work. Fighting is done by the combat goals,
 * which pre-empt this activity whenever there is a target.
 */
public final class PartyActivity implements Activity {
    private final SettlerEntity s;
    private int repath, chew, bubble;

    public PartyActivity(SettlerEntity s) { this.s = s; }

    @Override
    public boolean wants() {
        return s.inParty() && !s.isLeaving() && s.hasArrived();
    }

    @Override
    public void start() {
        if (s.isSleeping()) s.stopSleeping();
        repath = 0;
        chew = 0;
        bubble = 200 + s.getRandom().nextInt(400);
    }

    private static boolean food(ItemStack st) {
        return !st.isEmpty() && Storage.isFood(st);
    }

    @Override
    public boolean tick() {
        if (!s.inParty() || !(s.level() instanceof ServerLevel level)) return false;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(s.partyOwner());
        if (owner == null) {
            s.setStatus("party_waiting");
            s.getNavigation().stop();
            return true;
        }
        if (owner.level() != level) {
            Party.teleportNear(s, owner);
            return true;
        }
        double d = Math.sqrt(s.distanceToSqr(owner));
        if (d > 24) {
            Party.teleportNear(s, owner);
            return true;
        }
        if (s.food() < 8 && eat(owner, d)) return true;
        s.setStatus("party_with", owner.getName());
        if (d > 4.5) {
            if (--repath <= 0) {
                repath = 10;
                s.getNavigation().moveTo(owner, (d > 12 ? 1.0 : 0.75) * s.moveFactor());
            }
        } else {
            s.getNavigation().stop();
            if (s.tickCount % 60 == 0) s.getLookControl().setLookAt(owner, 30f, 30f);
        }
        if (--bubble <= 0) {
            bubble = 600 + s.getRandom().nextInt(900);
            s.say("party_idle");
        }
        return true;
    }

    private boolean eat(ServerPlayer owner, double d) {
        ItemStack food = ItemStack.EMPTY;
        for (ItemStack st : s.inventory().getItems()) if (food(st)) { food = st; break; }
        if (food.isEmpty() && d < 6 && food(owner.getOffhandItem())) food = owner.getOffhandItem();
        if (food.isEmpty()) return false;
        s.setStatus("eating");
        s.getNavigation().stop();
        if (++chew % 8 == 0) s.swingArm(InteractionHand.MAIN_HAND);
        if (chew >= 32) {
            chew = 0;
            s.eat(food.split(1));
            s.inventory().setChanged();
        }
        return true;
    }

    @Override
    public void stop() {
        s.getNavigation().stop();
    }
}
