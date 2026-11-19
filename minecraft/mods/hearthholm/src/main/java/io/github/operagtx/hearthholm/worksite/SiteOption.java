package io.github.operagtx.hearthholm.worksite;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * One configurable value of a work site. Values are ints (choice index / number / 0-1 toggle),
 * which keeps saving and networking generic: the config screen is generated from these.
 */
public record SiteOption(String key, Kind kind, int min, int max, int step, int def,
                         List<String> choiceKeys, @Nullable Supplier<List<String>> dynamicChoices) {

    public enum Kind { CHOICE, RANGE, TOGGLE }

    public static SiteOption range(String key, int min, int max, int step, int def) {
        return new SiteOption(key, Kind.RANGE, min, max, step, def, List.of(), null);
    }

    public static SiteOption toggle(String key, boolean def) {
        return new SiteOption(key, Kind.TOGGLE, 0, 1, 1, def ? 1 : 0, List.of(), null);
    }

    /** Choice whose labels are translation keys {@code <prefix>.<value>}. */
    public static SiteOption choice(String key, int def, String... values) {
        return new SiteOption(key, Kind.CHOICE, 0, values.length - 1, 1, def, List.of(values), null);
    }

    /** Choice with values computed at runtime (e.g. the blueprint list); labels are translation keys. */
    public static SiteOption dynamic(String key, Supplier<List<String>> labels) {
        return new SiteOption(key, Kind.CHOICE, 0, Integer.MAX_VALUE, 1, 0, List.of(), labels);
    }

    public List<String> choices() {
        return dynamicChoices != null ? dynamicChoices.get() : choiceKeys;
    }

    public int clamp(int value) {
        int hi = kind == Kind.CHOICE ? Math.max(0, choices().size() - 1) : max;
        return Math.max(min, Math.min(hi, value));
    }

    /** Cycles choices, steps ranges, flips toggles. */
    public int next(int value, int dir) {
        return switch (kind) {
            case TOGGLE -> value == 0 ? 1 : 0;
            case RANGE -> clamp(value + dir * step);
            case CHOICE -> {
                int n = Math.max(1, choices().size());
                yield Math.floorMod(value + dir, n);
            }
        };
    }

    public Component label() {
        return Component.translatable("option.hearthholm." + key);
    }

    public Component valueLabel(int value) {
        return switch (kind) {
            case TOGGLE -> Component.translatable(value != 0 ? "options.on" : "options.off");
            case RANGE -> Component.literal(Integer.toString(value));
            case CHOICE -> {
                List<String> c = choices();
                yield c.isEmpty() ? Component.literal("-")
                        : io.github.operagtx.hearthholm.util.Labels.orHumanized(dynamicChoices != null ? c.get(clamp(value)) : "option.hearthholm." + key + "." + c.get(clamp(value)));
            }
        };
    }
}
