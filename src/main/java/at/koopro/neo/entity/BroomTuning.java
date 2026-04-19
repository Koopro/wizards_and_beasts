package at.koopro.neo.entity;

/**
 * Physics and collision tuning for {@link BroomEntity}.
 */
public final class BroomTuning {

    private BroomTuning() {}

    public static final float MAX_SPEED = 1.15f;
    public static final float ACCELERATION = 0.11f;
    public static final float DECELERATION = 0.075f;
    public static final float BOOST_ACCELERATION = 0.06f;
    public static final float BOOST_MULTIPLIER = 1.45f;
    public static final float WEAK_GRAVITY = 0.012f;
    public static final float VERTICAL_SPEED = 0.12f;
    public static final float VERTICAL_RESPONSE = 0.18f;
    public static final float PITCH_LIFT_FACTOR = 0.42f;
    public static final float COAST_DRAG = 0.985f;
    public static final float INPUT_DRAG = 0.995f;
    public static final float LOW_SPEED_TURN_RATE = 10.0f;
    public static final float HIGH_SPEED_TURN_RATE = 4.0f;
    public static final float MAX_PITCH_RATE = 6.0f;
    public static final float MAX_PITCH_TILT = 25f;
    public static final float MAX_ROLL_TILT = 35f;
    public static final float MAX_FORWARD_LEAN = 20f;
    public static final float TILT_SMOOTHING = 0.35f;

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
    public static final float VERTICAL_IMPACT_WEIGHT = 0.65f;
}
