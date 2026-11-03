package at.koopro.wizardsandbeasts.wand.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.GUI_H;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.GUI_W;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.HOTBAR_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.INV_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.STATION_Y;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bench's slot geometry: the menu's slots and the screen's frames both come from
 * {@link WandmakersBenchMenu#slotOrigins()}, so what holds here holds for both.
 */
class WandmakersBenchGeometryTest {

    /** The parchment sheet's inner rule sits 6px in; a slot frame (one out from the item) must clear it. */
    private static final int FRAME_RULE = 6;

    @Test
    void thirtyNineSlotsInQuickMoveOrder() {
        List<int[]> o = WandmakersBenchMenu.slotOrigins();
        assertEquals(39, o.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(STATION_Y, o.get(i)[1], "bench slot " + i + " on the station row");
        }
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
    void framesStayInsideTheSheetAndClearItsRule() {
        for (int[] p : WandmakersBenchMenu.slotOrigins()) {
            int fx = p[0] - 1;
            int fy = p[1] - 1;
            assertTrue(fx > FRAME_RULE && fy > FRAME_RULE, "frame at " + fx + "," + fy + " meets the frame rule");
            assertTrue(fx + 18 < GUI_W - FRAME_RULE && fy + 18 < GUI_H - FRAME_RULE,
                    "frame at " + fx + "," + fy + " runs past the sheet");
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
    void stationSitsAboveTheInventoryWithRoomForItsControls() {
        // Station row, flexibility, status (two lines), tier row and the inventory label sit between.
        assertTrue(INV_Y - STATION_Y >= 110, "station band " + (INV_Y - STATION_Y) + "px");
        assertEquals(INV_Y + 3 * 18 + 4, HOTBAR_Y, "vanilla's 4px gap above the hotbar");
        assertTrue(GUI_H <= 240, "fits vanilla's 240px floor");
    }
}
