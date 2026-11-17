package io.github.operagtx.hearthholm.social;

import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.settlement.Festival;
import io.github.operagtx.hearthholm.settlement.ResidentRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** Server-side social life: speech bubbles, affinity between settlers, quarrels and evening chats. */
public final class Social {
    public static final int BUBBLE_TICKS = 80;
    public static final int FRIEND_AFFINITY = 40;
    /** Most bubbles visible near one settler at once. */
    public static final int MAX_NEARBY = 3;
    public static final int CHAT_WINDOW_START = 11000, CHAT_WINDOW_END = 12600;

    private Social() {}

    // ---- speech bubbles ---------------------------------------------------------------------

    /** Shows a line from the pool of {@code situation}; false when blocked (busy, asleep, too many bubbles nearby). */
    public static boolean say(SettlerEntity e, String situation, int cap) {
        if (!(e.level() instanceof ServerLevel sl) || e.isSleeping() || e.isLeaving() || e.isDeadOrDying()) return false;
        if (cap < 99 && e.hasBubble()) return false;
        if (cap < 99) {
            int near = 0;
            for (SettlerEntity o : sl.getEntitiesOfClass(SettlerEntity.class, e.getBoundingBox().inflate(16), x -> x != e && x.hasBubble())) near++;
            if (near >= cap) return false;
        }
        int idx = BubbleLines.pickIndex(situation, e.getRandom(), e.lastBubbleIndex(situation));
        if (idx < 0) return false;
        Settlement s = e.settlement();
        Component line = BubbleLines.line(situation, idx, s == null ? "" : s.displayName().getString(), s == null ? "" : s.ownerName);
        if (line == null) return false;
        e.rememberBubble(situation, idx);
        e.showBubble(line.copy().withStyle(e.profile().legendary() ? ChatFormatting.GOLD : ChatFormatting.WHITE), BUBBLE_TICKS + e.getRandom().nextInt(20));
        float pitch = 0.75f + (e.profile().nameIndex() % 9) * 0.05f + (e.profile().female() ? 0.3f : 0f);
        sl.playSound(null, e.blockPosition(), ModSounds.MURMUR, SoundSource.NEUTRAL, 0.35f, pitch);
        return true;
    }

    /** Every 2 s: maybe pick an ambient line. Respects the per-settler 30-90 s cooldown; silent when nobody is near. */
    public static void ambient(SettlerEntity e) {
        if (e.hasBubble() || e.isSleeping() || e.isLeaving() || !e.hasArrived()) return;
        if (e.sayCooldown() > 0) {
            e.setSayCooldown(e.sayCooldown() - 40);
            return;
        }
        if (!(e.level() instanceof ServerLevel sl)) return;
        ServerPlayer near = (ServerPlayer) sl.getNearestPlayer(e, 24);
        if (near == null) return;
        RandomSource r = e.getRandom();
        if (r.nextFloat() < 0.5f) return;
        String sit = choose(e, sl, near, r);
        if (sit != null && say(e, sit, MAX_NEARBY)) e.setSayCooldown(600 + r.nextInt(1200));
    }

    private static String choose(SettlerEntity e, ServerLevel sl, ServerPlayer near, RandomSource r) {
        Settlement s = e.settlement();
        long now = sl.getGameTime();
        if (s != null && near.getUUID().equals(s.owner) && near.distanceToSqr(e) < 49 && now >= e.greetedUntil()) {
            e.setGreetedUntil(now + 6000);
            return "greet";
        }
        if (e.food() < 8) return "hungry";
        if (s != null && e.bed() == null && r.nextFloat() < 0.6f) return "no_bed";
        if (s != null && Festival.active(sl, s)) return "festival";
        if (sl.isRainingAt(e.blockPosition()) && r.nextFloat() < 0.6f) return "rain";
        if (e.isSleepTime()) return "night";
        float roll = r.nextFloat();
        if (roll < 0.40f) {
            String prof = e.professionId().getPath();
            if (e.brain().currentActivity().equals("WorkActivity") || e.profession() == Professions.UNEMPLOYED) {
                if (BubbleLines.count("work_" + prof) > 0) return "work_" + prof;
            }
        } else if (roll < 0.70f && !e.profile().traits().isEmpty()) {
            var traits = e.profile().traits();
            String t = traits.get(r.nextInt(traits.size())).getPath();
            if (BubbleLines.count("trait_" + t) > 0) return "trait_" + t;
        }
        return "idle";
    }

    // ---- relationships ----------------------------------------------------------------------

    private static boolean calm(String activity) {
        return activity.equals("IdleActivity") || activity.equals("EatActivity") || activity.equals("FestivalActivity");
    }

    /** Every 10 s per settler: idling or eating next to someone slowly builds affinity; grumpy ones may quarrel. */
    public static void relations(SettlerEntity e) {
        if (e.isSleeping() || e.isLeaving() || !calm(e.brain().currentActivity()) || e.settlementId() == null) return;
        boolean festival = e.brain().currentActivity().equals("FestivalActivity");
        RandomSource r = e.getRandom();
        for (SettlerEntity o : e.level().getEntitiesOfClass(SettlerEntity.class, e.getBoundingBox().inflate(5),
                x -> x != e && e.settlementId().equals(x.settlementId()) && !x.isSleeping() && !x.isLeaving())) {
            if (!calm(o.brain().currentActivity()) || e.getUUID().compareTo(o.getUUID()) >= 0) continue;
            boolean grumpy = e.hasTrait("grumpy") || o.hasTrait("grumpy");
            boolean both = e.hasTrait("grumpy") && o.hasTrait("grumpy");
            if (grumpy && !festival && r.nextFloat() < (both ? 0.3f : 0.1f)) quarrel(e, o);
            else bond(e, o, festival ? 2 : 1);
        }
    }

    public static void bond(SettlerEntity a, SettlerEntity b, int delta) {
        int before = a.affinity(b.getUUID());
        a.addAffinity(b.getUUID(), delta);
        b.addAffinity(a.getUUID(), delta);
        if (before <= FRIEND_AFFINITY && a.affinity(b.getUUID()) > FRIEND_AFFINITY) {
            a.addThought("friend", 5, 36000);
            b.addThought("friend", 5, 36000);
            SettlementService.notifyOwner(a, Component.translatable("message.hearthholm.friends_made", a.getName(), b.getName()).withStyle(ChatFormatting.GREEN));
        }
    }

    public static void quarrel(SettlerEntity a, SettlerEntity b) {
        a.addAffinity(b.getUUID(), -8);
        b.addAffinity(a.getUUID(), -8);
        a.addThought("quarrel", -6, 24000);
        b.addThought("quarrel", -6, 24000);
        say(a, "quarrel", 4);
        say(b, "quarrel", 4);
        if (a.level() instanceof ServerLevel sl) {
            for (SettlerEntity x : new SettlerEntity[]{a, b})
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, x.getX(), x.getY() + 2.1, x.getZ(), 3, 0.25, 0.15, 0.25, 0.0);
        }
    }

    // ---- evening chats ----------------------------------------------------------------------

    /** Once a second per settlement: in the evening, pairs of friends find each other and have a chat. */
    public static void tickSettlement(ServerLevel level, Settlement s) {
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (tod < CHAT_WINDOW_START || tod >= CHAT_WINDOW_END || Festival.active(level, s) || s.residents.size() < 2) return;
        if (level.getRandom().nextFloat() > 0.35f) return;
        long day = SettlementService.day(level);
        List<SettlerEntity> free = new ArrayList<>();
        for (ResidentRecord r : s.residents) {
            if (level.getEntity(r.uuid) instanceof SettlerEntity e && e.canChat(day)) free.add(e);
        }
        for (SettlerEntity a : free) {
            for (SettlerEntity b : free) {
                if (a == b || a.getUUID().compareTo(b.getUUID()) >= 0) continue;
                if (Math.max(a.affinity(b.getUUID()), b.affinity(a.getUUID())) <= FRIEND_AFFINITY) continue;
                if (a.distanceToSqr(b) > 48 * 48) continue;
                startChat(level, a, b, day);
                return;
            }
        }
    }

    public static void startChat(ServerLevel level, SettlerEntity a, SettlerEntity b, long day) {
        long deadline = level.getGameTime() + 1100;
        a.beginChat(b.getUUID(), true, deadline, day);
        b.beginChat(a.getUUID(), false, deadline, day);
    }
}
