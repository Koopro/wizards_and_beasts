package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.map.MapGeometry;

/**
 * The camera over the parchment: where it is looking and how close.
 *
 * <p>Split out of the screen because it is pure arithmetic with no Minecraft in it, which makes the
 * one thing most likely to be subtly wrong — the world/screen transform — testable without a game.
 * Every off-by-one in a pannable map lives in these six methods.
 *
 * <p>The camera is held in <em>world block</em> coordinates, not tiles and not pixels. Tiles are too
 * coarse to centre on a player smoothly, and pixels move under the world the moment the zoom
 * changes.
 */
public final class MapView {

    /** Screen pixels one tile occupies at zoom 1. A tile is a chunk, so this is 8px per chunk. */
    public static final double BASE_TILE_PX = 8.0;

    /**
     * The three named stops the buttons and the keyboard step between, and the bounds of the
     * scroll wheel's continuous range.
     *
     * <p>Regional (0.5) fits a few thousand blocks on the page and is where you look for the shape
     * of a coastline. Local (1.5) is one chunk to twelve pixels, which is where the tile art is
     * meant to be read. Detailed (4.0) is close enough to pick one chunk out of a castle.
     */
    public static final double ZOOM_MIN = 0.35;
    public static final double ZOOM_REGIONAL = 0.5;
    public static final double ZOOM_LOCAL = 1.5;
    public static final double ZOOM_DETAILED = 4.0;
    public static final double ZOOM_MAX = 6.0;

    /** Multiplier per wheel notch. Small enough that a zoom reads as continuous. */
    public static final double ZOOM_STEP = 1.18;

    private double panX;
    private double panZ;
    private double zoom = ZOOM_LOCAL;

    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private float uiScale = 1.0F;

    public void setUiScale(float scale) {
        this.uiScale = Math.max(0.25F, scale);
    }

    /** Scales a native sprite size onto the pixel grid, never below one pixel. */
    public int scaled(int nativeSize) {
        return Math.max(1, Math.round(nativeSize * uiScale));
    }

    /** Sets the rectangle the map is drawn into, in screen pixels. */
    public void setViewport(int x, int y, int w, int h) {
        this.viewX = x;
        this.viewY = y;
        this.viewW = Math.max(1, w);
        this.viewH = Math.max(1, h);
    }

    public int viewX() {
        return viewX;
    }

    public int viewY() {
        return viewY;
    }

    public int viewW() {
        return viewW;
    }

    public int viewH() {
        return viewH;
    }

    public double panX() {
        return panX;
    }

    public double panZ() {
        return panZ;
    }

    public double zoom() {
        return zoom;
    }

    public void centerOn(double blockX, double blockZ) {
        this.panX = blockX;
        this.panZ = blockZ;
    }

    /** Drags the camera by a screen-pixel delta, which is what a mouse drag hands us. */
    public void panByPixels(double dxPixels, double dzPixels) {
        panX -= dxPixels / pixelsPerBlock();
        panZ -= dzPixels / pixelsPerBlock();
    }

    /** Screen pixels per world block at the current zoom. */
    public double pixelsPerBlock() {
        return BASE_TILE_PX * zoom / MapGeometry.BLOCKS_PER_TILE;
    }

    /** Screen pixels one tile occupies at the current zoom. */
    public double tilePixels() {
        return BASE_TILE_PX * zoom;
    }

    public double screenX(double blockX) {
        return viewX + viewW / 2.0 + (blockX - panX) * pixelsPerBlock();
    }

    public double screenY(double blockZ) {
        return viewY + viewH / 2.0 + (blockZ - panZ) * pixelsPerBlock();
    }

    public double worldX(double screenX) {
        return panX + (screenX - viewX - viewW / 2.0) / pixelsPerBlock();
    }

    public double worldZ(double screenY) {
        return panZ + (screenY - viewY - viewH / 2.0) / pixelsPerBlock();
    }

    public boolean contains(double screenX, double screenY) {
        return screenX >= viewX && screenX < viewX + viewW
                && screenY >= viewY && screenY < viewY + viewH;
    }

    /**
     * Zooms by {@code factor} while keeping the world point under {@code (anchorX, anchorY)} pinned
     * to that pixel.
     *
     * <p>Pinning the anchor rather than the centre is the whole difference between a map that zooms
     * where you are pointing and one that slides out from under the cursor.
     */
    public void zoomAbout(double anchorX, double anchorY, double factor) {
        double beforeX = worldX(anchorX);
        double beforeZ = worldZ(anchorY);
        zoom = Math.clamp(zoom * factor, ZOOM_MIN, ZOOM_MAX);
        panX = beforeX - (anchorX - viewX - viewW / 2.0) / pixelsPerBlock();
        panZ = beforeZ - (anchorY - viewY - viewH / 2.0) / pixelsPerBlock();
    }

    public void zoomAboutCenter(double factor) {
        zoomAbout(viewX + viewW / 2.0, viewY + viewH / 2.0, factor);
    }

    public void setZoom(double newZoom) {
        zoom = Math.clamp(newZoom, ZOOM_MIN, ZOOM_MAX);
    }

    /**
     * Tile step for the current zoom: how many tiles collapse into one drawn cell.
     *
     * <p>Below a few pixels a tile sprite is not a picture of anything, and drawing one per tile at
     * that size costs thousands of blits to render mush. Collapsing to a power-of-two block keeps
     * the drawn cell at a readable size and the blit count roughly constant however far out the
     * player zooms — which is the requirement that the map stay legible zoomed out, and the reason
     * the far view is cheaper than the near one rather than far more expensive.
     */
    public int lodStep() {
        int step = 1;
        double px = tilePixels();
        while (px * step < MIN_DRAWN_CELL_PX && step < MAX_LOD_STEP) {
            step *= 2;
        }
        return step;
    }

    /** Smallest a drawn terrain cell may get before tiles start collapsing into blocks. */
    public static final double MIN_DRAWN_CELL_PX = 6.0;
    /** Ceiling on collapsing, so an extreme zoom-out cannot merge the world into four cells. */
    public static final int MAX_LOD_STEP = 8;

    /** West-most tile the viewport touches, in absolute tile coordinates. */
    public int firstVisibleTileX() {
        return MapGeometry.blockToTile((int) Math.floor(worldX(viewX)));
    }

    public int firstVisibleTileZ() {
        return MapGeometry.blockToTile((int) Math.floor(worldZ(viewY)));
    }

    public int lastVisibleTileX() {
        return MapGeometry.blockToTile((int) Math.ceil(worldX(viewX + viewW)));
    }

    public int lastVisibleTileZ() {
        return MapGeometry.blockToTile((int) Math.ceil(worldZ(viewY + viewH)));
    }
}
