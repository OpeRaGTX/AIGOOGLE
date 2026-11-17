package io.github.operagtx.hearthholm.threat;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.RiftCoreBlock;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ashborn.AshChieftainEntity;
import io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity;
import io.github.operagtx.hearthholm.entity.ashborn.RiftWispEntity;
import io.github.operagtx.hearthholm.registry.ModBlocks;
import io.github.operagtx.hearthholm.registry.ModEntities;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.settlement.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Wave C threats: Ashborn raids and rift nights. Everything runs from the once-a-second settlement tick;
 * the static {@code force*} methods are test/debug hooks (also used by the Founder's whistle).
 *
 * <p>Raid: planned for a day (every {@link ThreatConfig#raidMinDays}..{@link ThreatConfig#raidMaxDays} days,
 * first one after the grace period once the settlement is a hamlet), starts at dusk with a 60 s warning
 * (horn, chat message with the direction, a boss-bar countdown, the alarm rings), then 2-3 waves spawn at the
 * settlement edge and march on the town hall. All dead = victory; after {@link #RAID_TIMEOUT} the survivors
 * retreat. Strength grows with renown, warehouse contents and population.
 */
public final class Threats {
    public static final int WARNING_TICKS = 1200;
    public static final int RAID_TIMEOUT = 6000;
    public static final int RIFT_LIFETIME = 36000;
    public static final int MAX_FIRES = 12;
    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();
    private static final List<FireMark> FIRES = new ArrayList<>();

    private record FireMark(ResourceKey<Level> dim, BlockPos pos, long sweepAt, int sweeps) {}

    private Threats() {}

    // ---- helpers ---------------------------------------------------------------------------------

    public static int tierIndex(Settlement s) {
        return SettlementTier.TIERS.indexOf(s.tier());
    }

    public static Component direction(int dir) {
        return Component.translatable("direction.hearthholm." + Math.floorMod(dir, 8));
    }

    private static Vec3 dirVector(int dir) {
        double a = dir * Math.PI / 4;
        return new Vec3(Math.sin(a), 0, -Math.cos(a));
    }

    private static int dirOf(BlockPos center, BlockPos p) {
        double a = Math.atan2(p.getX() - center.getX(), -(p.getZ() - center.getZ()));
        return Math.floorMod((int) Math.round(a / (Math.PI / 4)), 8);
    }

    private static void dirty(ServerLevel level) {
        SettlementService.manager(level).setDirty();
    }

    /** Raid strength (= number of raiders) from renown, warehouse contents and population, capped by tier (Balance): 3..24. */
    public static int strength(ServerLevel level, Settlement s) {
        int stacks = 0;
        for (Container c : Storage.resolve(level, s.storagePositions()))
            for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) stacks++;
        int v = io.github.operagtx.hearthholm.core.Balance.RAID_BASE + s.renown / io.github.operagtx.hearthholm.core.Balance.RAID_RENOWN_DIV + stacks / io.github.operagtx.hearthholm.core.Balance.RAID_STACKS_DIV + s.residents.size() / io.github.operagtx.hearthholm.core.Balance.RAID_POP_DIV;
        int cap = io.github.operagtx.hearthholm.core.Balance.RAID_CAP_BY_TIER[Math.max(0, Math.min(io.github.operagtx.hearthholm.core.Balance.RAID_CAP_BY_TIER.length - 1, tierIndex(s)))];
        return Math.max(io.github.operagtx.hearthholm.core.Balance.RAID_BASE, Math.min(cap, v));
    }

    /** A standing spot on solid ground near {@code around}, or null. */
    private static @Nullable BlockPos ground(ServerLevel level, int x, int z) {
        BlockPos probe = new BlockPos(x, level.getMinY() + 1, z);
        if (!level.isLoaded(probe)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        BlockPos below = p.below();
        if (!level.getFluidState(below).isEmpty() || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return null;
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) return null;
        return p;
    }

    /** Spawn point at the settlement edge in direction {@code dir}; moves inwards while chunks are unloaded. */
    public static BlockPos edgePoint(ServerLevel level, Settlement s, int dir, RandomSource r) {
        Vec3 v = dirVector(dir);
        Vec3 side = new Vec3(-v.z, 0, v.x);
        for (int dist = s.radius() + 4; dist >= 12; dist -= 6) {
            for (int k = 0; k < 6; k++) {
                double lat = r.nextInt(11) - 5;
                int x = (int) Math.floor(s.center.getX() + v.x * dist + side.x * lat);
                int z = (int) Math.floor(s.center.getZ() + v.z * dist + side.z * lat);
                BlockPos p = ground(level, x, z);
                if (p != null) return p;
            }
        }
        return s.center.offset(4, 0, 4);
    }

    private static List<SettlerEntity> settlers(ServerLevel level, Settlement s) {
        return level.getEntitiesOfClass(SettlerEntity.class, new AABB(s.center).inflate(s.radius() + 24, 64, s.radius() + 24),
                e -> s.id.equals(e.settlementId()));
    }

    private static void thought(ServerLevel level, Settlement s, String id, int value) {
        for (SettlerEntity e : settlers(level, s)) e.addThought(id, value, 48000);
    }

    private static ServerBossEvent bar(String key, Component name, BossEvent.BossBarColor color) {
        return BARS.computeIfAbsent(key, k -> new ServerBossEvent(UUID.randomUUID(), name, color, BossEvent.BossBarOverlay.PROGRESS));
    }

    private static void showBar(ServerLevel level, ServerBossEvent bar, Vec3 at, double range) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() == level && p.position().distanceToSqr(at) < range * range) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
    }

    private static void dropBar(String key) {
        ServerBossEvent b = BARS.remove(key);
        if (b != null) b.removeAllPlayers();
    }

    /** Gives items to the warehouse, the rest is dropped at the town hall desk. */
    private static void reward(ServerLevel level, Settlement s, ItemStack stack) {
        ItemStack rest = Storage.insert(Storage.resolve(level, s.storagePositions()), stack);
        if (!rest.isEmpty()) level.addFreshEntity(new ItemEntity(level, s.center.getX() + 0.5, s.center.getY() + 1, s.center.getZ() + 0.5, rest));
    }

    // ---- per-second tick ---------------------------------------------------------------------------

    private static boolean firesRestored;

    public static void serverTick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!firesRestored) {
            firesRestored = true;
            for (Settlement st : SettlementManager.get(server).all())
                for (BlockPos p : st.civic.fires) FIRES.add(new FireMark(st.dimension, p, now, 2));
        }
        if (FIRES.isEmpty()) return;
        List<FireMark> again = new ArrayList<>();
        FIRES.removeIf(m -> {
            if (m.sweepAt() > now) return false;
            ServerLevel level = server.getLevel(m.dim());
            if (level != null) extinguish(level, m.pos(), 5);
            if (m.sweeps() > 1) again.add(new FireMark(m.dim(), m.pos(), now + 200, m.sweeps() - 1));
            else if (level != null) forget(level, m.pos());
            return true;
        });
        FIRES.addAll(again);
    }

    public static void tick(ServerLevel level, Settlement s) {
        long now = level.getGameTime();
        long day = SettlementService.day(level);
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        RaidState r = s.raid;
        switch (r.phase) {
            case RaidState.WARNING -> tickWarning(level, s);
            case RaidState.ACTIVE -> tickRaid(level, s);
            default -> {
                if (ThreatConfig.raids && tierIndex(s) >= 1 && !s.residents.isEmpty()) {
                    if (r.nextDay < 0) {
                        r.nextDay = day + ThreatConfig.graceDays + level.getRandom().nextInt(2);
                        dirty(level);
                    } else if (day >= r.nextDay && tod >= 12000 && tod < 13000 && !s.rift.open()) {
                        startWarning(level, s, strength(level, s), WARNING_TICKS);
                    }
                }
            }
        }
        if (s.rift.open()) tickRift(level, s);
        else if (ThreatConfig.rifts && tierIndex(s) >= 1 && !s.residents.isEmpty()) {
            if (s.rift.nextDay < 0) {
                s.rift.nextDay = day + ThreatConfig.riftPeriod;
                dirty(level);
            } else if (day >= s.rift.nextDay && tod >= 14000 && tod < 15000 && r.phase == RaidState.NONE) {
                openRift(level, s);
            }
        }
        if (s.alarm && (now / 20) % 6 == 0) Defense.ringAll(level, s, 0.6f);
        if (s.alarm && r.phase == RaidState.NONE && !s.rift.open() && s.stats.getOrDefault("auto_alarm", 0) == 1) {
            s.stats.remove("auto_alarm");
            Defense.setAlarm(level, s, false, null);
        }
    }

    // ---- raids ---------------------------------------------------------------------------------------

    /** Test hook: starts the raid warning now (warningTicks 0 = raiders arrive at once). */
    public static void forceRaid(ServerLevel level, Settlement s, int strength, int warningTicks) {
        if (s.raid.phase != RaidState.NONE) return;
        startWarning(level, s, strength, Math.max(1, warningTicks));
        if (warningTicks <= 0) startRaid(level, s);
    }

    public static void startWarning(ServerLevel level, Settlement s, int strength, int ticks) {
        RaidState r = s.raid;
        long now = level.getGameTime();
        r.phase = RaidState.WARNING;
        r.phaseEnd = now + ticks;
        r.dir = level.getRandom().nextInt(8);
        r.strength = strength;
        r.chieftain = tierIndex(s) >= 3;
        r.raiders.clear();
        r.spawned = r.deaths = r.killed = 0;
        horn(level, s);
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.raid_warning", direction(r.dir), ticks / 20)
                .withStyle(ChatFormatting.RED));
        if (!s.alarm) {
            Defense.setAlarm(level, s, true, null);
            s.stats.put("auto_alarm", 1);
        }
        dirty(level);
        Hearthholm.LOG.info("Raid warning for {}: strength {} from {}", s.id, strength, r.dir);
    }

    private static void horn(ServerLevel level, Settlement s) {
        Vec3 v = dirVector(s.raid.dir);
        BlockPos far = s.center.offset((int) (v.x * s.radius()), 0, (int) (v.z * s.radius()));
        level.playSound(null, far, ModSounds.RAID_HORN, SoundSource.HOSTILE, 12f, 1f);
        level.playSound(null, s.center, ModSounds.RAID_HORN, SoundSource.HOSTILE, 4f, 1f);
    }

    private static void tickWarning(ServerLevel level, Settlement s) {
        RaidState r = s.raid;
        long left = r.phaseEnd - level.getGameTime();
        ServerBossEvent bar = bar(s.id + "/raid", Component.empty(), BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable("bossbar.hearthholm.raid_warning", direction(r.dir), Math.max(0, left / 20)));
        bar.setProgress(Math.max(0f, Math.min(1f, left / (float) WARNING_TICKS)));
        showBar(level, bar, Vec3.atCenterOf(s.center), s.radius() + 48);
        if (left > WARNING_TICKS / 2 - 20 && left <= WARNING_TICKS / 2) horn(level, s);
        if (left <= 0) startRaid(level, s);
    }

    public static void startRaid(ServerLevel level, Settlement s) {
        RaidState r = s.raid;
        long now = level.getGameTime();
        r.phase = RaidState.ACTIVE;
        r.phaseEnd = now + RAID_TIMEOUT;
        r.wavesLeft = r.strength > 10 ? 3 : 2;
        spawnWave(level, s);
        horn(level, s);
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.raid_start", direction(r.dir)).withStyle(ChatFormatting.DARK_RED));
        dirty(level);
    }

    private static void spawnWave(ServerLevel level, Settlement s) {
        RaidState r = s.raid;
        int waves = r.strength > 10 ? 3 : 2;
        int size = (int) Math.ceil(r.strength / (double) waves);
        int remaining = r.strength - r.spawned;
        int count = Math.min(size, remaining);
        if (r.wavesLeft == 1) count = remaining;
        RandomSource rnd = level.getRandom();
        BlockPos hall = s.center;
        int base = r.spawned;
        for (int i = 0; i < count; i++) {
            int n = base + i;
            EntityType<? extends AshbornEntity> type = n % 6 == 5 ? ModEntities.ASH_BRUTE
                    : n % 3 == 1 ? ModEntities.ASH_ARCHER
                    : (n % 4 == 2 && ThreatConfig.raidFire) ? ModEntities.ASH_ARSONIST : ModEntities.ASH_RAIDER;
            spawnRaider(level, s, type, edgePoint(level, s, r.dir, rnd), hall);
        }
        if (r.chieftain && base == 0) spawnRaider(level, s, ModEntities.ASH_CHIEFTAIN, edgePoint(level, s, r.dir, rnd), hall);
        r.wavesLeft--;
        r.nextWave = level.getGameTime() + 700;
        Hearthholm.LOG.info("Raid wave: {} raiders (spawned {}/{}), waves left {}", count, r.spawned, r.strength, r.wavesLeft);
    }

    private static @Nullable AshbornEntity spawnRaider(ServerLevel level, Settlement s, EntityType<? extends AshbornEntity> type, BlockPos at, BlockPos hall) {
        AshbornEntity e = type.spawn(level, at, EntitySpawnReason.EVENT);
        if (e == null) return null;
        e.joinRaid(s.id, hall);
        s.raid.raiders.add(e.getUUID());
        if (!(e instanceof AshChieftainEntity)) s.raid.spawned++;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        return e;
    }

    /** A raid member summoned by the chieftain joins the raid's bookkeeping. */
    public static void adopt(ServerLevel level, AshbornEntity e) {
        Settlement s = SettlementService.manager(level).byId(e.raidSettlement());
        if (s != null && s.raid.phase == RaidState.ACTIVE && !s.raid.raiders.contains(e.getUUID())) s.raid.raiders.add(e.getUUID());
    }

    /** Living raid members (loaded ones). */
    public static int alive(ServerLevel level, Settlement s) {
        int n = 0;
        for (UUID id : s.raid.raiders) if (level.getEntity(id) instanceof AshbornEntity a && a.isAlive()) n++;
        return n;
    }

    private static void tickRaid(ServerLevel level, Settlement s) {
        RaidState r = s.raid;
        long now = level.getGameTime();
        r.raiders.removeIf(id -> !(level.getEntity(id) instanceof AshbornEntity a) || !a.isAlive());
        int alive = r.raiders.size();
        if (r.wavesLeft > 0 && (now >= r.nextWave || alive <= Math.max(1, r.spawned / 4))) spawnWave(level, s);
        alive = r.raiders.size();
        ServerBossEvent bar = bar(s.id + "/raid", Component.empty(), BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable("bossbar.hearthholm.raid", alive));
        bar.setProgress(Math.max(0f, Math.min(1f, (alive + r.wavesLeft * 3) / (float) Math.max(1, r.strength))));
        showBar(level, bar, Vec3.atCenterOf(s.center), s.radius() + 48);
        if (r.wavesLeft <= 0 && alive == 0) endRaid(level, s, true);
        else if (now >= r.phaseEnd) {
            for (UUID id : List.copyOf(r.raiders)) {
                if (level.getEntity(id) instanceof AshbornEntity a) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, a.getX(), a.getY() + 1, a.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
                    a.discard();
                }
            }
            endRaid(level, s, false);
        }
    }

    public static void onRaiderDied(ServerLevel level, AshbornEntity e) {
        Settlement s = SettlementService.manager(level).byId(e.raidSettlement());
        if (s == null || s.raid.phase != RaidState.ACTIVE) return;
        s.raid.killed++;
    }

    public static void onSettlerDied(ServerLevel level, Settlement s) {
        if (s.raid.phase == RaidState.ACTIVE) s.raid.deaths++;
    }

    /** Ends the running raid (also a test hook): rewards and thoughts, the next raid is planned. */
    public static void endRaid(ServerLevel level, Settlement s, boolean won) {
        RaidState r = s.raid;
        long day = SettlementService.day(level);
        r.phase = RaidState.NONE;
        r.lastResult = won ? RaidState.RESULT_WON : RaidState.RESULT_LOST;
        r.lastDay = day;
        r.nextDay = day + ThreatConfig.raidMinDays + level.getRandom().nextInt(ThreatConfig.raidMaxDays - ThreatConfig.raidMinDays + 1);
        r.raiders.clear();
        dropBar(s.id + "/raid");
        if (won) {
            r.won++;
            int renown = io.github.operagtx.hearthholm.core.Balance.RAID_WIN_RENOWN_BASE + io.github.operagtx.hearthholm.core.Balance.RAID_WIN_RENOWN_PER_STRENGTH * r.strength;
            int marks = io.github.operagtx.hearthholm.core.Balance.RAID_WIN_MARKS_BASE + r.strength;
            s.addRenown(renown);
            reward(level, s, new ItemStack(ModItems.HEARTH_MARK, marks));
            thought(level, s, "held_line", io.github.operagtx.hearthholm.core.Balance.MOOD_HELD_LINE);
            SettlementService.notify(level, s, Component.translatable("message.hearthholm.raid_won", r.killed, renown, marks).withStyle(ChatFormatting.GOLD));
        } else {
            r.lost++;
            thought(level, s, "raid_losses", io.github.operagtx.hearthholm.core.Balance.MOOD_RAID_LOSSES);
            SettlementService.notify(level, s, Component.translatable("message.hearthholm.raid_retreat").withStyle(ChatFormatting.RED));
        }
        if (r.deaths > 0) thought(level, s, "raid_losses", io.github.operagtx.hearthholm.core.Balance.MOOD_RAID_LOSSES);
        s.count("raids", 1);
        if (s.alarm && s.stats.getOrDefault("auto_alarm", 0) == 1) {
            s.stats.remove("auto_alarm");
            Defense.setAlarm(level, s, false, null);
        }
        dirty(level);
        Hearthholm.LOG.info("Raid on {} ended: won={} killed={} deaths={}", s.id, won, r.killed, r.deaths);
    }

    /** 0 = no raids yet (too small), 1 = soon (within 2 days), 2 = far, 3 = warning or raid running. */
    public static int raidEta(ServerLevel level, Settlement s) {
        if (s.raid.phase != RaidState.NONE) return 3;
        if (!ThreatConfig.raids || s.raid.nextDay < 0) return 0;
        return s.raid.nextDay - SettlementService.day(level) <= 2 ? 1 : 2;
    }

    // ---- fire ------------------------------------------------------------------------------------------

    /** Lights a raid fire (respecting {@code raidFire} and the global cap); it is put out after ~8 s. */
    public static boolean ignite(ServerLevel level, BlockPos pos) {
        if (!ThreatConfig.raidFire || FIRES.size() >= MAX_FIRES || !level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
        FIRES.add(new FireMark(level.dimension(), pos.immutable(), level.getGameTime() + 100, 2));
        Settlement st = SettlementService.manager(level).at(level.dimension(), pos);
        if (st != null) {
            st.civic.fires.add(pos.immutable());
            SettlementService.manager(level).setDirty();
        }
        return true;
    }

    /** The fire marker is gone for good: drop it from the saved list. */
    private static void forget(ServerLevel level, BlockPos pos) {
        Settlement st = SettlementService.manager(level).at(level.dimension(), pos);
        if (st != null && st.civic.fires.remove(pos)) SettlementService.manager(level).setDirty();
    }

    private static void extinguish(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) level.removeBlock(p, false);
        }
    }

    /** Test hook: forget the in-memory fire list (as after a server restart); the saved list restores it. */
    public static void resetFiresForTest() {
        FIRES.clear();
        firesRestored = false;
    }

    public static int activeFires() {
        return FIRES.size();
    }

    // ---- rifts -----------------------------------------------------------------------------------------

    /** Test hook: opens a rift now near the settlement border. Returns the core position. */
    public static @Nullable BlockPos forceRift(ServerLevel level, Settlement s) {
        if (s.rift.open()) return s.rift.core;
        openRift(level, s);
        return s.rift.core;
    }

    public static void openRift(ServerLevel level, Settlement s) {
        RandomSource rnd = level.getRandom();
        BlockPos core = null;
        int dir = rnd.nextInt(8);
        for (int k = 0; k < 8 && core == null; k++) {
            int d = (dir + k) % 8;
            Vec3 v = dirVector(d);
            for (int dist = Math.max(12, s.radius() - 8); dist >= 10 && core == null; dist -= 6) {
                core = ground(level, (int) Math.floor(s.center.getX() + v.x * dist), (int) Math.floor(s.center.getZ() + v.z * dist));
            }
        }
        if (core == null) return;
        level.setBlock(core, ModBlocks.RIFT_CORE.defaultBlockState(), 3);
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2)
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (level.getBlockState(p).canBeReplaced()) level.setBlock(p, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
                }
        RiftState rift = s.rift;
        rift.core = core.immutable();
        rift.hp = RiftState.MAX_HP;
        rift.opened = level.getGameTime();
        rift.nextWave = level.getGameTime() + 40;
        rift.mobs.clear();
        level.playSound(null, core, ModSounds.RIFT_HUM, SoundSource.HOSTILE, 6f, 0.6f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, 80, 1, 1.5, 1, 0.2);
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.rift_open", direction(dirOf(s.center, core)))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        dirty(level);
        Hearthholm.LOG.info("Rift opened for {} at {}", s.id, core);
    }

    private static void tickRift(ServerLevel level, Settlement s) {
        RiftState rift = s.rift;
        BlockPos core = rift.core;
        long now = level.getGameTime();
        if (level.isLoaded(core) && !(level.getBlockState(core).getBlock() instanceof RiftCoreBlock)) {
            closeRift(level, s, null, true, false);
            return;
        }
        if (now - rift.opened > RIFT_LIFETIME) {
            closeRift(level, s, null, false, true);
            return;
        }
        // combat settlers next to the core hack at it
        for (SettlerEntity e : level.getEntitiesOfClass(SettlerEntity.class, new AABB(core).inflate(3.2), x -> x.isCombatant() && x.getTarget() == null)) {
            rift.hp -= 3;
            e.swingArm(InteractionHand.MAIN_HAND);
            e.getLookControl().setLookAt(Vec3.atCenterOf(core));
            level.sendParticles(ParticleTypes.CRIT, core.getX() + 0.5, core.getY() + 0.6, core.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
        }
        if (rift.hp <= 0) {
            closeRift(level, s, null, true, true);
            return;
        }
        rift.mobs.removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
        if (now >= rift.nextWave) {
            rift.nextWave = now + 400;
            if (rift.mobs.size() < 8) riftWave(level, s);
        }
        ServerBossEvent bar = bar(s.id + "/rift", Component.translatable("bossbar.hearthholm.rift"), BossEvent.BossBarColor.PURPLE);
        bar.setProgress(Math.max(0f, rift.hp / (float) RiftState.MAX_HP));
        showBar(level, bar, Vec3.atCenterOf(core), 48);
        dirty(level);
    }

    private static void riftWave(ServerLevel level, Settlement s) {
        RiftState rift = s.rift;
        RandomSource rnd = level.getRandom();
        List<EntityType<? extends Mob>> pool = List.<EntityType<? extends Mob>>of(net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.SKELETON, net.minecraft.world.entity.EntityTypes.SPIDER, net.minecraft.world.entity.EntityTypes.ZOMBIE);
        int monsters = 2 + rnd.nextInt(2), wisps = 1 + rnd.nextInt(2);
        for (int i = 0; i < monsters + wisps; i++) {
            BlockPos at = rift.core.offset(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3);
            BlockPos g = ground(level, at.getX(), at.getZ());
            if (g == null) g = rift.core.above(2);
            Mob m = i < monsters ? pool.get(rnd.nextInt(pool.size())).spawn(level, g, EntitySpawnReason.EVENT)
                    : ModEntities.RIFT_WISP.spawn(level, rift.core.above(2), EntitySpawnReason.EVENT);
            if (m == null) continue;
            if (m instanceof RiftWispEntity w) w.setHome(rift.core);
            rift.mobs.add(m.getUUID());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1, m.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
        }
        s.count("rift_spawned", monsters + wisps);
    }

    public static void onCoreBroken(ServerLevel level, BlockPos pos, @Nullable Entity by) {
        for (Settlement s : SettlementManager.get(level.getServer()).all()) {
            if (pos.equals(s.rift.core) && s.dimension.equals(level.dimension())) {
                closeRift(level, s, by, true, false);
                return;
            }
        }
    }

    /** Closes the settlement's rift (test hook too). {@code reward}: shards and renown; {@code removeCore}: clear the block. */
    public static void closeRift(ServerLevel level, Settlement s, @Nullable Entity by, boolean reward, boolean removeCore) {
        RiftState rift = s.rift;
        BlockPos core = rift.core;
        if (core == null) return;
        if (removeCore && level.getBlockState(core).getBlock() instanceof RiftCoreBlock) level.removeBlock(core, false);
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2)
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(Blocks.CRYING_OBSIDIAN)) level.removeBlock(p, false);
                }
        level.sendParticles(ParticleTypes.EXPLOSION, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.3);
        level.playSound(null, core, ModSounds.RIFT_HUM, SoundSource.HOSTILE, 3f, 1.6f);
        if (reward) {
            int shards = 3 + level.getRandom().nextInt(3);
            level.addFreshEntity(new ItemEntity(level, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, new ItemStack(ModItems.RIFT_SHARD, shards)));
            s.addRenown(30);
            rift.closed++;
            SettlementService.notify(level, s, Component.translatable("message.hearthholm.rift_closed", shards).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            SettlementService.notify(level, s, Component.translatable("message.hearthholm.rift_faded").withStyle(ChatFormatting.GRAY));
        }
        rift.core = null;
        rift.hp = RiftState.MAX_HP;
        rift.mobs.clear();
        rift.nextDay = SettlementService.day(level) + ThreatConfig.riftPeriod;
        dropBar(s.id + "/rift");
        dirty(level);
        Hearthholm.LOG.info("Rift of {} closed (reward={}, by={})", s.id, reward, by == null ? "settlers" : by.getName().getString());
    }

    // ---- boss --------------------------------------------------------------------------------------------

    /** Test hook: spawns the Ash Chieftain at {@code pos} (not tied to a raid). */
    public static @Nullable AshChieftainEntity spawnChieftain(ServerLevel level, BlockPos pos) {
        return ModEntities.ASH_CHIEFTAIN.spawn(level, pos, EntitySpawnReason.EVENT);
    }
}
