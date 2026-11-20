package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.core.Balance;
import net.minecraft.resources.Identifier;

/**
 * Wave L1: what makes the combat professions different, in one table. Reads {@link Balance} on every call (so the config and tests can change the
 * numbers) and is the only place that maps a profession to its role; {@code SettlerEntity.refreshCombatStats}, the distress signal and the
 * patrol code ask here instead of comparing profession ids. A new combat profession = one more case.
 *
 * <ul>
 *   <li>guard (and the knight he becomes): the sturdiest (100% health), 20% slower than a warrior, answers a call for help from the whole settlement;</li>
 *   <li>warrior (and the cavalryman): 90% of the guard's health, the baseline speed, patrols a territory, helps only close by;</li>
 *   <li>archer (and the crossbowman): 90% of the warrior's health (81%), the baseline speed, shoots from a distance and strafes.</li>
 * </ul>
 */
public final class CombatRoles {
    private CombatRoles() {}

    /** @param hpPercent percent of the reference (guard) health; @param speedPercent percent of the I2 walking and chasing speed;
     *  @param distressRadius how far away the settler answers "a resident is attacked" (0 = never) */
    public record Role(int hpPercent, int speedPercent, int distressRadius) {
        public static final Role NONE = new Role(100, 100, 0);
    }

    public static Role of(Identifier professionId) {
        return switch (professionId.getPath()) {
            case "guard", "knight" -> new Role(Balance.ROLE_HP_GUARD, Balance.ROLE_SPEED_GUARD, Balance.DISTRESS_RADIUS_GUARD);
            case "warrior", "cavalryman" -> new Role(Balance.ROLE_HP_WARRIOR, Balance.ROLE_SPEED_WARRIOR, Balance.DISTRESS_RADIUS_OTHER);
            case "archer", "crossbowman" -> new Role(Balance.ROLE_HP_ARCHER, Balance.ROLE_SPEED_ARCHER, Balance.DISTRESS_RADIUS_OTHER);
            default -> Role.NONE;
        };
    }
}
