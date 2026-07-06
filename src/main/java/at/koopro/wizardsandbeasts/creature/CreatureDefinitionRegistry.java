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

    public static Collection<CreatureDefinition> getAll() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static int size() {
        return DEFINITIONS.size();
    }
}
