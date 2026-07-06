package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.Nullable;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BroomDefinitionRegistry {
    private static final Identifier FALLBACK_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cleansweep_seven");
    /** Volatile-swapped immutable map: readers never observe a mid-reload empty/partial registry. */
    private static volatile Map<Identifier, BroomDefinition> DEFINITIONS = Map.of();

    private BroomDefinitionRegistry() {
    }

    public static void replaceAll(Map<Identifier, BroomDefinition> loaded) {
        DEFINITIONS = Map.copyOf(loaded);
    }

    @Nullable
    public static BroomDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    public static Collection<BroomDefinition> getAll() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static List<BroomDefinition> getByTier(BroomTier tier) {
        List<BroomDefinition> out = new ArrayList<>();
        for (BroomDefinition definition : DEFINITIONS.values()) {
            if (definition.tier() == tier) {
                out.add(definition);
            }
        }
        out.sort(Comparator.comparing(def -> def.displayName().getString()));
        return out;
    }

    public static BroomDefinition getFallback() {
        BroomDefinition fallback = DEFINITIONS.get(FALLBACK_ID);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("Missing fallback broom definition: " + FALLBACK_ID);
    }
}
