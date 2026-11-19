package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.pose.FlightPoseConstants.PartRotation;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Pose values for the three cast phases, in third person and in first person.
 *
 * <p>Authored 2026-11-04 and judged in a running client ({@code EquipmentShowcaseCapture}, mode {@code cast}:
 * each phase parked by the same payload a real cast sends, from three sides and in first person). They replace a
 * placeholder table whose three phases were too alike to read as a gather, a strike and a settle.
 *
 * <h2>The performance</h2>
 *
 * <ul>
 *   <li><b>Wind-up</b> — held for as long as the player holds the wand: the arm drawn up and back over the
 *       shoulder, wand raised, chest coiled away. Unmistakably "about to cast" from any side.</li>
 *   <li><b>Release</b> — the strike: the arm thrown out to point where the caster is looking, chest turned into
 *       it. A channelled spell (a beam) holds this pose for the whole channel.</li>
 *   <li><b>Recovery</b> — the arm settling down and forward, back into the vanilla hold.</li>
 * </ul>
 *
 * <p>No movement here is book canon except where a per-spell override says so ({@code WAND_CAST_POSE_SCHEMA} §3);
 * the three phases are a game gesture.
 *
 * <h2>Arm only, by D4</h2>
 *
 * <p>The pose layer owns the casting arm; the wand's {@code GeoItem} owns the wand's own bones and tip FX. These
 * values move the casting arm and give the chest and head only enough to stop the arm looking detached.
 *
 * <h2>Sign conventions</h2>
 *
 * <p>{@code ModelPart} rotations in degrees, written for a right-handed cast (the pass mirrors yaw and roll for a
 * left hand). Limb {@code xRot} negative swings forward: {@code -90} points straight ahead, {@code -180} straight
 * up. {@link CastPose#aim} is the share of the caster's look pitch added to the arm's {@code xRot}, so a strike
 * points where the player looks, as vanilla's bow aim does.
 *
 * <h2>First person</h2>
 *
 * <p>Holding a wand, vanilla draws the item and not the arm, so first person moves the held wand instead
 * ({@link FirstPersonCast}): a translation in screen blocks and a pitch, per phase.
 */
@NullMarked
public final class CastPoseConstants {

    private CastPoseConstants() {}

    /**
     * One phase's third-person pose.
     *
     * @param castingArm the wand arm — whichever {@code mainArm} reports
     * @param chest      torso twist; a cast that moves only the arm reads as a puppet
     * @param headPitch  added to the real look pitch
     * @param aim        0..1 share of the look pitch added to the arm's {@code xRot}
     * @param offArm     the free arm, written as the LEFT arm of a right-handed caster (inward is +yRot, outward
     *                   is -zRot) and mirrored with the rest for a left-hander; only shown by its {@code offShare}
     * @param offShare   0..1 how much the free arm is part of the gesture. Zero leaves it to vanilla (or to the
     *                   broom grip), which is what every single-arm set wants
     */
    public record CastPose(PartRotation castingArm, PartRotation chest, float headPitch, float aim,
                           PartRotation offArm, float offShare) {

        /** A one-armed pose: the free arm stays with whatever else is posing it. */
        public CastPose(PartRotation castingArm, PartRotation chest, float headPitch, float aim) {
            this(castingArm, chest, headPitch, aim, PartRotation.NONE, 0f);
        }

        public CastPose lerp(CastPose to, float t) {
            return new CastPose(
                    castingArm.lerp(to.castingArm(), t),
                    chest.lerp(to.chest(), t),
                    FlightPoseConstants.lerp(headPitch, to.headPitch(), t),
                    FlightPoseConstants.lerp(aim, to.aim(), t),
                    // The free arm's angles travel only while it matters; from a one-armed pose it starts where it
                    // is going, so a share rising from zero does not swing it in from straight down.
                    offShare <= 0f ? to.offArm() : to.offShare() <= 0f ? offArm : offArm.lerp(to.offArm(), t),
                    FlightPoseConstants.lerp(offShare, to.offShare(), t));
        }

        /** The same pose with the chest twist scaled and the free arm released: the broom variant (D7). */
        public CastPose mounted(float chestShare) {
            return new CastPose(castingArm,
                    new PartRotation(chest.xRot() * chestShare, chest.yRot() * chestShare, chest.zRot() * chestShare),
                    headPitch, aim, offArm, 0f);
        }
    }

    /**
     * One phase's first-person wand offset: where the held wand moves on screen.
     *
     * @param up      blocks up (+) in the hand's frame
     * @param forward blocks away from the camera (+)
     * @param pitch   degrees about the hand; negative lifts the point up and back toward the caster
     */
    public record FirstPersonPose(float up, float forward, float pitch) {

        public FirstPersonPose lerp(FirstPersonPose to, float t) {
            return new FirstPersonPose(
                    FlightPoseConstants.lerp(up, to.up(), t),
                    FlightPoseConstants.lerp(forward, to.forward(), t),
                    FlightPoseConstants.lerp(pitch, to.pitch(), t));
        }
    }

    /** Neutral. What the pose blends out of and back into. */
    public static final CastPose NEUTRAL =
            new CastPose(PartRotation.NONE, PartRotation.NONE, 0f, 0f);

    // ── third person ─────────────────────────────────────────────────────────

    /** The gather: arm up and back over the shoulder, wand raised, chest coiled away. */
    public static final CastPose WINDUP = new CastPose(
            new PartRotation(-150f, -25f, 10f),
            new PartRotation(0f, -18f, 0f),
            -6f, 0f);

    /** The strike, and the channel: arm thrown out along the look, chest turned into it. */
    public static final CastPose RELEASE = new CastPose(
            new PartRotation(-90f, 6f, 0f),
            new PartRotation(0f, 14f, 0f),
            0f, 1f);

    /** The settle: arm lowering forward, chest unwinding. */
    public static final CastPose RECOVERY = new CastPose(
            new PartRotation(-38f, 6f, 0f),
            new PartRotation(0f, 5f, 0f),
            0f, 0.3f);

    // ── category sets and per-spell overrides ────────────────────────────────

    /** Gather, strike and settle for one spell or one category. */
    public record PoseSet(CastPose windup, CastPose release, CastPose recovery) {}

    /**
     * {@code COMBAT}, and the fallback for anything unresolved: the gather over the shoulder and the thrown strike
     * judged in VR-1b. Source: {@code game}.
     */
    public static final PoseSet DEFAULT = new PoseSet(WINDUP, RELEASE, RECOVERY);
    public static final PoseSet COMBAT = DEFAULT;

    /**
     * {@code UTILITY}: charms and household magic. No coil and no throw — the wand lifts in front of the chest and
     * points, the way one would indicate a thing to be mended or lit. Source: {@code invented}.
     */
    public static final PoseSet UTILITY = new PoseSet(
            new CastPose(new PartRotation(-112f, -12f, 0f), new PartRotation(0f, -6f, 0f), -3f, 0.2f),
            new CastPose(new PartRotation(-72f, 14f, 0f), new PartRotation(0f, 3f, 0f), 2f, 0.5f),
            new CastPose(new PartRotation(-50f, 4f, 0f), new PartRotation(0f, 2f, 0f), 0f, 0.3f));

    /**
     * {@code DEFENSE}: a guard. The wand comes up across the face with the free hand raised behind it, then pushes
     * out. Source: {@code invented}.
     */
    public static final PoseSet DEFENSE = new PoseSet(
            new CastPose(new PartRotation(-118f, -38f, 0f), new PartRotation(4f, -8f, 0f), -2f, 0.2f,
                    new PartRotation(-95f, 20f, 0f), 0.85f),
            new CastPose(new PartRotation(-96f, -12f, 0f), new PartRotation(2f, 6f, 0f), 0f, 0.7f,
                    new PartRotation(-80f, 15f, 0f), 0.6f),
            new CastPose(new PartRotation(-55f, -8f, 0f), new PartRotation(0f, 2f, 0f), 0f, 0.3f,
                    new PartRotation(-30f, 0f, 0f), 0.2f));

    /**
     * {@code DARK_ARTS}: drawn low and back at the hip, head down, then whipped forward from below — the opposite of
     * the open combat throw, so a dark cast reads from across a field. Source: {@code invented}.
     */
    public static final PoseSet DARK_ARTS = new PoseSet(
            new CastPose(new PartRotation(22f, -8f, 14f), new PartRotation(0f, -24f, 0f), 8f, 0f),
            new CastPose(new PartRotation(-84f, -10f, -6f), new PartRotation(6f, 26f, 0f), 8f, 1f),
            new CastPose(new PartRotation(-40f, 10f, 0f), new PartRotation(0f, 8f, 0f), 2f, 0.3f));

    /**
     * Wingardium Leviosa's "swish and flick" — the one wand movement the books describe (<i>Philosopher's Stone</i>
     * ch. 10), so a per-spell override ({@code WAND_CAST_POSE_SCHEMA} G-1, {@code gesture_source: book}). A
     * channelled spell holds its strike, so the swish is held while the object floats, and the flick plays as the
     * wand is let go.
     */
    public static final PoseSet LEVIOSA = new PoseSet(
            new CastPose(new PartRotation(-100f, -30f, 0f), new PartRotation(0f, -10f, 0f), 0f, 0.5f),
            new CastPose(new PartRotation(-82f, 34f, 0f), new PartRotation(0f, 12f, 0f), 0f, 1f),
            new CastPose(new PartRotation(-125f, 18f, 0f), new PartRotation(0f, 6f, 0f), -4f, 0.4f));

    /** Expelliarmus: a slash from high across the body out to the wand side. G-1, {@code gesture_source: film}. */
    public static final PoseSet EXPELLIARMUS = new PoseSet(
            new CastPose(new PartRotation(-138f, -52f, 0f), new PartRotation(0f, -22f, 0f), -4f, 0f),
            new CastPose(new PartRotation(-86f, 32f, 0f), new PartRotation(0f, 18f, 0f), 0f, 1f),
            new CastPose(new PartRotation(-48f, 16f, 0f), new PartRotation(0f, 6f, 0f), 0f, 0.3f));

    /**
     * Expecto Patronum: a conjuration, not a strike. The wand sweeps up from low across the body to high and
     * forward, the free arm opens, the head lifts after it. G-1, {@code gesture_source: film}.
     */
    public static final PoseSet PATRONUS = new PoseSet(
            new CastPose(new PartRotation(-42f, -34f, 0f), new PartRotation(4f, -14f, 0f), 6f, 0f,
                    new PartRotation(-20f, 0f, 0f), 0.5f),
            new CastPose(new PartRotation(-148f, 8f, 0f), new PartRotation(-6f, 8f, 0f), -12f, 0.25f,
                    new PartRotation(-55f, 0f, -28f), 1f),
            new CastPose(new PartRotation(-110f, 6f, 0f), new PartRotation(-3f, 4f, 0f), -6f, 0.25f,
                    new PartRotation(-35f, 0f, -18f), 0.6f));

    /**
     * Avada Kedavra: cocked high beside the head with the body leaning back and coiled, then a straight jab driven
     * forward with the chest and head behind it. It must not
     * share a gesture with ordinary damage spells (G-1). {@code gesture_source: film}.
     */
    public static final PoseSet AVADA_KEDAVRA = new PoseSet(
            new CastPose(new PartRotation(-120f, 4f, 12f), new PartRotation(-4f, -28f, 0f), -2f, 0f),
            new CastPose(new PartRotation(-96f, 0f, 0f), new PartRotation(6f, 24f, 0f), 8f, 1f),
            new CastPose(new PartRotation(-70f, 4f, 0f), new PartRotation(2f, 10f, 0f), 2f, 0.6f));

    /**
     * Protego, held: the guard raised and kept up — wand upright before the face, the free hand braced open behind
     * it. Replaces the generic gather, which put the wand behind the head for the one spell that is about covering
     * yourself. {@code gesture_source: invented}.
     */
    public static final PoseSet PROTEGO = new PoseSet(
            new CastPose(new PartRotation(-122f, -24f, 0f), new PartRotation(5f, -6f, 0f), 2f, 0f,
                    new PartRotation(-100f, 26f, 0f), 1f),
            new CastPose(new PartRotation(-102f, -10f, 0f), new PartRotation(3f, 4f, 0f), 0f, 0.5f,
                    new PartRotation(-92f, 18f, 0f), 0.8f),
            new CastPose(new PartRotation(-55f, -6f, 0f), new PartRotation(0f, 2f, 0f), 0f, 0.3f,
                    new PartRotation(-30f, 0f, 0f), 0.2f));

    /**
     * The pose set a cast plays: its spell's override, else its category's set, else {@link #DEFAULT}.
     *
     * <p>Resolution order per {@code WAND_CAST_POSE_SCHEMA} §5: per-spell override, then category default. The
     * category is read from the spell registry, which the client holds too; an unknown id falls back to the combat
     * set rather than failing.
     */
    public static PoseSet setFor(String spellId) {
        String path = spellId.substring(spellId.indexOf(':') + 1);
        switch (path) {
            case "wingardium_leviosa": return LEVIOSA;
            case "expelliarmus": return EXPELLIARMUS;
            case "expecto_patronum": return PATRONUS;
            case "avada_kedavra": return AVADA_KEDAVRA;
            case "protego": return PROTEGO;
            default: break;
        }
        return forCategory(category(spellId));
    }

    /** The default set for a category. Null is a cast whose spell is unknown. */
    public static PoseSet forCategory(@Nullable SpellCategory category) {
        if (category == null) {
            return DEFAULT;
        }
        return switch (category) {
            case COMBAT -> COMBAT;
            case UTILITY -> UTILITY;
            case DEFENSE -> DEFENSE;
            case DARK_ARTS -> DARK_ARTS;
        };
    }

    private static @Nullable SpellCategory category(String spellId) {
        if (spellId.isEmpty()) {
            return null;
        }
        Spell spell = Spells.byId(spellId);
        return spell == null ? null : spell.getCategory();
    }

    // ── first person ─────────────────────────────────────────────────────────

    public static final FirstPersonPose FP_NEUTRAL = new FirstPersonPose(0f, 0f, 0f);
    /** The wand rises and draws back toward the shoulder, point tipped up. */
    public static final FirstPersonPose FP_WINDUP = new FirstPersonPose(0.26f, -0.06f, -22f);
    /** Thrust out to the centre of the view, point level. */
    public static final FirstPersonPose FP_RELEASE = new FirstPersonPose(0.06f, 0.28f, -4f);
    /** Settling back toward the hold. */
    public static final FirstPersonPose FP_RECOVERY = new FirstPersonPose(0.02f, 0.08f, 0f);

    /**
     * Resolves the third-person pose for a point in the cast.
     *
     * <p>Neutral → wind-up across the wind-up window, wind-up → release across the release window, release →
     * recovery → neutral across recovery. Recovery ends on neutral because the end of a cast <em>is</em> not
     * casting.
     */
    public static CastPose at(ClientCastAnimationState.ActiveCast cast, float progress) {
        PoseSet set = setFor(cast.spellId());
        float windup = cast.windup(progress);
        if (windup < 1f) {
            return NEUTRAL.lerp(set.windup(), windup);
        }
        float release = cast.release(progress);
        if (release < 1f) {
            return set.windup().lerp(set.release(), release);
        }
        float recovery = cast.recovery(progress);
        return recovery < 0.5f
                ? set.release().lerp(set.recovery(), recovery * 2f)
                : set.recovery().lerp(NEUTRAL, (recovery - 0.5f) * 2f);
    }

    /** The first-person counterpart of {@link #at}, over the same phases. */
    public static FirstPersonPose firstPersonAt(ClientCastAnimationState.ActiveCast cast, float progress) {
        float windup = cast.windup(progress);
        if (windup < 1f) {
            return FP_NEUTRAL.lerp(FP_WINDUP, windup);
        }
        float release = cast.release(progress);
        if (release < 1f) {
            return FP_WINDUP.lerp(FP_RELEASE, release);
        }
        float recovery = cast.recovery(progress);
        return recovery < 0.5f
                ? FP_RELEASE.lerp(FP_RECOVERY, recovery * 2f)
                : FP_RECOVERY.lerp(FP_NEUTRAL, (recovery - 0.5f) * 2f);
    }

    // ── tuning ───────────────────────────────────────────────────────────────

    /**
     * Ticks a timed cast eases out over at its end. The start is the crossfade in {@link ClientCastAnimationState}.
     *
     * <p>Short: a cast is a sharp action, and easing it over the same eight ticks the flight pose uses would eat
     * most of a thirty-tick cast.
     */
    public static final int FADE_OUT_TICKS = 3;

    /** Ticks a held gather takes to build to its full charge (raise plus tremor). */
    public static final int CHARGE_TICKS = 40;
    /** Degrees the casting arm keeps rising over a full charge. */
    public static final float CHARGE_RAISE_DEG = 9f;
    /** Peak tremor of a fully charged gather, degrees. Small: power held in check, not a shiver. */
    public static final float CHARGE_TREMOR_DEG = 1.6f;
    /** Share of the chest twist a mounted caster keeps (D7). */
    public static final float MOUNTED_CHEST_SHARE = 0.35f;

    /** Default duration for {@code /wandb debug pose cast play} when none is given. */
    public static final int DEFAULT_TEST_TICKS = 30;
}
