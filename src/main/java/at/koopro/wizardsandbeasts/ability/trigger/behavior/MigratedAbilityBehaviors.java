package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AnimagusAbilityService;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehaviors;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialAbility;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialServerLogic;
import org.jspecify.annotations.NullMarked;

/**
 * One-time wiring for every ability migrated off a dedicated keybind onto the wheel. Called once from the
 * mod constructor, alongside {@code AbilityDebugBehaviors.bootstrap()}.
 */
@NullMarked
public final class MigratedAbilityBehaviors {

    private MigratedAbilityBehaviors() {}

    public static void bootstrap() {
        AbilityBehaviors.register(AbilityIds.APPARITION, ApparitionAbilityBehavior.INSTANCE);
        AbilityBehaviors.register(AbilityIds.LEGILIMENCY, LegilimencyAbilityBehavior.INSTANCE);
        AbilityBehaviors.register(AbilityIds.ANIMAGUS_FORM, AnimagusFormAbilityBehavior.INSTANCE);
        AbilityBehaviors.register(AbilityIds.ANIMAGUS_BEAST_ABILITY,
                new SimpleAbilityBehavior(AnimagusAbilityService::useActive));
        AbilityBehaviors.register(AbilityIds.OBSCURIAL_FORM, ObscurialFormAbilityBehavior.INSTANCE);
        AbilityBehaviors.register(AbilityIds.OBSCURIAL_STRESS_VENT,
                new SimpleAbilityBehavior(ObscurialServerLogic::stressVent));
        AbilityBehaviors.register(AbilityIds.OBSCURUS_SURGE,
                new SimpleAbilityBehavior(player -> ObscurialServerLogic.useAbility(player, ObscurialAbility.SURGE.spellId())));
        AbilityBehaviors.register(AbilityIds.OBSCURUS_GRASP,
                new SimpleAbilityBehavior(player -> ObscurialServerLogic.useAbility(player, ObscurialAbility.GRASP.spellId())));
    }
}
