package at.koopro.wizardsandbeasts.skill.build.capstone;

import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Explorer capstone, {@code seasoned_traveller}: Apparition as a way of life.
 *
 * <ul>
 *   <li>Every splinching lands one rung lighter on the ladder — a minor splinch becomes a clean arrival, a
 *       catastrophic one still arrives (as a major). The release still has to be timed; a traveller who misses
 *       badly still gets hurt, just less.</li>
 *   <li>{@link #EXTRA_DESTINATIONS} more memorised destinations, and the one lift of the twelve-destination
 *       ceiling ({@code PlayerApparitionPoints.SEASONED_CEILING}); ordinary nodes still stop at twelve.</li>
 * </ul>
 *
 * <p>Wired at {@code ApparitionServerLogic.completeAttempt} (after the under-fire floor) and
 * {@code ApparitionAnchors.capacity} and {@code ApparitionAnchors.ceiling}.
 */
@NullMarked
public final class SeasonedTraveller {

    public static final String ABILITY = "seasoned_traveller";
    public static final int EXTRA_DESTINATIONS = 4;

    private SeasonedTraveller() {}

    public static boolean has(ServerPlayer player) {
        return SkillSystemAPI.hasAbility(player, ABILITY);
    }

    /** The tier a seasoned traveller lands at. */
    public static SplinchTier soften(SplinchTier tier, boolean seasoned) {
        if (!seasoned) {
            return tier;
        }
        return switch (tier) {
            case CLEAN, MINOR -> SplinchTier.CLEAN;
            case MAJOR -> SplinchTier.MINOR;
            case CATASTROPHIC -> SplinchTier.MAJOR;
        };
    }
}
