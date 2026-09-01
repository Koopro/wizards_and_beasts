package at.koopro.wizardsandbeasts.bestiary.harvest;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The loaded {@link HarvestRule}s, indexed by the entity type that yields them.
 *
 * <p>Rules name a <em>bestiary entry</em>, because that is what holds a tier; loot arrives with an
 * <em>entity type</em>. Resolving between the two needs {@link BestiaryEntryRegistry} to be populated,
 * and reload-listener order is not guaranteed — so the index is built on demand and rebuilt if it is
 * empty while entries exist. That is the same guard {@code BestiaryDiscoveryHandler} already uses for
 * the identical problem.
 *
 * <p>Volatile-swapped immutable maps, so a kill landing mid-reload sees one whole generation or the
 * other rather than a half-filled map. Server-side only: rules are never sent to clients, because
 * nothing on a client decides loot.
 */
@NullMarked
public final class HarvestTable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Identifier, HarvestRule> RULES = Map.of();
    private static volatile Map<Identifier, List<HarvestRule>> BY_ENTITY_TYPE = Map.of();

    private HarvestTable() {}

    public static void replaceAll(Map<Identifier, HarvestRule> rules) {
        RULES = Map.copyOf(rules);
        BY_ENTITY_TYPE = Map.of();
        rebuildIndex();
    }

    /** Rules yielded by {@code entityType}, in load order. Empty — never null — when none apply. */
    public static List<HarvestRule> forEntityType(Identifier entityType) {
        Map<Identifier, List<HarvestRule>> index = BY_ENTITY_TYPE;
        if (index.isEmpty() && !RULES.isEmpty()) {
            // Rules loaded before the entries they point at. Build now rather than answering "nothing
            // applies" for the rest of the session.
            rebuildIndex();
            index = BY_ENTITY_TYPE;
        }
        return index.getOrDefault(entityType, List.of());
    }

    /** True when no rule anywhere is authored — lets the loot hook return before it does any work. */
    public static boolean isEmpty() {
        return RULES.isEmpty();
    }

    public static int count() {
        return RULES.size();
    }

    public static Map<Identifier, HarvestRule> all() {
        return RULES;
    }

    /**
     * Resolves every rule's entry to an entity type.
     *
     * <p>A rule naming an entry that does not exist, or one with no {@code entityType}, is dropped with
     * a warning rather than silently ignored: an id into another registry that matches nothing is this
     * repository's classic invisible datapack failure, and the rule would otherwise simply never fire.
     */
    private static void rebuildIndex() {
        Map<Identifier, BestiaryEntry> known = new HashMap<>();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            known.put(entry.id(), entry);
        }
        if (known.isEmpty()) {
            // Entries not loaded yet. Leave the index empty so forEntityType retries later.
            return;
        }

        Map<Identifier, List<HarvestRule>> index = new HashMap<>();
        int dropped = 0;
        for (Map.Entry<Identifier, HarvestRule> pair : RULES.entrySet()) {
            HarvestRule rule = pair.getValue();
            BestiaryEntry entry = known.get(rule.entry());
            if (entry == null) {
                LOGGER.warn("[W&B] Harvest rule {} names bestiary entry {}, which does not exist.",
                        pair.getKey(), rule.entry());
                dropped++;
                continue;
            }
            if (entry.entityType().isEmpty()) {
                LOGGER.warn("[W&B] Harvest rule {} targets bestiary entry {}, which has no entityType, "
                        + "so nothing can ever drop it.", pair.getKey(), rule.entry());
                dropped++;
                continue;
            }
            index.computeIfAbsent(entry.entityType().get(), k -> new ArrayList<>(1)).add(rule);
        }
        index.replaceAll((type, list) -> Collections.unmodifiableList(list));
        BY_ENTITY_TYPE = Collections.unmodifiableMap(index);

        if (dropped > 0) {
            LOGGER.warn("[W&B] {} harvest rule(s) dropped; {} active.", dropped, RULES.size() - dropped);
        }
    }
}
