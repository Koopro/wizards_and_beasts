package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Server-side behavior bound to an ability id. Real behaviors register here without touching the trigger
 * dispatch. All hooks run server-authoritatively after {@link AbilityTriggerHandler} has validated
 * grant + module + cooldown (+ target kind/range for targeted abilities).
 *
 * <p>Toggle abilities come in two flavours. By default the framework owns the on/off bit in
 * {@code AbilitySelectionState.toggles}. A behavior that adapts an <b>existing</b> stateful system (the
 * Animagus form, whose truth is {@code PlayerAbilityHelper.isCurrentlyTransformed}) overrides
 * {@link #ownsToggleState()} so the framework neither reads nor writes that bit — avoiding a second,
 * independently-persisted copy that would desync on every non-wheel path.
 */
@NullMarked
public interface AbilityBehavior {

    /**
     * ACTIVE ability fired by the use / quick key. Return {@code true} if the activation occurred and should
     * start the definition's cooldown; {@code false} to indicate a soft no-op (no cooldown consumed).
     */
    default boolean onActivate(ServerPlayer player, AbilityDefinition def) {
        return true;
    }

    /**
     * Targeted overload — the client's validated pick rides along. Untargeted abilities receive
     * {@link AbilityTarget#NONE}; the default implementation discards it and calls
     * {@link #onActivate(ServerPlayer, AbilityDefinition)}.
     */
    default boolean onActivate(ServerPlayer player, AbilityDefinition def, AbilityTarget target) {
        return onActivate(player, def);
    }

    /**
     * TOGGLE ability flipping to {@code nowOn}. When {@link #ownsToggleState()} is {@code false} the flip is
     * already validated and persisted by the framework; when it is {@code true}, {@code nowOn} is only the
     * <i>requested</i> direction and the owning system decides (and may refuse).
     */
    default void onToggle(ServerPlayer player, AbilityDefinition def, boolean nowOn) {
    }

    /**
     * True if this behavior keeps its on/off state in an external system it does not want duplicated. The
     * framework then reads {@link #isToggledOn} instead of {@code AbilitySelectionState.toggles}, and never
     * writes that set for this ability.
     */
    default boolean ownsToggleState() {
        return false;
    }

    /** Current on/off state; consulted only when {@link #ownsToggleState()} is {@code true}. */
    default boolean isToggledOn(ServerPlayer player, AbilityDefinition def) {
        return false;
    }
}
