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
    /**
     * The first-year book list and the money for it, paid into the vault: the Grade 1 Standard Book of Spells and
     * The Dark Forces are bought at Flourish and Blotts (owner 2026-11-27, the canon route of PS ch.5).
     */
    FIRST_YEAR_FUNDS,
    /** The first-year books themselves, when there is no shop to buy them in (the economy switched off). */
    FIRST_YEAR_BOOKS,
    TWO_SICKLES,
    SKILL_POINT,
    DUELLING_DUMMY,
    GALLEON,
    FIVE_SICKLES;

    /**
     * What is switched on, as far as the rewards care.
     *
     * @param bookshop whether books can be bought (the economy is on, so Flourish and Blotts trades)
     */
    public record Systems(boolean handbook, boolean skillTrees, boolean bookshop) {}

    /** The book money: the two first-year books at Flourish and Blotts (2 Sickles each) with a Sickle to spare. */
    public static final long FIRST_YEAR_FUNDS_KNUTS = 5 * 29;

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
            case WAND -> out.add(systems.bookshop() ? FIRST_YEAR_FUNDS : FIRST_YEAR_BOOKS);
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
