package io.github.operagtx.hearthholm.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Speech-bubble line pools. The texts live in the lang files as {@code bubble.hearthholm.<situation>.<n>};
 * how many variants each situation has comes from data/hearthholm/bubbles.json (written by tools/gen_lang.py).
 */
public final class BubbleLines {
    private static Map<String, Integer> counts;

    private BubbleLines() {}

    private static synchronized Map<String, Integer> counts() {
        if (counts == null) {
            counts = new HashMap<>();
            try (var in = BubbleLines.class.getResourceAsStream("/data/hearthholm/bubbles.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    o.entrySet().forEach(e -> counts.put(e.getKey(), e.getValue().getAsInt()));
                }
            } catch (Exception ex) {
                Hearthholm.LOG.warn("bubbles.json unreadable", ex);
            }
        }
        return counts;
    }

    public static int count(String situation) {
        return counts().getOrDefault(situation, 0);
    }

    public static int total() {
        return counts().values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Line {@code n} of a situation, or null when the pool is empty. Args: settlement name, owner name. */
    public static @Nullable Component line(String situation, int n, Object... args) {
        return count(situation) <= 0 ? null : Component.translatable("bubble.hearthholm." + situation + "." + n, args);
    }

    public static int pickIndex(String situation, RandomSource random, int avoid) {
        int n = count(situation);
        if (n <= 0) return -1;
        int i = random.nextInt(n);
        if (n > 1 && i == avoid) i = (i + 1 + random.nextInt(n - 1)) % n;
        return i;
    }
}
