package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.core.Professions;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Wave L1: the promotion ladder of the combat professions, in one table. A level-5 settler pays {@code Balance.PROMOTE_MARKS} hearth marks (the same for
 * every rank) and becomes the next rank:
 * <ul>
 *   <li>guard to knight (also needs a set of iron armour from the warehouse and gets a shield);</li>
 *   <li>archer to crossbowman (may then be given a crossbow, which he also fetches from the warehouse like any tool);</li>
 *   <li>warrior to cavalryman (rides a horse of the settlement with a saddle and charges with a spear).</li>
 * </ul>
 * {@code key} names the texts: {@code gui.hearthholm.promote.<key>}, {@code message.hearthholm.promoted.<key>}, {@code gui.hearthholm.promote_tip.<key>}.
 */
public final class Promotions {
    private Promotions() {}

    public record Promotion(Identifier from, Identifier to, String key, boolean needsArmor) {}

    /** The promotion available to a settler of this profession, or null when there is none. */
    public static @Nullable Promotion of(Identifier from) {
        return switch (from.getPath()) {
            case "guard" -> new Promotion(from, Professions.KNIGHT_ID, "knight", true);
            case "archer" -> new Promotion(from, Professions.CROSSBOWMAN_ID, "crossbowman", false);
            case "warrior" -> new Promotion(from, Professions.CAVALRYMAN_ID, "cavalryman", false);
            default -> null;
        };
    }
}
