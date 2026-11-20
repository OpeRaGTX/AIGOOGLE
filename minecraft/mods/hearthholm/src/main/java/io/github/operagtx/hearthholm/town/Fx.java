package io.github.operagtx.hearthholm.town;

import io.github.operagtx.hearthholm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Wave F: work and event feedback (particles + sounds) in one place. Counters let the client test verify that effects fire. */
public final class Fx {
    public static int bursts, sounds;
    /** Effects fired per kind since the server started (for the client test). */
    public static final java.util.Map<String, Integer> COUNTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void hit(String kind) {
        bursts++;
        COUNTS.merge(kind, 1, Integer::sum);
    }

    private Fx() {}

    // ---- wave L3: the mod's own particles (registry/ModParticles); call sites say WHAT happened, the look is decided here
    /** Golden sparks rising from a point: celebrations, promotions, finished buildings. */
    public static void sparks(ServerLevel l, double x, double y, double z, int n, double spread) {
        hit("sparks");
        l.sendParticles(io.github.operagtx.hearthholm.registry.ModParticles.HEARTH_SPARK, x, y, z, n, spread, spread * 0.8, spread, 0.03);
    }

    /** Glowing embers and ash: raiders, fire, the rift. */
    public static void embers(ServerLevel l, double x, double y, double z, int n, double spread) {
        hit("embers");
        l.sendParticles(io.github.operagtx.hearthholm.registry.ModParticles.ASH_EMBER, x, y, z, n, spread, spread * 1.2, spread, 0.02);
    }

    /** A pale puff: a block placed, a wall raised. */
    public static void buildDust(ServerLevel l, BlockPos pos) {
        hit("buildDust");
        l.sendParticles(io.github.operagtx.hearthholm.registry.ModParticles.BUILD_DUST, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.3, 0.25, 0.3, 0.01);
    }

    private static void play(ServerLevel l, BlockPos p, SoundEvent s, SoundSource src, float vol, float pitch) {
        sounds++;
        l.playSound(null, p, s, src, vol, pitch);
    }

    /** A swing of the axe: wood chips and a thunk. */
    public static void chop(ServerLevel l, BlockPos pos, BlockState state) {
        hit("chop");
        play(l, pos, ModSounds.CHOP, SoundSource.BLOCKS, 0.8f, 0.9f + l.getRandom().nextFloat() * 0.2f);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
    }

    /** A pick strike: rock dust and a clink (soft blocks keep their own sound). */
    public static void mine(ServerLevel l, BlockPos pos, BlockState state, boolean hard) {
        hit("mine");
        if (hard) play(l, pos, ModSounds.PICK, SoundSource.NEUTRAL, 0.6f, 0.9f + l.getRandom().nextFloat() * 0.2f);
        else play(l, pos, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.5f, 0.8f);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.03);
    }

    /** Forge: a shower of sparks. */
    public static void forge(ServerLevel l, Vec3 top) {
        hit("forge");
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y + 0.1, top.z, 6, 0.2, 0.1, 0.2, 0.3);
        l.sendParticles(ParticleTypes.LAVA, top.x, top.y + 0.1, top.z, 1, 0.1, 0.05, 0.1, 0);
    }

    /** Cooking: a curl of cosy smoke. */
    public static void cook(ServerLevel l, Vec3 top) {
        hit("cook");
        l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.x, top.y + 0.2, top.z, 1, 0.1, 0.05, 0.1, 0.01);
    }

    /** A settler gained a level, or the settlement a tier. */
    public static void levelUp(ServerLevel l, BlockPos pos, boolean big) {
        hit("levelUp");
        play(l, pos, ModSounds.LEVEL_UP, SoundSource.NEUTRAL, big ? 1.4f : 0.7f, 1f);
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, big ? 30 : 12, 0.5, 0.5, 0.5, 0.1);
        sparks(l, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, big ? 24 : 8, 0.5);
    }

    /** Notes and confetti over the festival square. */
    public static void festival(ServerLevel l, BlockPos sq, int notes) {
        hit("festival");
        l.sendParticles(ParticleTypes.NOTE, sq.getX() + 0.5, sq.getY() + 2, sq.getZ() + 0.5, notes, 3, 1, 3, 1);
        sparks(l, sq.getX() + 0.5, sq.getY() + 3, sq.getZ() + 0.5, notes * 2, 3);
    }

    /** The caravan comes into view: a horn call and a cloud of dust. */
    public static void caravan(ServerLevel l, BlockPos spot) {
        hit("caravan");
        play(l, spot, ModSounds.CARAVAN_HORN, SoundSource.NEUTRAL, 3f, 1f);
        l.sendParticles(ParticleTypes.CLOUD, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 20, 1.5, 0.3, 1.5, 0.03);
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 1.5, spot.getZ() + 0.5, 10, 1, 0.6, 1, 0.1);
    }

    /** A quest is finished or paid out. */
    public static void quest(ServerLevel l, BlockPos pos) {
        hit("quest");
        play(l, pos, ModSounds.QUEST_DONE, SoundSource.NEUTRAL, 0.9f, 1f);
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 12, 0.5, 0.5, 0.5, 0.1);
        l.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.5, 0.5, 0.5, 0.5);
    }
}
