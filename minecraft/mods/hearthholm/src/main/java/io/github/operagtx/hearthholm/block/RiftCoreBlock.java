package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.threat.Threats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Heart of a rift: glows, hums and sheds particles; breaking it closes the rift. */
public class RiftCoreBlock extends Block {
    public RiftCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 0.5 + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
        }
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.05, 0.05, (random.nextDouble() - 0.5) * 0.05);
        if (random.nextInt(25) == 0) level.playLocalSound(pos, ModSounds.RIFT_HUM, SoundSource.HOSTILE, 1.2f, 0.9f + random.nextFloat() * 0.2f, false);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) Threats.onCoreBroken(server, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
}
