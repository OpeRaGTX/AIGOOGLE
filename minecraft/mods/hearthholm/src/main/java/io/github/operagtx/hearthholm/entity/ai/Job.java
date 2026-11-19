package io.github.operagtx.hearthholm.entity.ai;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What a profession does during work hours. Jobs keep almost no state of their own: they read
 * the world and the work-site block entity and return the next {@link Task}, so they survive
 * reloads and interruptions (eating, combat, night) without extra saving code.
 */
public interface Job {
    Job NONE = s -> null;

    /** Next task to run now, or null when there is nothing to do (the settler idles briefly). */
    @Nullable Task next(SettlerEntity settler);

    /** The work period ended or was interrupted. */
    default void pause(SettlerEntity settler) {}

    /** Combat jobs: whether this target is inside the area this settler protects. */
    default boolean defends(SettlerEntity settler, net.minecraft.world.entity.LivingEntity target) { return false; }

    /**
     * Wave K2: the shift is over. A trip that unloads the pockets (everything except tools, weapons, armour and a couple of food items) into
     * the chest near the work site, else the warehouse; null when there is nothing to unload or nowhere to put it.
     */
    default @Nullable Task endOfShift(SettlerEntity settler) { return null; }

    /**
     * Wave M1: how many items of {@code stack} this job's settler carries through the night instead of handing them in at shift end
     * (work supplies: torches, ladders, filler blocks). {@code keptSoFar} = items of the same kind already kept in this unload, so a job can cap a supply.
     * Tools, weapons, armour, shield, arrows and food are handled by {@code BaseJob.keepPlan} and need not be listed here.
     */
    default int shiftEndKeep(SettlerEntity settler, net.minecraft.world.item.ItemStack stack, int keptSoFar) { return 0; }

    /** Wave M1: how many food items this job's settler keeps at shift end (everybody 2, the miner 8). */
    default int shiftEndFood() { return io.github.operagtx.hearthholm.core.Balance.SHIFT_END_KEEP_FOOD; }

    /** Settler changes profession or leaves: release claims. */
    default void release(SettlerEntity settler) {}
}
