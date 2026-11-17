package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Procedurally generated sounds (tools/gen_sounds.py). */
public final class ModSounds {
    public static SoundEvent SAW, CHISEL, HAMMER, SIZZLE, BELL, MURMUR, FESTIVAL;
    public static SoundEvent RAID_HORN, ALARM_BELL, RIFT_HUM, BOSS_ROAR;

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
        BELL = reg("social.bell");
        MURMUR = reg("social.murmur");
        FESTIVAL = reg("social.festival");
        RAID_HORN = reg("threat.raid_horn");
        ALARM_BELL = reg("threat.alarm_bell");
        RIFT_HUM = reg("threat.rift_hum");
        BOSS_ROAR = reg("threat.boss_roar");
    }
}
