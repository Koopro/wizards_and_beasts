package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence;
import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import org.jspecify.annotations.NullMarked;

/**
 * The tunable rules of Apparition, in one place: the values the server reads, published from the world's
 * {@link ApparitionRulesData} at start and after every change (Control Center → Travel → Apparition).
 *
 * <h2>Why world data and not config keys</h2>
 *
 * <p>Apparition's three {@code Config} keys were deliberately deleted at {@code 0.1.0-alpha.1}: the rules of travel
 * belong to the world, edited by its operators, not to a file every server copies. The admin framework is that
 * surface. Each value defaults to the constant it replaced, so a world nobody has tuned behaves exactly as before.
 *
 * <p>Server-only state: every reader runs on the server ({@code ApparitionServerLogic}, {@code ApparitionLicence}).
 * Nothing here is synced; a client never decides an Apparition outcome.
 */
@NullMarked
public final class ApparitionRules {

    /**
     * One world's Apparition rules.
     *
     * @param windupDamageMode       how being hit mid-wind-up is treated
     * @param blinkCooldownTicks     cooldown after a line-of-sight jump
     * @param anchoredCooldownTicks  cooldown after a jump to a remembered destination
     * @param splinchSeverityPercent scale on the inflated miss before the splinch ladder (100 = as authored, 0 = never)
     * @param licenceProficiencyPercent practice needed before the Ministry's test (25 = as authored)
     */
    public record Tuning(WindupDamageMode windupDamageMode, int blinkCooldownTicks, int anchoredCooldownTicks,
                         int splinchSeverityPercent, int licenceProficiencyPercent) {

        public static final Tuning AUTHORED = new Tuning(WindupDamageMode.HYBRID,
                ApparitionTier.BLINK.cooldownTicks(), ApparitionTier.ANCHORED.cooldownTicks(), 100,
                Math.round(ApparitionLicence.REQUIRED_PROFICIENCY * 100f));
    }

    public static final int MAX_COOLDOWN_TICKS = 24_000;
    public static final int MAX_SPLINCH_SEVERITY_PERCENT = 300;

    private static volatile Tuning current = Tuning.AUTHORED;

    private ApparitionRules() {}

    /** Replaces the rules the server reads. Called by {@code ApparitionRulesService} only. */
    public static void publish(Tuning tuning) {
        current = tuning;
    }

    public static Tuning current() {
        return current;
    }

    /** How being hit mid-wind-up is treated. */
    public static WindupDamageMode windupDamageMode() {
        return current.windupDamageMode();
    }

    /** Cooldown after a completed jump of {@code tier} (before a splinch lockout, which can only lengthen it). */
    public static int cooldownTicks(ApparitionTier tier) {
        return tier == ApparitionTier.BLINK ? current.blinkCooldownTicks() : current.anchoredCooldownTicks();
    }

    /**
     * Scales an already-inflated miss by the world's splinch severity. The forced-discharge sentinel passes through
     * untouched (it is already the worst rung, and scaling it would overflow), and so does every miss at 100%.
     */
    public static int scaleMiss(int inflatedMissTicks) {
        int percent = current.splinchSeverityPercent();
        if (percent == 100 || ApparitionWindow.isForcedDischarge(inflatedMissTicks)) {
            return inflatedMissTicks;
        }
        return Math.round(inflatedMissTicks * (percent / 100f));
    }

    /** Practice a wizard needs before the licence test, 0–1. */
    public static float requiredLicenceProficiency() {
        return current.licenceProficiencyPercent() / 100f;
    }
}
