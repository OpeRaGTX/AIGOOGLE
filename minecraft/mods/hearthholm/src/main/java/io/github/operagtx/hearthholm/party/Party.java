package io.github.operagtx.hearthholm.party;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The war-horn party: up to four combat settlers follow their leader through the world. The party state lives on the
 * settlers themselves ({@code SettlerEntity#partyOwner}, saved), so it survives relogs and restarts; this class
 * only calls, dismisses and keeps members with the leader when the leader teleports or changes dimension.
 */
public final class Party {
    public static final int MAX = 4;
    /** Players who (probably) lead a party; checked every tick for sudden position jumps. */
    private static final Set<UUID> OWNERS = new HashSet<>();
    private static final Map<UUID, Sample> LAST = new HashMap<>();

    private record Sample(ResourceKey<Level> dim, Vec3 pos) {}

    private Party() {}

    public static void markOwner(UUID owner) {
        OWNERS.add(owner);
    }

    // ---- members -------------------------------------------------------------------------------

    /** All loaded members of this leader's party, in every dimension. */
    public static List<SettlerEntity> members(MinecraftServer server, UUID owner) {
        List<SettlerEntity> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels())
            out.addAll(l.getEntities(EntityTypeTest.forClass(SettlerEntity.class), e -> e.isAlive() && owner.equals(e.partyOwner())));
        return out;
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable("message.hearthholm." + key, args).withStyle(ChatFormatting.GOLD));
    }

    // ---- horn ----------------------------------------------------------------------------------

    /** Horn use: dismiss when a party exists (or on sneak), otherwise call one. */
    public static void toggle(ServerLevel level, ServerPlayer player, boolean forceDismiss) {
        List<SettlerEntity> current = members(level.getServer(), player.getUUID());
        level.playSound(null, player.blockPosition(), ModSounds.WAR_HORN, SoundSource.PLAYERS, 2f, 1f);
        int lost = unloadedMembers(level.getServer(), player.getUUID()).size();
        if (!current.isEmpty() || lost > 0 || forceDismiss) {
            if (current.isEmpty() && lost == 0) say(player, "party_empty");
            else if (!forceDismiss && summon(level.getServer(), player, current, lost)) { /* members were called in */ }
            else dismiss(level.getServer(), player, current);
        } else {
            call(level, player);
        }
    }

    public static int call(ServerLevel level, ServerPlayer player) {
        List<SettlerEntity> near = new ArrayList<>();
        for (SettlerEntity e : level.getEntitiesOfClass(SettlerEntity.class, new AABB(player.blockPosition()).inflate(48, 24, 48),
                x -> x.isAlive() && x.isCombatant() && !x.isLeaving() && x.hasArrived() && !x.inParty())) {
            Settlement st = e.settlement();
            if (st != null && (player.getUUID().equals(st.owner) || player.isCreative())) near.add(e);
        }
        near.sort(Comparator.comparingDouble(player::distanceToSqr));
        int n = 0;
        for (SettlerEntity e : near) {
            if (n >= MAX) break;
            e.joinParty(player.getUUID());
            ((ServerLevel) e.level()).sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + 2.2, e.getZ(), 2, 0.2, 0.1, 0.2, 1.0);
            e.say("party_join");
            n++;
        }
        if (n == 0) say(player, "party_none");
        else say(player, "party_called", n);
        return n;
    }

    /**
     * Horn with a party that has stragglers (far away, another dimension or in unloaded chunks): calls them in instead
     * of dismissing. Returns false when everybody is already close.
     */
    private static boolean summon(MinecraftServer server, ServerPlayer player, List<SettlerEntity> loaded, int unloaded) {
        int moved = 0;
        for (SettlerEntity e : loaded) {
            if (e.level() != player.level() || e.distanceToSqr(player) > 24 * 24) {
                teleportNear(e, player);
                moved++;
            }
        }
        int queued = requestUnloaded(server, player, Action.JOIN, true);
        if (moved == 0 && queued == 0 && unloaded == 0) return false;
        say(player, "party_summoned", moved + queued);
        return true;
    }

    public static void dismiss(MinecraftServer server, ServerPlayer player, List<SettlerEntity> members) {
        int n = requestUnloaded(server, player, Action.DISMISS, true);
        for (SettlerEntity e : members) {
            e.leaveParty();
            e.say("party_leave");
            sendHome(server, e);
            n++;
        }
        say(player, "party_dismissed", n);
        PartyRoster roster = PartyRoster.get(server);
        for (SettlerEntity e : members) roster.remove(e.getUUID());
    }

    /** Teleports a released member home when it is far from (or in another dimension than) its settlement. */
    private static void sendHome(MinecraftServer server, SettlerEntity e) {
        Settlement st = e.settlement();
        if (st == null) return;
        ServerLevel home = server.getLevel(st.dimension);
        if (home == null) return;
        boolean far = e.level() != home || e.blockPosition().distSqr(st.center) > 80 * 80;
        if (far) teleportTo(e, home, st.center.offset(2, 0, 2));
    }

    // ---- keeping up ----------------------------------------------------------------------------

    /** A standing spot (solid floor, two free blocks) within two blocks of {@code target}, nearest first. */
    public static @Nullable BlockPos standSpot(Level level, BlockPos target) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2))) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
            if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
            if (!level.getFluidState(p).isEmpty()) continue;
            BlockPos below = p.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
            double d = p.distSqr(target);
            if (d < bd) { bd = d; best = p.immutable(); }
        }
        return best;
    }

    /** Moves a settler next to {@code target} in {@code level}, across dimensions if needed. */
    public static void teleportTo(SettlerEntity e, ServerLevel level, BlockPos target) {
        level.getChunkAt(target);
        BlockPos spot = standSpot(level, target);
        Vec3 at = spot == null ? Vec3.atBottomCenterOf(target) : Vec3.atBottomCenterOf(spot);
        if (e.level() instanceof ServerLevel from) from.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
        e.getNavigation().stop();
        if (e.level() == level) e.teleportTo(at.x, at.y, at.z);
        else e.teleport(new TeleportTransition(level, at, Vec3.ZERO, e.getYRot(), 0f, TeleportTransition.DO_NOTHING));
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 6, 0.2, 0.3, 0.2, 0.02);
    }

    public static void teleportNear(SettlerEntity e, ServerPlayer owner) {
        BlockPos base = owner.blockPosition().offset(e.getRandom().nextInt(5) - 2, 0, e.getRandom().nextInt(5) - 2);
        teleportTo(e, owner.level(), base);
    }

    // ---- members in unloaded chunks (wave G) ------------------------------------------------------

    public enum Action { JOIN, DISMISS }

    private record Pending(UUID owner, ResourceKey<Level> dim, int cx, int cz, boolean wasForced, long deadline, Action action) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final int SCAN_EVERY = 100, ROSTER_EVERY = 40, PENDING_EVERY = 5, MAX_PENDING = 3, TIMEOUT = 300, RETRY_AFTER = 400;
    /** Statistics for the test and the logs. */
    public static int forcedLoads, rescued;

    /** Roster entries of this leader whose settler is not currently loaded anywhere. */
    public static List<PartyRoster.Entry> unloadedMembers(MinecraftServer server, UUID owner) {
        List<PartyRoster.Entry> out = new ArrayList<>();
        for (PartyRoster.Entry e : PartyRoster.get(server).all())
            if (e.owner.equals(owner) && !isLoaded(server, e.member)) out.add(e);
        return out;
    }

    private static boolean isLoaded(MinecraftServer server, UUID member) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(member) != null) return true;
        }
        return false;
    }

    /** Remembers where a member stands now (called when it joins, periodically and when its chunk unloads). */
    public static void remember(SettlerEntity e) {
        UUID owner = e.partyOwner();
        if (owner == null || !(e.level() instanceof ServerLevel l)) return;
        PartyRoster.get(l.getServer()).put(e.getUUID(), owner, l.dimension(), e.blockPosition());
    }

    /** Queues force-loading of the chunks of unloaded members; returns how many were queued (at most {@link #MAX_PENDING} in flight). */
    public static int requestUnloaded(MinecraftServer server, ServerPlayer leader, Action action, boolean ignoreRetry) {
        int queued = 0;
        for (PartyRoster.Entry e : unloadedMembers(server, leader.getUUID())) {
            if (PENDING.containsKey(e.member)) continue;
            if (PENDING.size() >= MAX_PENDING) break;
            if (!ignoreRetry && server.getTickCount() < e.retryAt) continue;
            ServerLevel l = server.getLevel(e.dim);
            if (l == null) continue;   // dimension gone: nothing to do
            int cx = e.pos.getX() >> 4, cz = e.pos.getZ() >> 4;
            boolean was = l.getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.pack(cx, cz));
            l.setChunkForced(cx, cz, true);
            forcedLoads++;
            e.retryAt = server.getTickCount() + RETRY_AFTER;
            PENDING.put(e.member, new Pending(leader.getUUID(), e.dim, cx, cz, was, server.getTickCount() + TIMEOUT, action));
            queued++;
        }
        return queued;
    }

    private static void finish(MinecraftServer server, UUID member, Pending p) {
        PENDING.remove(member);
        ServerLevel l = server.getLevel(p.dim);
        if (l != null && !p.wasForced) l.setChunkForced(p.cx, p.cz, false);
    }

    private static void processPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        PartyRoster roster = PartyRoster.get(server);
        for (Map.Entry<UUID, Pending> me : new ArrayList<>(PENDING.entrySet())) {
            UUID id = me.getKey();
            Pending p = me.getValue();
            ServerLevel l = server.getLevel(p.dim);
            if (l == null) { PENDING.remove(id); continue; }
            Entity found = l.getEntity(id);
            if (found instanceof SettlerEntity e && e.isAlive()) {
                ServerPlayer leader = server.getPlayerList().getPlayer(p.owner);
                if (p.action == Action.DISMISS) {
                    e.leaveParty();
                    sendHome(server, e);
                    roster.remove(id);
                    rescued++;
                } else if (leader != null && e.partyOwner() != null) {
                    teleportNear(e, leader);
                    rescued++;
                }
                finish(server, id, p);
            } else if (server.getTickCount() >= p.deadline) {
                PartyRoster.Entry re = roster.get(id);
                if (re != null && ++re.fails >= 3) roster.remove(id);   // never showed up: dead or removed
                finish(server, id, p);
            }
        }
    }

    /** Throttled: members that are loaded get their place saved; stragglers in unloaded chunks are brought in. */
    private static void keepRoster(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % PENDING_EVERY == 0) processPending(server);
        if (t % ROSTER_EVERY == 0) {
            PartyRoster roster = PartyRoster.get(server);
            for (UUID owner : OWNERS) {
                for (SettlerEntity e : members(server, owner)) remember(e);
            }
            // a loaded settler that left the party (or died) must not stay on the roster
            for (PartyRoster.Entry re : new ArrayList<>(roster.all())) {
                for (ServerLevel l : server.getAllLevels()) {
                    if (l.getEntity(re.member) instanceof SettlerEntity e && (!e.isAlive() || !re.owner.equals(e.partyOwner()))) roster.remove(re.member);
                }
            }
        }
        if (t % SCAN_EVERY == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!OWNERS.contains(p.getUUID())) continue;
                for (PartyRoster.Entry e : unloadedMembers(server, p.getUUID())) {
                    boolean far = !e.dim.equals(p.level().dimension()) || e.pos.distSqr(p.blockPosition()) > 64 * 64;
                    if (far) { requestUnloaded(server, p, Action.JOIN, false); break; }
                }
            }
        }
    }

    /** Every server tick: when a leader suddenly jumps (teleport, portal), pull the members that are still loaded. */
    public static void tick(MinecraftServer server) {
        if (OWNERS.isEmpty()) return;
        keepRoster(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!OWNERS.contains(p.getUUID())) continue;
            Sample prev = LAST.put(p.getUUID(), new Sample(p.level().dimension(), p.position()));
            if (prev == null) continue;
            boolean jumped = !prev.dim.equals(p.level().dimension()) || prev.pos.distanceToSqr(p.position()) > 20 * 20;
            if (!jumped) continue;
            for (SettlerEntity e : members(server, p.getUUID())) {
                if (e.level() != p.level() || e.distanceToSqr(p) > 20 * 20) teleportNear(e, p);
            }
            requestUnloaded(server, p, Action.JOIN, false);
        }
    }
}
