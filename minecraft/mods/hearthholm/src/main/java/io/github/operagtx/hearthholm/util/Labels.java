package io.github.operagtx.hearthholm.util;

import net.minecraft.network.chat.Component;

/**
 * Wave M1: one place for "translate this key, but never show the raw key to the player".
 * A translatable component carries its own fallback text, so it works on the server and the client without a language lookup.
 */
public final class Labels {
    private Labels() {}

    /** Translatable label that falls back to a humanized id ("blueprint.hearthholm.fisher_hut" -> "Fisher hut") when the key has no translation. */
    public static Component orHumanized(String key) {
        return Component.translatableWithFallback(key, humanize(key));
    }

    /** Last dotted segment of a key, underscores to spaces, first letter upper-cased. Pure function, covered by the M1 test. */
    public static String humanize(String key) {
        String tail = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ').trim();
        if (tail.isEmpty()) return key;
        return Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }
}
