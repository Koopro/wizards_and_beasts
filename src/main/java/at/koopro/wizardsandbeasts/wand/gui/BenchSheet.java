package at.koopro.wizardsandbeasts.wand.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The parchment bench sheet's geometry: the station row (three slots) over the player's inventory. The making side
 * drew on it until the 2026-11-26 remake gave it the bench top as an artefact screen; the refine side still does,
 * until P5 folds it into the same screen.
 *
 * <p>Slot x/y are the item's top-left, as vanilla's Slot takes them; a screen draws each frame one pixel out.
 */
public final class BenchSheet {

    public static final int GUI_W = 180;
    public static final int GUI_H = 232;
    public static final int STATION_Y = 30;
    public static final int BLANK_X = 30;
    public static final int CORE_X = 66;
    public static final int OUTPUT_X = 130;
    public static final int INV_X = 10;
    public static final int INV_Y = 150;
    public static final int HOTBAR_Y = INV_Y + 3 * 18 + 4;

    private BenchSheet() {}

    /** Every slot's origin in slot-index order: the three station slots, 27 inventory slots, 9 hotbar slots. */
    public static List<int[]> slotOrigins() {
        return origins(BLANK_X, STATION_Y, CORE_X, STATION_Y, OUTPUT_X, STATION_Y, INV_X, INV_Y, HOTBAR_Y);
    }

    /** Three station slots at the given origins, then the player's inventory and hotbar. */
    public static List<int[]> origins(int ax, int ay, int bx, int by, int cx, int cy, int invX, int invY, int hotbarY) {
        List<int[]> out = new ArrayList<>(39);
        out.add(new int[]{ax, ay});
        out.add(new int[]{bx, by});
        out.add(new int[]{cx, cy});
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                out.add(new int[]{invX + col * 18, invY + row * 18});
            }
        }
        for (int col = 0; col < 9; ++col) {
            out.add(new int[]{invX + col * 18, hotbarY});
        }
        return out;
    }
}
