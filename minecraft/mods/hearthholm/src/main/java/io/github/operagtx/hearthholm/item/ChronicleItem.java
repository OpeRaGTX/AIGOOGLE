package io.github.operagtx.hearthholm.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Wave F: the "Founder's Chronicle", the in-game guide. Opens a book screen (client side, via {@link #OPENER}). */
public class ChronicleItem extends Item {
    /** Set by the client entrypoint to open the book screen. */
    public static Runnable OPENER;

    public ChronicleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() && OPENER != null) OPENER.run();
        return InteractionResult.SUCCESS;
    }
}
