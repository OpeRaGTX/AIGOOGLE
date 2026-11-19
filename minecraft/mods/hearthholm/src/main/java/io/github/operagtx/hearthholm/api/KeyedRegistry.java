package io.github.operagtx.hearthholm.api;

import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Small ordered registry for Hearthholm content (professions, traits, work sites).
 * Entries are referenced by {@link Identifier} in saves, so adding new entries never breaks
 * old worlds, and an unknown id simply falls back to a default.
 */
public final class KeyedRegistry<T> {
    private final String kind;
    private final LinkedHashMap<Identifier, T> entries = new LinkedHashMap<>();

    public KeyedRegistry(String kind) {
        this.kind = kind;
    }

    public synchronized T register(Identifier id, T value) {
        if (entries.putIfAbsent(id, value) != null) {
            throw new IllegalStateException("Duplicate " + kind + " id: " + id);
        }
        return value;
    }

    public Optional<T> get(Identifier id) {
        return Optional.ofNullable(entries.get(id));
    }

    public T getOr(Identifier id, T fallback) {
        return entries.getOrDefault(id, fallback);
    }

    public Collection<T> values() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Set<Identifier> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public int size() {
        return entries.size();
    }
}
