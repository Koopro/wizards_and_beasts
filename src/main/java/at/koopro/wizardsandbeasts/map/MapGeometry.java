package at.koopro.wizardsandbeasts.map;

/**
 * The one place the Marauder's Map's three coordinate spaces are converted between.
 *
 * <p>The map does not draw blocks. It draws <em>tiles</em>, and a tile is exactly one Minecraft
 * chunk — 16x16 blocks. That choice is what keeps the map recognisably a map of <em>this</em>
 * world: chunk borders are the grain the terrain already has, and sampling on any other pitch
 * produces a picture whose features do not line up with the ones the player walked through.
 *
 * <p>Tiles are grouped into <em>regions</em> of 32x32 tiles (512x512 blocks, the same span as a
 * vanilla region file). Regions are the unit of storage, of network transfer and of the
 * client's draw cache, because a per-tile granularity at any of those three layers means a
 * packet, a map entry and a dirty flag for every chunk a player has ever walked past.
 *
 * <p>All of it is integer shift arithmetic on purpose: {@code /} and {@code %} round toward
 * zero, so a naive conversion puts the four tiles around the origin into the same tile and
 * mirrors the whole negative half of the world. {@code >>} floors, which is the behaviour a
 * coordinate space needs.
 */
public final class MapGeometry {

    /** Blocks along one edge of a tile. One tile is one chunk. */
    public static final int BLOCKS_PER_TILE = 16;
    /** {@code log2(BLOCKS_PER_TILE)}. */
    public static final int TILE_SHIFT = 4;

    /** Tiles along one edge of a region. */
    public static final int TILES_PER_REGION = 32;
    /** {@code log2(TILES_PER_REGION)}. */
    public static final int REGION_SHIFT = 5;
    /** Tiles in one region. */
    public static final int TILES_PER_REGION_SQ = TILES_PER_REGION * TILES_PER_REGION;
    /** Mask that reduces a tile coordinate to its index within its region. */
    public static final int REGION_MASK = TILES_PER_REGION - 1;

    /** Blocks along one edge of a region. */
    public static final int BLOCKS_PER_REGION = BLOCKS_PER_TILE * TILES_PER_REGION;

    private MapGeometry() {
    }

    /** Tile coordinate containing the given block coordinate, on either axis. */
    public static int blockToTile(int block) {
        return block >> TILE_SHIFT;
    }

    /** West/north block edge of a tile, on either axis. */
    public static int tileToBlock(int tile) {
        return tile << TILE_SHIFT;
    }

    /** Centre block of a tile, on either axis — what the surveyor samples. */
    public static int tileCenterBlock(int tile) {
        return (tile << TILE_SHIFT) + BLOCKS_PER_TILE / 2;
    }

    /** Region coordinate containing the given tile coordinate, on either axis. */
    public static int tileToRegion(int tile) {
        return tile >> REGION_SHIFT;
    }

    /**
     * Packs a region coordinate pair into the {@code long} keys {@link MapAtlas} stores regions
     * under. Both halves are masked before packing: sign-extension of a negative {@code rx}
     * would otherwise flood the high word and collide every south-west region onto one key.
     */
    public static long regionKey(int regionX, int regionZ) {
        return (regionX & 0xFFFFFFFFL) | ((long) regionZ << 32);
    }

    public static int regionKeyX(long key) {
        return (int) key;
    }

    public static int regionKeyZ(long key) {
        return (int) (key >> 32);
    }

    /** Index of a tile within its own region's flat arrays, row-major in Z. */
    public static int tileIndex(int tileX, int tileZ) {
        return ((tileZ & REGION_MASK) << REGION_SHIFT) | (tileX & REGION_MASK);
    }

    /** Tile-X of a flat region index, relative to the region's west edge. */
    public static int indexLocalX(int index) {
        return index & REGION_MASK;
    }

    /** Tile-Z of a flat region index, relative to the region's north edge. */
    public static int indexLocalZ(int index) {
        return (index >> REGION_SHIFT) & REGION_MASK;
    }
}
