package io.github.operagtx.hearthholm.core;

import net.minecraft.network.chat.Component;

/** Innate settler attributes, rolled 1..10 on arrival. */
public enum Stat {
    STRENGTH, DEXTERITY, ENDURANCE, WITS, PERCEPTION;

    public Component label() {
        return Component.translatable("stat.hearthholm." + name().toLowerCase());
    }
}
