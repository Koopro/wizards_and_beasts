package at.koopro.wizardsandbeasts.creature;

import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime store of loaded {@link CreatureDefinition}s, mirroring {@code BroomDefinitionRegistry}. */
public final class CreatureDefinitionRegistry {
    private static final Map<Identifier, CreatureDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private CreatureDefinitionRegistry() {}

    public static void replaceAll(Map<Identifier, CreatureDefinition> loaded) {
        DEFINITIONS.clear();
        DEFINITIONS.putAll(loaded);
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
