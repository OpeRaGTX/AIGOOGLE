package io.github.operagtx.hearthholm.core;

/**
 * Wave F: every tunable number of the game economy in one place (see DESIGN.md "Баланс" for the reasoning).
 * Plain constants on purpose: change them here and rebuild. Raid timing lives in {@code ThreatConfig} (config file).
 */
public final class Balance {
    private Balance() {}

    /** Optional override file {@code config/hearthholm.json}: every field below by name; missing keys keep the defaults. */
    public static void load() {
        java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("hearthholm.json");
        try {
            if (java.nio.file.Files.exists(file)) {
                var parsed = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) apply(parsed.getAsJsonObject());
            }
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(toJson()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            io.github.operagtx.hearthholm.Hearthholm.LOG.warn("Cannot read hearthholm.json, using default balance", ex);
        }
    }

    /** Current values of every tunable (int or int[] field), keyed by field name. */
    public static com.google.gson.JsonObject toJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        try {
            for (java.lang.reflect.Field f : Balance.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !java.lang.reflect.Modifier.isPublic(f.getModifiers())) continue;
                if (f.getType() == int.class) o.addProperty(f.getName(), f.getInt(null));
                else if (f.getType() == int[].class) {
                    com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                    for (int v : (int[]) f.get(null)) a.add(v);
                    o.add(f.getName(), a);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    /** Applies the known keys of {@code o}; wrong types or array lengths are skipped. Returns the number of values changed. */
    public static int apply(com.google.gson.JsonObject o) {
        int n = 0;
        for (java.lang.reflect.Field f : Balance.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !java.lang.reflect.Modifier.isPublic(f.getModifiers()) || !o.has(f.getName())) continue;
            try {
                var v = o.get(f.getName());
                if (f.getType() == int.class && v.isJsonPrimitive()) {
                    int nv = v.getAsInt();
                    if (nv != f.getInt(null)) { f.setInt(null, nv); n++; }
                } else if (f.getType() == int[].class && v.isJsonArray()) {
                    int[] cur = (int[]) f.get(null);
                    var a = v.getAsJsonArray();
                    if (a.size() != cur.length) continue;
                    int[] next = new int[cur.length];
                    for (int i = 0; i < next.length; i++) next[i] = a.get(i).getAsInt();
                    if (!java.util.Arrays.equals(cur, next)) { System.arraycopy(next, 0, cur, 0, next.length); n++; }
                }
            } catch (Exception ignored) {
                // malformed entry: keep the default
            }
        }
        return n;
    }

    // ---- settlement growth: renown needed per tier (camp, hamlet, village, town, city), residents and radius per tier
    public static int[] TIER_RENOWN = {0, 60, 300, 1000, 3000};
    public static int[] TIER_POP = {2, 5, 10, 16, 24};
    public static int[] TIER_RADIUS = {40, 56, 72, 88, 104};

    // ---- hiring and festival (paid in nutrition points of food)
    public static int HIRE_BASE = 10, HIRE_PER_RESIDENT = 5;
    public static int FESTIVAL_COST_PER_RESIDENT = 4, FESTIVAL_RENOWN_BASE = 10;

    // ---- raids: strength = base + renown/RENOWN_DIV + stacks/STACKS_DIV + residents/POP_DIV, capped per tier
    public static int RAID_BASE = 3, RAID_RENOWN_DIV = 250, RAID_STACKS_DIV = 10, RAID_POP_DIV = 2;
    public static int[] RAID_CAP_BY_TIER = {3, 5, 8, 14, 24};
    public static int RAID_WIN_RENOWN_BASE = 20, RAID_WIN_RENOWN_PER_STRENGTH = 5, RAID_WIN_MARKS_BASE = 2;

    // ---- caravan prices in hearth marks
    public static int PRICE_SEEDS = 2, PRICE_SEEDS_RARE = 3, PRICE_SAPLING = 5;
    public static int PRICE_BLUEPRINT_BASE = 15, PRICE_BLUEPRINT_PER_TIER = 10, PRICE_RARE_HIRE = 30;

    // ---- bulletin board rewards
    public static int QUEST_BRING_MARKS_BASE = 3, QUEST_BRING_MARKS_DIV = 4;
    public static int QUEST_BRING_RENOWN_BASE = 12, QUEST_BRING_RENOWN_DIV = 2;
    public static int QUEST_KILL_MARKS_BASE = 6, QUEST_KILL_RENOWN_BASE = 20, QUEST_KILL_RENOWN_PER_MOB = 4;

    // ---- mood thoughts: value (and duration in ticks where it is shared by several callers)
    public static int MOOD_FESTIVAL = 12, MOOD_TASTY_MEAL = 8, MOOD_TAVERN_EVENING = 8, MOOD_TAVERN_MEAL = 6;
    public static int MOOD_STREET = 6, MOOD_FRIEND = 5, MOOD_QUARREL = -6, MOOD_KNIGHTED = 10, MOOD_PROUD = 6;
    public static int MOOD_COMRADES = 4, MOOD_PATCHED = 3, MOOD_HELD_LINE = 10, MOOD_RAID_LOSSES = -10;

    // ---- wave I1: builder pace (ticks standing at a block before it is placed, minus a percent bonus per level above 1)
    public static int BUILD_DELAY_TICKS = 26, BUILD_SKILL_PERCENT_PER_LEVEL = 8;

    // ---- wave I1: mining (torch spacing in dug steps, vein size cap, break pace and skill bonus, work-time XP)
    public static int MINE_TORCH_EVERY_SPIRAL = 6, MINE_TORCH_EVERY_TUNNEL = 8, MINE_VEIN_CAP = 32;
    /** Settlers need this percent of a player's time per block (swings, repositioning), minus a bonus per level above 1. */
    public static int BREAK_PACE_PERCENT = 150, BREAK_SKILL_PERCENT_PER_LEVEL = 4;
    /** XP curve for every profession: cumulative XP for levels 1..5. One point per XP_WORK_TICKS ticks of active work. */
    public static int[] LEVEL_XP = {0, 100, 300, 600, 950};
    public static int XP_WORK_TICKS = 110, XP_ACTION_PERCENT = 10;

    // ---- wave K2: the civilian workday, the end-of-shift unload and the miner's pace
    /** The workday starts at sunrise (tick 0 = 06:00, when undead begin to burn) and lasts this percent of the 12000 daylight ticks (75 = until ~15:00). */
    public static int WORKDAY_START_TICK = 0, WORKDAY_DAYLIGHT_PERCENT = 75;
    /** At the end of the shift a settler keeps this many food items (plus tools, weapons and armour) and deposits everything else. */
    public static int SHIFT_END_KEEP_FOOD = 2;
    /** The miner needs this percent of the usual time per block at level 1 and reaches the usual pace at {@code MINER_FULL_PACE_LEVEL}. */
    public static int MINER_SLOW_PERCENT_AT_LEVEL1 = 200, MINER_FULL_PACE_LEVEL = 5;

    /** Length of the civilian workday in ticks. */
    public static int workdayTicks() { return 12000 * WORKDAY_DAYLIGHT_PERCENT / 100; }

    /** Whether the time of day {@code t} (0..23999) falls into the civilian workday. */
    public static boolean inWorkday(long t) { return Math.floorMod(t - WORKDAY_START_TICK, 24000L) < workdayTicks(); }

    // ---- wave I2: combat settlers move like a player (blocks/second x10), damage comes from the held weapon
    /** Walking and chasing speed in blocks/second x10 (player: walk 4.3, sprint 5.6). A mob's drive is both its speed and its forward input, so blocks/second = {@code COMBAT_BPS_PER_ATTR_SQ} x attribute squared (measured: 44, e.g. 0.1 gives 0.44 b/s). */
    public static int COMBAT_WALK_BPS_X10 = 43, COMBAT_SPRINT_BPS_X10 = 56, COMBAT_BPS_PER_ATTR_SQ = 44;
    /** Bonus on top of the weapon (bare hand = 1): percent of a point per level above 1 and per Strength point; knights get a flat extra. */
    public static int COMBAT_DMG_PER_LEVEL_PCT = 15, COMBAT_DMG_PER_STRENGTH_PCT = 6, KNIGHT_DMG_BONUS_PCT = 100;
    /** Shortest pause between two blows in ticks (the weapon's own cooldown is 20 / attack speed). */
    public static int ATTACK_INTERVAL_MIN_TICKS = 10;
    /** Re-fetch pause in ticks when a fighter holds no weapon. */
    public static int WEAPON_RETRY_TICKS = 600;

    // ---- wave I2: archers (draw time, arrow stocks, bonus per level in percent of a point of arrow base damage)
    public static int ARCHER_DRAW_TICKS = 20, ARCHER_DMG_PER_LEVEL_PCT = 5, ARROWS_LOW = 12, ARROWS_FETCH = 48;

    // ---- wave I2: loot carried home (extra search radius around the post, ticks a drop stays claimable)
    public static int LOOT_EXTRA_RADIUS = 4, LOOT_CLAIM_TICKS = 6000;
}
