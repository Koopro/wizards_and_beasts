package at.koopro.wizardsandbeasts.admin.spell;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The per-spell values an administrator may change. Each maps to exactly one accessor the cast or learning
 * pipeline already reads (see {@link at.koopro.wizardsandbeasts.spell.tuning.SpellOverride}); a value that
 * exists only in the UI does not belong here.
 *
 * <p>To add one: a field on {@code SpellOverride} (and its codec and sync flags), the {@code SpellTuning}
 * accessor the pipeline reads, a case in {@link SpellSettingProvider}, and three lang keys
 * ({@code admin.wizards_and_beasts.spell_property.<id>}, {@code .desc}, {@code .warning}).
 */
@NullMarked
public enum SpellProperty {
    ENABLED,
    COOLDOWN_TICKS,
    DAMAGE,
    RANGE,
    PREREQUISITE,
    REQUIRED_SKILL;

    /** Stable id: the last segment of the setting id and the command argument. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable SpellProperty byId(String id) {
        for (SpellProperty property : values()) {
            if (property.id().equals(id)) {
                return property;
            }
        }
        return null;
    }
}
