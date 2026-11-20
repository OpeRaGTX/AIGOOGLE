package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.mixin.MobAccessor;
import io.github.operagtx.hearthholm.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Wave L1: the cavalryman's mount. All the horse bookkeeping is here; the behaviour is in {@code CavalrymanJob} (find, saddle, mount, patrol) and
 * {@link CavalryChargeGoal} (ram, ride on, turn, ram again).
 *
 * <p>How a settler steers a horse: in 26.3 only a player controls a saddled horse, so a settler cannot. Instead, while a cavalryman sits on a horse that
 * carries the {@link #TAG}, {@code SettlerEntity.getNavigation()} hands out the HORSE's navigation: every existing task (walk to a point, patrol, chase) then drives
 * the horse without knowing about it. The horse's own goals (wandering, panic, bucking) are held back by {@link HeldGoal}, which only exists while the tag is set.
 */
public final class Cavalry {
    /** Entity tag of a horse that carries a cavalryman. Removed when he dismounts, which also releases the horse's own AI. */
    public static final String TAG = "hh_cavalry_mount";

    private Cavalry() {}

    /** The horse this settler sits on as a cavalryman, or null. */
    public static @Nullable Horse mountOf(SettlerEntity s) {
        return s.getVehicle() instanceof Horse h && h.entityTags().contains(TAG) ? h : null;
    }

    /** The nearest horse of the settlement territory that nobody rides, or null. */
    public static @Nullable Horse findHorse(SettlerEntity s, Settlement st) {
        if (!(s.level() instanceof ServerLevel sl)) return null;
        int r = Math.max(16, st.radius());
        AABB box = new AABB(st.center).inflate(r, 24, r);
        return sl.getEntitiesOfClass(Horse.class, box, h -> h.isAlive() && !h.isVehicle() && !h.isBaby()).stream()
                .min(Comparator.comparingDouble(s::distanceToSqr)).orElse(null);
    }

    public static boolean saddled(Horse h) {
        return !h.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
    }

    /** Puts a saddle from the settler's pockets on the horse; false when he has none (or the horse already wears one, which counts as done). */
    public static boolean saddleFromPockets(SettlerEntity s, Horse h) {
        if (saddled(h)) return true;
        ItemStack saddle = s.takeFirst(st -> st.is(Items.SADDLE), 1);
        if (saddle.isEmpty()) return false;
        h.setItemSlot(EquipmentSlot.SADDLE, saddle);
        return true;
    }

    /** Sits the settler on the horse and holds the horse's own AI back. */
    public static boolean mount(SettlerEntity s, Horse h) {
        if (h.isVehicle() || !saddled(h)) return false;
        h.setTamed(true);
        h.addTag(TAG);
        hold(h);
        // force = true: the usual "can this rider board" rules (shift key, boarding cooldown) are meant for players and mobs that spawn on the mount; the
        // job has already decided that this settler rides this free, saddled horse
        if (s.startRiding(h, true, true)) return true;
        io.github.operagtx.hearthholm.Hearthholm.LOG.warn("Cavalryman {} could not board the horse at {} (passengers {}, vehicle {})", s.getName().getString(), h.blockPosition(), h.getPassengers().size(), s.getVehicle());
        h.removeTag(TAG);
        return false;
    }

    /** Gets off (shift over, retreat, other profession). The horse becomes an ordinary horse again. */
    public static void dismount(SettlerEntity s) {
        if (s.getVehicle() instanceof Horse h) {
            h.removeTag(TAG);
            s.stopRiding();
        }
    }

    /** Installs the goal that keeps the horse still-minded (once per horse; it only acts while the tag is set). */
    private static void hold(Horse h) {
        var selector = ((MobAccessor) h).hearthholm$goalSelector();
        if (selector.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof HeldGoal)) selector.addGoal(0, new HeldGoal(h));
    }

    /** Top-priority goal that blocks every other movement goal of the horse while a cavalryman sits on it; it does nothing itself (the rider drives the navigation). */
    private static final class HeldGoal extends Goal {
        private final Horse horse;

        HeldGoal(Horse horse) {
            this.horse = horse;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override public boolean canUse() { return horse.entityTags().contains(TAG) && horse.isVehicle(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean isInterruptable() { return false; }
    }

    /** Where the horse stands (for the tasks): block position of the horse, or the settler's when he is on foot. */
    public static BlockPos feet(SettlerEntity s) {
        return s.getVehicle() != null ? s.getVehicle().blockPosition() : s.blockPosition();
    }
}
