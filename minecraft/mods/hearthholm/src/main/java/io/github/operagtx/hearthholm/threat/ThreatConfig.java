package io.github.operagtx.hearthholm.threat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.operagtx.hearthholm.Hearthholm;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/hearthholm-threats.json}: raids and rift nights on/off, whether arsonists may light fires
 * ({@code raidFire}), raid interval and the rift period. Missing keys keep their defaults.
 */
public final class ThreatConfig {
    public static boolean raids = true;
    public static boolean raidFire = true;
    public static boolean rifts = true;
    public static int raidMinDays = 5;
    public static int raidMaxDays = 8;
    public static int graceDays = 3;
    public static int riftPeriod = 10;

    private ThreatConfig() {}

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("hearthholm-threats.json");
        try {
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("raids")) raids = o.get("raids").getAsBoolean();
                if (o.has("raidFire")) raidFire = o.get("raidFire").getAsBoolean();
                if (o.has("rifts")) rifts = o.get("rifts").getAsBoolean();
                if (o.has("raidMinDays")) raidMinDays = Math.max(1, o.get("raidMinDays").getAsInt());
                if (o.has("raidMaxDays")) raidMaxDays = Math.max(raidMinDays, o.get("raidMaxDays").getAsInt());
                if (o.has("graceDays")) graceDays = Math.max(0, o.get("graceDays").getAsInt());
                if (o.has("riftPeriod")) riftPeriod = Math.max(2, o.get("riftPeriod").getAsInt());
            }
            JsonObject root = new JsonObject();
            root.addProperty("raids", raids);
            root.addProperty("raidFire", raidFire);
            root.addProperty("rifts", rifts);
            root.addProperty("raidMinDays", raidMinDays);
            root.addProperty("raidMaxDays", raidMaxDays);
            root.addProperty("graceDays", graceDays);
            root.addProperty("riftPeriod", riftPeriod);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Hearthholm.LOG.warn("Cannot read threat config, using defaults", ex);
        }
    }
}
