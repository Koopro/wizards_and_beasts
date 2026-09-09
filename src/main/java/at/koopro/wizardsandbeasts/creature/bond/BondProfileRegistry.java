package at.koopro.wizardsandbeasts.creature.bond;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Runtime store of loaded {@link BondProfile}s, keyed by creature id, mirroring
 * {@code CreatureDefinitionRegistry}.
 *
 * <p>The map is volatile-swapped whole, so a creature ticking during a reload sees either the old
 * table or the new one and never a half-filled map.
 */
@NullMarked
public final class BondProfileRegistry {

    private static volatile Map<Identifier, BondProfile> PROFILES = Map.of();

    private BondProfileRegistry() {}

    public static void replaceAll(Map<Identifier, BondProfile> loaded) {
        PROFILES = Map.copyOf(loaded);
    }

    /** The profile for this creature, or {@code null} when the species does not bond. */
    @Nullable
    public static BondProfile get(Identifier creatureId) {
        return PROFILES.get(creatureId);
    }

    /** True when a profile is loaded for this creature — the whole of the opt-in test. */
    public static boolean bonds(Identifier creatureId) {
        return PROFILES.containsKey(creatureId);
    }

    public static Set<Identifier> ids() {
        return PROFILES.keySet();
    }

    public static int size() {
        return PROFILES.size();
    }
}
