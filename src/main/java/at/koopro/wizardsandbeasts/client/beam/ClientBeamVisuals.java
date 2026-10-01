package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisuals;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * This client's copy of the server's beam visual overrides ({@code BeamVisualSyncS2CPayload}). Replaced whole on each
 * sync, cleared on logout, so one world's looks never leak into the next server.
 *
 * <p>Read when a beam is spawned and when the table changes (live beams are restyled then) — not every frame.
 */
@NullMarked
public final class ClientBeamVisuals {

    private static volatile Map<String, Map<String, String>> overrides = Map.of();

    private ClientBeamVisuals() {}

    public static void accept(Map<String, Map<String, String>> table) {
        Map<String, Map<String, String>> copy = new HashMap<>();
        table.forEach((spell, values) -> {
            String key = BeamVisualDefaults.key(spell);
            if (key != null) {
                copy.put(key, BeamVisuals.sanitize(values));
            }
        });
        overrides = Map.copyOf(copy);
        BeamChannelClient.restyleAll();
    }

    public static Map<String, String> overridesFor(String spellKey) {
        return overrides.getOrDefault(spellKey, Map.of());
    }

    /** The look this server wants for {@code spell}, or null when it is not a beam spell. */
    public static @Nullable BeamVisual effective(@Nullable Spell spell) {
        if (spell == null) {
            return null;
        }
        String key = BeamVisualDefaults.key(spell.getId());
        return key == null ? null : BeamVisuals.effective(key, spell.getColor(), overridesFor(key));
    }

    /** How many beam spells this server has customised. */
    public static int overriddenCount() {
        return overrides.size();
    }

    public static void clear() {
        overrides = Map.of();
    }
}
