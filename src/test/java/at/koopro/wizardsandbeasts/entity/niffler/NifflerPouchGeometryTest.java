package at.koopro.wizardsandbeasts.entity.niffler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pouch's slot geometry, adult (3 rows) and baby (1): menu slots and screen frames share it. */
class NifflerPouchGeometryTest {

    @Test
    void rowsFollowThePouchSize() {
        assertEquals(3, NifflerPouchMenu.rowsFor(27));
        assertEquals(1, NifflerPouchMenu.rowsFor(9));
    }

    @Test
    void slotsAreInQuickMoveOrderStayOnTheSheetAndNeverOverlap() {
        for (int rows : new int[]{1, 3}) {
            List<int[]> o = NifflerPouchMenu.slotOrigins(rows);
            int h = NifflerPouchMenu.guiHeight(rows);
            assertEquals(rows * 9 + 36, o.size());
            assertEquals(NifflerPouchMenu.hotbarY(rows), o.get(o.size() - 1)[1], "hotbar last");
            assertTrue(h <= 240, rows + " rows: fits vanilla's 240px floor");
            for (int i = 0; i < o.size(); i++) {
                int[] a = o.get(i);
                assertTrue(a[0] - 1 > 6 && a[0] + 17 < NifflerPouchMenu.GUI_W - 6, "slot " + i + " sideways");
                assertTrue(a[1] - 1 > 6 && a[1] + 17 < h - 6, rows + " rows: slot " + i + " vertically");
                for (int j = i + 1; j < o.size(); j++) {
                    int[] b = o.get(j);
                    assertFalse(Math.abs(a[0] - b[0]) < 18 && Math.abs(a[1] - b[1]) < 18, "slots " + i + "/" + j);
                }
            }
            int pouchBottom = NifflerPouchMenu.POUCH_Y + rows * 18;
            assertTrue(NifflerPouchMenu.invY(rows) - pouchBottom >= 20, "room for the fur's edge and the label");
        }
    }
}
