package io.github.operagtx.hearthholm.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.operagtx.hearthholm.core.Stat;
import io.github.operagtx.hearthholm.core.Trait;
import io.github.operagtx.hearthholm.core.Traits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** Who a settler is: name, look, innate stats and traits. Immutable; rolled once. */
public record SettlerProfile(int nameIndex, boolean female, int appearance, List<Integer> stats, List<Identifier> traits,
                             boolean legendary, int title) {
    public static final float LEGENDARY_CHANCE = 0.08f;
    public static final int TITLES = 8;

    public static final int NAMES_PER_GENDER = 48;

    public SettlerProfile(int nameIndex, boolean female, int appearance, List<Integer> stats, List<Identifier> traits) {
        this(nameIndex, female, appearance, stats, traits, false, -1);
    }

    public static final Codec<SettlerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("name").forGetter(SettlerProfile::nameIndex),
            Codec.BOOL.fieldOf("female").forGetter(SettlerProfile::female),
            Codec.INT.fieldOf("look").forGetter(SettlerProfile::appearance),
            Codec.INT.listOf().fieldOf("stats").forGetter(SettlerProfile::stats),
            Identifier.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(SettlerProfile::traits),
            Codec.BOOL.optionalFieldOf("legendary", false).forGetter(SettlerProfile::legendary),
            Codec.INT.optionalFieldOf("title", -1).forGetter(SettlerProfile::title)
    ).apply(i, SettlerProfile::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SettlerProfile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SettlerProfile::nameIndex,
            ByteBufCodecs.BOOL, SettlerProfile::female,
            ByteBufCodecs.INT, SettlerProfile::appearance,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SettlerProfile::stats,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), SettlerProfile::traits,
            ByteBufCodecs.BOOL, SettlerProfile::legendary,
            ByteBufCodecs.VAR_INT, SettlerProfile::title,
            SettlerProfile::new);

    public static final SettlerProfile DEFAULT = new SettlerProfile(0, false, 0, List.of(5, 5, 5, 5, 5), List.of());

    public static SettlerProfile random(RandomSource r) {
        return random(r, r.nextFloat() < LEGENDARY_CHANCE);
    }

    /** Rolls a settler; a legendary one has 2 traits, +2 in two abilities and a unique title. */
    public static SettlerProfile random(RandomSource r, boolean legendary) {
        boolean female = r.nextBoolean();
        List<Integer> stats = new ArrayList<>();
        for (Stat ignored : Stat.values()) stats.add(Math.max(1, Math.min(10, 1 + r.nextInt(5) + r.nextInt(6))));
        // Everyone is good at something: lift the best stat a little.
        int best = 0;
        for (int i = 1; i < stats.size(); i++) if (stats.get(i) > stats.get(best)) best = i;
        stats.set(best, Math.min(10, stats.get(best) + 1 + r.nextInt(2)));
        if (!legendary) {
            return new SettlerProfile(r.nextInt(NAMES_PER_GENDER), female, Appearance.random(r, female).pack(), stats, Traits.roll(r), false, -1);
        }
        int a = r.nextInt(stats.size()), b = (a + 1 + r.nextInt(stats.size() - 1)) % stats.size();
        stats.set(a, Math.min(10, stats.get(a) + 2));
        stats.set(b, Math.min(10, stats.get(b) + 2));
        return new SettlerProfile(r.nextInt(NAMES_PER_GENDER), female, Appearance.random(r, female).pack(), stats,
                Traits.roll(r, 2), true, r.nextInt(TITLES));
    }

    public Component name() {
        return Component.translatable("settler.hearthholm.name." + (female ? "f" : "m") + "." + nameIndex);
    }

    /** Name with the legendary star and title ("★ Ivar the Stonebreaker"), gold; plain name otherwise. */
    public Component fullName() {
        if (!legendary) return name();
        return Component.literal("★ ").append(name()).append(" ").append(titleText()).withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    /** Name for lists: star prefix and gold for legends. */
    public Component listName() {
        return legendary ? Component.literal("★ ").append(name()).withStyle(net.minecraft.ChatFormatting.GOLD) : name();
    }

    public Component titleText() {
        return legendary && title >= 0 ? Component.translatable("settler.hearthholm.title." + title) : Component.empty();
    }

    public Appearance look() {
        return Appearance.unpack(appearance);
    }

    public int stat(Stat stat) {
        return stat.ordinal() < stats.size() ? stats.get(stat.ordinal()) : 5;
    }

    public List<Trait> traitList() {
        List<Trait> out = new ArrayList<>();
        for (Identifier id : traits) Traits.REGISTRY.get(id).ifPresent(out::add);
        return out;
    }

    public boolean hasTrait(Identifier id) {
        return traits.contains(id);
    }
}
