package io.github.operagtx.hearthholm.entity.ai.activity;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Activity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Eats from the pocket, or fetches food from the warehouse first. */
public final class EatActivity implements Activity {
    private final SettlerEntity s;
    private Task trip;
    private int chew;
    private long noFoodUntil;

    /** Cooked meals are taken first: they lift the mood ("Tasty meal"). */
    private static final java.util.function.Predicate<ItemStack> MEAL =
            st -> io.github.operagtx.hearthholm.crafting.Workshop.MEALS.contains(st.getItem()) && Storage.isFood(st);

    public EatActivity(SettlerEntity s) { this.s = s; }

    @Override
    public boolean wants() {
        if (s.isLeaving() || !s.hasArrived()) return false;
        boolean hungry = s.food() < 8 || (s.isLunchTime() && s.food() < 15);
        if (!hungry) return false;
        if (s.count(Storage::isFood) > 0) return true;
        return s.level().getGameTime() >= noFoodUntil && findFood() != null;
    }

    private BlockPos findFood() {
        Settlement st = s.settlement();
        if (st == null || !(s.level() instanceof ServerLevel sl)) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : st.storagePositions()) {
            if (!(sl.getBlockEntity(p) instanceof Container c)) continue;
            if (Storage.count(List.of(c), Storage::isFood) == 0) continue;
            double d = p.distSqr(s.blockPosition());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    @Override
    public void start() {
        trip = null;
        chew = 0;
    }

    @Override
    public boolean tick() {
        if (s.food() >= 18) return false;
        ItemStack food = ItemStack.EMPTY;
        for (ItemStack st : s.inventory().getItems()) if (Storage.isFood(st)) { food = st; break; }
        for (ItemStack st : s.inventory().getItems()) if (MEAL.test(st)) { food = st; break; }
        if (!food.isEmpty()) {
            s.setStatus("eating");
            s.getNavigation().stop();
            if (++chew % 8 == 0) s.swingArm(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (chew >= 32) {
                chew = 0;
                s.eat(food.split(1));
                s.inventory().setChanged();
            }
            return true;
        }
        if (trip == null) {
            BlockPos p = findFood();
            if (p == null) {
                s.setStatus("no_food");
                noFoodUntil = s.level().getGameTime() + 1200;
                return false;
            }
            s.setStatus("fetching_food");
            trip = new ContainerTrip(p, (st, c) -> {
                ItemStack meal = Storage.take(st.settlement() == null ? List.of(c) : Storage.resolve((ServerLevel) st.level(),
                        st.settlement().storagePositions()), MEAL, 4);
                st.store(meal.isEmpty() ? Storage.take(List.of(c), Storage::isFood, 4) : meal);
            });
        }
        Task.Status r = trip.tick(s);
        if (r != Task.Status.RUNNING) {
            trip.stop(s);
            trip = null;
            if (r == Task.Status.FAILED) return false;
        }
        return true;
    }

    @Override
    public void stop() {
        if (trip != null) trip.stop(s);
        trip = null;
    }
}
