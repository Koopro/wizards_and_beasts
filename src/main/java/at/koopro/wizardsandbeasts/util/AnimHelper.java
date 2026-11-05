package at.koopro.wizardsandbeasts.util;

import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.creature.profile.HasActivity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Factory methods for common GeckoLib AnimationController patterns.
 * Reduces per-entity animation boilerplate for standard movement behaviors.
 */
public final class AnimHelper {

    private AnimHelper() {}

    /**
     * Creates a movement controller that plays walkAnim when moving, idleAnim when still.
     */
    public static <T extends GeoEntity> AnimationController<T> movementController(
            String entityName,
            int transitionTicks,
            RawAnimation idleAnim,
            RawAnimation walkAnim) {
        return new AnimationController<>(entityName + "_movement", transitionTicks, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(walkAnim);
            }
            return state.setAndContinue(idleAnim);
        });
    }

    /**
     * Creates a simple idle-only controller (for entities that don't walk).
     */
    public static <T extends GeoEntity> AnimationController<T> idleController(
            String entityName,
            int transitionTicks,
            RawAnimation idleAnim) {
        return new AnimationController<>(entityName + "_idle", transitionTicks,
                state -> state.setAndContinue(idleAnim));
    }

    /**
     * Builds a looping RawAnimation with the standard naming convention:
     * "animation.{entityName}.{animName}"
     */
    public static RawAnimation loop(String entityName, String animName) {
        return RawAnimation.begin().thenLoop("animation." + entityName + "." + animName);
    }

    /**
     * Builds a play-once RawAnimation with the standard naming convention:
     * "animation.{entityName}.{animName}"
     */
    public static RawAnimation playOnce(String entityName, String animName) {
        return RawAnimation.begin().thenPlay("animation." + entityName + "." + animName);
    }

    // ── activity ─────────────────────────────────────────────────────────────

    /**
     * Whether a rig's animation file defines a clip ({@code asset}, {@code clip}). Answers "no" until the client
     * installs the real lookup into GeckoLib's baked animations ({@code RigClips}), so nothing on a server, and
     * nothing before resources load, ever asks GeckoLib for a clip it may not have.
     */
    private static volatile BiPredicate<String, String> rigHasClip = (asset, clip) -> false;

    public static void installRigLookup(BiPredicate<String, String> lookup) {
        rigHasClip = lookup;
    }

    public static boolean rigHas(String asset, String clip) {
        return rigHasClip.test(asset, clip);
    }

    /** Looping clips by full name, made once each: the controller runs every frame. */
    private static final Map<String, RawAnimation> LOOPS = new ConcurrentHashMap<>();

    public static RawAnimation activityLoop(String asset, String clip) {
        return LOOPS.computeIfAbsent(asset + "." + clip, key -> loop(asset, clip));
    }

    /**
     * The movement controller for a creature that reports a {@link CreatureActivity}: the activity's own clip when
     * the rig has one, else a stand-in made from {@code idle} and {@code base} at another speed. With activity
     * {@code IDLE} it is exactly {@link #movementController}: {@code base} while moving, {@code idle} when still.
     *
     * @param base the rig's moving loop ({@code walk}, {@code fly}, {@code swim})
     */
    public static <T extends GeoEntity & HasActivity> AnimationController<T> activityController(
            String asset, int transitionTicks, String base) {
        return new AnimationController<>(asset + "_movement", transitionTicks, state -> {
            CreatureActivity.Loop loop = state.animatable().activity()
                    .loop(state.isMoving(), clip -> rigHas(asset, clip), base);
            state.setControllerSpeed((float) loop.speed());
            return state.setAndContinue(activityLoop(asset, loop.clip()));
        });
    }
}
