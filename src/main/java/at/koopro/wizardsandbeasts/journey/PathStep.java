package at.koopro.wizardsandbeasts.journey;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The steps of the Apprentice's Path, in the order the path suggests them. Each step is a fact some other system
 * already owns (heritage, wand, spell data, bestiary); the path only notices it, pays for it once and points at the
 * next one. The saved form is {@link #id()}, never the ordinal, so steps can be reordered or added between saves.
 */
@NullMarked
public enum PathStep {
    /** A heritage chosen at the ceremony. */
    ARRIVE(Needs.NOTHING),
    /** A wand that has chosen this wizard, in their inventory. */
    WAND(Needs.WAND_HAND),
    /** Any spell cast. */
    FIRST_LIGHT(Needs.WAND_HAND),
    /** A spell beyond the starter charms, which every gameplay route now teaches through a trace. */
    OWN_SPELL(Needs.WAND_HAND),
    /** Any spell practised to Proficient. */
    PRACTICE(Needs.WAND_HAND),
    /** Any creature's journal page at OBSERVED or better. */
    FIELD_STUDY(Needs.BESTIARY);

    /** What a player or server needs before the step applies at all. */
    public enum Needs {
        NOTHING,
        /** A heritage that holds a wand and casts, with wands and spells switched on. */
        WAND_HAND,
        /** The bestiary switched on. */
        BESTIARY
    }

    private static final String KEY = "journey.wizards_and_beasts.step.";

    private final Needs needs;

    PathStep(Needs needs) {
        this.needs = needs;
    }

    public Needs needs() {
        return needs;
    }

    /** Saved and wire form. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable PathStep byId(String id) {
        for (PathStep step : values()) {
            if (step.id().equals(id)) {
                return step;
            }
        }
        return null;
    }

    /** The step's short name: "A wand of your own". */
    public String titleKey() {
        return KEY + id();
    }

    /** What to do and where, written to be read once and acted on. Some take keybind arguments. */
    public String hintKey() {
        return KEY + id() + ".hint";
    }
}
