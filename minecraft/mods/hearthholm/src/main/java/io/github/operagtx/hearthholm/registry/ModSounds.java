package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Procedurally generated workshop sounds (tools/gen_sounds.py). */
public final class ModSounds {
    public static SoundEvent SAW, CHISEL, HAMMER, SIZZLE;

    private ModSounds() {}

    private static SoundEvent reg(String name) {
        Identifier id = Hearthholm.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        SAW = reg("workshop.saw");
        CHISEL = reg("workshop.chisel");
        HAMMER = reg("workshop.hammer");
        SIZZLE = reg("workshop.sizzle");
    }
}
