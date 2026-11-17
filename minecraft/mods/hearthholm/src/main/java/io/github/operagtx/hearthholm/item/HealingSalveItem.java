package io.github.operagtx.hearthholm.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A herbal salve. Players can use it too (heals 8 health); settlers use it when no healer is near. */
public class HealingSalveItem extends Item {
    public static final float HEAL = 8f;

    public HealingSalveItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.getHealth() >= player.getMaxHealth()) return InteractionResult.FAIL;
        if (level instanceof ServerLevel sl) {
            player.heal(HEAL);
            sl.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
            sl.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
