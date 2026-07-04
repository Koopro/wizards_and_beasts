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

    public static @Nullable VocationDefinition get(Identifier id) {
        return BY_ID.get(id);
    }

    public static @Nullable VocationDefinition forTree(SkillTreeId tree) {
        return BY_TREE.get(tree);
    }

    public static Collection<VocationDefinition> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Symmetric opposition: true if either side lists the other. Absent ids never oppose. */
    public static boolean areOpposed(Identifier a, Identifier b) {
        if (a.equals(b)) {
            return false;
        }
        VocationDefinition da = BY_ID.get(a);
        VocationDefinition db = BY_ID.get(b);
        return (da != null && da.oppositions().contains(b))
                || (db != null && db.oppositions().contains(a));
    }
}
