package io.github.operagtx.hearthholm.settlement;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Look of a settler packed into one int (synced to clients, which compose the skin texture).
 * Layout (bits): skin 0-3 | hair style 4-7 | hair color 8-11 | eyes 12-14 | beard 15-16 |
 * shirt 17-20 | pants 21-23 | female 24 | outfit 25-27 | headwear 28-30 | elder 31.
 * The wave H2 fields (outfit, headwear, elder) are 0 in older saves, which keeps their old look.
 */
public record Appearance(int skin, int hairStyle, int hairColor, int eyes, int beard, int shirt, int pants, boolean female,
                         int outfit, int headwear, boolean elder) {
    public static final int SKIN_TONES = 6;
    public static final int MALE_HAIR = 8;    // 0 = bald; 5-7 are wave H2 styles
    public static final int FEMALE_HAIR = 10; // 5-9 are wave H2 styles
    public static final int HAIR_COLORS = 8;
    public static final int EYE_COLORS = 5;
    public static final int BEARDS = 4;       // 0 = none
    public static final int SHIRTS = 10;
    public static final int PANTS = 6;
    /** Outfits: 0 plain; women 1 dress, 2 skirt + blouse, 3 pinafore; men 4 vest, 5 belted tunic, 6 long robe. */
    public static final int OUTFITS = 7;
    /** Headwear: 0 none, 1 headscarf, 2 flat cap, 3 woollen cap, 4 hooded shawl. */
    public static final int HEADWEAR = 5;

    public Appearance(int skin, int hairStyle, int hairColor, int eyes, int beard, int shirt, int pants, boolean female) {
        this(skin, hairStyle, hairColor, eyes, beard, shirt, pants, female, 0, 0, false);
    }

    public int pack() {
        return (skin & 15) | (hairStyle & 15) << 4 | (hairColor & 15) << 8 | (eyes & 7) << 12 | (beard & 3) << 15
                | (shirt & 15) << 17 | (pants & 7) << 21 | (female ? 1 : 0) << 24 | (outfit & 7) << 25 | (headwear & 7) << 28 | (elder ? 1 : 0) << 31;
    }

    public static Appearance unpack(int v) {
        return new Appearance(v & 15, v >> 4 & 15, v >> 8 & 15, v >> 12 & 7, v >> 15 & 3, v >> 17 & 15, v >> 21 & 7, (v >> 24 & 1) == 1,
                v >> 25 & 7, v >> 28 & 7, (v >>> 31) == 1);
    }

    private static Appearance p(boolean f, int skin, int hair, int color, int beard, int shirt, int pants, int outfit, int hat, boolean elder) {
        return new Appearance(skin, hair, color, 0, beard, shirt, pants, f, outfit, hat, elder);
    }

    /**
     * Wave H2: 20 hand-designed looks (10 women, 10 men; two elders of each kind). New settlers mostly roll one of
     * these (eye colour still random); the old free combination remains for the rest.
     * Hair colours: 0 black, 1 dark brown, 2 brown, 3 auburn, 4 ginger, 5 blond, 6 pale, 7 grey.
     */
    public static final List<Appearance> PRESETS = List.of(
            // women: skin, hair style, hair colour, beard, shirt, pants, outfit, headwear, elder
            p(true, 1, 0, 1, 0, 2, 0, 1, 0, false),   // long straight hair, blue dress
            p(true, 0, 1, 4, 0, 3, 0, 3, 0, false),   // ginger braid, green pinafore
            p(true, 4, 3, 0, 0, 5, 1, 2, 0, false),   // dark bun, mustard skirt
            p(true, 5, 6, 0, 0, 6, 2, 1, 0, false),   // two braids, teal dress
            p(true, 2, 9, 2, 0, 1, 3, 3, 0, false),   // ponytail, red pinafore
            p(true, 1, 3, 7, 0, 8, 1, 1, 1, true),    // grey-haired elder, scarf and dress
            p(true, 0, 5, 5, 0, 7, 0, 2, 0, false),   // blond long hair, purple skirt
            p(true, 3, 8, 1, 0, 4, 4, 1, 1, false),   // short bob, brown dress, scarf
            p(true, 2, 7, 3, 0, 2, 0, 3, 0, false),   // low bun and fringe, blue pinafore
            p(true, 2, 4, 6, 0, 9, 5, 2, 4, true),    // white-haired elder, hooded shawl
            // men
            p(false, 2, 1, 1, 2, 0, 0, 4, 0, false),  // brown hair, full beard, vest
            p(false, 1, 3, 4, 1, 5, 1, 5, 0, false),  // ginger, stubble, tunic
            p(false, 1, 6, 7, 2, 8, 4, 6, 0, true),   // receding grey elder with beard, robe
            p(false, 4, 2, 0, 3, 1, 3, 4, 0, false),  // dark, moustache, red vest
            p(false, 0, 5, 5, 0, 2, 1, 5, 0, false),  // long blond hair, blue tunic
            p(false, 3, 7, 0, 0, 6, 0, 4, 2, false),  // curly hair, flat cap
            p(false, 5, 4, 0, 2, 3, 2, 5, 0, false),  // short dark hair, beard, green tunic
            p(false, 2, 0, 7, 2, 4, 4, 6, 3, true),   // bald grey elder, woollen cap, robe
            p(false, 1, 1, 2, 0, 9, 3, 4, 3, false),  // woollen cap, grey vest
            p(false, 0, 3, 3, 1, 7, 0, 5, 2, false)   // auburn, stubble, flat cap, purple tunic
    );

    public static int presetCount() { return PRESETS.size(); }

    public static int femalePresets() { return (int) PRESETS.stream().filter(Appearance::female).count(); }

    private static List<Appearance> of(boolean female) {
        List<Appearance> out = new ArrayList<>();
        for (Appearance a : PRESETS) if (a.female == female) out.add(a);
        return out;
    }

    public static Appearance random(RandomSource r, boolean female) {
        if (r.nextFloat() < 0.8f) {
            List<Appearance> list = of(female);
            Appearance a = list.get(r.nextInt(list.size()));
            return new Appearance(a.skin, a.hairStyle, a.hairColor, r.nextInt(EYE_COLORS), a.beard, a.shirt, a.pants, a.female, a.outfit, a.headwear, a.elder);
        }
        int hairColor = r.nextInt(HAIR_COLORS);
        return new Appearance(
                r.nextInt(SKIN_TONES),
                female ? r.nextInt(5) : (r.nextFloat() < 0.12f ? 0 : 1 + r.nextInt(4)),
                hairColor,
                r.nextInt(EYE_COLORS),
                female ? 0 : (r.nextFloat() < 0.45f ? 1 + r.nextInt(BEARDS - 1) : 0),
                r.nextInt(SHIRTS),
                r.nextInt(PANTS),
                female);
    }
}
