package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrantSources;
import at.koopro.wizardsandbeasts.ability.grant.DebugAutoGrantSource;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * One-time wiring for the framework's verification abilities: binds {@link LogAbilityBehavior} to the two
 * triggerable test ids and registers the {@link DebugAutoGrantSource}. Called once from the mod constructor.
 * The PASSIVE test ability intentionally gets no behavior (it is never triggered).
 */
@NullMarked
public final class AbilityDebugBehaviors {

    public static final Identifier DEBUG_ACTIVE = id("debug_active");
    public static final Identifier DEBUG_TOGGLE = id("debug_toggle");
    public static final Identifier DEBUG_PASSIVE = id("debug_passive");

    private AbilityDebugBehaviors() {}

    public static void bootstrap() {
        LogAbilityBehavior log = new LogAbilityBehavior();
        AbilityBehaviors.register(DEBUG_ACTIVE, log);
        AbilityBehaviors.register(DEBUG_TOGGLE, log);
        AbilityGrantSources.register(new DebugAutoGrantSource());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }
}
