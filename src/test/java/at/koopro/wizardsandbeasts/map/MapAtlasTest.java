package at.koopro.wizardsandbeasts.map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapAtlasTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final Identifier PLAINS = Identifier.fromNamespaceAndPath("minecraft", "plains");
    private static final Identifier FOREST = Identifier.fromNamespaceAndPath("minecraft", "forest");

    @Test
    void survey_recordsATileAndReportsItsRegion() {
        MapAtlas atlas = new MapAtlas();
        Long key = atlas.survey(OVERWORLD, 5, 7, PLAINS, MapRelief.LOWLAND);
        assertNotNull(key);
        assertEquals(MapGeometry.regionKey(0, 0), key.longValue());
        assertTrue(atlas.isSurveyed(OVERWORLD, 5, 7));
        assertEquals(1, atlas.surveyedTiles());
    }

    @Test
    void survey_returnsNullWhenNothingChanged() {
        MapAtlas atlas = new MapAtlas();
        assertNotNull(atlas.survey(OVERWORLD, 5, 7, PLAINS, MapRelief.LOWLAND));
        // The steady state. A surveyor that reported this as a change would mark the save dirty
        // and re-ship the region every pass, forever, for a world that has not moved.
        assertNull(atlas.survey(OVERWORLD, 5, 7, PLAINS, MapRelief.LOWLAND));
    }

    @Test
    void dimensionsAreSeparateBooks() {
        MapAtlas atlas = new MapAtlas();
        atlas.survey(OVERWORLD, 0, 0, PLAINS, MapRelief.LOWLAND);
        assertTrue(atlas.isSurveyed(OVERWORLD, 0, 0));
        // The bug this guards: showing Overworld terrain while the holder stands in the Nether.
        assertFalse(atlas.isSurveyed(NETHER, 0, 0));
        assertTrue(atlas.regionKeys(NETHER).isEmpty());
    }

    @Test
    void palette_isStableAndReversible() {
        MapAtlas atlas = new MapAtlas();
        short plains = atlas.internBiome(PLAINS);
        short forest = atlas.internBiome(FOREST);
        assertEquals(plains, atlas.internBiome(PLAINS), "interning is idempotent");
        assertEquals(PLAINS, atlas.biomeOf(plains));
        assertEquals(FOREST, atlas.biomeOf(forest));
        assertNull(atlas.biomeOf((short) 99), "an index past the palette has no name");
        assertNull(atlas.biomeOf(MapRegion.UNSURVEYED));
    }

    @Test
    void discover_mergesNearbyDuplicatesOfTheSameKind() {
        MapAtlas atlas = new MapAtlas();
        MapMarker first = atlas.discover(MapMarkerTypes.HOGWARTS, OVERWORLD,
                new BlockPos(100, 70, 100), "map.test.castle", 0L);
        // Landmark detection fires per chunk, so a castle spanning six chunks would plant six
        // castles without this.
        MapMarker again = atlas.discover(MapMarkerTypes.HOGWARTS, OVERWORLD,
                new BlockPos(140, 70, 120), "map.test.castle", 20L);
        assertSame(first, again);
        assertEquals(1, atlas.markers().size());
    }

    @Test
    void discover_keepsDistinctPlacesApart() {
        MapAtlas atlas = new MapAtlas();
        atlas.discover(MapMarkerTypes.HOGWARTS, OVERWORLD, new BlockPos(0, 70, 0), "a", 0L);
        atlas.discover(MapMarkerTypes.HOGWARTS, OVERWORLD,
                new BlockPos(MapAtlas.MERGE_TOLERANCE * 2, 70, 0), "b", 0L);
        atlas.discover(MapMarkerTypes.HOGSMEADE, OVERWORLD, new BlockPos(0, 70, 0), "c", 0L);
        atlas.discover(MapMarkerTypes.HOGWARTS, NETHER, new BlockPos(0, 70, 0), "d", 0L);
        assertEquals(4, atlas.markers().size());
    }

    @Test
    void recordDeath_keepsOnlyTheLatestPerPlayer() {
        MapAtlas atlas = new MapAtlas();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        atlas.recordDeath(OVERWORLD, new BlockPos(10, 64, 10), alice, 100L);
        atlas.recordDeath(OVERWORLD, new BlockPos(900, 64, 900), alice, 200L);
        atlas.recordDeath(OVERWORLD, new BlockPos(-50, 64, -50), bob, 300L);

        assertEquals(2, atlas.markers().size(), "one grave each, not a field of them");
        MapMarker aliceGrave = atlas.markers().stream()
                .filter(m -> m.owner().map(alice::equals).orElse(false))
                .findFirst().orElseThrow();
        assertEquals(900, aliceGrave.x());
    }

    @Test
    void waypointCount_isPerOwner() {
        MapAtlas atlas = new MapAtlas();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        atlas.put(MapMarker.waypoint(MapMarkerTypes.HOME, OVERWORLD, BlockPos.ZERO, "a", alice, 0L));
        atlas.put(MapMarker.waypoint(MapMarkerTypes.HOME, OVERWORLD, BlockPos.ZERO, "b", alice, 0L));
        atlas.put(MapMarker.waypoint(MapMarkerTypes.HOME, OVERWORLD, BlockPos.ZERO, "c", bob, 0L));
        assertEquals(2, atlas.waypointCount(alice));
        assertEquals(1, atlas.waypointCount(bob));
    }

    @Test
    void markerVisibility_isOwnerScoped() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        MapMarker mine = MapMarker.waypoint(MapMarkerTypes.HOME, OVERWORLD, BlockPos.ZERO, "a", alice, 0L);
        MapMarker shared = MapMarker.discovered(MapMarkerTypes.HOGWARTS, OVERWORLD, BlockPos.ZERO, "k", 0L);

        assertTrue(mine.visibleTo(alice));
        // Another player's pins must not even reach the wire; this is the predicate that decides.
        assertFalse(mine.visibleTo(bob));
        assertTrue(shared.visibleTo(alice));
        assertTrue(shared.visibleTo(bob));
    }

    @Test
    void labelIsTruncatedToWhatATooltipCanHold() {
        String huge = "x".repeat(400);
        MapMarker marker = MapMarker.waypoint(MapMarkerTypes.WAYPOINT, OVERWORLD, BlockPos.ZERO,
                huge, null, 0L);
        assertEquals(MapMarker.MAX_LABEL_LENGTH, marker.label().length());
    }

    @Test
    void forgetDimension_dropsItsTerrainAndItsMarkers() {
        MapAtlas atlas = new MapAtlas();
        atlas.survey(NETHER, 0, 0, PLAINS, MapRelief.LOWLAND);
        atlas.survey(OVERWORLD, 0, 0, PLAINS, MapRelief.LOWLAND);
        atlas.discover(MapMarkerTypes.FORTRESS, NETHER, BlockPos.ZERO, "k", 0L);

        assertTrue(atlas.forgetDimension(NETHER));
        assertFalse(atlas.isSurveyed(NETHER, 0, 0));
        assertTrue(atlas.markers().isEmpty());
        assertTrue(atlas.isSurveyed(OVERWORLD, 0, 0), "the other dimension is untouched");
    }

    @Test
    void putRegion_mergesRatherThanReplaces() {
        MapAtlas atlas = new MapAtlas();
        atlas.survey(OVERWORLD, 0, 0, PLAINS, MapRelief.LOWLAND);

        MapRegion incoming = new MapRegion();
        incoming.set(1, 0, (short) 0, MapRelief.HILL);
        atlas.putRegion(OVERWORLD, MapGeometry.regionKey(0, 0), incoming);

        // A sync that only carries part of a region must not blank the rest of the page.
        assertTrue(atlas.isSurveyed(OVERWORLD, 0, 0));
        assertTrue(atlas.isSurveyed(OVERWORLD, 1, 0));
    }
}
