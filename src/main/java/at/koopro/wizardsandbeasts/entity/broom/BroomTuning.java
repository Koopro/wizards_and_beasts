package at.koopro.wizardsandbeasts.entity.broom;

/**
 * Physics and collision tuning for {@link BroomEntity}.
 *
 * <p><b>What belongs here and what does not.</b> Anything that varies per broom — top speed,
 * acceleration, boost, gravity, turn and climb rates — lives on {@code BroomDefinition} and is
 * authored in {@code data/wizards_and_beasts/broom_definitions/}. What is left here is the shared
 * feel every broom has in common: how fast the nose can swing, what counts as a crash, what counts
 * as a landing.
 *
 * <p>Six constants used to sit here with zero readers — {@code ACCELERATION}, {@code DECELERATION},
 * {@code BOOST_ACCELERATION}, {@code WEAK_GRAVITY}, {@code VERTICAL_SPEED} and
 * {@code MAX_FORWARD_LEAN} — left behind when those values moved into the definition. They are gone.
 * A tuning constant nothing reads is worse than no constant: it looks like the value in force, so the
 * next person to tune the broom edits it and watches nothing happen.
 *
 * <p>{@code COAST_DRAG} and {@code INPUT_DRAG} left the same way when momentum became per-broom.
 */
public final class BroomTuning {

    private BroomTuning() {}

    // ── Steering ────────────────────────────────────────────────────────────────────────────────

    /** Degrees of yaw per tick at a standstill — a broom turns tightest when it is slowest. */
    public static final float LOW_SPEED_TURN_RATE = 13.0f;
    /** Degrees of yaw per tick at this broom's ceiling. */
    public static final float HIGH_SPEED_TURN_RATE = 7.0f;
    /** Degrees of pitch per tick. Caps how fast the nose can swing, not how far. */
    public static final float MAX_PITCH_RATE = 9.0f;
    /**
     * How far the nose may point up or down, in degrees.
     *
     * <p>Deliberately short of vertical. At 90 the forward vector's horizontal component reaches zero
     * and the broom stops being steerable at exactly the moment a player is most likely to be diving
     * at the ground and wanting to pull out of it.
     */
    public static final float MAX_PITCH_DEGREES = 75.0f;
    /** How much of the forward vector's vertical component becomes real lift. */
    public static final float PITCH_LIFT_FACTOR = 0.70f;
    /** How quickly vertical velocity chases its target. */
    public static final float VERTICAL_RESPONSE = 0.32f;
    // COAST_DRAG (0.990) and INPUT_DRAG (0.995) moved out on 2026-08-27. Drag is per-broom now,
    // derived from the definition's momentumRetention by BroomHandling#coastDrag, whose mapping is
    // anchored so the default 0.90 reproduces these two figures exactly. Deleted rather than left
    // here for reference, per the rule this file's own header states: a tuning constant nothing
    // reads looks like the value in force, so the next person to tune the broom edits it and
    // watches nothing happen.

    /**
     * The fastest any shipped broom goes: the Firebolt Supreme's {@code maxSpeed * boostMultiplier},
     * 1.05 × 2.5.
     *
     * <p>A fallback denominator, and nothing else. Any code holding the definition being flown must
     * divide by <em>that</em> broom's ceiling instead.
     *
     * <p>It replaces {@code MAX_SPEED * BOOST_MULTIPLIER} (1.15 × 1.45 = 1.6675), a pair left over
     * from before brooms had definitions, describing a broom that never existed and sitting between
     * the real extremes rather than above them. Everything normalised against it was wrong in both
     * directions at once: the starter broom tops out at 0.455, which is 0.27 of that denominator and
     * below {@link #MINOR_IMPACT_THRESHOLD} — it could not register <em>any</em> impact at any speed,
     * into any wall. The Firebolt Supreme reaches {@link #SEVERE_IMPACT_THRESHOLD} at 64% of its own
     * top speed, so its cruising speed was a fatal crash. Per-definition normalisation is what makes
     * the thresholds mean "a fraction of what this broom can do", which is the only reading under
     * which one set of numbers can serve an eight-broom range.
     */
    public static final float REFERENCE_TOP_SPEED = 2.625f;

    // ── Sink ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Fastest a broom sinks under its own {@code weakGravity} with no input. Well under free-fall:
     * letting go of the controls should feel like settling, and should never kill a player who let go
     * over a long drop.
     */
    public static final float TERMINAL_SINK_SPEED = 0.35f;

    // ── Landing ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Descent rate at or under which touching the ground is a landing rather than an impact.
     * Roughly the speed of stepping off a block, so an ordinary controlled descent is always free.
     */
    public static final float GENTLE_LANDING_MAX_DESCENT = 0.18f;
    /**
     * Forward speed, as a fraction of this broom's ceiling, at or under which a touchdown still counts
     * as a landing. Above it you are not landing, you are skidding.
     */
    public static final float GENTLE_LANDING_MAX_SPEED_RATIO = 0.35f;

    // ── Tilt (visual only) ──────────────────────────────────────────────────────────────────────

    public static final float MAX_PITCH_TILT = 35f;
    public static final float MAX_ROLL_TILT = 50f;
    public static final float TILT_SMOOTHING = 0.55f;

    // ── Impacts ─────────────────────────────────────────────────────────────────────────────────

    public static final float CRASH_SPEED_THRESHOLD = 0.5f;
    public static final float MAX_CRASH_DAMAGE = 20f;
    public static final float CRASH_KNOCKBACK = 0.6f;
    public static final float ENTITY_HIT_DAMAGE = 4f;
    public static final float KNOCKBACK_FORCE = 1.5f;
    public static final float COLLISION_SPEED_MIN = 0.3f;
    public static final int COLLISION_COOLDOWN_TICKS = 10;
    public static final float MINOR_IMPACT_THRESHOLD = 0.40f;
    public static final float MODERATE_IMPACT_THRESHOLD = 0.72f;
    public static final float SEVERE_IMPACT_THRESHOLD = 1.0f;
    public static final float MINOR_SPEED_DAMPING = 0.82f;
    public static final float MODERATE_SPEED_DAMPING = 0.58f;
    public static final float MINOR_BUMP_Y = 0.05f;
    public static final float MODERATE_BUMP_Y = 0.12f;
    /**
     * How much a vertical impact counts relative to a head-on one at the same speed.
     *
     * <p>Raised from 0.65 when landings became their own category. At 0.65 a broom diving into the
     * ground at its absolute ceiling scored 0.65 — inside the <em>minor</em> band, the same bracket as
     * clipping a fence post. That was survivable at 0.65 only because gentle landings were not
     * exempt, so the whole scale had to stay soft to keep ordinary touchdowns from hurting. With
     * {@link #GENTLE_LANDING_MAX_DESCENT} handling the touchdown case, the impact scale is free to
     * mean what it says.
     *
     * <p>Still below 1: a fall is softer than a wall. At 0.85 a maximum dive lands in the moderate
     * band and a corner can approach but not cross severe, so {@link #MAX_CRASH_DAMAGE} stays
     * reserved for what it should be — flying flat out into a cliff face.
     */
    public static final float VERTICAL_IMPACT_WEIGHT = 0.85f;
    /**
     * Multiplier when a tick clips a wall <em>and</em> the floor. Above 1 because a corner is worse
     * than either alone, but the two are combined by {@code max} rather than added — a glancing corner
     * should not read as a head-on crash.
     */
    public static final float CORNER_IMPACT_WEIGHT = 1.1f;

    // ── Fluid bail-out ──────────────────────────────────────────────────────────────────────────

    /**
     * Blocks of clearance searched around a bail-out or dismount point before giving up and using the
     * broom's own position. Small on purpose: this is a courtesy scan, not a teleport.
     */
    public static final int DISMOUNT_SEARCH_RADIUS = 2;
    /** How far below the broom a dismount will look for standable ground before landing you in air. */
    public static final int DISMOUNT_DROP_SEARCH = 4;
}
