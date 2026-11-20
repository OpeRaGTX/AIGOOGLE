package io.github.operagtx.hearthholm.client;

import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.registry.ModParticles;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wave L3: the ghost blueprint. While the player holds a Hearthholm item and stands within {@link #RANGE} blocks of a construction stake, the blocks of the
 * chosen blueprint that are not built yet are shown as translucent pictures (30% opacity) where they will stand. They are {@code ModParticles.GHOST_BLOCK}
 * particles that live 14 ticks and are renewed every 10, so there is no state to keep: change the blueprint or the facing and the ghost follows; build a block
 * and its ghost disappears. At most {@link #MAX_GHOSTS} blocks are shown (the nearest to the player) to keep the particle count low.
 */
public final class GhostPreview {
    public static final int RANGE = 48, MAX_GHOSTS = 600;

    private GhostPreview() {}

    /** Whether this work site's blueprint is previewed for the player (a construction stake with a blueprint, near enough). */
    public static boolean applies(LocalPlayer player, WorkSiteBlockEntity w) {
        return w.type().id().equals(WorkSiteTypes.CONSTRUCTION_ID) && player.distanceToSqr(w.getBlockPos().getX() + 0.5, w.getBlockPos().getY(), w.getBlockPos().getZ() + 0.5) <= RANGE * RANGE;
    }

    /** The cells to show now: not yet built ({@code want} differs from the world), nearest to the player first, capped. Pure of rendering, covered by the test. */
    public static List<Blueprint.BuildOp> pending(ClientLevel level, LocalPlayer player, WorkSiteBlockEntity w) {
        Blueprint bp = BlueprintLibrary.byIndex(w.option("blueprint"));
        if (bp == null) return List.of();
        List<Blueprint.BuildOp> out = new ArrayList<>();
        for (Blueprint.BuildOp op : bp.ops(w.getBlockPos(), w.facing())) {
            if (op.clear() || op.state().isAir()) continue;
            BlockState now = level.getBlockState(op.pos());
            if (now.getBlock() == op.state().getBlock()) continue;   // built (the exact state may differ in shape or colour: the block is what counts)
            out.add(op);
        }
        if (out.size() > MAX_GHOSTS) {
            out.sort(Comparator.comparingDouble(op -> op.pos().distToCenterSqr(player.position())));
            out = new ArrayList<>(out.subList(0, MAX_GHOSTS));
        }
        return out;
    }

    /** Shows one round of ghosts (called every 10 ticks by {@link ZonePreview}). Returns how many were shown. */
    public static int show(ClientLevel level, LocalPlayer player, WorkSiteBlockEntity w) {
        int n = 0;
        for (Blueprint.BuildOp op : pending(level, player, w)) {
            BlockPos p = op.pos();
            level.addParticle(new BlockParticleOption(ModParticles.GHOST_BLOCK, op.state()), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, 0, 0);
            n++;
        }
        return n;
    }
}
