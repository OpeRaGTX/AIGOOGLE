package io.github.operagtx.hearthholm.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A timed mood entry ("Saw the festival +12"): translation key suffix, mood value, game time it fades. Saved on the settler. */
public record Thought(String id, int value, long expiry) {
    public static final Codec<Thought> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Thought::id),
            Codec.INT.fieldOf("value").forGetter(Thought::value),
            Codec.LONG.optionalFieldOf("expiry", 0L).forGetter(Thought::expiry)
    ).apply(i, Thought::new));
}
