package io.github.operagtx.hearthholm.worksite;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A kind of work zone marker. The marker block stores int options; the matching profession's
 * job reads them. {@code preview} returns the world box drawn as a particle outline.
 */
public record WorkSiteType(Identifier id, Identifier profession, List<SiteOption> options, int color,
                           Function<WorkSiteBlockEntity, @Nullable AABB> preview) {

    public @Nullable SiteOption option(String key) {
        for (SiteOption o : options) if (o.key().equals(key)) return o;
        return null;
    }

    public Map<String, Integer> defaults() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (SiteOption o : options) map.put(o.key(), o.def());
        return map;
    }

    public Component label() {
        return Component.translatable("worksite." + id.getNamespace() + "." + id.getPath());
    }

    public Component hint() {
        return Component.translatable("worksite." + id.getNamespace() + "." + id.getPath() + ".hint");
    }
}
