package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.NullMarked;

/**
 * How badly a blood-drinker needs to feed, as four named bands over one float.
 *
 * <p>Bands rather than a raw percentage everywhere because three separate things need to agree about
 * "low": the debuffs the server applies, the colour the HUD draws, and the message the player is told.
 * Each deriving its own threshold from the percentage is how a bar turns red one frame before the
 * penalty lands.
 *
 * <p>The floors themselves live in {@link VampireBloodConfig}, not here, so a server can move them; the
 * shipped shape is comfortable for the top third (70%), uncomfortable through the middle (40%) and
 * dangerous at the bottom (15%). The only thing each constant carries is the colour its band draws in,
 * which is not a server's business — see {@code NutritionBarTheme}.
 */
@NullMarked
public enum ThirstStage {

    /** Fed. Nothing is wrong and nothing is applied. */
    SATED(0xFFC8324B),

    /** The edge of appetite: told about, not yet punished. */
    THIRSTY(0xFFB4283F),

    /** Weak, and healing badly — the mirrored food level has already stopped natural regeneration. */
    PARCHED(0xFF8E1C30),

    /** Dying of it. Heavy debuffs, and attrition damage once the pool is all but empty. */
    STARVING(0xFF5C1220);

    private final int barColour;

    ThirstStage(int barColour) {
        this.barColour = barColour;
    }

    /** ARGB the blood meter fills with while in this band. Darker and duller as the pool empties. */
    public int barColour() {
        return barColour;
    }

    public String translationKey() {
        return "thirst." + WizardsAndBeastsMod.MODID + "." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The band a blood fraction falls in, using the configured floors.
     *
     * <p>Walks in declaration order, highest floor first, so a mis-ordered config (a THIRSTY floor above
     * the SATED one, say) degrades to "the first band that accepts it" rather than to an unreachable band.
     */
    public static ThirstStage of(float percent) {
        if (percent >= VampireBloodConfig.satedFloor) {
            return SATED;
        }
        if (percent >= VampireBloodConfig.thirstyFloor) {
            return THIRSTY;
        }
        if (percent >= VampireBloodConfig.parchedFloor) {
            return PARCHED;
        }
        return STARVING;
    }
}
