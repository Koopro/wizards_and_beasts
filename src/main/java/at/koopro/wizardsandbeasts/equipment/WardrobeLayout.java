package at.koopro.wizardsandbeasts.equipment;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Where the wardrobe's slots sit, shared by the server menu and the client screen so the two can never disagree.
 *
 * <p>The wardrobe is an open armoire (UI renaissance P6, 2026-11-18): slots down both sides as pairs, the outer one
 * <em>worn</em> (Curios' real slot: stats, protection) and the inner one the <em>look</em> (Curios' cosmetic slot,
 * drawn instead of the worn piece). Coordinates are item origins in GUI pixels from the screen's top-left.
 */
@NullMarked
public final class WardrobeLayout {

    public static final int W = 196;
    public static final int ARMOIRE_H = 134;
    public static final int STRIP_TOP = ARMOIRE_H - 4;
    public static final int H = STRIP_TOP + 96;
    /** Player inventory's first slot (item origin): 9x3, then the hotbar 4 px lower, as in vanilla. */
    public static final int INV_X = (W - 9 * 18) / 2 + 1;
    public static final int INV_Y = STRIP_TOP + 18;
    public static final int HOTBAR_Y = INV_Y + 58;

    public static final int ROW_Y0 = 22;
    public static final int ROW_STEP = 21;
    public static final int LEFT_WORN_X = 15;
    public static final int LEFT_LOOK_X = 36;
    public static final int RIGHT_LOOK_X = W - 36 - 16;
    public static final int RIGHT_WORN_X = W - 15 - 16;

    /**
     * One place in the wardrobe: a Curios slot id and the index inside it (the ring slot holds two), its side and row,
     * the equipment icon cell drawn when it is empty, and the lang key of its name.
     */
    public record Place(String slotId, int index, boolean right, int row, int iconCell, String key) {
        public int wornX() {
            return right ? RIGHT_WORN_X : LEFT_WORN_X;
        }

        public int lookX() {
            return right ? RIGHT_LOOK_X : LEFT_LOOK_X;
        }

        public int y() {
            return ROW_Y0 + row * ROW_STEP;
        }
    }

    /**
     * Left top to bottom, then right top to bottom. The Curios slot ids are {@code EquipmentType}'s; icon cells follow
     * {@code tools/ui_icons.py} EQUIPMENT (head face body back hands belt necklace ring charm).
     */
    public static final List<Place> PLACES = List.of(
            new Place("head", 0, false, 0, 0, "head"),
            new Place("face", 0, false, 1, 1, "face"),
            new Place("necklace", 0, false, 2, 6, "necklace"),
            new Place("back", 0, false, 3, 3, "back"),
            new Place("body", 0, false, 4, 2, "body"),
            new Place("hands", 0, true, 0, 4, "hands"),
            new Place("belt", 0, true, 1, 5, "belt"),
            new Place("ring", 0, true, 2, 7, "ring"),
            new Place("ring", 1, true, 3, 7, "ring"),
            new Place("charm", 0, true, 4, 8, "charm"));

    /** Menu slots before the player's inventory: a worn and a look slot per place, in that order. */
    public static final int WARDROBE_SLOTS = PLACES.size() * 2;

    private WardrobeLayout() {}
}
