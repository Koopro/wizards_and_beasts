package at.koopro.wizardsandbeasts.creature.companion;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Where a creature stands with its person, in the order it gets there.
 *
 * <p>Derived, never stored as truth: {@link CompanionRules#stage} reads the bond level and the training a
 * creature has, so a datapack that moves a threshold moves every creature with it. The last stage seen is
 * kept only to notice a change ({@code BondState#lastStage}).
 */
@NullMarked
public enum CompanionStage {
    /** No owner, or barely tolerates you. */
    WILD,
    /** Lets you near; takes food from your hand. */
    CURIOUS,
    /** Will stay and follow when asked. */
    TRUSTING,
    /** Yours: returns, searches and helps. */
    BONDED,
    /** Taught: defends, patrols and fights at your word. */
    TRAINED,
    /** Mastered: the full trust a Magizoologist spends years on. */
    MASTERED;

    public String key() {
        return "companion.wizards_and_beasts.stage." + name().toLowerCase(Locale.ROOT);
    }

    public boolean atLeast(CompanionStage other) {
        return ordinal() >= other.ordinal();
    }
}
