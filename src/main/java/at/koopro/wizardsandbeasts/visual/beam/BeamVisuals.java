package at.koopro.wizardsandbeasts.visual.beam;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composes a beam's effective look: its authored default ({@link BeamVisualDefaults}) with the administrator's
 * overrides laid on top, property by property. An override that does not parse is skipped, so a corrupt stored value
 * or a stale key can never break a beam — it just falls back to the default for that one property.
 */
@NullMarked
public final class BeamVisuals {

    private BeamVisuals() {}

    /** {@code base} with every valid entry of {@code overrides} (property id → text) applied. */
    public static BeamVisual apply(BeamVisual base, Map<String, String> overrides) {
        BeamVisual out = base;
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            BeamVisualProperty property = BeamVisualProperty.byId(entry.getKey());
            if (property != null) {
                out = property.with(out, entry.getValue()).orElse(out);
            }
        }
        return out;
    }

    /** The effective look of {@code spellId}, or null when it is not a beam spell. */
    public static @Nullable BeamVisual effective(@Nullable String spellId, int color, Map<String, String> overrides) {
        BeamVisual base = BeamVisualDefaults.forSpell(spellId, color);
        return base == null ? null : apply(base, overrides);
    }

    /** Only the known, valid entries of {@code overrides}, in canonical text. */
    public static Map<String, String> sanitize(Map<String, String> overrides) {
        Map<String, String> out = new LinkedHashMap<>();
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            String raw = overrides.get(property.id());
            String canonical = raw == null ? null : property.canonical(raw);
            if (canonical != null) {
                out.put(property.id(), canonical);
            }
        }
        return out;
    }

    /** Every property of {@code visual} as canonical text, in editor order. */
    public static Map<String, String> toText(BeamVisual visual) {
        Map<String, String> out = new LinkedHashMap<>();
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            out.put(property.id(), property.text(visual));
        }
        return out;
    }

    /** The properties where {@code visual} differs from {@code base}, as canonical text. */
    public static Map<String, String> diff(BeamVisual base, BeamVisual visual) {
        Map<String, String> out = new LinkedHashMap<>();
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            String value = property.text(visual);
            if (!value.equals(property.text(base))) {
                out.put(property.id(), value);
            }
        }
        return out;
    }
}
