package at.koopro.wizardsandbeasts.ability.def;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Volatile-swapped immutable registry of {@link AbilityDefinition}s, mirroring {@code BroomDefinitionRegistry}:
 * readers never observe a mid-reload partial map. Populated on the server by {@link AbilityDefinitionLoader};
 * on the client it is populated from {@code AbilityDefinitionsSyncS2CPayload} so the wheel can read metadata.
 */
@NullMarked
public final class AbilityDefinitionRegistry {

    private static volatile Map<Identifier, AbilityDefinition> DEFINITIONS = Map.of();

    private AbilityDefinitionRegistry() {}

    public static void replaceAll(Map<Identifier, AbilityDefinition> loaded) {
        DEFINITIONS = Map.copyOf(loaded);
    }

    @Nullable
    public static AbilityDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    public static boolean contains(Identifier id) {
        return DEFINITIONS.containsKey(id);
    }

    public static Collection<AbilityDefinition> getAll() {
        return List.copyOf(DEFINITIONS.values());
    }

    /** Wheel-eligible (ACTIVE/TOGGLE) definitions, sorted by {@code sortOrder} then id — the wheel's stable order. */
    public static List<AbilityDefinition> wheelEligibleSorted() {
        List<AbilityDefinition> out = new ArrayList<>();
        for (AbilityDefinition def : DEFINITIONS.values()) {
            if (def.isWheelEligible()) {
                out.add(def);
            }
        }
        out.sort(Comparator.comparingInt(AbilityDefinition::sortOrder)
                .thenComparing(def -> def.id().toString()));
        return out;
    }

    public static int size() {
        return DEFINITIONS.size();
    }
}
