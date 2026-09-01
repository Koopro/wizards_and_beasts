package at.koopro.wizardsandbeasts.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapReliefTest {

    private static final int SEA = 63;

    @Test
    void waterIsWhereTheSurfaceSitsAboveTheFloor() {
        // OCEAN_FLOOR stops at the seabed and WORLD_SURFACE at the water's top, so the gap
        // between them *is* the water column. That is the whole detection.
        assertEquals(MapRelief.WATER, MapRelief.classify(63, 58, SEA, true));
        assertEquals(MapRelief.DEEP_WATER, MapRelief.classify(63, 40, SEA, true));
    }

    @Test
    void shoreNeedsBothLowGroundAndNearbyWater() {
        assertEquals(MapRelief.SHORE, MapRelief.classify(64, 64, SEA, true));
        // The same flat ground inland is a lowland: a beach with no sea is a field.
        assertEquals(MapRelief.LOWLAND, MapRelief.classify(64, 64, SEA, false));
    }

    @Test
    void landBandsClimbWithElevation() {
        assertEquals(MapRelief.LOWLAND, MapRelief.classify(75, 75, SEA, false));
        assertEquals(MapRelief.HILL, MapRelief.classify(95, 95, SEA, false));
        assertEquals(MapRelief.MOUNTAIN, MapRelief.classify(125, 125, SEA, false));
        assertEquals(MapRelief.PEAK, MapRelief.classify(180, 180, SEA, false));
    }

    @Test
    void seaLevelIsReadFromTheLevelNotAssumed() {
        // A superflat or a datapack dimension can put sea level anywhere; the same column has
        // to classify differently depending on where "sea level" is for that world.
        assertEquals(MapRelief.LOWLAND, MapRelief.classify(70, 70, 63, false));
        assertEquals(MapRelief.MOUNTAIN, MapRelief.classify(70, 70, 20, false));
    }

    @Test
    void byId_survivesUnknownValues() {
        for (MapRelief relief : MapRelief.values()) {
            assertEquals(relief, MapRelief.byId(relief.id()));
        }
        // A save from a newer build, or an unsurveyed tile, must degrade to blank parchment
        // rather than throwing on the screen that opens it.
        assertNull(MapRelief.byId(MapRelief.UNSURVEYED));
        assertNull(MapRelief.byId((byte) 99));
        assertNull(MapRelief.byId((byte) -7));
    }

    @Test
    void isWater_coversExactlyTheTwoWaterBands() {
        assertTrue(MapRelief.DEEP_WATER.isWater());
        assertTrue(MapRelief.WATER.isWater());
        assertFalse(MapRelief.SHORE.isWater());
        assertFalse(MapRelief.LOWLAND.isWater());
    }
}
