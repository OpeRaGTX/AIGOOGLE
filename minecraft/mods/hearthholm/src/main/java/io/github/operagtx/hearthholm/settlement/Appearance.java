package io.github.operagtx.hearthholm.settlement;

import net.minecraft.util.RandomSource;

/**
 * Look of a settler packed into one int (synced to clients, which compose the skin texture).
 * Layout (bits): skin 0-3 | hair style 4-7 | hair color 8-11 | eyes 12-14 | beard 15-16 |
 * shirt 17-20 | pants 21-23 | female 24.
 */
public record Appearance(int skin, int hairStyle, int hairColor, int eyes, int beard, int shirt, int pants, boolean female) {
    public static final int SKIN_TONES = 6;
    public static final int MALE_HAIR = 5;    // 0 = bald
    public static final int FEMALE_HAIR = 5;
    public static final int HAIR_COLORS = 8;
    public static final int EYE_COLORS = 5;
    public static final int BEARDS = 4;       // 0 = none
    public static final int SHIRTS = 10;
    public static final int PANTS = 6;

    public int pack() {
        return (skin & 15) | (hairStyle & 15) << 4 | (hairColor & 15) << 8 | (eyes & 7) << 12 | (beard & 3) << 15
                | (shirt & 15) << 17 | (pants & 7) << 21 | (female ? 1 : 0) << 24;
    }

    public static Appearance unpack(int v) {
        return new Appearance(v & 15, v >> 4 & 15, v >> 8 & 15, v >> 12 & 7, v >> 15 & 3, v >> 17 & 15, v >> 21 & 7, (v >> 24 & 1) == 1);
    }

    public static Appearance random(RandomSource r, boolean female) {
        int hairColor = r.nextInt(HAIR_COLORS);
        return new Appearance(
                r.nextInt(SKIN_TONES),
                female ? r.nextInt(FEMALE_HAIR) : (r.nextFloat() < 0.12f ? 0 : 1 + r.nextInt(MALE_HAIR - 1)),
                hairColor,
                r.nextInt(EYE_COLORS),
                female ? 0 : (r.nextFloat() < 0.45f ? 1 + r.nextInt(BEARDS - 1) : 0),
                r.nextInt(SHIRTS),
                r.nextInt(PANTS),
                female);
    }
}
