package io.github.operagtx.hearthholm.threat;

import io.github.operagtx.hearthholm.block.PalisadeGateBlock;
import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The alarm (bells), palisade gates and trophy banners. While the alarm is up civilians shelter at home or in
 * the town hall and combat settlers gather at the nearest bell (see {@code AlarmActivity}); gates are shut.
 */
public final class Defense {
    private Defense() {}

    private static @Nullable Settlement at(ServerLevel level, BlockPos pos) {
        return SettlementService.manager(level).at(level.dimension(), pos);
    }

    public static void onBellPlaced(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Settlement s = at(level, pos);
        if (s == null) {
            if (player != null) player.sendSystemMessage(Component.translatable("message.hearthholm.outside").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!s.bells.contains(pos)) s.bells.add(pos.immutable());
        SettlementService.manager(level).setDirty();
        if (player != null) player.sendSystemMessage(Component.translatable("message.hearthholm.bell_placed").withStyle(ChatFormatting.GRAY));
    }

    public static void ringBell(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Settlement s = at(level, pos);
        level.playSound(null, pos, ModSounds.ALARM_BELL, SoundSource.BLOCKS, 2f, 1f);
        if (s == null) return;
        if (!s.bells.contains(pos)) s.bells.add(pos.immutable());
        s.stats.remove("auto_alarm");
        setAlarm(level, s, !s.alarm, player);
    }

    /** Raises or lowers the alarm of the settlement (test hook too). */
    public static void setAlarm(ServerLevel level, Settlement s, boolean on, @Nullable ServerPlayer by) {
        if (s.alarm == on) return;
        s.alarm = on;
        ringAll(level, s, 2f);
        if (on) closeGates(level, s);
        SettlementService.notify(level, s, Component.translatable(on ? "message.hearthholm.alarm_on" : "message.hearthholm.alarm_off")
                .withStyle(on ? ChatFormatting.RED : ChatFormatting.GREEN));
        SettlementService.manager(level).setDirty();
    }

    public static void ringAll(ServerLevel level, Settlement s, float volume) {
        if (s.bells.isEmpty()) level.playSound(null, s.center, ModSounds.ALARM_BELL, SoundSource.BLOCKS, volume, 1f);
        for (BlockPos b : s.bells) if (level.isLoaded(b)) level.playSound(null, b, ModSounds.ALARM_BELL, SoundSource.BLOCKS, volume, 1f);
    }

    /** Shuts every open palisade gate of the settlement. Returns how many were closed. */
    public static int closeGates(ServerLevel level, Settlement s) {
        int n = 0;
        for (BlockPos g : s.gates) {
            if (!level.isLoaded(g)) continue;
            BlockState st = level.getBlockState(g);
            if (st.getBlock() instanceof PalisadeGateBlock gate && st.getValue(DoorBlock.OPEN)) {
                gate.setOpen(null, level, st, g, false);
                n++;
            }
        }
        return n;
    }

    public static void onGatePlaced(ServerLevel level, BlockPos pos) {
        Settlement s = at(level, pos);
        if (s == null || s.gates.contains(pos)) return;
        s.gates.add(pos.immutable());
        SettlementService.manager(level).setDirty();
    }

    public static void onTrophyPlaced(ServerLevel level, BlockPos pos) {
        Settlement s = at(level, pos);
        if (s == null) return;
        if (!s.trophies.contains(pos)) s.trophies.add(pos.immutable());
        s.lastScan = -100000;
        SettlementService.manager(level).setDirty();
    }

    /** Nearest bell to {@code from}, or the town hall when there is none. */
    public static BlockPos rallyPoint(Settlement s, BlockPos from) {
        BlockPos best = s.center;
        double bd = Double.MAX_VALUE;
        for (BlockPos b : s.bells) {
            double d = b.distSqr(from);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }
}
