package io.github.operagtx.hearthholm.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Helpers to read and move items in a set of containers (warehouse chests, site chests). */
public final class Storage {
    private Storage() {}

    public static List<Container> resolve(ServerLevel level, List<BlockPos> positions) {
        List<Container> out = new ArrayList<>();
        for (BlockPos p : positions) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof Container c) out.add(c);
        }
        return out;
    }

    public static int nutrition(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    public static boolean isFood(ItemStack stack) {
        return nutrition(stack) > 0;
    }

    public static int count(List<Container> containers, Predicate<ItemStack> filter) {
        int n = 0;
        for (Container c : containers)
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && filter.test(s)) n += s.getCount();
            }
        return n;
    }

    public static int totalNutrition(List<Container> containers) {
        int n = 0;
        for (Container c : containers)
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                n += nutrition(s) * s.getCount();
            }
        return n;
    }

    /** Takes up to {@code max} items of the first matching kind. */
    public static ItemStack take(List<Container> containers, Predicate<ItemStack> filter, int max) {
        ItemStack taken = ItemStack.EMPTY;
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize() && (taken.isEmpty() || taken.getCount() < max); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !filter.test(s)) continue;
                if (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, s)) continue;
                int want = max - (taken.isEmpty() ? 0 : taken.getCount());
                ItemStack part = s.split(Math.min(want, taken.isEmpty() ? Math.min(max, s.getMaxStackSize()) : want));
                if (taken.isEmpty()) taken = part; else taken.grow(part.getCount());
                c.setChanged();
            }
            if (!taken.isEmpty() && taken.getCount() >= max) break;
        }
        return taken;
    }

    /** Inserts as much as fits; returns the remainder. */
    public static ItemStack insert(List<Container> containers, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (Container c : containers) {
            rest = insert(c, rest);
            if (rest.isEmpty()) break;
        }
        return rest;
    }

    public static ItemStack insert(Container c, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest) && s.getCount() < s.getMaxStackSize()) {
                int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                rest.shrink(move);
                c.setChanged();
            }
        }
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, rest)) {
                c.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
                c.setChanged();
            }
        }
        return rest;
    }

    /** Removes food worth at least {@code points} nutrition, cheapest items first is not attempted: simple order. */
    public static int consumeNutrition(List<Container> containers, int points) {
        int paid = 0;
        for (Container c : containers)
            for (int i = 0; i < c.getContainerSize() && paid < points; i++) {
                ItemStack s = c.getItem(i);
                int n = nutrition(s);
                while (n > 0 && !s.isEmpty() && paid < points) {
                    s.shrink(1);
                    paid += n;
                    c.setChanged();
                }
            }
        return paid;
    }
}
