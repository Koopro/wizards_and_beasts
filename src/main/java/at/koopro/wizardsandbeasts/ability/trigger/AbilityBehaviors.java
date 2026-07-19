package at.koopro.wizardsandbeasts.ability.trigger;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link AbilityBehavior}s keyed by ability id. Follow-up prompts register real behaviors here;
 * ids without a registration fall back to {@link #NOOP} (validated-but-inert), so the wheel/trigger path is
 * fully exercisable before any behavior exists.
 */
@NullMarked
public final class AbilityBehaviors {

    /** Validated-but-inert behavior: activation "succeeds" (consumes cooldown) but does nothing. */
    public static final AbilityBehavior NOOP = new AbilityBehavior() {};

    private static final Map<Identifier, AbilityBehavior> BEHAVIORS = new ConcurrentHashMap<>();

    private AbilityBehaviors() {}

    public static void register(Identifier abilityId, AbilityBehavior behavior) {
        BEHAVIORS.put(abilityId, behavior);
    }

    public static AbilityBehavior get(Identifier abilityId) {
        return BEHAVIORS.getOrDefault(abilityId, NOOP);
    }

    public static boolean isRegistered(Identifier abilityId) {
        return BEHAVIORS.containsKey(abilityId);
    }
}
