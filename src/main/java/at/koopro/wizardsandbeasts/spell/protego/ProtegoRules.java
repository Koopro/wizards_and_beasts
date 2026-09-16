package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;

/**
 * Every number behind the Shield Charm, and nothing that needs a running game.
 *
 * <p>Pure on purpose: this class holds the tier ladder, the integrity model and the geometry the
 * shield uses to decide what it stops, so all of it can be unit-tested. It reads no config — see
 * {@code tasks/lessons.md}, "reading Config from a pure class kills the test JVM".
 *
 * <h2>How a tier is chosen</h2>
 * One spell, four shapes. The wand hold picks the shape the caster is <em>reaching</em> for,
 * proficiency puts a ceiling on how far they can reach (and how fast they get there), and Dark Arts
 * study is what lets anyone hold the last one at all.
 */
public final class ProtegoRules {

    /** Unlocked Dark Arts nodes needed before Horribilis can be raised at all. */
    public static final int HORRIBILIS_DARK_ARTS_DEPTH = 3;

    /** Even a harmless bolt costs the ward something to turn aside. */
    public static final float MIN_SPELL_COST = 3.0f;
    /** What a Dark bolt costs a shield that swallows it whole (Horribilis). */
    public static final float DARK_ABSORB_FACTOR = 0.35f;
    /** What a Dark bolt costs every other tier — Dark magic leans harder on an ordinary ward. */
    public static final float DARK_PRESSURE_FACTOR = 1.25f;

    public static final float PLANTED_INTEGRITY_FACTOR = 1.35f;
    public static final float PLANTED_LIFETIME_FACTOR = 1.5f;

    /** Bounds on how far the cast's other multipliers (wand, skills, corruption) may move integrity. */
    public static final float MIN_POWER_FACTOR = 0.5f;
    public static final float MAX_POWER_FACTOR = 2.0f;

    /** Below this fraction of its pool the shield starts to sound and look like it is going. */
    public static final float LOW_INTEGRITY_FRACTION = 0.3f;

    /** A planted dome stands on its own, but not once its caster is this far away. */
    public static final double PLANT_LEASH_BLOCKS = 48.0;

    /**
     * How far off the caster's aim an attack may arrive and still meet the T0 disc. Slightly wider
     * than a hemisphere (dot 0) so a hit from exactly beside the caster still clips the edge, while
     * anything from behind goes straight through.
     */
    public static final double FRONTAL_MIN_DOT = -0.1;

    /** Magic damage Horribilis deals its own caster when it is broken rather than allowed to fade. */
    public static final float HORRIBILIS_BACKLASH_DAMAGE = 4.0f;

    private ProtegoRules() {}

    // ── tier selection ──────────────────────────────────────────────────────────────────────────

    /** Why the tier the caster reached for is not the tier they got. */
    public enum Limit {
        /** They got what they held for. */
        NONE,
        /** They have not practised Protego far enough to hold that shape. */
        PROFICIENCY,
        /** Horribilis: they have not studied enough of the Dark Arts to ward against them. */
        DARK_ARTS
    }

    /**
     * @param tier          what will actually be raised
     * @param reachedByHold what the hold alone was long enough for
     * @param limit         why the two differ, for the charge-time hint
     */
    public record TierResolution(ProtegoTier tier, ProtegoTier reachedByHold, Limit limit) {

        public boolean capped() {
            return limit != Limit.NONE;
        }
    }

    /** Practice makes the wand answer faster: a mastered caster reaches each shape in 70% of the ticks. */
    public static float chargeSpeed(Proficiency proficiency) {
        return switch (proficiency) {
            case NOVICE -> 1.0f;
            case PROFICIENT -> 0.85f;
            case MASTERED -> 0.7f;
        };
    }

    /** Hold ticks this caster needs before {@code tier} is available. */
    public static int chargeTicks(ProtegoTier tier, Proficiency proficiency) {
        return Math.round(tier.baseChargeTicks() * chargeSpeed(proficiency));
    }

    /** The strongest shape this caster is allowed to hold, ignoring the hold time. */
    public static ProtegoTier proficiencyCeiling(Proficiency proficiency) {
        return switch (proficiency) {
            case NOVICE -> ProtegoTier.TOTALUM;
            case PROFICIENT -> ProtegoTier.MAXIMA;
            case MASTERED -> ProtegoTier.HORRIBILIS;
        };
    }

    /** The strongest shape the hold time alone reaches, ignoring every gate. */
    public static ProtegoTier tierReachedByHold(int heldTicks, Proficiency proficiency) {
        ProtegoTier reached = ProtegoTier.PROTEGO;
        for (ProtegoTier tier : ProtegoTier.values()) {
            if (heldTicks >= chargeTicks(tier, proficiency)) {
                reached = tier;
            }
        }
        return reached;
    }

    /**
     * Hold time, then the proficiency ceiling, then the Dark Arts gate. Order matters for the hint:
     * a novice who holds forever is told to practise Protego, not to go and study the Dark Arts.
     */
    public static TierResolution resolveTier(int heldTicks, Proficiency proficiency, int darkArtsDepth) {
        ProtegoTier reached = tierReachedByHold(Math.max(0, heldTicks), proficiency);
        ProtegoTier tier = reached;
        Limit limit = Limit.NONE;

        ProtegoTier ceiling = proficiencyCeiling(proficiency);
        if (tier.index() > ceiling.index()) {
            tier = ceiling;
            limit = Limit.PROFICIENCY;
        }
        if (tier == ProtegoTier.HORRIBILIS && darkArtsDepth < HORRIBILIS_DARK_ARTS_DEPTH) {
            tier = ProtegoTier.MAXIMA;
            limit = Limit.DARK_ARTS;
        }
        return new TierResolution(tier, reached, limit);
    }

    /**
     * How far the hold has come towards the <em>next</em> shape, 0 to 1.
     *
     * <p>Drives everything continuous in the charge feedback — the hum's pitch, the vignette's
     * strength — so the climb reads as a climb between the chimes rather than four silent steps.
     * Answers 1 at the last shape this caster can reach: there is nothing further to fill towards,
     * and a bar that keeps filling into a wall is a lie.
     */
    public static float chargeProgress(int heldTicks, Proficiency proficiency, int darkArtsDepth) {
        TierResolution resolution = resolveTier(heldTicks, proficiency, darkArtsDepth);
        ProtegoTier tier = resolution.tier();
        if (resolution.capped() || tier == ProtegoTier.HORRIBILIS) {
            return 1.0f;
        }
        int from = chargeTicks(tier, proficiency);
        int to = chargeTicks(tier.next(), proficiency);
        if (to <= from) {
            return 1.0f;
        }
        return clamp((Math.max(0, heldTicks) - from) / (float) (to - from), 0.0f, 1.0f);
    }

    /** Planting is a release decision, not a tier: the two dome tiers can be set down, the others cannot. */
    public static boolean canPlant(ProtegoTier tier, boolean sneaking) {
        return tier.plantable() && sneaking;
    }

    // ── integrity and lifetime ──────────────────────────────────────────────────────────────────

    /**
     * The absorb pool the shield is raised with, in the same units as damage.
     *
     * @param proficiencyScalar {@code Spell#getProficiencyScalar} — 0.33 / 0.66 / 1.0
     * @param castPower         the cast's situational × skill multipliers. Deliberately <em>not</em>
     *                          the full {@code finalDamage()}: that already carries a proficiency
     *                          channel, and proficiency is applied here explicitly. Counting it in
     *                          both places is the double-dip this codebase has been bitten by before.
     */
    public static float integrity(ProtegoTier tier, float proficiencyScalar, float castPower, boolean planted) {
        float practice = 0.6f + 0.4f * clamp01(proficiencyScalar);
        float power = clamp(safe(castPower, 1.0f), MIN_POWER_FACTOR, MAX_POWER_FACTOR);
        float plant = planted ? PLANTED_INTEGRITY_FACTOR : 1.0f;
        return tier.baseIntegrity() * practice * power * plant;
    }

    public static int lifetimeTicks(ProtegoTier tier, float proficiencyScalar, boolean planted) {
        float base = tier.baseLifetimeTicks() + tier.lifetimePerProficiencyTicks() * clamp01(proficiencyScalar);
        return Math.max(20, Math.round(base * (planted ? PLANTED_LIFETIME_FACTOR : 1.0f)));
    }

    // ── what an impact costs ────────────────────────────────────────────────────────────────────

    /**
     * Integrity spent turning one spell aside. Dark bolts are the whole point of the top tier: it
     * swallows them for a third of the cost, and every lesser ward pays a premium for them.
     */
    public static float spellImpactCost(ProtegoTier tier, float spellDamage, boolean darkSpell) {
        float cost = Math.max(MIN_SPELL_COST, Math.max(0.0f, safe(spellDamage, 0.0f)));
        if (darkSpell) {
            cost *= tier.absorbsDark() ? DARK_ABSORB_FACTOR : DARK_PRESSURE_FACTOR;
        }
        return cost;
    }

    /**
     * Integrity spent soaking {@code amount} of ordinary damage, and the mirror of
     * {@link #spellImpactCost}: Horribilis pays a third for anything Dark, every lesser ward pays a
     * premium. No floor here — a one-point graze should cost the ward one point, not three.
     */
    public static float damageImpactCost(ProtegoTier tier, float amount, boolean darkSource) {
        float cost = Math.max(0.0f, safe(amount, 0.0f));
        if (darkSource) {
            cost *= tier.absorbsDark() ? DARK_ABSORB_FACTOR : DARK_PRESSURE_FACTOR;
        }
        return cost;
    }

    /**
     * How much damage a pool of {@code integrity} can soak, given what this shape pays per point.
     *
     * <p>The two are not the same number once a discount is in play: a Horribilis with 10 left
     * stands in front of nearly 29 points of a dementor's chill, and a Maxima with 10 left stands in
     * front of 8.
     */
    public static float absorbableDamage(ProtegoTier tier, float integrity, float amount, boolean darkSource) {
        float perPoint = damageImpactCost(tier, 1.0f, darkSource);
        if (perPoint <= 0.0f) {
            return Math.max(0.0f, amount);
        }
        return Math.min(Math.max(0.0f, amount), Math.max(0.0f, integrity) / perPoint);
    }

    /** True when this hit takes the shield from "holding" to "about to go". */
    public static boolean crossedLowIntegrity(float before, float after, float max) {
        if (max <= 0.0f) {
            return false;
        }
        return before / max > LOW_INTEGRITY_FRACTION && after / max <= LOW_INTEGRITY_FRACTION && after > 0.0f;
    }

    // ── geometry ────────────────────────────────────────────────────────────────────────────────

    /**
     * Whether something arriving from {@code (toX, toZ)} (a vector from the shield's centre to the
     * attack) meets a disc aimed along {@code (aimX, aimZ)}. Horizontal only: a disc held out in
     * front covers the arc the caster is facing, whatever the pitch.
     *
     * <p>Degenerate input answers {@code true}: an attack with no direction we can read is not one
     * the caster should be punished for.
     */
    public static boolean isFrontal(double aimX, double aimZ, double toX, double toZ) {
        double aimLen = Math.sqrt(aimX * aimX + aimZ * aimZ);
        double toLen = Math.sqrt(toX * toX + toZ * toZ);
        if (aimLen < 1.0e-6 || toLen < 1.0e-6) {
            return true;
        }
        return (aimX * toX + aimZ * toZ) / (aimLen * toLen) >= FRONTAL_MIN_DOT;
    }

    /**
     * Where a moving bolt first crosses into the shield's sphere, as a fraction of the segment
     * {@code a → b}, or {@code -1} if it never does.
     *
     * <p>Swept, not sampled. A point-in-sphere test per tick misses anything faster than the sphere
     * is wide — the same tunnelling that made the first spell-clash implementation almost never
     * fire (see {@code project_spell_clash}). A segment that <em>starts</em> inside also answers
     * {@code -1}: a bolt cast from within the dome was never stopped by its wall.
     */
    public static double sphereEntry(double ax, double ay, double az,
                                     double bx, double by, double bz,
                                     double cx, double cy, double cz,
                                     double radius) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double fx = ax - cx;
        double fy = ay - cy;
        double fz = az - cz;

        double outside = fx * fx + fy * fy + fz * fz - radius * radius;
        if (outside <= 0.0) {
            return -1.0; // started inside the ward
        }
        double a = dx * dx + dy * dy + dz * dz;
        if (a < 1.0e-9) {
            return -1.0; // not moving
        }
        double b = 2.0 * (fx * dx + fy * dy + fz * dz);
        double discriminant = b * b - 4.0 * a * outside;
        if (discriminant < 0.0) {
            return -1.0; // misses the sphere entirely
        }
        double t = (-b - Math.sqrt(discriminant)) / (2.0 * a);
        return t >= 0.0 && t <= 1.0 ? t : -1.0;
    }

    // ── small helpers ───────────────────────────────────────────────────────────────────────────

    private static float clamp01(float value) {
        return clamp(safe(value, 0.0f), 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** NaN and infinity are balance bugs somewhere else; here they must not become an invincible ward. */
    private static float safe(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
