package at.koopro.wizardsandbeasts.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The coordinate spaces, with the negative half of the world given equal billing.
 *
 * <p>Every one of these would pass if the conversions used {@code /} and {@code %}, for positive
 * inputs only. Division rounds toward zero, which folds the four tiles around the origin into one
 * and mirrors everything west and north of spawn onto everything east and south of it — a bug that
 * is invisible in the world most people test in and catastrophic in the other three quadrants.
 */
class MapGeometryTest {

    @Test
    void blockToTile_floorsRatherThanTruncating() {
        assertEquals(0, MapGeometry.blockToTile(0));
        assertEquals(0, MapGeometry.blockToTile(15));
        assertEquals(1, MapGeometry.blockToTile(16));
        // The pair that division gets wrong: both would be tile 0.
        assertEquals(-1, MapGeometry.blockToTile(-1));
        assertEquals(-1, MapGeometry.blockToTile(-16));
        assertEquals(-2, MapGeometry.blockToTile(-17));
    }

    @Test
    void tileToBlock_isTheWestNorthEdge() {
        assertEquals(0, MapGeometry.tileToBlock(0));
        assertEquals(16, MapGeometry.tileToBlock(1));
        assertEquals(-16, MapGeometry.tileToBlock(-1));
        assertEquals(8, MapGeometry.tileCenterBlock(0));
        assertEquals(-8, MapGeometry.tileCenterBlock(-1));
    }

    @Test
    void blockToTile_roundTripsThroughEveryBlockOfATile() {
        for (int block = -64; block < 64; block++) {
            int tile = MapGeometry.blockToTile(block);
            int edge = MapGeometry.tileToBlock(tile);
            assertEquals(tile, MapGeometry.blockToTile(edge),
                    "tile " + tile + " does not contain its own west edge");
            assertEquals(tile, MapGeometry.blockToTile(edge + MapGeometry.BLOCKS_PER_TILE - 1),
                    "tile " + tile + " does not contain its own east edge");
        }
    }

    @Test
    void tileToRegion_floors() {
        assertEquals(0, MapGeometry.tileToRegion(0));
        assertEquals(0, MapGeometry.tileToRegion(31));
        assertEquals(1, MapGeometry.tileToRegion(32));
        assertEquals(-1, MapGeometry.tileToRegion(-1));
        assertEquals(-1, MapGeometry.tileToRegion(-32));
        assertEquals(-2, MapGeometry.tileToRegion(-33));
    }

    @Test
    void regionKey_roundTripsIncludingNegatives() {
        int[][] pairs = {{0, 0}, {1, 1}, {-1, -1}, {-1, 1}, {1, -1},
                {1000, -1000}, {Integer.MIN_VALUE, Integer.MAX_VALUE}};
        for (int[] pair : pairs) {
            long key = MapGeometry.regionKey(pair[0], pair[1]);
            assertEquals(pair[0], MapGeometry.regionKeyX(key));
            assertEquals(pair[1], MapGeometry.regionKeyZ(key));
        }
    }

    @Test
    void regionKey_doesNotCollideAcrossQuadrants() {
        // The failure this guards: sign-extending a negative x floods the high word, so every
        // region in the south-west lands on the same key as one in the north-east.
        long a = MapGeometry.regionKey(-1, 0);
        long b = MapGeometry.regionKey(-1, -1);
        long c = MapGeometry.regionKey(0, -1);
        assertNotEquals(a, b);
        assertNotEquals(b, c);
        assertNotEquals(a, c);
    }

    @Test
    void tileIndex_coversEveryTileOfARegionExactlyOnce() {
        boolean[] seen = new boolean[MapGeometry.TILES_PER_REGION_SQ];
        for (int tz = 0; tz < MapGeometry.TILES_PER_REGION; tz++) {
            for (int tx = 0; tx < MapGeometry.TILES_PER_REGION; tx++) {
                int index = MapGeometry.tileIndex(tx, tz);
                assertEquals(false, seen[index], "index " + index + " reused");
                seen[index] = true;
                assertEquals(tx, MapGeometry.indexLocalX(index));
                assertEquals(tz, MapGeometry.indexLocalZ(index));
            }
        }
    }

    @Test
    void tileIndex_wrapsNegativeTilesIntoTheirOwnRegion() {
        // Tile -1 is the last tile of region -1, not an out-of-bounds index.
        assertEquals(MapGeometry.tileIndex(31, 31), MapGeometry.tileIndex(-1, -1));
        assertEquals(MapGeometry.tileIndex(0, 0), MapGeometry.tileIndex(-32, -32));
    }
}
