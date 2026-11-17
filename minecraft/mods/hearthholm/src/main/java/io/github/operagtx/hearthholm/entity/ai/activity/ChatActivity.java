package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.social.Social;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Two friends walk up to each other, face each other and talk: bubbles, notes and hearts. */
public final class ChatActivity implements Activity {
    private static final int LINE_TICKS = 70, LINES = 6;
    private final SettlerEntity s;
    private int talk, walk;

    public ChatActivity(SettlerEntity s) { this.s = s; }

    @Override
    public boolean wants() {
        if (s.isLeaving() || !s.hasArrived() || s.chatPartnerId() == null) return false;
        if (s.partner() == null || s.level().getGameTime() > s.chatDeadline()) {
            s.endChat();
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        talk = 0;
        walk = 0;
    }

    @Override
    public boolean tick() {
        SettlerEntity p = s.partner();
        if (p == null || s.level().getGameTime() > s.chatDeadline() || p.isSleeping()) {
            s.endChat();
            return false;
        }
        s.setStatus("chatting");
        if (s.distanceTo(p) > 2.6) {
            if (walk++ % 20 == 0) s.getNavigation().moveTo(p, 0.5 * s.moveFactor());
            return true;
        }
        s.getNavigation().stop();
        s.getLookControl().setLookAt(p, 40f, 40f);
        double dx = p.getX() - s.getX(), dz = p.getZ() - s.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        s.setYRot(yaw);
        s.yBodyRot = yaw;
        s.yHeadRot = yaw;
        talk++;
        int turn = talk / LINE_TICKS;
        if (talk % LINE_TICKS == 5 && s.isChatInitiator() == (turn % 2 == 0) && s.level() instanceof ServerLevel sl) {
            Social.say(s, "chat", 4);
            sl.sendParticles(turn % 3 == 2 ? ParticleTypes.HEART : ParticleTypes.NOTE, s.getX(), s.getY() + 2.2, s.getZ(), 2, 0.25, 0.1, 0.25, 0.5);
        }
        if (talk >= LINE_TICKS * LINES && s.isChatInitiator()) {
            Social.bond(s, p, 4);
            s.addThought("friend", io.github.operagtx.hearthholm.core.Balance.MOOD_FRIEND, 36000);
            p.addThought("friend", io.github.operagtx.hearthholm.core.Balance.MOOD_FRIEND, 36000);
            p.endChat();
            s.endChat();
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        s.getNavigation().stop();
    }
}
