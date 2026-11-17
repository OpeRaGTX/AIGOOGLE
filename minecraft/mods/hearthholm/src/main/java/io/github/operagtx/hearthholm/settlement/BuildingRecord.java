package io.github.operagtx.hearthholm.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A room found by the last scan. A room can have several roles at once (an early town hall
 * with a bed is also housing). {@code problem} is a translation suffix, empty when valid.
 */
public record BuildingRecord(int roles, BlockPos anchor, List<BlockPos> beds, List<BlockPos> containers,
                             int comfort, int size, String problem) {
    public static final int HALL = 1, WAREHOUSE = 2, HOUSING = 4;

    public static final Codec<BuildingRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("roles").forGetter(BuildingRecord::roles),
            BlockPos.CODEC.fieldOf("anchor").forGetter(BuildingRecord::anchor),
            BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(BuildingRecord::beds),
            BlockPos.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(BuildingRecord::containers),
            Codec.INT.optionalFieldOf("comfort", 0).forGetter(BuildingRecord::comfort),
            Codec.INT.optionalFieldOf("size", 0).forGetter(BuildingRecord::size),
            Codec.STRING.optionalFieldOf("problem", "").forGetter(BuildingRecord::problem)
    ).apply(i, BuildingRecord::new));

    public boolean valid() { return problem.isEmpty(); }
    public boolean is(int role) { return (roles & role) != 0; }

    public Component title() {
        if (is(HALL)) return Component.translatable("building.hearthholm.hall");
        if (is(WAREHOUSE)) return Component.translatable("building.hearthholm.warehouse");
        return Component.translatable("building.hearthholm.house");
    }
}
