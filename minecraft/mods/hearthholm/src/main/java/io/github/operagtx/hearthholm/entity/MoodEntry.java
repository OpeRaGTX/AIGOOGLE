package io.github.operagtx.hearthholm.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One line of the mood breakdown shown in the settler screen ("Own bed +10"). */
public record MoodEntry(Component label, int value) {
    public static final StreamCodec<RegistryFriendlyByteBuf, MoodEntry> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, MoodEntry::label,
            ByteBufCodecs.VAR_INT, MoodEntry::value,
            MoodEntry::new);

    public static MoodEntry of(String key, int value) {
        return new MoodEntry(Component.translatable("mood.hearthholm." + key), value);
    }
}
