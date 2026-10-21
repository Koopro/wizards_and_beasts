package at.koopro.wizardsandbeasts.client.heritage;

import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import at.koopro.wizardsandbeasts.client.heritage.gui.HeritageSelectionLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heritage gate's geometry at the screen sizes players get (GUI-scaled 1080p at 2/3/4, 768p and 720p
 * at 3, vanilla's 320x240 floor), with the worst case the data has: Wizardkind's five lineages and a
 * condition row.
 */
class HeritageSelectionLayoutTest {

    private static final int[][] SCREENS = {
            {960, 540}, {640, 360}, {480, 270}, {455, 256}, {427, 240}, {320, 240}};
    /** "Pure-Blood", "Half-Blood", "Muggle-Born", "Squib", "Wizard-Raised" in the vanilla font, padded. */
    private static final int[] FIVE_LINEAGES = {62, 62, 68, 40, 76};

    private static HeritageSelectionLayout at(int[] s, boolean condition) {
        return HeritageSelectionLayout.of(s[0], s[1], 8, FIVE_LINEAGES, condition, 60, 56);
    }

    private static boolean inside(Rect inner, Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    @Test
    void everyRegionStaysOnTheSheet() {
        for (int[] s : SCREENS) {
            for (boolean condition : new boolean[]{false, true}) {
                HeritageSelectionLayout l = at(s, condition);
                String at = s[0] + "x" + s[1] + (condition ? " +condition" : "");
                assertTrue(inside(l.panel(), new Rect(0, 0, s[0], s[1])), at + " panel off screen");
                List<Rect> regions = new ArrayList<>(List.of(l.title(), l.list(), l.dossier(), l.hint(),
                        l.randomise(), l.confirm()));
                regions.addAll(l.lineageChips());
                if (l.preview() != null) {
                    regions.add(l.preview());
                    regions.add(l.traits());
                }
                if (condition) {
                    regions.add(l.condition());
                    regions.add(l.conditionPrev());
                    regions.add(l.conditionNext());
                }
                for (Rect r : regions) {
                    assertTrue(inside(r, l.content()), at + " " + r + " leaves " + l.content());
                }
            }
        }
    }

    @Test
    void allEightHeritagesAreAlwaysVisible() {
        for (int[] s : SCREENS) {
            HeritageSelectionLayout l = at(s, true);
            assertEquals(8, l.rows().size());
            for (Rect row : l.rows()) {
                assertTrue(inside(row, l.list()), s[0] + "x" + s[1] + " heritage row below the list well");
            }
        }
    }

    @Test
    void regionsNeverOverlapAndTheDossierHasRoom() {
        for (int[] s : SCREENS) {
            HeritageSelectionLayout l = at(s, true);
            String at = s[0] + "x" + s[1];
            assertFalse(overlap(l.list(), l.dossier()), at + " list/dossier");
            assertFalse(overlap(l.randomise(), l.confirm()), at + " randomise/confirm");
            assertFalse(overlap(l.hint(), l.randomise()), at + " hint/randomise");
            for (Rect chip : l.lineageChips()) {
                assertFalse(overlap(chip, l.dossier()), at + " lineage chip on the dossier");
                assertFalse(overlap(chip, l.condition()), at + " lineage chip on the condition row");
            }
            assertFalse(overlap(l.condition(), l.dossier()), at + " condition/dossier");
            if (l.preview() != null) {
                assertFalse(overlap(l.preview(), l.dossier()), at + " preview/dossier");
                assertFalse(overlap(l.preview(), l.traits()), at + " preview/traits");
                assertTrue(l.preview().h() >= 60, at + " preview too short to show a player");
            }
            assertTrue(l.dossier().h() >= 60, at + " dossier " + l.dossier().h() + "px tall");
            assertTrue(l.dossier().w() >= 140, at + " dossier " + l.dossier().w() + "px wide");
        }
    }

    @Test
    void threeColumnsWhereTheyFitTwoBelow() {
        assertEquals(HeritageSelectionLayout.Mode.THREE, at(SCREENS[0], false).mode());
        assertEquals(HeritageSelectionLayout.Mode.THREE, at(new int[]{427, 240}, false).mode());
        HeritageSelectionLayout narrow = at(new int[]{320, 240}, false);
        assertEquals(HeritageSelectionLayout.Mode.TWO, narrow.mode());
        assertNull(narrow.preview());
        assertNull(narrow.traits());
        assertNull(narrow.condition(), "no condition row when the lineage can carry none");
        assertNotNull(at(new int[]{320, 240}, true).condition());
    }

    @Test
    void confirmCardFitsAndHoldsItsButtons() {
        for (int[] s : SCREENS) {
            HeritageSelectionLayout l = at(s, false);
            assertTrue(inside(l.overlay(), new Rect(0, 0, s[0], s[1])));
            assertTrue(inside(l.overlayCancel(), l.overlay()));
            assertTrue(inside(l.overlaySeal(), l.overlay()));
            assertFalse(overlap(l.overlayCancel(), l.overlaySeal()));
        }
    }

    @Test
    void rowHitTestingMatchesTheRows() {
        HeritageSelectionLayout l = at(SCREENS[2], false);
        for (int i = 0; i < l.rows().size(); i++) {
            Rect r = l.rows().get(i);
            assertEquals(i, l.rowAt(r.x() + 1, r.y() + r.h() / 2.0));
            assertEquals(-1, l.rowAt(r.x() + 1, r.bottom()), "the gap between rows is no row");
        }
        assertEquals(-1, l.rowAt(l.dossier().x() + 2, l.dossier().y() + 2));
    }
}
