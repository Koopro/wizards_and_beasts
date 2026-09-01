package at.koopro.wizardsandbeasts.spell.cast;

import org.jspecify.annotations.NullMarked;

/**
 * The one place a cast's damage and cooldown multipliers are composed.
 *
 * <h2>The formula</h2>
 * <pre>
 *   damage   = baseDamage   * softCap(situational * proficiency * skill)
 *   cooldown = baseCooldown * clamp  (situational * proficiency * skill)
 * </pre>
 * Three named channels, multiplied in that order, then bounded <em>once</em>:
 * <ul>
 *   <li><b>situational</b> — everything the {@link ModifierStack} accumulated for this cast: wand
 *       corruption and allegiance, dark corruption, vocation, Niffler happiness, player stats.</li>
 *   <li><b>proficiency</b> — how well this caster knows this spell, from
 *       {@code ProficiencyScaler}. Gated on {@code Module.PROFICIENCY}; {@code 1.0} when off.</li>
 *   <li><b>skill</b> — the skill web: the category multiplier plus the node's per-spell bonus.</li>
 * </ul>
 *
 * <h2>Why this class exists</h2>
 * <p>The composition used to be written inline at four call sites, and it was wrong in three ways
 * that only a single owner can fix:
 *
 * <ol>
 *   <li><b>Proficiency was counted twice.</b> {@code SkillSystemAPI.applyDamageModifiers} multiplied
 *       in the {@code Proficiency} enum tier (1.0/1.10/1.20) <em>and</em> the caller multiplied in
 *       {@code ProficiencyScaler}'s float curve (0.65–1.50). Both are driven by the same counter,
 *       incremented at the same call site, so one cast paid for the same practice twice — and the
 *       enum channel ignored {@code Module.PROFICIENCY} entirely, so switching the module off still
 *       handed out 1.2x at mastery.</li>
 *   <li><b>The bound was applied to the wrong quantity.</b> {@link ModifierStack} clamped its own
 *       accumulator to [0.25, 3.0] and the caller then multiplied by up to 1.5, so the number that
 *       actually reached the damage call could reach 4.5x — the clamp read as a guarantee and was
 *       not one.</li>
 *   <li><b>Nothing was diminishing.</b> Every channel was a raw product, so each new multiplier
 *       source compounded on the last.</li>
 * </ol>
 *
 * <h2>Soft cap rather than a wall</h2>
 * <p>Damage is compressed above a knee instead of being truncated at a ceiling. Below the knee a
 * multiplier is worth exactly what it says; above it, each further point buys less, approaching the
 * maximum without ever reaching it. That keeps a player's investment always worth something — a
 * hard clamp makes every source past the ceiling worth literally nothing, which reads as a bug —
 * while removing the possibility of an exponential blow-up from stacking sources.
 *
 * <p>Cooldown is clamped rather than soft-capped: its danger is the <em>floor</em> (a spell that
 * comes off cooldown instantly), and a floor is a floor, not a curve.
 *
 * <p>Every method here is pure and deterministic — no player, no level, no config read, no
 * Minecraft type at all — so the formula is unit-testable and the client can compute the exact
 * number the server will use, which is what lets a tooltip promise a multiplier without lying.
 * Bounds are pushed in by {@code Config} through {@link #applyBounds}; this class never reaches for
 * them, so nothing here can drag a mod environment into a caller that only wants arithmetic.
 */
@NullMarked
public final class SpellPower {

    /**
     * Where compression starts, where it ends, and the floors.
     *
     * @param softCapKnee  multiplier below which no compression happens at all
     * @param max          asymptotic damage ceiling; the composed value approaches but never reaches it
     * @param min          hard damage floor
     * @param cooldownMin  hard cooldown floor (lower = faster); the anti-spam guard
     * @param cooldownMax  hard cooldown ceiling, so a penalty stack cannot lock a spell away
     */
    public record Bounds(float softCapKnee, float max, float min, float cooldownMin, float cooldownMax) {

        public Bounds {
            if (!(softCapKnee > 0.0f) || !(max > softCapKnee)) {
                throw new IllegalArgumentException("need 0 < softCapKnee < max, got " + softCapKnee + " / " + max);
            }
            if (!(min > 0.0f) || min > softCapKnee) {
                throw new IllegalArgumentException("need 0 < min <= softCapKnee, got " + min);
            }
            if (!(cooldownMin > 0.0f) || cooldownMax < cooldownMin) {
                throw new IllegalArgumentException("need 0 < cooldownMin <= cooldownMax");
            }
        }
    }

    /** The shipped tuning. Tests pass their own bounds rather than reading config. */
    public static final Bounds DEFAULT_BOUNDS = new Bounds(1.5f, 3.0f, 0.25f, 0.25f, 2.0f);

    /**
     * One cast's damage multiplier, channel by channel.
     *
     * <p>Carried as a record rather than a bare float because the tooltip has to show the player
     * <em>why</em> a number is what it is, and because {@link #clamped()} is the difference between
     * "your next node is worth +10%" and "your next node is worth nothing".
     */
    public record Breakdown(float situational, float proficiency, float skill, float raw, float total) {

        /** True when the soft cap or a bound actually moved the number. */
        public boolean clamped() {
            return Math.abs(raw - total) > 1.0e-4f;
        }

        /** Percentage points the player would see, e.g. {@code 1.25f} renders as {@code +25%}. */
        public int percent() {
            return Math.round((total - 1.0f) * 100.0f);
        }
    }

    private SpellPower() {}

    // -- damage ----------------------------------------------------------------------------------

    /**
     * Compose the three damage channels and bound the result.
     *
     * <p>Order is fixed and multiplication is commutative, so the order is a documentation choice
     * rather than a numerical one — but it is the order the breakdown reports and the order the
     * tests lock, so changing it changes what the tooltip claims.
     */
    public static Breakdown damage(float situational, float proficiency, float skill, Bounds bounds) {
        float raw = nonNegative(situational) * nonNegative(proficiency) * nonNegative(skill);
        return new Breakdown(situational, proficiency, skill, raw, boundDamage(raw, bounds));
    }

    public static Breakdown damage(float situational, float proficiency, float skill) {
        return damage(situational, proficiency, skill, configuredBounds());
    }

    /**
     * Apply the floor and the soft cap to an already-composed multiplier.
     *
     * <p>Below the knee this is the identity. Above it the surplus decays exponentially toward
     * {@code max}: at the knee the curve is continuous and its slope is 1, so there is no visible
     * kink where compression starts.
     */
    public static float boundDamage(float raw, Bounds bounds) {
        float floored = Math.max(bounds.min(), nonNegative(raw));
        if (floored <= bounds.softCapKnee()) {
            return floored;
        }
        float headroom = bounds.max() - bounds.softCapKnee();
        float surplus = floored - bounds.softCapKnee();
        return bounds.max() - headroom * (float) Math.exp(-surplus / headroom);
    }

    // -- cooldown --------------------------------------------------------------------------------

    /**
     * Compose the three cooldown channels and clamp. Lower is faster, so {@code cooldownMin} is the
     * value that matters: it is what stops a fully-invested caster from having no cooldown at all.
     */
    public static Breakdown cooldown(float situational, float proficiency, float skill, Bounds bounds) {
        float raw = nonNegative(situational) * nonNegative(proficiency) * nonNegative(skill);
        float total = Math.max(bounds.cooldownMin(), Math.min(bounds.cooldownMax(), raw));
        return new Breakdown(situational, proficiency, skill, raw, total);
    }

    public static Breakdown cooldown(float situational, float proficiency, float skill) {
        return cooldown(situational, proficiency, skill, configuredBounds());
    }

    // -- active bounds ---------------------------------------------------------------------------

    /**
     * Bounds in force right now. Pushed in by {@code Config} on load rather than pulled out of it.
     *
     * <p>The direction matters. Reading {@code Config} from here would make every caller of this
     * class — including a unit test that only wants to check arithmetic — trigger {@code Config}'s
     * static initializer, which builds the entire {@code ModConfigSpec} and needs a live mod
     * environment to do it. That coupling took the test JVM down with an initializer error before
     * the dependency was inverted. This class now depends on nothing.
     */
    private static volatile Bounds active = DEFAULT_BOUNDS;

    /**
     * Install new bounds. Called once from the config-load handler; a tuning the {@link Bounds}
     * constructor rejects leaves the previous values in place rather than throwing inside a cast,
     * because config is hand-edited and a typo must not break casting.
     */
    public static void applyBounds(float softCapKnee, float max, float min,
                                   float cooldownMin, float cooldownMax) {
        try {
            active = new Bounds(softCapKnee, max, min, cooldownMin, cooldownMax);
        } catch (IllegalArgumentException badTuning) {
            active = DEFAULT_BOUNDS;
        }
    }

    /** The bounds a cast will actually use. */
    public static Bounds configuredBounds() {
        return active;
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 0.0f;
    }
}
