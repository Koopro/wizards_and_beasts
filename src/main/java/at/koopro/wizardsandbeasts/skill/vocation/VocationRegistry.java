package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory store of loaded {@link VocationDefinition}s, populated by {@link VocationLoader} on datapack
 * reload (mirrors {@code BestiaryEntryRegistry}). Always populated regardless of module state — gating
 * controls access, not registration.
 */
@NullMarked
public final class VocationRegistry {
    private static final Map<Identifier, VocationDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<SkillTreeId, VocationDefinition> BY_TREE = new EnumMap<>(SkillTreeId.class);

    private VocationRegistry() {}

    public static void replaceAll(Map<Identifier, VocationDefinition> definitions) {
        BY_ID.clear();
        BY_TREE.clear();
        for (VocationDefinition def : definitions.values()) {
            BY_ID.put(def.id(), def);
            // One Vocation owns a tree; first wins on the (unexpected) duplicate.
            BY_TREE.putIfAbsent(def.tree(), def);
        }
    }

    /**
     * Client-side ingest of the catalogue synced by {@code SyncVocationDefinitionsPayload}. A dedicated
     * server's client never runs {@link VocationLoader}, so without this the selection screen has nothing
     * to list. In single-player both sides share this map and the synced content is identical, which makes
     * the write a no-op in practice.
     */
    public static void setClientDefinitions(java.util.List<VocationDefinition> definitions) {
        Map<Identifier, VocationDefinition> byId = new LinkedHashMap<>();
        for (VocationDefinition def : definitions) {
            byId.put(def.id(), def);
        }
        replaceAll(byId);
    }

    public static @Nullable VocationDefinition get(Identifier id) {
        return BY_ID.get(id);
    }

    public static @Nullable VocationDefinition forTree(SkillTreeId tree) {
        return BY_TREE.get(tree);
    }

    public static Collection<VocationDefinition> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }
}
