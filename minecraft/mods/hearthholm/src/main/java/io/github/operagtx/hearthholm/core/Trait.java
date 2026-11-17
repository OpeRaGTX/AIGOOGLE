package io.github.operagtx.hearthholm.core;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A personality trait. All modifiers are multiplicative factors (1 = no change) or flat bonuses.
 *
 * @param conflictGroup traits sharing a non-empty group never roll together (e.g. lazy/hardworking)
 */
public record Trait(Identifier id, String conflictGroup, float workSpeed, float hungerRate, int mood,
                    float combat, float moveSpeed, int luck, boolean fearless, float maxHealthBonus) {

    public Component label() {
        return Component.translatable("trait." + id.getNamespace() + "." + id.getPath());
    }

    public Component description() {
        return Component.translatable("trait." + id.getNamespace() + "." + id.getPath() + ".desc");
    }
}
