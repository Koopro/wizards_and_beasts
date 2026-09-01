package at.koopro.wizardsandbeasts.map;

import com.mojang.serialization.DataResult;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run-length codec, which is the piece that both the save file and the network depend on.
 *
 * <p>Half of these are hostile-input tests. This decoder runs on bytes that arrived over the wire,
 * and the failure modes worth guarding are not "wrong picture" but "run length of two billion" and
 * "reads past the end of the array".
 */
class MapRegionTest {

    private static MapRegion decoded(byte[] runs) {
        DataResult<MapRegion> result = MapRegion.decode(runs);
        assertTrue(result.result().isPresent(), () -> "decode failed: " + result.error().orElseThrow().message());
        return result.result().orElseThrow();
    }

    @Test
    void freshRegionIsEmptyAndUnsurveyed() {
        MapRegion region = new MapRegion();
        assertTrue(region.isEmpty());
        assertEquals(0, region.surveyedCount());
        assertFalse(region.isSurveyed(0, 0));
        assertEquals(MapRegion.UNSURVEYED, region.biomeAt(0, 0));
        assertNull(region.reliefAt(0, 0));
    }

    @Test
    void set_reportsOnlyRealChanges() {
        MapRegion region = new MapRegion();
        assertTrue(region.set(3, 4, (short) 7, MapRelief.LOWLAND), "first write is a change");
        // Re-surveying an unchanged tile is the common case once a player settles somewhere.
        // Reporting it as a change would keep the save file and the network permanently busy.
        assertFalse(region.set(3, 4, (short) 7, MapRelief.LOWLAND), "identical rewrite is not");
        assertTrue(region.set(3, 4, (short) 7, MapRelief.HILL), "relief change is");
        assertTrue(region.set(3, 4, (short) 8, MapRelief.HILL), "biome change is");
        assertEquals(1, region.surveyedCount());
    }

    @Test
    void emptyRegionRoundTrips() {
        MapRegion region = decoded(new MapRegion().encode());
        assertTrue(region.isEmpty());
    }

    @Test
    void solidRegionRoundTripsAndCompressesHard() {
        MapRegion region = new MapRegion();
        for (int tz = 0; tz < MapGeometry.TILES_PER_REGION; tz++) {
            for (int tx = 0; tx < MapGeometry.TILES_PER_REGION; tx++) {
                region.set(tx, tz, (short) 3, MapRelief.DEEP_WATER);
            }
        }
        byte[] runs = region.encode();
        // One run for 1024 tiles: two varint bytes plus the three-byte body. An ocean region is
        // the case the whole format is shaped around.
        assertTrue(runs.length <= 8, "solid region encoded to " + runs.length + " bytes");

        MapRegion back = decoded(runs);
        assertEquals(MapGeometry.TILES_PER_REGION_SQ, back.surveyedCount());
        assertEquals((short) 3, back.biomeAt(17, 29));
        assertEquals(MapRelief.DEEP_WATER, back.reliefAt(17, 29));
    }

    @Test
    void noisyRegionRoundTripsExactly() {
        Random random = new Random(20260821L);
        MapRegion region = new MapRegion();
        short[] biomes = new short[MapGeometry.TILES_PER_REGION_SQ];
        byte[] reliefs = new byte[MapGeometry.TILES_PER_REGION_SQ];
        for (int tz = 0; tz < MapGeometry.TILES_PER_REGION; tz++) {
            for (int tx = 0; tx < MapGeometry.TILES_PER_REGION; tx++) {
                short biome = (short) random.nextInt(40);
                MapRelief relief = MapRelief.values()[random.nextInt(MapRelief.values().length)];
                region.set(tx, tz, biome, relief);
                biomes[MapGeometry.tileIndex(tx, tz)] = biome;
                reliefs[MapGeometry.tileIndex(tx, tz)] = relief.id();
            }
        }

        MapRegion back = decoded(region.encode());
        short[] backBiomes = new short[MapGeometry.TILES_PER_REGION_SQ];
        byte[] backReliefs = new byte[MapGeometry.TILES_PER_REGION_SQ];
        for (int tz = 0; tz < MapGeometry.TILES_PER_REGION; tz++) {
            for (int tx = 0; tx < MapGeometry.TILES_PER_REGION; tx++) {
                backBiomes[MapGeometry.tileIndex(tx, tz)] = back.biomeAt(tx, tz);
                backReliefs[MapGeometry.tileIndex(tx, tz)] = back.reliefAt(tx, tz).id();
            }
        }
        assertArrayEquals(biomes, backBiomes);
        assertArrayEquals(reliefs, backReliefs);
    }

    @Test
    void partiallySurveyedRegionKeepsItsHoles() {
        MapRegion region = new MapRegion();
        region.set(5, 5, (short) 1, MapRelief.LOWLAND);
        region.set(6, 5, (short) 1, MapRelief.LOWLAND);

        MapRegion back = decoded(region.encode());
        assertTrue(back.isSurveyed(5, 5));
        assertTrue(back.isSurveyed(6, 5));
        // The uncharted tiles have to survive the round trip as uncharted, or the client
        // paints the whole world as whatever biome happened to sit at palette index 0.
        assertFalse(back.isSurveyed(7, 5));
        assertEquals(2, back.surveyedCount());
    }

    @Test
    void mergeFrom_addsWithoutErasing() {
        MapRegion base = new MapRegion();
        base.set(1, 1, (short) 4, MapRelief.HILL);

        MapRegion incoming = new MapRegion();
        incoming.set(2, 2, (short) 5, MapRelief.WATER);
        incoming.set(1, 1, (short) 9, MapRelief.PEAK);

        base.mergeFrom(incoming);
        assertEquals(2, base.surveyedCount());
        assertEquals((short) 9, base.biomeAt(1, 1), "incoming wins on overlap");
        assertEquals((short) 5, base.biomeAt(2, 2));
        // A tile only the base knows about is untouched by a merge that does not mention it.
        assertFalse(base.isSurveyed(3, 3));
    }

    @Test
    void decode_rejectsTruncatedPayload() {
        byte[] runs = new MapRegion().encode();
        byte[] truncated = new byte[runs.length - 1];
        System.arraycopy(runs, 0, truncated, 0, truncated.length);
        assertTrue(MapRegion.decode(truncated).error().isPresent());
    }

    @Test
    void decode_rejectsRunThatOverflowsTheRegion() {
        // Varint 0xFF 0xFF 0x03 = 65535, far past a region's 1024 tiles.
        byte[] hostile = {(byte) 0xFF, (byte) 0xFF, 0x03, 0x00, 0x01, 0x02};
        assertTrue(MapRegion.decode(hostile).error().isPresent());
    }

    @Test
    void decode_rejectsShortRegion() {
        // A single run of one tile: syntactically fine, but it does not describe a region.
        byte[] hostile = {0x01, 0x00, 0x01, 0x02};
        assertTrue(MapRegion.decode(hostile).error().isPresent());
    }

    @Test
    void decode_rejectsUnterminatedVarint() {
        byte[] hostile = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80};
        assertTrue(MapRegion.decode(hostile).error().isPresent());
    }
}
