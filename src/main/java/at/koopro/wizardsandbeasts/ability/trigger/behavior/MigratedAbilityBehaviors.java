package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehaviors;
import org.jspecify.annotations.NullMarked;

/**
 * One-time wiring for the three abilities migrated off dedicated keybinds onto the wheel. Called once from
 * the mod constructor, alongside {@code AbilityDebugBehaviors.bootstrap()}.
 */
@NullMarked
public final class MigratedAbilityBehaviors {

    private MigratedAbilityBehaviors() {}

    public static void bootstrap() {
        AbilityBehaviors.register(AbilityIds.APPARITION, ApparitionAbilityBehavior.INSTANCE);
        AbilityBehaviors.register(AbilityIds.LEGILIMENCY, LegilimencyAbilityBehavior.INSTANCE);
        AbilityBehaviors.register(AbilityIds.ANIMAGUS_FORM, AnimagusFormAbilityBehavior.INSTANCE);
    }
}
