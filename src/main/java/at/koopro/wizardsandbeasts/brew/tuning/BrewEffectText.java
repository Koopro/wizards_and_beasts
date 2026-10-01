package at.koopro.wizardsandbeasts.brew.tuning;

import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The text form of a brew's drink-time effect list, as an administrator edits it and as it is stored:
 * <pre>minecraft:speed 1200 0; -minecraft:luck 600 1; wizards_and_beasts:wolfsbane 3600 0 ambient</pre>
 * One entry per effect: {@code [-]<effect id> <duration ticks> <amplifier> [ambient]}. A leading {@code -} keeps
 * the row but switches it off — the effect editor's "enabled" toggle — so an administrator can disable an effect
 * without losing its numbers.
 *
 * <p>One text value means the whole list is one admin setting: it goes through the same authorisation, validation,
 * confirmation, history and undo as any other, and a change is atomic. {@link #parse} is strict — an unknown
 * effect id, a duration or amplifier out of bounds, a duplicate effect or a malformed entry makes the whole value
 * invalid — and {@link #format} is canonical, so two spellings of the same list compare equal.
 */
@NullMarked
public final class BrewEffectText {

    /** One hour. Longer than any shipped brew, short enough that a typo is not a permanent state. */
    public static final int MAX_DURATION = 72_000;
    /** Amplifier 0–9 (level I–X). Vanilla allows more; nothing a brew should grant does. */
    public static final int MAX_AMPLIFIER = 9;
    public static final int MAX_EFFECTS = 12;
    private static final String AMBIENT = "ambient";

    private BrewEffectText() {}

    /** One row of the effect editor. */
    public record Line(Identifier effect, int duration, int amplifier, boolean ambient, boolean enabled) {

        public BrewEffect.ApplyEffects.EffectSpec spec() {
            return new BrewEffect.ApplyEffects.EffectSpec(effect, duration, amplifier, ambient);
        }

        public static Line of(BrewEffect.ApplyEffects.EffectSpec spec) {
            return new Line(spec.id(), spec.duration(), spec.amplifier(), spec.ambient(), true);
        }
    }

    /**
     * The canonical list, or null when the text is not a valid effect list.
     *
     * @param knownEffect whether an id names a registered mob effect
     */
    public static @Nullable List<Line> parse(String text, Predicate<Identifier> knownEffect) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] entries = trimmed.split("[;\\n]");
        if (entries.length > MAX_EFFECTS) {
            return null;
        }
        List<Line> out = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            boolean enabled = !entry.startsWith("-");
            if (!enabled) {
                entry = entry.substring(1).trim();
            }
            String[] parts = entry.split("\\s+");
            if (parts.length < 3 || parts.length > 4) {
                return null;
            }
            Identifier id = Identifier.tryParse(parts[0].toLowerCase(Locale.ROOT));
            if (id == null || !knownEffect.test(id) || !seen.add(id)) {
                return null;
            }
            Integer duration = integer(parts[1]);
            Integer amplifier = integer(parts[2]);
            if (duration == null || amplifier == null || duration < 1 || duration > MAX_DURATION
                    || amplifier < 0 || amplifier > MAX_AMPLIFIER) {
                return null;
            }
            boolean ambient = false;
            if (parts.length == 4) {
                if (!parts[3].equalsIgnoreCase(AMBIENT)) {
                    return null;
                }
                ambient = true;
            }
            out.add(new Line(id, duration, amplifier, ambient, enabled));
        }
        return List.copyOf(out);
    }

    private static @Nullable Integer integer(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public static String format(List<Line> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (Line line : lines) {
            out.add((line.enabled() ? "" : "-") + line.effect() + " " + line.duration() + " " + line.amplifier()
                    + (line.ambient() ? " " + AMBIENT : ""));
        }
        return String.join("; ", out);
    }

    public static String formatSpecs(List<BrewEffect.ApplyEffects.EffectSpec> specs) {
        return format(specs.stream().map(Line::of).toList());
    }
}
