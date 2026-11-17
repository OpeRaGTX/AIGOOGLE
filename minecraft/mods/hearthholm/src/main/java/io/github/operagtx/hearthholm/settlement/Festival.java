package io.github.operagtx.hearthholm.settlement;

import io.github.operagtx.hearthholm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * The harvest festival: every {@link #PERIOD} days at noon, if the warehouse holds 4 food per resident, the
 * settlers gather at the open square by the town hall for {@link #DURATION} ticks and dance.
 */
public final class Festival {
    public static final int PERIOD = 7;
    public static final int DURATION = 2400;
    public static final int NOON = 6000;
    public static final int TUNE_TICKS = 200;

    private Festival() {}

    public static int cost(Settlement s) {
        return io.github.operagtx.hearthholm.core.Balance.FESTIVAL_COST_PER_RESIDENT * s.residents.size();
    }

    public static boolean active(ServerLevel level, Settlement s) {
        return s.festivalActive(level.getGameTime());
    }

    /** -1 while a festival runs, else the number of days until the next regular one (0 = due today). */
    public static int daysUntil(ServerLevel level, Settlement s) {
        if (active(level, s)) return -1;
        long day = SettlementService.day(level);
        long next = (s.lastFestival < 0 ? day : s.lastFestival) + PERIOD;
        return (int) Math.max(0, next - day);
    }

    /** Pays the feast and opens the festival. Returns false (nothing changes) when the warehouse lacks the food. */
    public static boolean start(ServerLevel level, Settlement s, boolean early) {
        if (active(level, s) || s.residents.isEmpty()) return false;
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        int cost = cost(s);
        if (Storage.totalNutrition(storage) < cost) return false;
        Storage.consumeNutrition(storage, cost);
        long now = level.getGameTime();
        s.festivalStart = now;
        s.festivalEnd = now + DURATION;
        s.lastFestival = SettlementService.day(level);
        s.square = null;
        s.count("festivals", 1);
        BlockPos sq = square(level, s);
        level.playSound(null, sq, ModSounds.BELL, SoundSource.RECORDS, 3f, 1f);
        io.github.operagtx.hearthholm.town.Fx.festival(level, sq, 16);
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.festival_start").withStyle(ChatFormatting.GOLD));
        SettlementService.manager(level).setDirty();
        return true;
    }

    /** Ends the running festival at once (used by the end timer and tests). */
    public static void end(ServerLevel level, Settlement s) {
        s.festivalEnd = -1;
        int renown = io.github.operagtx.hearthholm.core.Balance.FESTIVAL_RENOWN_BASE + s.residents.size();
        s.addRenown(renown);
        BlockPos sq = square(level, s);
        level.playSound(null, sq, ModSounds.BELL, SoundSource.RECORDS, 2f, 0.8f);
        SettlementService.notify(level, s, Component.translatable("message.hearthholm.festival_end", renown).withStyle(ChatFormatting.GOLD));
        SettlementService.manager(level).setDirty();
    }

    /** Called once a second per settlement. */
    public static void tick(ServerLevel level, Settlement s) {
        long now = level.getGameTime();
        long day = SettlementService.day(level);
        if (s.lastFestival < 0) {
            s.lastFestival = day;
            SettlementService.manager(level).setDirty();
        }
        if (s.festivalEnd > 0 && now >= s.festivalEnd) {
            end(level, s);
            return;
        }
        if (s.festivalActive(now)) {
            if ((now - s.festivalStart) % TUNE_TICKS < 20) {
                level.playSound(null, square(level, s), ModSounds.FESTIVAL, SoundSource.RECORDS, 3f, 1f);
            }
            if ((now - s.festivalStart) % 60 < 20) io.github.operagtx.hearthholm.town.Fx.festival(level, square(level, s), 4);
            return;
        }
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (day >= s.lastFestival + PERIOD && tod >= NOON && tod < NOON + 400 && !s.residents.isEmpty()) {
            if (!start(level, s, false) && s.stats.getOrDefault("festival_warn", -1) != day) {
                s.stats.put("festival_warn", (int) day);
                SettlementService.notify(level, s, Component.translatable("message.hearthholm.festival_no_food", cost(s)).withStyle(ChatFormatting.GRAY));
                SettlementService.manager(level).setDirty();
            }
        }
    }

    /** The open, flat spot near the town hall where everybody gathers (cached until the next festival). */
    public static BlockPos square(ServerLevel level, Settlement s) {
        if (s.square != null) return s.square;
        BlockPos best = s.center;
        outer:
        for (int r = 6; r <= 18; r += 2) {
            for (int a = 0; a < 12; a++) {
                double ang = a * Math.PI / 6;
                int x = s.center.getX() + (int) Math.round(Math.cos(ang) * r), z = s.center.getZ() + (int) Math.round(Math.sin(ang) * r);
                if (!level.isLoaded(new BlockPos(x, s.center.getY(), z))) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - s.center.getY()) > 1) continue;
                BlockPos p = new BlockPos(x, y, z);
                boolean ok = level.getFluidState(p.below()).isEmpty() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP);
                for (int k = 0; ok && k < 4; k++) {
                    int dx = (k % 2 == 0 ? 2 : -2) * (k < 2 ? 1 : 0), dz = (k % 2 == 0 ? 2 : -2) * (k < 2 ? 0 : 1);
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                    ok = Math.abs(h - y) <= 1 && level.isLoaded(new BlockPos(x + dx, y, z + dz));
                }
                if (ok) { best = p; break outer; }
            }
        }
        s.square = best.immutable();
        return s.square;
    }
}
