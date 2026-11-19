package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Wave M1: which hand tool suits which block, for jobs that clear mixed terrain (the builder). Pure helpers, no state: a job asks
 * {@link #kindFor} what the block needs, {@link #matches} whether an item is that tool, and {@link #takeBest} to pick the best one out of a chest.
 * New tool kinds (axe, hoe...) are one enum constant plus one tag line here.
 */
public final class ToolKit {
    private ToolKit() {}

    public enum Kind {
        PICKAXE(ItemTags.PICKAXES), SHOVEL(ItemTags.SHOVELS);

        private final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag;
        Kind(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) { this.tag = tag; }
        public boolean matches(ItemStack st) { return !st.isEmpty() && st.is(tag); }
        public Predicate<ItemStack> asPredicate() { return this::matches; }
    }

    /** The tool this block should be dug with, or null when any hand is fine (axe blocks, plants, glass...). Stone, ores and metal need the pickaxe (they drop nothing without it). */
    public static @Nullable Kind kindFor(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return Kind.PICKAXE;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Kind.SHOVEL;
        return null;
    }

    /** Best material first, then the least worn. */
    public static long score(ItemStack st) {
        return (long) st.getMaxDamage() * 10000L + (st.getMaxDamage() - st.getDamageValue());
    }

    /** Takes the best matching tool out of {@code c} into the settler's pockets; false when the container has none (or the pockets are full). */
    public static boolean takeBest(SettlerEntity s, Container c, Predicate<ItemStack> want) {
        int best = -1;
        long bestScore = -1;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!want.test(st)) continue;
            long sc = score(st);
            if (sc > bestScore) { bestScore = sc; best = i; }
        }
        if (best < 0) return false;
        ItemStack tool = c.removeItem(best, 1);
        c.setChanged();
        ItemStack rest = s.inventory().addItem(tool);
        if (!rest.isEmpty()) {   // pockets full: put it back, nothing is lost
            io.github.operagtx.hearthholm.settlement.Storage.insert(c, rest);
            return false;
        }
        return true;
    }
}
