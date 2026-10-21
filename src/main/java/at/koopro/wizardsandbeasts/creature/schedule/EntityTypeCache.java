package at.koopro.wizardsandbeasts.creature.schedule;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A per-entity-type view of a datapack table keyed by creature id (ecology profiles, bond profiles), so a hot path
 * asks one identity-hashed map instead of the entity-type registry and then the table.
 *
 * <p>Invalidated exactly when the table is: every read passes the table's current map, and the cache is rebuilt the
 * first time it sees a different map object — the registries swap their whole map on a datapack reload, so the cache
 * can never answer from an old one. The check and the swap are one volatile read and (on a reload) one write.
 *
 * <p><b>Cost:</b> one {@code ConcurrentHashMap} entry per entity type ever asked about (a few hundred at most); after
 * the first lookup per type, a read is one hash lookup and no allocation.
 *
 * @param <V> the table's value; tables whose answer may be absent use {@code Optional}
 */
@NullMarked
public final class EntityTypeCache<V> {

    private record Generation<V>(Object source, Map<EntityType<?>, V> byType) {}

    private final Function<Identifier, V> lookup;
    private volatile Generation<V> generation = new Generation<>(new Object(), new ConcurrentHashMap<>());

    /** @param lookup the table's own lookup by creature id; must never return {@code null} */
    public EntityTypeCache(Function<Identifier, V> lookup) {
        this.lookup = lookup;
    }

    /** The table's answer for {@code type}, given the table's current backing map {@code source}. */
    public V get(EntityType<?> type, Object source) {
        Generation<V> current = generation;
        if (current.source() != source) {
            current = new Generation<>(source, new ConcurrentHashMap<>());
            generation = current;
        }
        V cached = current.byType().get(type);
        if (cached == null) {
            cached = lookup.apply(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            current.byType().put(type, cached);
        }
        return cached;
    }
}
