package at.koopro.wizardsandbeasts.visual.beam;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one parser behind beam settings, storage and sync: canonical text round-trips, bad text is refused, never bent. */
class BeamVisualPropertyTest {

    private static final BeamVisual CRUCIO = BeamVisualDefaults.forSpell(BeamVisualDefaults.CRUCIO, 0xFFAA2222);

    @Test
    void everyPropertyRoundTripsItsOwnText() {
        assertNotNull(CRUCIO);
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            String text = property.text(CRUCIO);
            assertTrue(property.accepts(text), property + " refused its own text " + text);
            assertEquals(CRUCIO, property.with(CRUCIO, text).orElseThrow(), property + " changed on a round trip");
            assertEquals(text, property.canonical(text), property + " text is not canonical: " + text);
        }
    }

    @Test
    void idsAreUniqueAndResolve() {
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            assertEquals(property, BeamVisualProperty.byId(property.id()));
            assertTrue(property.id().matches("[a-z][a-z_]*"), property.id());
        }
        assertNull(BeamVisualProperty.byId("length"), "length is gameplay (spell range), never a visual property");
        assertNull(BeamVisualProperty.byId("trail"));
        assertNull(BeamVisualProperty.byId("sound"));
    }

    @Test
    void invalidValuesAreRefused() {
        String[][] cases = {
                {"core_width", "0"}, {"core_width", "13"}, {"core_width", "2.5"}, {"core_width", "wide"},
                {"glow_shells", "3"}, {"core_brightness", "1.01"}, {"core_brightness", "-0.1"},
                {"core_brightness", "NaN"}, {"spark_density", "Infinity"}, {"spin", "20.5"},
                {"core_color", "#GG0000"}, {"core_color", "#12345"}, {"core_color", "red"}, {"core_color", "0x112233"},
                {"shape", "SPIRAL"}, {"enabled", "yes"}, {"crackle_ticks", "0"}, {"fade_out_ticks", "41"},
                {"impact_intensity", "2.5"}, {"segments", ""},
        };
        for (String[] c : cases) {
            BeamVisualProperty property = BeamVisualProperty.byId(c[0]);
            assertNotNull(property, c[0]);
            assertFalse(property.accepts(c[1]), c[0] + " accepted " + c[1]);
            assertTrue(property.with(CRUCIO, c[1]).isEmpty(), c[0] + " applied " + c[1]);
        }
    }

    @Test
    void lenientInputBecomesCanonical() {
        assertEquals("#AABBCC", BeamVisualProperty.CORE_COLOR.canonical("aabbcc"));
        assertEquals("#AABBCC", BeamVisualProperty.CORE_COLOR.canonical(" #aAbBcC "));
        assertEquals("LIGHTNING", BeamVisualProperty.SHAPE.canonical("lightning"));
        assertEquals("0.5", BeamVisualProperty.GLOW_BRIGHTNESS.canonical("0.50"));
        assertEquals("1", BeamVisualProperty.IMPACT_INTENSITY.canonical("1.0"));
        assertEquals("true", BeamVisualProperty.ADDITIVE.canonical("TRUE"));
    }

    @Test
    void applySkipsBadAndUnknownEntries() {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("core_width", "5");
        overrides.put("core_color", "not a colour");
        overrides.put("no_such_property", "1");
        BeamVisual out = BeamVisuals.apply(CRUCIO, overrides);
        assertEquals(5, out.coreWidth());
        assertEquals(CRUCIO.coreColor(), out.coreColor());
        assertEquals(Map.of("core_width", "5"), BeamVisuals.sanitize(overrides));
    }

    @Test
    void diffNamesOnlyChangedProperties() {
        BeamVisual edited = BeamVisualProperty.SPIN.with(CRUCIO, "3").orElseThrow();
        assertEquals(Map.of("spin", "3"), BeamVisuals.diff(CRUCIO, edited));
        assertTrue(BeamVisuals.diff(CRUCIO, CRUCIO).isEmpty());
        assertEquals(BeamVisualProperty.values().length, BeamVisuals.toText(CRUCIO).size());
    }

    @Test
    void boundsMatchWhatTheRendererDraws() {
        // BeamGeometry draws two glow shells and rounds sizes to whole pixels.
        assertEquals(2, BeamVisualProperty.GLOW_SHELLS.max());
        assertEquals(BeamVisualProperty.Kind.INT, BeamVisualProperty.CORE_WIDTH.kind());
        assertEquals(BeamVisualProperty.Kind.INT, BeamVisualProperty.CORE_HEIGHT.kind());
    }
}
