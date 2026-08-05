package at.koopro.wizardsandbeasts.animagus;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every loaded {@link AnimagusFormDefinition}, keyed by its datapack id.
 * <p>
 * Mirrors {@code BestiaryEntryRegistry}: the map is swapped wholesale on reload rather than mutated,
 * so a reader mid-reload sees either the old registry or the new one, never a half-filled map.
 */
@NullMarked
public final class AnimagusFormRegistry {

    private static volatile Map<Identifier, AnimagusFormDefinition> FORMS = Map.of();

    /** Client-side cache, populated on sync receipt. Read only on the client. */
    private static volatile Map<Identifier, AnimagusFormDefinition> CLIENT_FORMS = Map.of();

    private AnimagusFormRegistry() {}

    public static void replaceAll(Map<Identifier, AnimagusFormDefinition> forms) {
        FORMS = Map.copyOf(forms);
    }

    public static @Nullable AnimagusFormDefinition get(Identifier id) {
        return FORMS.get(id);
    }

    public static Collection<AnimagusFormDefinition> getAll() {
        return FORMS.values();
    }

    public static Set<Identifier> ids() {
        return FORMS.keySet();
    }

    public static boolean contains(Identifier id) {
        return FORMS.containsKey(id);
    }

    /** Replace the client cache with the synced form list. */
    public static void setClientForms(Map<Identifier, AnimagusFormDefinition> forms) {
        CLIENT_FORMS = Map.copyOf(forms);
    }

    public static @Nullable AnimagusFormDefinition clientGet(Identifier id) {
        return CLIENT_FORMS.get(id);
    }

    public static Collection<AnimagusFormDefinition> clientGetAll() {
        return CLIENT_FORMS.values();
    }

    /**
     * Every form carrying the given capability, in id order so command suggestions and any future
     * listing are stable across reloads.
     */
    public static List<Identifier> withCapability(AnimagusCapability capability) {
        Map<Identifier, AnimagusFormDefinition> snapshot = new HashMap<>(FORMS);
        return snapshot.entrySet().stream()
                .filter(e -> e.getValue().hasCapability(capability))
                .map(Map.Entry::getKey)
                .sorted(Identifier::compareTo)
                .toList();
    }
}
