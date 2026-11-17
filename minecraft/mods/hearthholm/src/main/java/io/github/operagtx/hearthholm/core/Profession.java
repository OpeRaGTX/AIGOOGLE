package io.github.operagtx.hearthholm.core;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.settlement.SettlerProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A job a settler can hold. Everything a profession needs is declared here so that adding one
 * means: one builder call + one {@link Job} class (+ optionally a work-site type and a texture).
 */
public final class Profession {
    private final Identifier id;
    private final EnumMap<Stat, Integer> weights;
    private final Predicate<ItemStack> tool;
    private final @Nullable Identifier siteType;
    private final Function<SettlerEntity, Job> jobFactory;
    private final boolean combat;
    private final boolean nightShift;
    private final int color;
    private final Supplier<ItemStack> icon;

    private Profession(Builder b) {
        this.id = b.id;
        this.weights = b.weights;
        this.tool = b.tool;
        this.siteType = b.siteType;
        this.jobFactory = b.jobFactory;
        this.combat = b.combat;
        this.nightShift = b.nightShift;
        this.color = b.color;
        this.icon = b.icon;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() { return id; }
    public boolean isCombat() { return combat; }
    public boolean nightShift() { return nightShift; }
    public int color() { return color; }
    public @Nullable Identifier siteType() { return siteType; }
    public boolean isTool(ItemStack stack) { return !stack.isEmpty() && tool.test(stack); }
    public Job createJob(SettlerEntity settler) { return jobFactory.apply(settler); }
    public ItemStack icon() { return icon.get(); }

    public String translationKey() {
        return "profession." + id.getNamespace() + "." + id.getPath();
    }

    public Component label() {
        return Component.translatable(translationKey());
    }

    /** 1..10: weighted average of the stats this profession cares about. */
    public int aptitude(SettlerProfile profile) {
        if (weights.isEmpty()) return 5;
        int sum = 0, total = 0;
        for (Map.Entry<Stat, Integer> e : weights.entrySet()) {
            sum += profile.stat(e.getKey()) * e.getValue();
            total += e.getValue();
        }
        return Math.max(1, Math.min(10, Math.round(sum / (float) total)));
    }

    public static final class Builder {
        private final Identifier id;
        private final EnumMap<Stat, Integer> weights = new EnumMap<>(Stat.class);
        private Predicate<ItemStack> tool = s -> false;
        private @Nullable Identifier siteType;
        private Function<SettlerEntity, Job> jobFactory = s -> Job.NONE;
        private boolean combat;
        private boolean nightShift;
        private int color = 0xFFFFFF;
        private Supplier<ItemStack> icon = () -> new ItemStack(Items.BREAD);

        private Builder(Identifier id) { this.id = id; }

        public Builder weight(Stat stat, int weight) { weights.put(stat, weight); return this; }
        public Builder tool(Predicate<ItemStack> tool) { this.tool = tool; return this; }
        public Builder site(Identifier siteType) { this.siteType = siteType; return this; }
        public Builder job(Function<SettlerEntity, Job> factory) { this.jobFactory = factory; return this; }
        public Builder combat() { this.combat = true; return this; }
        public Builder nightShift() { this.nightShift = true; return this; }
        public Builder color(int rgb) { this.color = rgb; return this; }
        public Builder icon(Supplier<ItemStack> icon) { this.icon = icon; return this; }
        public Profession build() { return new Profession(this); }
    }
}
