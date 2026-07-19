package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Server-side behavior bound to an ability id. This prompt ships only the framework and no-op/log behaviors;
 * real behaviors (Apparition, Legilimency, …) arrive in follow-up prompts and register here without touching
 * the trigger dispatch. All hooks run server-authoritatively after {@link AbilityTriggerHandler} has validated
 * grant + module + cooldown.
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

    /** TOGGLE ability whose state just flipped to {@code nowOn} (already validated + persisted). */
    default void onToggle(ServerPlayer player, AbilityDefinition def, boolean nowOn) {
    }
}
