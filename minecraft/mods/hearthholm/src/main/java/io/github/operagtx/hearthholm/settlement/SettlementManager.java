package io.github.operagtx.hearthholm.settlement;

import com.mojang.serialization.Codec;
import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** All settlements of the server, saved in the overworld data folder. */
public final class SettlementManager extends SavedData {
    private static final Codec<SettlementManager> CODEC = Settlement.CODEC.listOf()
            .xmap(SettlementManager::new, m -> List.copyOf(m.byId.values()));
    private static final SavedDataType<SettlementManager> TYPE = new SavedDataType<>(
            Hearthholm.id("settlements"), SettlementManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final LinkedHashMap<UUID, Settlement> byId = new LinkedHashMap<>();

    public SettlementManager() {}

    private SettlementManager(List<Settlement> list) {
        for (Settlement s : list) byId.put(s.id, s);
    }

    public static SettlementManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Settlement> all() {
        return byId.values();
    }

    public @Nullable Settlement byId(@Nullable UUID id) {
        return id == null ? null : byId.get(id);
    }

    public @Nullable Settlement at(ResourceKey<Level> dim, BlockPos pos) {
        Settlement best = null;
        double bestDist = Double.MAX_VALUE;
        for (Settlement s : byId.values()) {
            if (!s.contains(dim, pos)) continue;
            double d = s.center.distSqr(pos);
            if (d < bestDist) { best = s; bestDist = d; }
        }
        return best;
    }

    /** Settlement whose (future, largest) radius would overlap a new one at pos. */
    public @Nullable Settlement near(ResourceKey<Level> dim, BlockPos pos, int extra) {
        for (Settlement s : byId.values()) {
            if (!s.dimension.equals(dim)) continue;
            long dx = pos.getX() - s.center.getX(), dz = pos.getZ() - s.center.getZ();
            int r = s.radius() + extra;
            if (dx * dx + dz * dz <= (long) r * r) return s;
        }
        return null;
    }

    public Settlement create(UUID owner, String ownerName, ResourceKey<Level> dim, BlockPos center) {
        Settlement s = new Settlement(UUID.randomUUID(), owner, dim, center.immutable());
        s.ownerName = ownerName;
        byId.put(s.id, s);
        setDirty();
        return s;
    }

    public void remove(UUID id) {
        if (byId.remove(id) != null) setDirty();
    }
}
