package io.github.operagtx.hearthholm.client.particle;

import io.github.operagtx.hearthholm.registry.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wave L3: the client side of {@link ModParticles}: one small class per particle and one registration method. All four are camera-facing quads;
 * sparks, embers and dust take their frames from a sprite set by age, the ghost block shows a block's own sprite at 30% opacity.
 */
public final class HearthParticles {
    private HearthParticles() {}

    public static void register() {
        ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
        r.register(ModParticles.HEARTH_SPARK, sprites -> (ParticleProvider<SimpleParticleType>) (type, level, x, y, z, dx, dy, dz, rnd) ->
                new Rising(level, x, y, z, dx, dy, dz, sprites, 18, 0.045f, 0.14f, 1f, 0.82f, 0.4f, true));
        r.register(ModParticles.ASH_EMBER, sprites -> (ParticleProvider<SimpleParticleType>) (type, level, x, y, z, dx, dy, dz, rnd) ->
                new Rising(level, x, y, z, dx, dy, dz, sprites, 26, 0.02f, 0.10f, 1f, 0.55f, 0.25f, true));
        r.register(ModParticles.BUILD_DUST, sprites -> (ParticleProvider<SimpleParticleType>) (type, level, x, y, z, dx, dy, dz, rnd) ->
                new Rising(level, x, y, z, dx, dy, dz, sprites, 16, -0.002f, 0.16f, 0.92f, 0.86f, 0.74f, false));
        r.register(ModParticles.GHOST_BLOCK, new GhostBlockParticle.Provider());
    }

    /**
     * Rises (or settles for negative lift) and fades out; the sprite follows the age. {@code glow}: full brightness regardless of the light level.
     * One class serves the spark, the ember and the dust: they differ by lifetime, lift, size, tint and glow only.
     */
    private static final class Rising extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final boolean glow;
        private final float baseSize;

        Rising(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, int life, float lift, float size,
               float r, float g, float b, boolean glow) {
            super(level, x, y, z, sprites.first());
            this.sprites = sprites;
            this.glow = glow;
            this.lifetime = life + random.nextInt(Math.max(1, life / 3));
            this.xd = dx * 0.4 + (random.nextDouble() - 0.5) * 0.02;
            this.yd = dy * 0.4 + lift * 1.6;
            this.zd = dz * 0.4 + (random.nextDouble() - 0.5) * 0.02;
            this.friction = 0.94f;
            this.gravity = 0f;
            this.hasPhysics = false;
            this.baseSize = size * (0.8f + random.nextFloat() * 0.5f);
            this.quadSize = baseSize;
            this.rCol = r; this.gCol = g; this.bCol = b;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float t = age / (float) lifetime;
            this.alpha = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
            this.alpha *= glow ? 0.95f : 0.6f;
            this.quadSize = glow ? baseSize * (1f - 0.35f * t) : baseSize * (1f + 0.9f * t);   // sparks shrink, dust swells
        }

        @Override public float getQuadSize(float partial) { return quadSize; }
        @Override protected int getLightCoords(float partial) { return glow ? 15728880 : super.getLightCoords(partial); }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }

    /** A block's own picture at 30% opacity, still, for {@code lifetime} ticks: the ghost blueprint preview. */
    public static final class GhostBlockParticle extends SingleQuadParticle {
        public static final float ALPHA = 0.30f;

        GhostBlockParticle(ClientLevel level, double x, double y, double z, BlockState state) {
            super(level, x, y, z, Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite());
            this.lifetime = 14;
            this.gravity = 0f;
            this.hasPhysics = false;
            this.xd = 0; this.yd = 0; this.zd = 0;
            this.quadSize = 0.5f;
            this.alpha = ALPHA;
        }

        @Override public float getQuadSize(float partial) { return 0.5f; }
        @Override protected int getLightCoords(float partial) { return 15728880; }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT_TERRAIN; }

        public static final class Provider implements ParticleProvider<BlockParticleOption> {
            @Override
            public @Nullable Particle createParticle(BlockParticleOption o, ClientLevel level, double x, double y, double z, double dx, double dy, double dz, RandomSource rnd) {
                return new GhostBlockParticle(level, x, y, z, o.getState());
            }
        }
    }
}
