package at.koopro.wizardsandbeasts.wand.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.GUI_H;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.GUI_W;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.HOTBAR_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.INV_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.TOP_H;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bench's slot geometry: the menu's slots, the screen's frames and the wells painted by tools/bench_screen.py all
 * come from {@link WandmakersBenchMenu}'s numbers, so what holds here holds for all three. Since the 2026-11-26 remake
 * the bench slots lie on the bench top ({@code TOP_H} tall) and the player's inventory on a parchment strip below it.
 */
class WandmakersBenchGeometryTest {

    /** The parchment strip's inner rule sits 6px in; a slot frame (one out from the item) must clear it. */
    private static final int FRAME_RULE = 6;
    /** The strip begins this far above the bench top's lower edge (it hangs under the front edge). */
    private static final int STRIP_OVERLAP = 6;

    @Test
    void thirtyNineSlotsInQuickMoveOrder() {
        List<int[]> o = WandmakersBenchMenu.slotOrigins();
        assertEquals(39, o.size());
        for (int i = 3; i < 30; i++) {
            assertEquals(INV_Y + (i - 3) / 9 * 18, o.get(i)[1], "inventory slot " + i);
        }
        for (int i = 30; i < 39; i++) {
            assertEquals(HOTBAR_Y, o.get(i)[1], "hotbar slot " + i);
        }
        // Hotbar 0 is inventory index 0 (slot 30), the vanilla convention quickMoveStack assumes.
        assertEquals(o.get(3)[0], o.get(30)[0]);
    }

    @Test
    void benchSlotsLieOnTheBenchAndTheInventoryOnTheStrip() {
        List<int[]> o = WandmakersBenchMenu.slotOrigins();
        for (int i = 0; i < 3; i++) {
            int[] p = o.get(i);
            assertTrue(p[0] - 1 >= 0 && p[0] + 17 <= GUI_W && p[1] + 17 <= TOP_H - STRIP_OVERLAP,
                    "bench slot " + i + " at " + p[0] + "," + p[1] + " is off the bench top");
        }
        int stripTop = TOP_H - STRIP_OVERLAP;
        for (int i = 3; i < o.size(); i++) {
            int fx = o.get(i)[0] - 1;
            int fy = o.get(i)[1] - 1;
            assertTrue(fy > stripTop + FRAME_RULE - 1 && fx > FRAME_RULE, "frame at " + fx + "," + fy + " meets the strip's rule");
            assertTrue(fx + 18 < GUI_W - FRAME_RULE && fy + 18 < GUI_H - FRAME_RULE,
                    "frame at " + fx + "," + fy + " runs past the strip");
        }
    }

    @Test
    void noTwoFramesOverlap() {
        List<int[]> o = WandmakersBenchMenu.slotOrigins();
        for (int i = 0; i < o.size(); i++) {
            for (int j = i + 1; j < o.size(); j++) {
                int[] a = o.get(i);
                int[] b = o.get(j);
                boolean overlap = Math.abs(a[0] - b[0]) < 18 && Math.abs(a[1] - b[1]) < 18;
                assertFalse(overlap, "slots " + i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void fitsAVanillaWindowWithAVanillaHotbarGap() {
        assertEquals(INV_Y + 3 * 18 + 4, HOTBAR_Y, "vanilla's 4px gap above the hotbar");
        assertTrue(GUI_H <= 240, "fits vanilla's 240px floor: " + GUI_H);
    }
}
