package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.schedule.EntityTypeCache;
import net.minecraft.core.registries.BuiltInRegistries;
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

    public static void replaceAll(Map<Identifier, EcologyProfile> loaded) {
        PROFILES = Map.copyOf(loaded);
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
