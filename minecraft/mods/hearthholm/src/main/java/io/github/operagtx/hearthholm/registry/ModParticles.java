package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Wave L3: the mod's own particle types (the vanilla ones were used before). The server may send the simple ones with {@code sendParticles}
 * (the client side lives in {@code client/particle/}, sprites in {@code assets/hearthholm/particles/*.json} and {@code textures/particle/}):
 * <ul>
 *   <li>{@link #HEARTH_SPARK}: a warm golden spark that rises and fades: level-ups, promotions, festivals, finished buildings;</li>
 *   <li>{@link #ASH_EMBER}: a drifting glowing ember with grey ash: raiders, fire and the rift;</li>
 *   <li>{@link #BUILD_DUST}: a pale puff that swells and fades: the builder at work;</li>
 *   <li>{@link #GHOST_BLOCK}: a translucent picture of a block, client-only (the ghost blueprint preview of the construction stake).</li>
 * </ul>
 */
public final class ModParticles {
    public static SimpleParticleType HEARTH_SPARK, ASH_EMBER, BUILD_DUST;
    public static ParticleType<BlockParticleOption> GHOST_BLOCK;

    private ModParticles() {}

    public static void init() {
        HEARTH_SPARK = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Hearthholm.id("hearth_spark"), FabricParticleTypes.simple());
        ASH_EMBER = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Hearthholm.id("ash_ember"), FabricParticleTypes.simple());
        BUILD_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Hearthholm.id("build_dust"), FabricParticleTypes.simple());
        GHOST_BLOCK = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Hearthholm.id("ghost_block"),
                FabricParticleTypes.complex(BlockParticleOption::codec, BlockParticleOption::streamCodec));
    }
}
