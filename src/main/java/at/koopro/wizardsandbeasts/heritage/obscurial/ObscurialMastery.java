package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * How far an Obscurial has learned to live with what is in them, and which way they went about it. Pure, so every
 * number here unit-tests without a world.
 *
 * <p><b>Mastery</b> is one 0..100 score, kept beside the other meters as the {@code obscurial_mastery} flag. It is
 * earned only by the hard thing: venting when the strain is real ({@link #VENT_MIN_STRESS}), and holding human form
 * while agitated. It is lost in a collapse. Both sources are rate-limited by things the player cannot skip — the vent
 * cooldown, and stress that has to be genuinely high — so there is no idle loop that fills it.
 *
 * <p><b>The path is the origin</b>, chosen when the Obscurus came about and never changed, so there is no state in
 * which a character is on both paths or neither (Heritage 2.0: a hybrid, safer controlled path and a riskier
 * higher-output one):
 * <ul>
 *   <li>{@link Path#CONTROLLED} ({@link ConditionOrigin#SUPPRESSED}) — mastery makes the dark form last longer, makes
 *       the Obscurus harder to tear loose, and softens a collapse. Surge and Grasp stay as they are.</li>
 *   <li>{@link Path#UNLEASHED} ({@link ConditionOrigin#UNLEASHED}) — mastery makes Surge and Grasp cheaper and gives
 *       Grasp teeth, but stress always builds faster, and a collapse is longer and wounds the host.</li>
 * </ul>
 * Grasp asks a little mastery on either path ({@link #GRASP_MASTERY}), in place of the Imperius proficiency it used to
 * ask, which an Obscurial — who cannot cast — could never have.
 */
@NullMarked
public final class ObscurialMastery {

    /** The flag mastery is kept under. Starts with {@code obscurial_}, so a cure clears it with the other meters. */
    public static final String FLAG = "obscurial_mastery";
    public static final float MAX = 100f;

    /** Earned by a vent taken while agitated or worse. With the 16 s cooldown, at most 7.5 a minute. */
    public static final float VENT_GAIN = 2.0f;
    /** Below this stress a vent is relief, not discipline, and earns nothing. The AGITATED line. */
    public static final float VENT_MIN_STRESS = 40f;
    /** Earned per tick held in human form while agitated or worse: 2.4 a minute. */
    public static final float HELD_GAIN_PER_TICK = 0.002f;
    /** Lost when the Obscurus collapses out of the host. */
    public static final float COLLAPSE_LOSS = 6.0f;
    /** Mastery Grasp asks, on either path. */
    public static final float GRASP_MASTERY = 20f;

    /** Which way an Obscurial went about it. */
    public enum Path {
        CONTROLLED("controlled"),
        UNLEASHED("unleashed");

        private final String id;

        Path(String id) {
            this.id = id;
        }

        public String translationKey() {
            return "obscurial." + WizardsAndBeastsMod.MODID + ".path." + id;
        }
    }

    private ObscurialMastery() {}

    /** The path an origin sets; {@code null} for anything that is not an Obscurus. */
    public static @Nullable Path path(@Nullable ConditionOrigin origin) {
        if (origin == ConditionOrigin.SUPPRESSED) {
            return Path.CONTROLLED;
        }
        if (origin == ConditionOrigin.UNLEASHED) {
            return Path.UNLEASHED;
        }
        return null;
    }

    public static float clamp(float mastery) {
        return Math.max(0f, Math.min(MAX, mastery));
    }

    private static float fraction(float mastery) {
        return clamp(mastery) / MAX;
    }

    /** Multiplies the dark form's per-tick drain on Control. Controlled mastery stretches the form by up to a third. */
    public static float darkDrainMultiplier(Path path, float mastery) {
        return path == Path.CONTROLLED ? 1f - 0.35f * fraction(mastery) : 1f;
    }

    /** Multiplies every stress gain. The price of the unleashed path, paid at any mastery. */
    public static float stressGainMultiplier(Path path) {
        return path == Path.UNLEASHED ? 1.25f : 1f;
    }

    /** Multiplies a collapse's lockout and its weakness and slowness. */
    public static float collapseMultiplier(Path path, float mastery) {
        return path == Path.CONTROLLED ? 1f - 0.5f * fraction(mastery) : 1.25f;
    }

    /** Damage a collapse does to its host. */
    public static float collapseDamage(Path path) {
        return path == Path.UNLEASHED ? 4.0f : 0f;
    }

    /** The health ratio at or under which the Obscurus tears itself loose. Controlled mastery holds it down longer. */
    public static float forcedBreakThreshold(Path path, float mastery, float base) {
        return path == Path.CONTROLLED ? base - 0.15f * fraction(mastery) : base;
    }

    /** Multiplies what Surge and Grasp cost in Control, Pressure and stress. */
    public static float abilityCostMultiplier(Path path, float mastery) {
        return path == Path.UNLEASHED ? 1f - 0.3f * fraction(mastery) : 1f;
    }

    /** Damage Grasp does to each creature it pulls: none on the controlled path, and none until the Obscurus is held. */
    public static float graspDamage(Path path, float mastery) {
        if (path != Path.UNLEASHED || clamp(mastery) < GRASP_MASTERY) {
            return 0f;
        }
        return 2.0f + 4.0f * fraction(mastery);
    }

    /** Mastery an ability asks before it answers. */
    public static float required(ObscurialAbility ability) {
        return ability == ObscurialAbility.GRASP ? GRASP_MASTERY : 0f;
    }
}
