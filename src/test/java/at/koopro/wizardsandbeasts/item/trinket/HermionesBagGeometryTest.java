package at.koopro.wizardsandbeasts.item.trinket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.GUI_H;
import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.GUI_W;
import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.HOTBAR_Y;
import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.INV_Y;
import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bag's slot geometry: menu slots and screen frames both read {@link HermionesBagMenu#slotOrigins()}.
 */
class HermionesBagGeometryTest {

    @Test
    void ninetySlotsInQuickMoveOrder() {
        List<int[]> o = HermionesBagMenu.slotOrigins();
        assertEquals(SIZE + 36, o.size());
        assertEquals(54, SIZE, "capacity is the bag's, not the screen's to change");
        for (int i = SIZE; i < SIZE + 27; i++) {
            assertEquals(INV_Y + (i - SIZE) / 9 * 18, o.get(i)[1], "inventory slot " + i);
        }
        for (int i = SIZE + 27; i < SIZE + 36; i++) {
            assertEquals(HOTBAR_Y, o.get(i)[1], "hotbar slot " + i);
        }
    }

    @Test
    void framesStayOnTheScreenAndNeverOverlap() {
        List<int[]> o = HermionesBagMenu.slotOrigins();
        for (int i = 0; i < o.size(); i++) {
            int[] a = o.get(i);
            assertTrue(a[0] - 1 > 6 && a[0] + 17 < GUI_W - 6, "slot " + i + " frame leaves the sheet sideways");
            assertTrue(a[1] - 1 > 6 && a[1] + 17 < GUI_H - 6, "slot " + i + " frame leaves the sheet vertically");
            for (int j = i + 1; j < o.size(); j++) {
                int[] b = o.get(j);
                assertFalse(Math.abs(a[0] - b[0]) < 18 && Math.abs(a[1] - b[1]) < 18, "slots " + i + "/" + j + " overlap");
            }
        }
    }

    @Test
    void theBagSitsAboveTheInventoryAndTheWholeFitsTheSmallestScreen() {
        int bagBottom = HermionesBagMenu.BAG_Y + HermionesBagMenu.ROWS * 18;
        assertTrue(INV_Y - bagBottom >= 20, "room for the bag's lower edge and the inventory label");
        assertTrue(GUI_H <= 240, "fits vanilla's 240px floor");
    }
}
