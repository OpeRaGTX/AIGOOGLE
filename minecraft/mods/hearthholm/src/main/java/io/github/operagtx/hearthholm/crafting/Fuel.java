package io.github.operagtx.hearthholm.crafting;

import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Wave E: cooking-type recipes (smelting, smoking, blasting...) burn fuel from the warehouse. Each batch costs
 * {@link #BATCH} burn units; an item is taken from storage only when the buffer in the settlement stats runs dry.
 */
public final class Fuel {
    public static final int BATCH = 200;
    public static final String BUFFER = "fuel_buffer", USED = "fuel_used";

    private Fuel() {}

    /** Burn units of one item (a simplified, fixed table; not the furnace's own fuel component). */
    public static int burn(ItemStack s) {
        return burn(s.getItem());
    }

    public static int burn(Item it) {
        if (it == Items.COAL || it == Items.CHARCOAL) return 1600;
        if (it == Items.COAL_BLOCK) return 16000;
        if (it == Items.STICK) return 100;
        if (it.builtInRegistryHolder().is(ItemTags.LOGS_THAT_BURN) || it.builtInRegistryHolder().is(ItemTags.PLANKS)) return 300;
        return 0;
    }

    /** Units of fuel in a stock map. */
    public static int available(Map<Item, Integer> stock) {
        int n = 0;
        for (Map.Entry<Item, Integer> e : stock.entrySet()) n += burn(e.getKey()) * Math.max(0, e.getValue());
        return n;
    }

    /** Pays {@code units} of heat; false (nothing consumed) when the buffer plus the warehouse cannot cover it. */
    public static boolean consume(ServerLevel level, Settlement s, int units) {
        int buffer = s.stats.getOrDefault(BUFFER, 0);
        if (buffer >= units) {
            s.stats.put(BUFFER, buffer - units);
            return true;
        }
        List<Container> storage = Storage.resolve(level, s.storagePositions());
        while (buffer < units) {
            ItemStack got = Storage.take(storage, st -> st.is(Items.COAL) || st.is(Items.CHARCOAL) || st.is(Items.COAL_BLOCK), 1);
            if (got.isEmpty()) got = Storage.take(storage, st -> st.is(Items.STICK) || burn(st) == 300, 1);
            if (got.isEmpty()) {
                s.stats.put(BUFFER, buffer);
                return false;
            }
            buffer += burn(got);
            s.count(USED, 1);
        }
        s.stats.put(BUFFER, buffer - units);
        return true;
    }
}
