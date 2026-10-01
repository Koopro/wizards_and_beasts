package at.koopro.wizardsandbeasts.spell.resistance;

import org.jspecify.annotations.NullMarked;

/**
 * What it takes for an enchantment to take hold on a magic-resistant hide, as pure rules.
 *
 * <p>Canon is consistent about the shape and silent about the numbers: a dragon is "too strong and too magical to be
 * knocked out by a single Stunner — you need about half a dozen wizards at a time" (<i>Goblet of Fire</i> ch. 19);
 * Hagrid, with a giant's blood, stays on his feet under several Stunners at once (<i>Order of the Phoenix</i> ch. 31);
 * trolls and Graphorns shrug off what a wizard throws at them. So a hide does not make a creature immune, and it does
 * not make a spell land by chance: it asks for <em>several spells at once</em>. Enough of them inside a short window
 * and the stun, the bind or the lift takes; fewer and the hide turns each one aside.
 *
 * <p>The hide's strength is the same {@code resist_fraction} a creature's {@code spell_resist} ability already
 * declares for damage, so one authored number describes the whole hide. No dice anywhere.
 */
@NullMarked
public final class MagicResistanceRules {

    /** Spells must land within this many ticks of the first to count as "at once": five seconds. */
    public static final long WINDOW_TICKS = 100L;
    /** The strongest hide these rules take seriously; beyond it a creature would simply be immune. */
    public static final float MAX_HIDE = 0.9f;

    private MagicResistanceRules() {}

    /** Spells landed on one hide inside the current window, and the tick the window opened. */
    public record Strain(int spells, long since) {
        public static final Strain NONE = new Strain(0, 0L);
    }

    /**
     * How many spells must land at once to get through a hide of this strength: one for no hide, two for a troll
     * (0.4) or a half-giant (0.5), three for a Graphorn (0.6), four for a Blast-Ended Skrewt (0.75).
     */
    public static int spellsToOvercome(float hide) {
        if (!(hide > 0.0f)) {
            return 1;
        }
        double h = Math.min(hide, MAX_HIDE);
        // The epsilon keeps an exact 2.0 (hide 0.5) at two rather than rounding float noise up to three.
        return (int) Math.ceil(1.0 / (1.0 - h) - 1.0e-6);
    }

    /** The strain after one more spell lands at {@code now}; a spell after the window closed starts a new one. */
    public static Strain afterSpell(Strain strain, long now) {
        if (strain.spells() <= 0 || now < strain.since() || now - strain.since() > WINDOW_TICKS) {
            return new Strain(1, now);
        }
        return new Strain(strain.spells() + 1, strain.since());
    }

    /** Whether this strain is enough to get a spell through a hide needing {@code needed}. */
    public static boolean overcome(Strain strain, int needed) {
        return strain.spells() >= needed;
    }
}
