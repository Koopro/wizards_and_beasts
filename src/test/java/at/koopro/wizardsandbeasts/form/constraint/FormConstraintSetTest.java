package at.koopro.wizardsandbeasts.form.constraint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transformed-player contract, asserted rather than described.
 *
 * <p>{@link FormConstraintSet} is deliberately free of level, entity and registry, so the two presets
 * the whole mod runs on can be checked without a Minecraft bootstrap. The one that matters most is
 * {@link #beastHands_neverTrapsAnAnimagus()}: it is the line between a discipline and an affliction, and
 * it is the kind of thing a later refactor "tidies" by accident.
 */
class FormConstraintSetTest {

    // -- the presets -----------------------------------------------------------------------------

    @Test
    void beastHands_takesEveryHandConstraint() {
        FormConstraintSet set = FormConstraintSet.BEAST_HANDS;
        assertTrue(set.denies(FormConstraint.NO_ITEM_USE));
        assertTrue(set.denies(FormConstraint.NO_BLOCK_INTERACT));
        assertTrue(set.denies(FormConstraint.NO_BLOCK_BREAK));
        assertTrue(set.denies(FormConstraint.NO_ENTITY_INTERACT));
        assertTrue(set.denies(FormConstraint.NO_INVENTORY));
        assertTrue(set.denies(FormConstraint.NO_ITEM_DROP));
        assertTrue(set.denies(FormConstraint.NO_SPELLCASTING));
    }

    /**
     * An Animagus transformation is voluntary in both directions. A form that could trap a wizard in a
     * beast's body would be a different mechanic wearing the same name.
     */
    @Test
    void beastHands_neverTrapsAnAnimagus() {
        assertFalse(FormConstraintSet.BEAST_HANDS.denies(FormConstraint.NO_VOLUNTARY_EXIT));
    }

    @Test
    void feral_isBeastHandsPlusNoWayOut() {
        assertTrue(FormConstraintSet.FERAL.denies(FormConstraint.NO_VOLUNTARY_EXIT));
        for (FormConstraint constraint : FormConstraintSet.BEAST_HANDS.denied()) {
            assertTrue(FormConstraintSet.FERAL.denies(constraint),
                    "FERAL must be at least as strict as BEAST_HANDS, missing " + constraint);
        }
    }

    @Test
    void none_forbidsNothing() {
        assertTrue(FormConstraintSet.NONE.isEmpty());
        for (FormConstraint constraint : FormConstraint.values()) {
            assertFalse(FormConstraintSet.NONE.denies(constraint));
        }
    }

    // -- composition -----------------------------------------------------------------------------

    /** A player under two systems gets the strictest reading of both, never the last one to answer. */
    @Test
    void union_takesTheStrictestOfBoth() {
        FormConstraintSet a = FormConstraintSet.of(FormConstraint.NO_ITEM_USE);
        FormConstraintSet b = FormConstraintSet.of(FormConstraint.NO_INVENTORY);
        FormConstraintSet both = a.union(b);
        assertTrue(both.denies(FormConstraint.NO_ITEM_USE));
        assertTrue(both.denies(FormConstraint.NO_INVENTORY));
    }

    @Test
    void union_withNoneChangesNothing() {
        assertSame(FormConstraintSet.BEAST_HANDS,
                FormConstraintSet.BEAST_HANDS.union(FormConstraintSet.NONE));
        assertSame(FormConstraintSet.BEAST_HANDS,
                FormConstraintSet.NONE.union(FormConstraintSet.BEAST_HANDS));
    }

    /** An Animagus who is also a werewolf mid-moon is feral: the stricter claim wins. */
    @Test
    void union_ofBeastHandsAndFeralIsFeral() {
        assertTrue(FormConstraintSet.BEAST_HANDS.union(FormConstraintSet.FERAL)
                .denies(FormConstraint.NO_VOLUNTARY_EXIT));
        assertTrue(FormConstraintSet.FERAL.union(FormConstraintSet.BEAST_HANDS)
                .denies(FormConstraint.NO_VOLUNTARY_EXIT));
    }

    @Test
    void union_isCommutative() {
        FormConstraintSet a = FormConstraintSet.of(FormConstraint.NO_ITEM_USE, FormConstraint.NO_INVENTORY);
        FormConstraintSet b = FormConstraintSet.of(FormConstraint.NO_INVENTORY, FormConstraint.NO_SPELLCASTING);
        assertEquals(a.union(b).denied(), b.union(a).denied());
    }

    // -- immutability ----------------------------------------------------------------------------

    @Test
    void with_and_without_leaveTheOriginalAlone() {
        FormConstraintSet base = FormConstraintSet.BEAST_HANDS;
        FormConstraintSet stricter = base.with(FormConstraint.NO_VOLUNTARY_EXIT);
        FormConstraintSet looser = base.without(FormConstraint.NO_ITEM_USE);

        assertFalse(base.denies(FormConstraint.NO_VOLUNTARY_EXIT), "with() mutated the preset");
        assertTrue(base.denies(FormConstraint.NO_ITEM_USE), "without() mutated the preset");
        assertTrue(stricter.denies(FormConstraint.NO_VOLUNTARY_EXIT));
        assertFalse(looser.denies(FormConstraint.NO_ITEM_USE));
    }

    @Test
    void with_returnsTheSameInstanceWhenNothingChanges() {
        assertSame(FormConstraintSet.BEAST_HANDS,
                FormConstraintSet.BEAST_HANDS.with(FormConstraint.NO_ITEM_USE));
    }
}
