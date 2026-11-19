package io.github.operagtx.hearthholm.settlement;

import io.github.operagtx.hearthholm.core.Balance;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Growth stages. Each needs renown and (from hamlet on) a valid town hall and warehouse. */
public record SettlementTier(String key, int renown, int popCap, int radius, boolean needsBuildings) {
    public static final List<SettlementTier> TIERS = List.of(
            tier("camp", 0, false), tier("hamlet", 1, true), tier("village", 2, true), tier("town", 3, true), tier("city", 4, true));

    private static SettlementTier tier(String key, int i, boolean needsBuildings) {
        return new SettlementTier(key, Balance.TIER_RENOWN[i], Balance.TIER_POP[i], Balance.TIER_RADIUS[i], needsBuildings);
    }

    public int index() {
        return TIERS.indexOf(this);
    }

    public Component label() {
        return Component.translatable("tier.hearthholm." + key);
    }
}
