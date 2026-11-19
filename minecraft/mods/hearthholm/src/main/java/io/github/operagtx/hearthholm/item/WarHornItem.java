package io.github.operagtx.hearthholm.item;

import io.github.operagtx.hearthholm.party.Party;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * War horn: use it to call up to four of your combat settlers (guards, warriors, archers, knights) into a party that
 * follows you; use it again, or sneak and use it, to send them home.
 */
public class WarHornItem extends Item {
    public WarHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) return InteractionResult.FAIL;
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
        Party.toggle(sl, sp, player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }
}
