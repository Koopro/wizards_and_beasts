package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * What a page may show, decided in one place. Pure: a layout and the player's depth of knowledge in, one
 * {@link SectionView} per section out.
 *
 * <p>The journal never switches a whole page from hidden to visible. Each section opens at its own depth; some show a
 * partial view first. A section the player has not reached is drawn as struck-through pencil under its heading — and
 * <b>every</b> page draws the same headings, whether or not the creature has anything to say under them, so a locked
 * "Harvesting" line tells a player nothing about whether this creature yields anything. An undiscovered creature has
 * no page at all.
 *
 * <p>The client calls this with tiers the server decided ({@code EncounterRule}) and notes the server wrote; it has
 * no way to raise its own depth. {@code JournalRevealTest} holds the rule.
 */
@NullMarked
public final class JournalReveal {

    /** How much of a section is shown. */
    public enum State {
        /** Not yet: heading and a struck line, plus how to earn it. */
        LOCKED,
        /** What the player could partly know at this depth. */
        PARTIAL,
        /** Everything. */
        OPEN
    }

    public record SectionView(JournalSection section, JournalLayout.Page page, State state, DiscoveryTier nextReveal) {}

    private JournalReveal() {}

    /** Every section of the layout at this depth; empty for an undiscovered creature. */
    public static List<SectionView> reveal(JournalLayout layout, DiscoveryTier tier) {
        List<SectionView> out = new ArrayList<>();
        if (tier == DiscoveryTier.UNKNOWN) {
            return out;
        }
        for (JournalLayout.Slot slot : layout.slots()) {
            State state = tier.atLeast(slot.reveal()) ? State.OPEN
                    : slot.partial().isPresent() && tier.atLeast(slot.partial().get()) ? State.PARTIAL
                    : State.LOCKED;
            DiscoveryTier next = state == State.LOCKED && slot.partial().isPresent() ? slot.partial().get() : slot.reveal();
            out.add(new SectionView(slot.section(), slot.page(), state, next));
        }
        return out;
    }

    /** The state of one section, or {@code LOCKED} when the layout leaves it out. */
    public static State stateOf(JournalLayout layout, DiscoveryTier tier, JournalSection section) {
        for (SectionView view : reveal(layout, tier)) {
            if (view.section() == section) {
                return view.state();
            }
        }
        return State.LOCKED;
    }
}
