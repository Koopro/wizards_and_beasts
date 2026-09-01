package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.map.MapGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world/screen transform, which is where every off-by-one in a pannable map lives.
 *
 * <p>{@link MapView} has no Minecraft in it precisely so this can exist: navigation that feels
 * wrong is nearly impossible to debug through a running game, and nearly trivial to pin down here.
 */
class MapViewTest {

    private static final double EPSILON = 1e-6;

    private MapView view;

    @BeforeEach
    void setUp() {
        view = new MapView();
        view.setViewport(20, 40, 400, 240);
        view.centerOn(1000, -500);
    }

    @Test
    void centreOfTheViewportIsTheCentreOfTheCamera() {
        assertEquals(20 + 200, view.screenX(1000), EPSILON);
        assertEquals(40 + 120, view.screenY(-500), EPSILON);
    }

    @Test
    void worldAndScreenRoundTrip() {
        for (double zoom : new double[]{0.35, 0.5, 1.0, 1.5, 4.0, 6.0}) {
            view.setZoom(zoom);
            for (double x : new double[]{-4000, -1, 0, 1, 1000, 30_000_000}) {
                assertEquals(x, view.worldX(view.screenX(x)), 1e-3,
                        "x round trip at zoom " + zoom);
            }
            for (double z : new double[]{-4000, -1, 0, 1, -500, 30_000_000}) {
                assertEquals(z, view.worldZ(view.screenY(z)), 1e-3,
                        "z round trip at zoom " + zoom);
            }
        }
    }

    @Test
    void panByPixels_dragsThePaperRatherThanTheCamera() {
        view.setZoom(1.0);
        double grabbed = view.worldX(200);
        view.panByPixels(37, 0);

        // Dragging right by 37px moves the *sheet* right by 37px, so the point the cursor
        // grabbed is now 37px further right -- and the point now under x=200 is the one that
        // used to be to its left. Getting this sign backwards is the difference between
        // dragging a map and dragging a camera over one, and players notice immediately.
        assertEquals(grabbed, view.worldX(237), 1e-6);
        assertEquals(grabbed - 37 / view.pixelsPerBlock(), view.worldX(200), 1e-6);
    }

    @Test
    void panByPixels_scalesWithZoomSoDragsFeelTheSame() {
        view.setZoom(4.0);
        double before = view.panX();
        view.panByPixels(40, 0);
        double closeDelta = Math.abs(view.panX() - before);

        view.setZoom(0.5);
        before = view.panX();
        view.panByPixels(40, 0);
        double farDelta = Math.abs(view.panX() - before);

        // The same drag covers more world when zoomed out. Dividing by pixels-per-block is what
        // makes the sheet stay stuck to the cursor at every zoom instead of sliding.
        assertTrue(farDelta > closeDelta * 7, "a far-out drag should cover far more ground");
    }

    @Test
    void zoomAbout_pinsThePointUnderTheCursor() {
        double anchorX = 137;
        double anchorY = 199;
        double worldBefore = view.worldX(anchorX);
        double worldZBefore = view.worldZ(anchorY);

        view.zoomAbout(anchorX, anchorY, MapView.ZOOM_STEP);

        // The whole difference between "zooms where I am pointing" and "slides out from under
        // the cursor".
        assertEquals(worldBefore, view.worldX(anchorX), 1e-6);
        assertEquals(worldZBefore, view.worldZ(anchorY), 1e-6);
    }

    @Test
    void zoomIsClampedAtBothEnds() {
        for (int i = 0; i < 100; i++) {
            view.zoomAboutCenter(MapView.ZOOM_STEP);
        }
        assertEquals(MapView.ZOOM_MAX, view.zoom(), EPSILON);
        for (int i = 0; i < 200; i++) {
            view.zoomAboutCenter(1.0 / MapView.ZOOM_STEP);
        }
        assertEquals(MapView.ZOOM_MIN, view.zoom(), EPSILON);
    }

    @Test
    void zoomingAllTheWayInAndBackOutLeavesTheCentreWhereItWas() {
        double panX = view.panX();
        double panZ = view.panZ();
        for (int i = 0; i < 6; i++) {
            view.zoomAboutCenter(MapView.ZOOM_STEP);
        }
        for (int i = 0; i < 6; i++) {
            view.zoomAboutCenter(1.0 / MapView.ZOOM_STEP);
        }
        assertEquals(panX, view.panX(), 1e-6);
        assertEquals(panZ, view.panZ(), 1e-6);
    }

    @Test
    void contains_isHalfOpenSoAdjacentPanelsDoNotBothClaimAPixel() {
        assertTrue(view.contains(20, 40));
        assertTrue(view.contains(419, 279));
        assertFalse(view.contains(19, 40));
        assertFalse(view.contains(420, 40));
        assertFalse(view.contains(20, 280));
    }

    @Test
    void lodStep_keepsDrawnCellsReadableAndBounded() {
        view.setZoom(MapView.ZOOM_MAX);
        assertEquals(1, view.lodStep(), "no collapsing when tiles are already large");

        view.setZoom(MapView.ZOOM_MIN);
        int step = view.lodStep();
        assertTrue(step > 1, "far out, tiles must collapse");
        assertTrue(step <= MapView.MAX_LOD_STEP);
        assertTrue(view.tilePixels() * step >= MapView.MIN_DRAWN_CELL_PX,
                "a collapsed cell is still big enough to be a picture of something");
    }

    @Test
    void lodStep_isAlwaysAPowerOfTwo() {
        // Non-power-of-two steps would not align to the world-anchored sampling grid, and the
        // terrain would visibly crawl as the map is panned.
        for (double zoom = MapView.ZOOM_MIN; zoom <= MapView.ZOOM_MAX; zoom += 0.05) {
            view.setZoom(zoom);
            int step = view.lodStep();
            assertEquals(0, step & (step - 1), "step " + step + " at zoom " + zoom);
        }
    }

    @Test
    void visibleTileRangeCoversTheWholeViewport() {
        view.setZoom(1.0);
        int firstX = view.firstVisibleTileX();
        int lastX = view.lastVisibleTileX();
        assertTrue(MapGeometry.tileToBlock(firstX) <= view.worldX(view.viewX()));
        assertTrue(MapGeometry.tileToBlock(lastX) + MapGeometry.BLOCKS_PER_TILE
                >= view.worldX(view.viewX() + view.viewW()));
    }

    @Test
    void scaled_neverCollapsesASpriteToNothing() {
        view.setUiScale(0.01F);
        assertTrue(view.scaled(16) >= 1);
        view.setUiScale(2.0F);
        assertEquals(32, view.scaled(16));
    }
}
