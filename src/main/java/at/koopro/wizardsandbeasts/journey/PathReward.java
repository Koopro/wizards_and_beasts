package at.koopro.wizardsandbeasts.journey;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The parts a step can pay. Which parts a step pays is decided here, purely, so a part whose system is switched off
 * is neither handed over nor promised in the toast.
 */
@NullMarked
public enum PathReward {
    HANDBOOK,
    RECIPES,
    STARTER_ON_WHEEL,
    TWO_SICKLES,
    SKILL_POINT,
    DUELLING_DUMMY,
    GALLEON,
    FIVE_SICKLES;

    /** What is switched on, as far as the rewards care. */
    public record Systems(boolean handbook, boolean skillTrees) {}

    public String translationKey() {
        return "journey.wizards_and_beasts.reward." + name().toLowerCase(Locale.ROOT);
    }

    /** The parts {@code step} pays with these systems on, in the order the toast names them. */
    public static List<PathReward> of(PathStep step, Systems systems) {
        List<PathReward> out = new ArrayList<>(2);
        switch (step) {
            case ARRIVE -> {
                if (systems.handbook()) {
                    out.add(HANDBOOK);
                }
                out.add(RECIPES);
            }
            case WAND -> out.add(STARTER_ON_WHEEL);
            case FIRST_LIGHT -> out.add(TWO_SICKLES);
            case OWN_SPELL -> {
                if (systems.skillTrees()) {
                    out.add(SKILL_POINT);
                }
                out.add(DUELLING_DUMMY);
            }
            case PRACTICE -> out.add(GALLEON);
            case FIELD_STUDY -> out.add(FIVE_SICKLES);
        }
        return out;
    }
}
