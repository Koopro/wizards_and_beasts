package at.koopro.wizardsandbeasts.visual.beam;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A named beam look. Presets are <em>templates</em>: applying one copies its values into a spell's settings (as drafts
 * the administrator then applies), and editing a spell never edits a preset.
 *
 * <p>Built-in presets are each beam spell's authored default ({@code builtin:<spell>}); they are computed, never stored,
 * so they cannot be renamed, overwritten or deleted. Custom presets live in world data with ids that can never
 * contain a colon, so the two id spaces cannot collide.
 *
 * @param values every {@link BeamVisualProperty} as canonical text
 */
@NullMarked
public record BeamPreset(String id, String name, boolean builtin, Map<String, String> values) {

    public static final String BUILTIN_PREFIX = "builtin:";
    public static final int MAX_NAME = 32;
    public static final int MAX_CUSTOM = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _'\\-]{1,32}");

    public BeamPreset {
        values = Map.copyOf(values);
    }

    public static String builtinId(String spellKey) {
        return BUILTIN_PREFIX + spellKey;
    }

    /** The built-in preset of each beam spell, given its colour. */
    public static List<BeamPreset> builtins(java.util.function.ToIntFunction<String> colorOf) {
        List<BeamPreset> out = new ArrayList<>();
        for (String spell : BeamVisualDefaults.SPELLS) {
            BeamVisual visual = BeamVisualDefaults.forSpell(spell, colorOf.applyAsInt(spell));
            if (visual != null) {
                out.add(new BeamPreset(builtinId(spell), spell, true, BeamVisuals.toText(visual)));
            }
        }
        return out;
    }

    /** Whether {@code name} is an acceptable preset name (letters, digits, space, {@code _ ' -}, 1–32 chars). */
    public static boolean validName(@Nullable String name) {
        return name != null && NAME.matcher(name).matches() && !name.isBlank();
    }

    /** The storage id a custom preset named {@code name} gets: lower case, runs of other characters as one '_'. */
    public static String idFor(String name) {
        String id = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return id.isEmpty() ? "preset" : id;
    }

    /** The look this preset describes, over {@code base} for any property it somehow lacks. */
    public BeamVisual visual(BeamVisual base) {
        return BeamVisuals.apply(base, values);
    }
}
