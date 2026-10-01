package at.koopro.wizardsandbeasts.creature;

import org.jspecify.annotations.Nullable;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Runtime store of loaded {@link CreatureDefinition}s, mirroring {@code BroomDefinitionRegistry}. */
public final class CreatureDefinitionRegistry {
    /** Volatile-swapped immutable map: readers never observe a mid-reload empty/partial registry. */
    private static volatile Map<Identifier, CreatureDefinition> DEFINITIONS = Map.of();

    private CreatureDefinitionRegistry() {}

    public static void replaceAll(Map<Identifier, CreatureDefinition> loaded) {
        DEFINITIONS = Map.copyOf(loaded);
    }

    @Nullable
    public static CreatureDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    /**
     * The current definitions map itself. A reload replaces it with a new instance, so a caller that remembers
     * which instance it read from can cache a lookup and know exactly when the cache went stale — which is what
     * {@code GenericBeastEntity.definition()} does, because vanilla asks a creature things like
     * {@code fireImmune()} many times per tick (documentation/PERFORMANCE_AUDIT.md).
     */
    public static Map<Identifier, CreatureDefinition> snapshot() {
        return DEFINITIONS;
    }

    public static Collection<CreatureDefinition> getAll() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static int size() {
        return DEFINITIONS.size();
    }
}
