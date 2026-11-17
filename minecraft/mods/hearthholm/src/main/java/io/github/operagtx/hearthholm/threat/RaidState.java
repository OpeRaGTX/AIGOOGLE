package io.github.operagtx.hearthholm.threat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Raid bookkeeping of one settlement (saved inside the settlement; every field has a default). */
public final class RaidState {
    public static final int NONE = 0, WARNING = 1, ACTIVE = 2;
    public static final int RESULT_NONE = 0, RESULT_WON = 1, RESULT_LOST = 2;

    public static final Codec<RaidState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("next_day", -1L).forGetter(r -> r.nextDay),
            Codec.INT.optionalFieldOf("phase", NONE).forGetter(r -> r.phase),
            Codec.LONG.optionalFieldOf("phase_end", 0L).forGetter(r -> r.phaseEnd),
            Codec.INT.optionalFieldOf("dir", 0).forGetter(r -> r.dir),
            Codec.INT.optionalFieldOf("strength", 0).forGetter(r -> r.strength),
            Codec.INT.optionalFieldOf("waves_left", 0).forGetter(r -> r.wavesLeft),
            Codec.LONG.optionalFieldOf("next_wave", 0L).forGetter(r -> r.nextWave),
            UUIDUtil.CODEC.listOf().optionalFieldOf("raiders", List.of()).forGetter(r -> List.copyOf(r.raiders)),
            Codec.INT.optionalFieldOf("spawned", 0).forGetter(r -> r.spawned),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(r -> r.deaths),
            Codec.INT.optionalFieldOf("last_result", RESULT_NONE).forGetter(r -> r.lastResult),
            Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(r -> r.lastDay),
            Codec.INT.optionalFieldOf("won", 0).forGetter(r -> r.won),
            Codec.INT.optionalFieldOf("lost", 0).forGetter(r -> r.lost),
            Codec.BOOL.optionalFieldOf("chieftain", false).forGetter(r -> r.chieftain),
            Codec.INT.optionalFieldOf("killed", 0).forGetter(r -> r.killed)
    ).apply(i, RaidState::new));

    /** Day of the next raid (-1 = not planned yet: the settlement is too small). */
    public long nextDay;
    public int phase;
    /** WARNING: game time the raiders arrive; ACTIVE: game time they give up and retreat. */
    public long phaseEnd;
    /** Compass direction the raid comes from, 0 = north, clockwise in steps of 45 degrees. */
    public int dir;
    public int strength;
    public int wavesLeft;
    public long nextWave;
    public final List<UUID> raiders = new ArrayList<>();
    public int spawned, deaths;
    public int lastResult;
    public long lastDay;
    public int won, lost;
    public boolean chieftain;
    public int killed;

    public RaidState() {
        this(-1, NONE, 0, 0, 0, 0, 0, List.of(), 0, 0, RESULT_NONE, -1, 0, 0, false, 0);
    }

    private RaidState(long nextDay, int phase, long phaseEnd, int dir, int strength, int wavesLeft, long nextWave, List<UUID> raiders,
                      int spawned, int deaths, int lastResult, long lastDay, int won, int lost, boolean chieftain, int killed) {
        this.nextDay = nextDay;
        this.phase = phase;
        this.phaseEnd = phaseEnd;
        this.dir = dir;
        this.strength = strength;
        this.wavesLeft = wavesLeft;
        this.nextWave = nextWave;
        this.raiders.addAll(raiders);
        this.spawned = spawned;
        this.deaths = deaths;
        this.lastResult = lastResult;
        this.lastDay = lastDay;
        this.won = won;
        this.lost = lost;
        this.chieftain = chieftain;
        this.killed = killed;
    }
}
