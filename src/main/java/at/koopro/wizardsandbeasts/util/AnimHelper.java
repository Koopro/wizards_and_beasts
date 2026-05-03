package at.koopro.wizardsandbeasts.util;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

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
}
