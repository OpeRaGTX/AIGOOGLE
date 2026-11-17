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
        if (!current.isEmpty() || forceDismiss) {
            if (current.isEmpty()) say(player, "party_empty");
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

    public static void dismiss(MinecraftServer server, ServerPlayer player, List<SettlerEntity> members) {
        int n = 0;
        for (SettlerEntity e : members) {
            e.leaveParty();
            e.say("party_leave");
            sendHome(server, e);
            n++;
        }
        say(player, "party_dismissed", n);
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

    /** Every server tick: when a leader suddenly jumps (teleport, portal), pull the members that are still loaded. */
    public static void tick(MinecraftServer server) {
        if (OWNERS.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!OWNERS.contains(p.getUUID())) continue;
            Sample prev = LAST.put(p.getUUID(), new Sample(p.level().dimension(), p.position()));
            if (prev == null) continue;
            boolean jumped = !prev.dim.equals(p.level().dimension()) || prev.pos.distanceToSqr(p.position()) > 20 * 20;
            if (!jumped) continue;
            ServerLevel from = server.getLevel(prev.dim);
            if (from == null) continue;
            for (SettlerEntity e : from.getEntities(EntityTypeTest.forClass(SettlerEntity.class), x -> x.isAlive() && p.getUUID().equals(x.partyOwner())))
                teleportNear(e, p);
        }
    }
}
