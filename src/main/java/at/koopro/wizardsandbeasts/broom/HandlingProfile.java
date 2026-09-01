package at.koopro.wizardsandbeasts.broom;

import java.util.Locale;
import java.util.Optional;

/**
 * A named bundle of handling defaults — what a broom feels like before anyone tunes it.
 *
 * <p>A profile is <em>not</em> a fifth set of physics. It supplies the starting value for each field
 * in {@link BroomHandling}, and any key the JSON authors explicitly wins over it. That is what keeps
 * the field from being decorative: an enum nothing reads would be exactly the {@code wood_tint}
 * failure again, a value authored into every file and consulted by nothing.
 *
 * <p>{@link #BALANCED} is the default, and it is deliberately the behaviour the broom already had
 * before any of these fields existed — momentum, crash damage and durability loss all reproduce the
 * old hardcoded constants. A definition that authors no handling keys at all therefore flies exactly
 * as it did, which is the whole point of the fields being optional.
 *
 * <p>The one exception is {@link BroomHandling#yawDrift()}, which is {@code 0.02} on BALANCED rather
 * than zero. That is the brief's stated default and it is a real, small change: every broom now
 * weaves by about half a degree at top speed where it used to track a perfect line. Authored as a
 * deviation rather than silently rounded to zero, because a broom that holds an exact heading at
 * full speed is the thing the field exists to stop.
 */
public enum HandlingProfile {

    /**
     * Training brooms: wanders off line, bleeds speed, wobbles under boost, barely punches the FOV —
     * and is the gentlest thing in the game to crash. The crash multiplier was 1.15 on the reasoning
     * that a cheap broom is a flimsy one; that is backwards for the broom students are handed. A
     * school broom is built to survive being flown badly, which is the entire point of it.
     */
    SCHOOL(0.06f, 0.86f, 0.35f, 0.05f, 0.85f, 1, 4),

    /** The default. Reproduces the pre-profile broom, bar the half-degree of drift. */
    BALANCED(0.02f, 0.90f, 0.00f, 0.00f, 1.00f, 1, 3),

    /** Racing brooms: holds a line, keeps its momentum, steady under boost, big FOV punch. */
    RACING(0.008f, 0.94f, 0.02f, 0.14f, 1.35f, 1, 8),

    /**
     * Heavy antiques: ponderous but hard to shift once moving, and they shrug off a knock.
     *
     * <p>Glancing knocks, specifically — {@code TankHandling} halves the durability those cost. A
     * real crash lands in full, which is why this multiplier is neutral rather than the 0.75 it
     * started at: a broom nothing can hurt is a broom with no reason to fly carefully.
     */
    TANK(0.03f, 0.97f, 0.08f, 0.04f, 1.0f, 1, 2);

    private final float yawDrift;
    private final float momentumRetention;
    private final float wobbleAtBoost;
    private final float boostFovPunch;
    private final float crashDamageMultiplier;
    private final int minorImpactDurabilityLoss;
    private final int severeImpactDurabilityLoss;

    HandlingProfile(float yawDrift, float momentumRetention, float wobbleAtBoost, float boostFovPunch,
                    float crashDamageMultiplier, int minorImpactDurabilityLoss,
                    int severeImpactDurabilityLoss) {
        this.yawDrift = yawDrift;
        this.momentumRetention = momentumRetention;
        this.wobbleAtBoost = wobbleAtBoost;
        this.boostFovPunch = boostFovPunch;
        this.crashDamageMultiplier = crashDamageMultiplier;
        this.minorImpactDurabilityLoss = minorImpactDurabilityLoss;
        this.severeImpactDurabilityLoss = severeImpactDurabilityLoss;
    }

    public float yawDrift() {
        return yawDrift;
    }

    public float momentumRetention() {
        return momentumRetention;
    }

    public float wobbleAtBoost() {
        return wobbleAtBoost;
    }

    public float boostFovPunch() {
        return boostFovPunch;
    }

    public float crashDamageMultiplier() {
        return crashDamageMultiplier;
    }

    public int minorImpactDurabilityLoss() {
        return minorImpactDurabilityLoss;
    }

    public int severeImpactDurabilityLoss() {
        return severeImpactDurabilityLoss;
    }

    /** The id a datapack writes: lower case, so {@code "racing"} rather than {@code "RACING"}. */
    public String profileId() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resolves a {@code handlingProfile} value, case-insensitively. */
    public static Optional<HandlingProfile> byId(String id) {
        for (HandlingProfile profile : values()) {
            if (profile.profileId().equalsIgnoreCase(id)) return Optional.of(profile);
        }
        return Optional.empty();
    }
}
