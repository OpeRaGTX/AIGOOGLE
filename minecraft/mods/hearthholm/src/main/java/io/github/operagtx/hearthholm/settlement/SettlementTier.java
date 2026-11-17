package io.github.operagtx.hearthholm.settlement;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Growth stages. Each needs renown and (from hamlet on) a valid town hall and warehouse. */
public record SettlementTier(String key, int renown, int popCap, int radius, boolean needsBuildings) {
    public static final List<SettlementTier> TIERS = List.of(
            new SettlementTier("camp", 0, 2, 40, false),
            new SettlementTier("hamlet", 60, 5, 56, true),
            new SettlementTier("village", 300, 10, 72, true),
            new SettlementTier("town", 1000, 16, 88, true),
            new SettlementTier("city", 3000, 24, 104, true));

    public int index() {
        return TIERS.indexOf(this);
    }

    public Component label() {
        return Component.translatable("tier.hearthholm." + key);
    }
}
