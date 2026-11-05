package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.schedule.EntityTypeCache;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/** Loaded ecology profiles by creature id. A species with no file has {@link EcologyProfile#NONE}. */
@NullMarked
public final class EcologyProfileRegistry {

    private static volatile Map<Identifier, EcologyProfile> PROFILES = Map.of();

    /** Per-type answers, rebuilt whenever {@link #PROFILES} is replaced; keeps the registry out of sensing loops. */
    private static final EntityTypeCache<EcologyProfile> BY_TYPE = new EntityTypeCache<>(EcologyProfileRegistry::get);

    private EcologyProfileRegistry() {}

    /** Every capability some loaded profile has: lets an event skip its query when nobody could care. */
    private static volatile java.util.Set<EcologyCapability> PRESENT = java.util.EnumSet.noneOf(EcologyCapability.class);

    /** What the datapacks declare, before overrides. */
    private static volatile Map<Identifier, EcologyProfile> LOADED = Map.of();
    /** Test overrides, laid over {@link #LOADED}; each removed only by its own handle. */
    private static final Map<Identifier, EcologyProfile> OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void replaceAll(Map<Identifier, EcologyProfile> loaded) {
        LOADED = Map.copyOf(loaded);
        rebuild();
    }

    private static synchronized void rebuild() {
        Map<Identifier, EcologyProfile> merged = new java.util.HashMap<>(LOADED);
        merged.putAll(OVERRIDES);
        java.util.Set<EcologyCapability> present = java.util.EnumSet.noneOf(EcologyCapability.class);
        merged.values().forEach(p -> present.addAll(p.capabilities()));
        PRESENT = present;
        PROFILES = Map.copyOf(merged);
    }

    /** Whether any loaded creature has {@code capability}. */
    public static boolean anyHas(EcologyCapability capability) {
        return PRESENT.contains(capability);
    }

    /**
     * Lays a profile over one creature's until the returned handle is closed — for GameTests that need a creature with
     * exactly the capabilities under test, whatever the shipped data says. Survives a reload; not for gameplay.
     */
    public static AutoCloseable override(Identifier creatureId, EcologyProfile profile) {
        // A layer, not a snapshot: closing removes this override only, so two scenarios running side by side cannot
        // restore each other's "before" over a profile the other has just set.
        OVERRIDES.put(creatureId, profile);
        rebuild();
        return () -> {
            OVERRIDES.remove(creatureId, profile);
            rebuild();
        };
    }

    /** Whether any creature has an ecology at all. */
    public static boolean anyLoaded() {
        return !PROFILES.isEmpty();
    }

    public static EcologyProfile get(Identifier creatureId) {
        return PROFILES.getOrDefault(creatureId, EcologyProfile.NONE);
    }

    /** The profile for a type: one cached hash lookup, no registry access after the first ask per type. */
    public static EcologyProfile of(EntityType<?> type) {
        return BY_TYPE.get(type, PROFILES);
    }

    public static Map<Identifier, EcologyProfile> snapshot() {
        return PROFILES;
    }
}
