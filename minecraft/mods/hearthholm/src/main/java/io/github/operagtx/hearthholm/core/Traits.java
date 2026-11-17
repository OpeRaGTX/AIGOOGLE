package io.github.operagtx.hearthholm.core;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.api.KeyedRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class Traits {
    public static final KeyedRegistry<Trait> REGISTRY = new KeyedRegistry<>("trait");

    private Traits() {}

    private static void add(String path, String group, float work, float hunger, int mood, float combat,
                            float move, int luck, boolean fearless, float hp) {
        REGISTRY.register(Hearthholm.id(path),
                new Trait(Hearthholm.id(path), group, work, hunger, mood, combat, move, luck, fearless, hp));
    }

    public static void bootstrap() {
        //   id            group      work  hunger mood combat move  luck fearless hp
        add("hardworking", "effort", 1.15f, 1.1f, 0, 1f, 1f, 0, false, 0);
        add("lazy", "effort", 0.85f, 1f, 6, 1f, 1f, 0, false, 0);
        add("glutton", "appetite", 1f, 1.5f, 4, 1f, 1f, 0, false, 0);
        add("frugal", "appetite", 1f, 0.7f, 0, 1f, 1f, 0, false, 0);
        add("cheerful", "temper", 1f, 1f, 12, 1f, 1f, 0, false, 0);
        add("grumpy", "temper", 1.05f, 1f, -10, 1f, 1f, 0, false, 0);
        add("brave", "nerve", 1f, 1f, 0, 1.25f, 1f, 0, true, 0);
        add("coward", "nerve", 1f, 1f, 0, 0.75f, 1.1f, 0, false, 0);
        add("nimble", "body", 1.05f, 1f, 0, 1f, 1.15f, 0, false, 0);
        add("sturdy", "body", 1f, 1.1f, 0, 1.1f, 0.95f, 0, false, 6);
        add("lucky", "", 1f, 1f, 3, 1f, 1f, 2, false, 0);
        add("night_owl", "", 1f, 1f, 0, 1f, 1f, 0, false, 0);
    }

    /** Rolls 1-2 non-conflicting traits. */
    public static List<Identifier> roll(RandomSource random) {
        List<Trait> pool = new ArrayList<>(REGISTRY.values());
        List<Identifier> picked = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        int count = random.nextFloat() < 0.45f ? 2 : 1;
        for (int i = 0; i < 20 && picked.size() < count && !pool.isEmpty(); i++) {
            Trait t = pool.get(random.nextInt(pool.size()));
            if (picked.contains(t.id()) || (!t.conflictGroup().isEmpty() && groups.contains(t.conflictGroup()))) continue;
            picked.add(t.id());
            groups.add(t.conflictGroup());
        }
        return picked;
    }
}
