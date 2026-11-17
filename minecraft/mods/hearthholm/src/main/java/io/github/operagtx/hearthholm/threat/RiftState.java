package io.github.operagtx.hearthholm.threat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Rift-night bookkeeping of one settlement (saved inside the settlement; every field has a default). */
public final class RiftState {
    public static final int MAX_HP = 60;

    public static final Codec<RiftState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("next_day", -1L).forGetter(r -> r.nextDay),
            BlockPos.CODEC.optionalFieldOf("core").forGetter(r -> Optional.ofNullable(r.core)),
            Codec.INT.optionalFieldOf("hp", MAX_HP).forGetter(r -> r.hp),
            Codec.LONG.optionalFieldOf("opened", 0L).forGetter(r -> r.opened),
            Codec.INT.optionalFieldOf("closed", 0).forGetter(r -> r.closed),
            UUIDUtil.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(r -> List.copyOf(r.mobs)),
            Codec.LONG.optionalFieldOf("next_wave", 0L).forGetter(r -> r.nextWave)
    ).apply(i, (next, core, hp, opened, closed, mobs, wave) -> {
        RiftState r = new RiftState();
        r.nextDay = next;
        r.core = core.orElse(null);
        r.hp = hp;
        r.opened = opened;
        r.closed = closed;
        r.mobs.addAll(mobs);
        r.nextWave = wave;
        return r;
    }));

    public long nextDay = -1;
    public @Nullable BlockPos core;
    public int hp = MAX_HP;
    public long opened;
    public int closed;
    public final List<UUID> mobs = new ArrayList<>();
    public long nextWave;

    public boolean open() {
        return core != null;
    }
}
