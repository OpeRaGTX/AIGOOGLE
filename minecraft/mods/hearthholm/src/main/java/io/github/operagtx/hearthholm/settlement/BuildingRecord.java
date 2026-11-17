package io.github.operagtx.hearthholm.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.operagtx.hearthholm.crafting.Workshop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A room found by the last scan. A room can have several roles at once (an early town hall
 * with a bed is also housing, a kitchen can share a room with a carpentry). {@code problem} is a
 * translation suffix, empty when valid. Roles are bit flags, so new roles keep old saves valid;
 * workshop roles come from {@link Workshop#role()}. {@code stations} are the workshop blocks in the
 * room, {@code features} extra equipment flags ({@link #FURNACE}).
 */
public record BuildingRecord(int roles, BlockPos anchor, List<BlockPos> beds, List<BlockPos> containers,
                             int comfort, int size, String problem, List<BlockPos> stations, int features) {
    public static final int HALL = 1, WAREHOUSE = 2, HOUSING = 4;
    public static final int WORKSHOPS = 8 | 16 | 32 | 64 | 128 | 256 | 512;
    public static final int FURNACE = 1;

    public static final Codec<BuildingRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("roles").forGetter(BuildingRecord::roles),
            BlockPos.CODEC.fieldOf("anchor").forGetter(BuildingRecord::anchor),
            BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(BuildingRecord::beds),
            BlockPos.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(BuildingRecord::containers),
            Codec.INT.optionalFieldOf("comfort", 0).forGetter(BuildingRecord::comfort),
            Codec.INT.optionalFieldOf("size", 0).forGetter(BuildingRecord::size),
            Codec.STRING.optionalFieldOf("problem", "").forGetter(BuildingRecord::problem),
            BlockPos.CODEC.listOf().optionalFieldOf("stations", List.of()).forGetter(BuildingRecord::stations),
            Codec.INT.optionalFieldOf("features", 0).forGetter(BuildingRecord::features)
    ).apply(i, BuildingRecord::new));

    public boolean valid() { return problem.isEmpty(); }
    public boolean is(int role) { return (roles & role) != 0; }
    public boolean has(int feature) { return (features & feature) != 0; }

    public Component title() {
        return title(roles);
    }

    /** "Town Hall + House", "Carpentry + Kitchen"... (also used by the client). */
    public static Component title(int roles) {
        String main = (roles & HALL) != 0 ? "hall" : (roles & WAREHOUSE) != 0 ? "warehouse" : null;
        net.minecraft.network.chat.MutableComponent out = main == null ? null : Component.translatable("building.hearthholm." + main);
        for (Workshop w : Workshop.values()) {
            if ((roles & w.role()) == 0) continue;
            out = out == null ? w.label().copy() : out.append(" + ").append(w.label());
        }
        if (out == null) return Component.translatable("building.hearthholm.house");
        if ((roles & HOUSING) != 0) out.append(" + ").append(Component.translatable("building.hearthholm.house"));
        return out;
    }
}
