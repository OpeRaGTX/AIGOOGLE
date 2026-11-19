package io.github.operagtx.hearthholm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

/** Stone cap for the top of a 1x1 chimney: a hollow opening under a small slab hood. Lets campfire-style smoke rise on the client; no fire, no light. */
public class ChimneyCapBlock extends DecorBlock {
    public ChimneyCapBlock(Properties properties) {
        super(properties, Shapes.or(net.minecraft.world.level.block.Block.box(3, 0, 3, 13, 5, 13), net.minecraft.world.level.block.Block.box(1, 5, 1, 15, 13, 15)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        spawnSmoke(level, pos, random);
    }

    /** Throttled like a vanilla campfire (1 in 5 ticks), plus a thin wisp of plain smoke now and then. */
    public static void spawnSmoke(Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, pos.getX() + 0.5 + random.nextDouble() / 4 * (random.nextBoolean() ? 1 : -1),
                    pos.getY() + 0.6 + random.nextDouble() * 0.2, pos.getZ() + 0.5 + random.nextDouble() / 4 * (random.nextBoolean() ? 1 : -1), 0, 0.07, 0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.75, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
        }
    }
}
