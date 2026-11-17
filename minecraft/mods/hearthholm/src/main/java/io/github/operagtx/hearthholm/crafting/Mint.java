package io.github.operagtx.hearthholm.crafting;

import com.google.gson.*;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hearth marks: the town hall mints surplus goods from the warehouse into coins. Rates live in
 * {@code config/hearthholm-mint.json} (written with defaults on first start): for each item, how
 * many make one mark and how many always stay in the warehouse.
 */
public final class Mint {
    public record Rate(Item item, int perMark, int keep) {}

    private static final List<Rate> DEFAULTS = List.of(
            new Rate(Items.IRON_INGOT, 16, 16), new Rate(Items.GOLD_INGOT, 16, 0), new Rate(Items.EMERALD, 16, 0),
            new Rate(Items.WHEAT, 64, 32), new Rate(Items.CARROT, 64, 32), new Rate(Items.POTATO, 64, 32),
            new Rate(Items.BEETROOT, 64, 32));
    private static List<Rate> rates = DEFAULTS;

    private Mint() {}

    public static List<Rate> rates() { return rates; }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("hearthholm-mint.json");
        try {
            if (!Files.exists(file)) {
                JsonArray arr = new JsonArray();
                for (Rate r : DEFAULTS) {
                    JsonObject o = new JsonObject();
                    o.addProperty("item", BuiltInRegistries.ITEM.getKey(r.item()).toString());
                    o.addProperty("per_mark", r.perMark());
                    o.addProperty("keep", r.keep());
                    arr.add(o);
                }
                JsonObject root = new JsonObject();
                root.addProperty("_comment", "Town hall mint: 'per_mark' items of a kind become one Hearth mark; 'keep' stay in the warehouse.");
                root.add("rates", arr);
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Rate> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("rates")) {
                JsonObject o = e.getAsJsonObject();
                Identifier id = Identifier.tryParse(o.get("item").getAsString());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                int per = o.has("per_mark") ? o.get("per_mark").getAsInt() : 16;
                int keep = o.has("keep") ? o.get("keep").getAsInt() : 0;
                if (per > 0) out.add(new Rate(BuiltInRegistries.ITEM.getValue(id), per, Math.max(0, keep)));
            }
            if (!out.isEmpty()) rates = List.copyOf(out);
        } catch (IOException | RuntimeException e) {
            Hearthholm.LOG.warn("Hearthholm: cannot read {}, using default mint rates", file, e);
        }
    }

    /** Takes surplus out of the containers; returns how many marks it is worth (0 = nothing taken). */
    public static int mint(List<Container> storage, List<Component> spentOut) {
        int marks = 0;
        for (Rate r : rates) {
            int have = Storage.count(storage, s -> s.is(r.item()) && !s.isDamaged());
            int n = Math.max(0, have - r.keep()) / r.perMark();
            if (n <= 0) continue;
            int left = n * r.perMark();
            while (left > 0) {
                ItemStack got = Storage.take(storage, s -> s.is(r.item()) && !s.isDamaged(), Math.min(left, r.item().getDefaultMaxStackSize()));
                if (got.isEmpty()) break;
                left -= got.getCount();
            }
            marks += n;
            spentOut.add(Component.literal(n * r.perMark() + "× ").append(new ItemStack(r.item()).getHoverName()));
        }
        return marks;
    }

    /** "16× Iron Ingot (keep 16), ..." for the tooltip of the mint button. */
    public static Component describe() {
        MutableComponent out = Component.translatable("gui.hearthholm.mint_rates");
        for (Rate r : rates) {
            out.append("\n").append(Component.literal(r.perMark() + "× ")).append(new ItemStack(r.item()).getHoverName());
            if (r.keep() > 0) out.append(Component.translatable("gui.hearthholm.mint_keep", r.keep()));
        }
        return out;
    }
}
