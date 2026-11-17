package io.github.operagtx.hearthholm.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Blueprints shipped in {@code data/hearthholm/blueprints/} (listed in {@code index.json}) plus
 * any registered through the API. Order is stable: construction stakes store an index, so new
 * blueprints must be appended to the index, never inserted.
 */
public final class BlueprintLibrary {
    private static final List<Identifier> IDS = new ArrayList<>();
    private static final Map<Identifier, JsonObject> RAW = new HashMap<>();
    private static final Map<Identifier, Blueprint> PARSED = new HashMap<>();

    private BlueprintLibrary() {}

    public static void bootstrap() {
        JsonObject index = read("/data/hearthholm/blueprints/index.json");
        if (index == null) return;
        for (JsonElement e : index.getAsJsonArray("blueprints")) {
            String name = e.getAsString();
            JsonObject json = read("/data/hearthholm/blueprints/" + name + ".json");
            if (json == null) {
                Hearthholm.LOG.warn("Missing blueprint {}", name);
                continue;
            }
            Identifier id = Hearthholm.id(name);
            IDS.add(id);
            RAW.put(id, json);
        }
    }

    private static @Nullable JsonObject read(String path) {
        try (InputStream in = BlueprintLibrary.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            Hearthholm.LOG.error("Cannot read {}", path, ex);
            return null;
        }
    }

    public static synchronized void register(Blueprint bp) {
        if (!IDS.contains(bp.id())) IDS.add(bp.id());
        PARSED.put(bp.id(), bp);
    }

    public static List<Identifier> ids() {
        return Collections.unmodifiableList(IDS);
    }

    public static List<String> labelKeys() {
        List<String> out = new ArrayList<>();
        for (Identifier id : IDS) out.add("blueprint." + id.getNamespace() + "." + id.getPath());
        return out;
    }

    public static List<Blueprint> all() {
        List<Blueprint> out = new ArrayList<>();
        for (Identifier id : IDS) {
            Blueprint b = get(id);
            if (b != null) out.add(b);
        }
        return out;
    }

    public static @Nullable Blueprint byIndex(int index) {
        return index >= 0 && index < IDS.size() ? get(IDS.get(index)) : null;
    }

    public static synchronized @Nullable Blueprint get(Identifier id) {
        Blueprint b = PARSED.get(id);
        if (b != null) return b;
        JsonObject raw = RAW.get(id);
        if (raw == null) return null;
        try {
            b = parse(id, raw);
            PARSED.put(id, b);
            return b;
        } catch (Exception ex) {
            Hearthholm.LOG.error("Bad blueprint {}", id, ex);
            RAW.remove(id);
            return null;
        }
    }

    private static Blueprint parse(Identifier id, JsonObject json) throws Exception {
        Map<Character, BlockState> palette = new HashMap<>();
        palette.put('.', Blocks.AIR.defaultBlockState());
        palette.put(' ', Blocks.AIR.defaultBlockState());
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("palette").entrySet()) {
            String value = e.getValue().getAsString();
            BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, value, false).blockState();
            palette.put(e.getKey().charAt(0), state);
        }
        JsonArray layers = json.getAsJsonArray("layers");
        int height = layers.size();
        int depth = layers.get(0).getAsJsonArray().size();
        int width = layers.get(0).getAsJsonArray().get(0).getAsString().length();
        BlockState[] cells = new BlockState[width * height * depth];
        for (int y = 0; y < height; y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < depth; z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < width; x++) {
                    char c = x < row.length() ? row.charAt(x) : '~';
                    if (c == '~') continue;
                    BlockState s = palette.get(c);
                    if (s == null) throw new IllegalArgumentException("Unknown palette char '" + c + "' in " + id);
                    cells[(y * depth + z) * width + x] = s;
                }
            }
        }
        return new Blueprint(id, width, height, depth, cells);
    }
}
