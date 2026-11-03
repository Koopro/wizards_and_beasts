package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import at.koopro.wizardsandbeasts.client.spell.gui.SpellMenuLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spell loadout's geometry at the screen sizes players actually get.
 *
 * <p>Sizes are GUI-scaled: 1920x1080 at GUI 2/3/4, 1366x768 at GUI 3 (Auto), 1280x720 at GUI 3, and
 * vanilla's floor of 320x240. Painting and hit-testing both read {@link SpellMenuLayout}, so what holds
 * here holds for what the player sees and what the player can click.
 */
class SpellMenuLayoutTest {

    private static final int[][] SCREENS = {
            {960, 540}, {640, 360}, {480, 270}, {455, 256}, {427, 240}, {320, 240}};
    /** Roughly "All", "Combat", "Utility", "Defence", "Dark Arts" in the vanilla font, plus padding. */
    private static final int[] CHIPS = {23, 45, 45, 49, 55};

    private static SpellMenuLayout at(int[] s) {
        return SpellMenuLayout.of(s[0], s[1], CHIPS);
    }

    private static boolean inside(Rect inner, Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    @Test
    void everyRegionStaysOnTheSheetAndOnTheScreen() {
        for (int[] s : SCREENS) {
            SpellMenuLayout l = at(s);
            String at = s[0] + "x" + s[1];
            assertTrue(inside(l.panel(), new Rect(0, 0, s[0], s[1])), at + " panel off screen");
            List<Rect> regions = new ArrayList<>(List.of(l.title(), l.skills(), l.library(), l.wand(),
                    l.detail(), l.hint()));
            regions.addAll(l.sockets());
            regions.addAll(l.bindings());
            for (Rect r : regions) {
                assertTrue(inside(r, l.content()), at + " " + r + " leaves the content area " + l.content());
            }
            for (Rect r : List.of(l.search(), l.list(), l.scrollbar())) {
                assertTrue(inside(r, l.library()), at + " " + r + " leaves the library well");
            }
            for (Rect r : l.chips()) {
                assertTrue(inside(r, l.library()), at + " chip " + r + " leaves the library well");
            }
        }
    }

    @Test
    void columnsNeverOverlap() {
        for (int[] s : SCREENS) {
            SpellMenuLayout l = at(s);
            String at = s[0] + "x" + s[1];
            assertFalse(overlap(l.library(), l.wand()), at + " library/wand");
            assertFalse(overlap(l.library(), l.detail()), at + " library/detail");
            assertFalse(overlap(l.wand(), l.detail()), at + " wand/detail");
            assertFalse(overlap(l.title(), l.skills()), at + " title/skills");
            assertFalse(overlap(l.list(), l.scrollbar()), at + " list/scrollbar");
            for (Rect socket : l.sockets()) {
                assertTrue(inside(socket, l.wand()), at + " socket outside the wand column");
                for (Rect row : l.bindings()) {
                    assertFalse(overlap(socket, row), at + " socket under a bound-spell row");
                }
            }
        }
    }

    @Test
    void socketsSitInTheHudDiamondsOrder() {
        for (int[] s : SCREENS) {
            List<Rect> k = at(s).sockets();
            Rect up = k.get(0), right = k.get(1), down = k.get(2), left = k.get(3);
            assertEquals(up.x(), down.x(), "up and down share a column");
            assertEquals(left.y(), right.y(), "left and right share a row");
            assertTrue(up.y() < left.y() && left.y() < down.y(), "up above the middle row, down below");
            assertTrue(left.x() < up.x() && up.x() < right.x(), "left of the column, right of it");
        }
    }

    @Test
    void theListShowsEnoughRowsAndTheDetailsHaveRoom() {
        for (int[] s : SCREENS) {
            SpellMenuLayout l = at(s);
            String at = s[0] + "x" + s[1];
            assertTrue(l.visibleRows() >= 6, at + " only " + l.visibleRows() + " rows");
            assertTrue(l.detail().w() >= 120, at + " details " + l.detail().w() + "px wide");
            assertTrue(l.detail().h() >= 48, at + " details " + l.detail().h() + "px tall");
        }
    }

    @Test
    void wideOnlyWhereThreeColumnsFit() {
        assertEquals(SpellMenuLayout.Mode.WIDE, at(new int[]{960, 540}).mode());
        assertEquals(SpellMenuLayout.Mode.WIDE, at(new int[]{640, 360}).mode());
        assertEquals(SpellMenuLayout.Mode.WIDE, at(new int[]{480, 270}).mode());
        assertEquals(SpellMenuLayout.Mode.STACKED, at(new int[]{455, 256}).mode());
        assertEquals(SpellMenuLayout.Mode.STACKED, at(new int[]{320, 240}).mode());
        assertEquals(4, at(new int[]{640, 360}).bindings().size(), "wide shows the bound-spell rows");
    }

    @Test
    void hitTestingFindsWhatWasDrawn() {
        for (int[] s : SCREENS) {
            SpellMenuLayout l = at(s);
            for (int i = 0; i < l.visibleRows(); i++) {
                Rect r = l.row(i);
                assertEquals(i, l.rowAt(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0));
                assertEquals(i, l.rowAt(r.x(), r.y()), "top-left texel belongs to the row");
                assertEquals(-1, l.rowAt(r.x(), r.bottom()), "the gap under a row is no row");
            }
            assertEquals(-1, l.rowAt(l.list().x() - 1, l.list().y() + 1));
            for (int i = 0; i < 4; i++) {
                Rect socket = l.sockets().get(i);
                assertEquals(i, l.slotAt(socket.x() + socket.w() / 2.0, socket.y() + socket.h() / 2.0));
                if (i < l.bindings().size()) {
                    Rect row = l.bindings().get(i);
                    assertEquals(i, l.slotAt(row.x() + 2, row.y() + row.h() / 2.0), "a bound-spell row is its socket");
                }
            }
            assertEquals(-1, l.slotAt(l.hubX(), l.hubY()), "the hub is no socket");
        }
    }

    @Test
    void chipsWrapInsideTheWell() {
        SpellMenuLayout l = SpellMenuLayout.of(320, 240, new int[]{60, 60, 60, 60, 60});
        assertTrue(l.chips().get(l.chips().size() - 1).y() > l.chips().get(0).y(), "five wide chips need a second row");
        assertTrue(l.list().y() > l.chips().get(l.chips().size() - 1).bottom(), "the list starts under the last chip row");
    }
}
