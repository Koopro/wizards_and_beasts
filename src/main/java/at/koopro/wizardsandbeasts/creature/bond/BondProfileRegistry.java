package at.koopro.wizardsandbeasts.creature.bond;

import at.koopro.wizardsandbeasts.creature.schedule.EntityTypeCache;
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

    /** Per-type answers, rebuilt whenever {@link #PROFILES} is replaced: {@code tickBond} asks every tick. */
    private static final EntityTypeCache<java.util.Optional<BondProfile>> BY_TYPE =
            new EntityTypeCache<>(id -> java.util.Optional.ofNullable(PROFILES.get(id)));

    private BondProfileRegistry() {}

    public static void replaceAll(Map<Identifier, BondProfile> loaded) {
        PROFILES = Map.copyOf(loaded);
    }

    /** The profile for this creature, or {@code null} when the species does not bond. */
    @Nullable
    public static BondProfile get(Identifier creatureId) {
        return PROFILES.get(creatureId);
    }

    /** The profile for an entity type, or {@code null}: one cached lookup, no registry access after the first. */
    @Nullable
    public static BondProfile of(net.minecraft.world.entity.EntityType<?> type) {
        return BY_TYPE.get(type, PROFILES).orElse(null);
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
