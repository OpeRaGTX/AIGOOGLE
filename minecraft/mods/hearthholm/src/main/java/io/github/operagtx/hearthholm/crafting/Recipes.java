package io.github.operagtx.hearthholm.crafting;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.*;

/**
 * Index of the vanilla (and modded) recipes that make catalogue items, read from the server
 * {@link RecipeManager}. Rebuilt lazily whenever the recipe manager changes (/reload). Only
 * shaped, shapeless and single-item recipes (smelting, smoking, stonecutting...) are used;
 * recipes that unpack storage blocks are skipped so nobody turns iron blocks back into ingots.
 */
public final class Recipes {
    public record Option(Item result, ItemStack output, List<Ingredient> inputs, RecipeType<?> type) {
        public boolean cooking() { return Workshop.cooking(type); }
    }

    private static RecipeManager cachedFor;
    private static Map<Item, List<Option>> index = Map.of();

    private Recipes() {}

    public static synchronized List<Option> options(ServerLevel level, Item item) {
        RecipeManager rm = level.getServer().getRecipeManager();
        if (rm != cachedFor) {
            index = build(rm);
            cachedFor = rm;
        }
        return index.getOrDefault(item, List.of());
    }

    /** Options a workshop may use; cooking ones only with heat (stove or a furnace in the room). */
    public static List<Option> usable(ServerLevel level, Item item, Workshop shop, boolean heat) {
        List<Option> out = new ArrayList<>();
        for (Option o : options(level, item)) {
            if (!shop.recipeTypes().contains(o.type())) continue;
            if (o.cooking() && !heat) continue;
            out.add(o);
        }
        return out;
    }

    private static Map<Item, List<Option>> build(RecipeManager rm) {
        Set<Item> wanted = new HashSet<>();
        for (Workshop w : Workshop.values()) wanted.addAll(w.catalog());
        Map<Item, List<Option>> out = new HashMap<>();
        int n = 0;
        for (RecipeHolder<?> h : rm.getRecipes()) {
            Option o;
            try {
                o = option(h.value());
            } catch (RuntimeException e) {
                continue;
            }
            if (o == null || !wanted.contains(o.result())) continue;
            out.computeIfAbsent(o.result(), k -> new ArrayList<>()).add(o);
            n++;
        }
        for (List<Option> list : out.values()) list.sort(Comparator.comparingInt(o -> o.cooking() ? 1 : 0));
        Hearthholm.LOG.info("Hearthholm: indexed {} workshop recipes for {} items", n, out.size());
        return out;
    }

    private static Option option(Recipe<?> r) {
        List<Ingredient> inputs;
        ItemStack result;
        if (r instanceof ShapedRecipe sr) {
            List<ItemStack> grid = new ArrayList<>();
            inputs = new ArrayList<>();
            for (Optional<Ingredient> oi : sr.getIngredients()) {
                grid.add(oi.map(Recipes::first).orElse(ItemStack.EMPTY));
                oi.ifPresent(inputs::add);
            }
            result = sr.assemble(CraftingInput.of(sr.getWidth(), sr.getHeight(), grid));
        } else if (r instanceof ShapelessRecipe sl) {
            inputs = List.copyOf(sl.placementInfo().ingredients());
            List<ItemStack> grid = new ArrayList<>();
            for (Ingredient i : inputs) grid.add(first(i));
            result = sl.assemble(CraftingInput.of(grid.size(), 1, grid));
        } else if (r instanceof SingleItemRecipe si) {
            inputs = List.of(si.input());
            result = si.assemble(new SingleRecipeInput(first(si.input())));
        } else {
            return null;
        }
        if (result.isEmpty() || inputs.isEmpty()) return null;
        for (Ingredient i : inputs) {
            if (i.isEmpty()) return null;
            Optional<Holder<Item>> single = i.items().findFirst();
            if (single.isPresent() && BuiltInRegistries.ITEM.getKey(single.get().value()).getPath().endsWith("_block")) return null;
        }
        return new Option(result.getItem(), result.copy(), inputs, r.getType());
    }

    private static ItemStack first(Ingredient i) {
        return i.items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
    }
}
