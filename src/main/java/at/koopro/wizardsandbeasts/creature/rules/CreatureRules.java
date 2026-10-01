package at.koopro.wizardsandbeasts.creature.rules;

import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * The live creature rules, keyed by creature id path ({@code hippogriff}). Pure — no server — so the spawn gate and
 * the variant roll stay unit-testable.
 *
 * <p>Server-only state. Unlike heritage or spell rules, nothing a client does reads these: natural spawning and the
 * variant roll both happen on the server, and the admin panel sees them as ordinary setting values. So there is no
 * sync payload and no remote layer.
 */
@NullMarked
public final class CreatureRules {

    private static volatile Map<String, CreatureRule> rules = Map.of();

    private CreatureRules() {}

    public static Map<String, CreatureRule> current() {
        return rules;
    }

    public static CreatureRule rule(String creatureId) {
        return rules.getOrDefault(creatureId, CreatureRule.NONE);
    }

    public static synchronized void publish(Map<String, CreatureRule> next) {
        rules = Map.copyOf(next);
    }

    /** Whether natural and chunk-generation spawns of this creature may succeed. On unless an admin said otherwise. */
    public static boolean naturalSpawn(String creatureId) {
        return rule(creatureId).naturalSpawn().orElse(Boolean.TRUE);
    }

    public static boolean variantEnabled(String creatureId, CreatureVariant variant) {
        return rule(creatureId).variantEnabled().getOrDefault(variant.variantId(), Boolean.TRUE);
    }

    public static int variantWeight(String creatureId, CreatureVariant variant) {
        return rule(creatureId).variantWeight().getOrDefault(variant.variantId(), variant.authoredWeight());
    }
}
