package io.github.operagtx.hearthholm.item;

import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.threat.RaidState;
import io.github.operagtx.hearthholm.threat.Threats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Founder's whistle (creative only, a debug tool): sneak + use cycles the mode, use triggers it for the
 * settlement you stand in: raid (10 s warning), rift, Ash Chieftain in front of you, or end all threats.
 */
public class FoundersWhistleItem extends Item {
    private static final String[] MODES = {"raid", "rift", "chieftain", "calm"};
    private static final Map<UUID, Integer> MODE = new HashMap<>();

    public FoundersWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!player.isCreative()) {
            player.sendOverlayMessage(Component.translatable("message.hearthholm.whistle_creative").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        int mode = MODE.getOrDefault(player.getUUID(), 0);
        if (player.isShiftKeyDown()) {
            mode = (mode + 1) % MODES.length;
            MODE.put(player.getUUID(), mode);
            player.sendOverlayMessage(Component.translatable("message.hearthholm.whistle_mode", Component.translatable("whistle.hearthholm." + MODES[mode])));
            return InteractionResult.SUCCESS;
        }
        Settlement s = SettlementService.manager(sl).near(sl.dimension(), player.blockPosition(), 32);
        if (s == null && mode != 2) {
            player.sendOverlayMessage(Component.translatable("message.hearthholm.outside").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        switch (mode) {
            case 0 -> Threats.forceRaid(sl, s, Threats.strength(sl, s), 200);
            case 1 -> Threats.forceRift(sl, s);
            case 2 -> Threats.spawnChieftain(sl, player.blockPosition().relative(player.getDirection(), 4));
            default -> {
                if (s.raid.phase != RaidState.NONE) Threats.endRaid(sl, s, false);
                if (s.rift.open()) Threats.closeRift(sl, s, player, false, true);
            }
        }
        player.sendOverlayMessage(Component.translatable("whistle.hearthholm." + MODES[mode]).withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
