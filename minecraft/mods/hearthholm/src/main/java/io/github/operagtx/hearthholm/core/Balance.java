package io.github.operagtx.hearthholm.core;

/**
 * Wave F: every tunable number of the game economy in one place (see DESIGN.md "Баланс" for the reasoning).
 * Plain constants on purpose: change them here and rebuild. Raid timing lives in {@code ThreatConfig} (config file).
 */
public final class Balance {
    private Balance() {}

    // ---- settlement growth: renown needed per tier (camp, hamlet, village, town, city), residents and radius per tier
    public static final int[] TIER_RENOWN = {0, 60, 300, 1000, 3000};
    public static final int[] TIER_POP = {2, 5, 10, 16, 24};
    public static final int[] TIER_RADIUS = {40, 56, 72, 88, 104};

    // ---- hiring and festival (paid in nutrition points of food)
    public static final int HIRE_BASE = 10, HIRE_PER_RESIDENT = 5;
    public static final int FESTIVAL_COST_PER_RESIDENT = 4, FESTIVAL_RENOWN_BASE = 10;

    // ---- raids: strength = base + renown/RENOWN_DIV + stacks/STACKS_DIV + residents/POP_DIV, capped per tier
    public static final int RAID_BASE = 3, RAID_RENOWN_DIV = 250, RAID_STACKS_DIV = 10, RAID_POP_DIV = 2;
    public static final int[] RAID_CAP_BY_TIER = {3, 5, 8, 14, 24};
    public static final int RAID_WIN_RENOWN_BASE = 20, RAID_WIN_RENOWN_PER_STRENGTH = 5, RAID_WIN_MARKS_BASE = 2;

    // ---- caravan prices in hearth marks
    public static final int PRICE_SEEDS = 2, PRICE_SEEDS_RARE = 3, PRICE_SAPLING = 5;
    public static final int PRICE_BLUEPRINT_BASE = 15, PRICE_BLUEPRINT_PER_TIER = 10, PRICE_RARE_HIRE = 30;

    // ---- bulletin board rewards
    public static final int QUEST_BRING_MARKS_BASE = 3, QUEST_BRING_MARKS_DIV = 4;
    public static final int QUEST_BRING_RENOWN_BASE = 12, QUEST_BRING_RENOWN_DIV = 2;
    public static final int QUEST_KILL_MARKS_BASE = 6, QUEST_KILL_RENOWN_BASE = 20, QUEST_KILL_RENOWN_PER_MOB = 4;

    // ---- mood thoughts: value (and duration in ticks where it is shared by several callers)
    public static final int MOOD_FESTIVAL = 12, MOOD_TASTY_MEAL = 8, MOOD_TAVERN_EVENING = 8, MOOD_TAVERN_MEAL = 6;
    public static final int MOOD_STREET = 6, MOOD_FRIEND = 5, MOOD_QUARREL = -6, MOOD_KNIGHTED = 10, MOOD_PROUD = 6;
    public static final int MOOD_COMRADES = 4, MOOD_PATCHED = 3, MOOD_HELD_LINE = 10, MOOD_RAID_LOSSES = -10;
}
