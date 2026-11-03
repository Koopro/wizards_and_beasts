package at.koopro.wizardsandbeasts.client.currency;

import at.koopro.wizardsandbeasts.client.currency.gui.GringottsLayout;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Gringotts counter at the screen sizes players get (1080p at GUI 2/3/4, 768p and 720p at GUI 3, and
 * vanilla's 320x240 floor): every control on its page, none on another, and arrows between the figures.
 */
class GringottsLayoutTest {

    private static final int[][] SCREENS = {
            {960, 540}, {640, 360}, {480, 270}, {455, 256}, {427, 240}, {320, 240}};
    /** "1", "10", "64", "All" chips; "99,999" columns; "Counter" tab, in the vanilla font, padded. */
    private static final GringottsLayout.Measure M =
            new GringottsLayout.Measure(new int[]{17, 22, 22, 26}, 36, 36, 56);

    private static GringottsLayout at(int[] s) {
        return GringottsLayout.of(s[0], s[1], M);
    }

    private static boolean inside(Rect a, Rect b) {
        return a.x() >= b.x() && a.y() >= b.y() && a.right() <= b.right() && a.bottom() <= b.bottom();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static List<Rect> vaultControls(GringottsLayout l) {
        List<Rect> r = new ArrayList<>(l.vault().amounts());
        r.addAll(l.vault().deposit());
        r.addAll(l.vault().withdraw());
        r.add(l.vault().depositAll());
        r.add(l.vault().withdrawAll());
        return r;
    }

    private static List<Rect> counterControls(GringottsLayout l) {
        List<Rect> r = new ArrayList<>(l.counter().exchange());
        r.addAll(l.counter().dragot());
        r.add(l.counter().business());
        return r;
    }

    @Test
    void everyControlSitsOnItsPage() {
        for (int[] s : SCREENS) {
            GringottsLayout l = at(s);
            String at = s[0] + "x" + s[1];
            assertTrue(inside(l.panel(), new Rect(0, 0, s[0], s[1])), at + " panel off screen");
            for (Rect r : vaultControls(l)) {
                assertTrue(inside(r, l.vaultPage()), at + " vault control " + r + " off its page");
            }
            for (Rect r : counterControls(l)) {
                assertTrue(inside(r, l.counterPage()), at + " counter control " + r + " off its page");
            }
            assertTrue(l.vault().totalsY() + 20 <= l.vaultPage().bottom(), at + " totals run off the page");
        }
    }

    @Test
    void controlsNeverOverlap() {
        for (int[] s : SCREENS) {
            GringottsLayout l = at(s);
            for (List<Rect> group : List.of(vaultControls(l), counterControls(l))) {
                for (int i = 0; i < group.size(); i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        assertFalse(overlap(group.get(i), group.get(j)),
                                s[0] + "x" + s[1] + " " + group.get(i) + " / " + group.get(j));
                    }
                }
            }
            // The Dragot buttons sit under the board, not on it.
            assertTrue(l.counter().dragot().get(0).y() >= l.counter().boardY() + 20);
            assertTrue(l.counter().dragot().get(3).bottom() <= l.counter().business().y());
        }
    }

    @Test
    void arrowsSitBetweenTheVaultAndWalletFigures() {
        for (int[] s : SCREENS) {
            GringottsLayout.Vault v = at(s).vault();
            for (int i = 0; i < 3; i++) {
                assertTrue(v.deposit().get(i).x() > v.vaultRight(), "deposit arrow right of the vault figure");
                assertTrue(v.withdraw().get(i).right() < v.walletRight() - M.walletColW(), "withdraw arrow left of the wallet figure");
                assertTrue(v.deposit().get(i).right() <= v.withdraw().get(i).x(), "into-the-vault arrow first");
            }
            assertTrue(v.nameRight() > v.nameX(), "room for at least the coin's letter");
        }
    }

    @Test
    void twoPagesWhereTheyFitTabsBelow() {
        assertEquals(GringottsLayout.Mode.TWO_PAGE, at(new int[]{480, 270}).mode());
        assertEquals(GringottsLayout.Mode.TWO_PAGE, at(new int[]{427, 240}).mode());
        GringottsLayout narrow = at(new int[]{320, 240});
        assertEquals(GringottsLayout.Mode.TABBED, narrow.mode());
        assertEquals(2, narrow.tabs().size());
        assertTrue(narrow.vaultPage().y() >= narrow.tabs().get(0).bottom(), "page under its tabs");
        assertFalse(overlap(at(new int[]{480, 270}).vaultPage(), at(new int[]{480, 270}).counterPage()));
    }
}
