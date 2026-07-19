package at.koopro.wizardsandbeasts.apparition;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saved-destination model: name hygiene, the cap, replace-by-name, and codec round-tripping. */
class ApparitionPointsTest {

    private static final Gson GSON = new Gson();

    private static ResourceKey<Level> overworld() {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    }

    private static ApparitionPoint point(String name) {
        return new ApparitionPoint(name, overworld(), new Vec3(1.5, 64.0, -2.5), 90f);
    }

    // ── name hygiene ──

    @Test
    void namesAreTrimmedCollapsedAndTruncated() {
        assertEquals("The Burrow", new ApparitionPoint("  The   Burrow  ", overworld(), Vec3.ZERO, 0f).name());

        String tooLong = "x".repeat(ApparitionPoint.MAX_NAME_LENGTH + 40);
        assertEquals(ApparitionPoint.MAX_NAME_LENGTH, point(tooLong).name().length(),
                "an unbounded label must not reach storage");
    }

    @Test
    void nameMatchingIsCaseAndWhitespaceInsensitive() {
        ApparitionPoint p = point("The Burrow");
        assertTrue(p.matches("the burrow"));
        assertTrue(p.matches("  THE   BURROW "));
        assertFalse(p.matches("the burrows"));
    }

    // ── list behaviour ──

    @Test
    void markingTheSameNameReplacesRatherThanDuplicates() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY
                .with(point("Home"))
                .with(new ApparitionPoint("home", overworld(), new Vec3(900, 70, 900), 0f));

        assertEquals(1, points.points().size());
        assertNotNull(points.byName("Home"));
        assertEquals(900.0, points.byName("Home").position().x, 1e-9, "the newer mark wins");
    }

    @Test
    void theListIsCappedAndRefusesOverflow() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY;
        for (int i = 0; i < PlayerApparitionPoints.MAX_POINTS; i++) {
            points = points.with(point("Point " + i));
        }
        assertTrue(points.isFull());

        PlayerApparitionPoints overflowed = points.with(point("One Too Many"));
        assertSame(points, overflowed, "a full list is returned unchanged");
        assertNull(overflowed.byName("One Too Many"));
    }

    @Test
    void replacingIsStillAllowedWhenFull() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY;
        for (int i = 0; i < PlayerApparitionPoints.MAX_POINTS; i++) {
            points = points.with(point("Point " + i));
        }
        PlayerApparitionPoints updated = points.with(
                new ApparitionPoint("Point 0", overworld(), new Vec3(5, 5, 5), 0f));

        assertEquals(PlayerApparitionPoints.MAX_POINTS, updated.points().size());
        assertEquals(5.0, updated.byName("Point 0").position().x, 1e-9);
    }

    @Test
    void forgettingAnUnknownNameChangesNothing() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY.with(point("Home"));
        assertSame(points, points.without("Hogsmeade"));
        assertEquals(0, points.without("Home").points().size());
    }

    @Test
    void indexLookupIsBoundsSafe() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY.with(point("Home"));
        assertNotNull(points.byIndex(0));
        assertNull(points.byIndex(1), "an out-of-range index from a client packet must not throw");
        assertNull(points.byIndex(-1));
    }

    // ── persistence ──

    @Test
    void pointsSurviveACodecRoundTrip() {
        PlayerApparitionPoints original = PlayerApparitionPoints.EMPTY
                .with(new ApparitionPoint("The Burrow", overworld(), new Vec3(12.5, 64.0, -7.25), 135f));

        JsonElement encoded = PlayerApparitionPoints.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        PlayerApparitionPoints restored = PlayerApparitionPoints.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(GSON.toJson(encoded), JsonElement.class))
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        ApparitionPoint point = restored.byName("The Burrow");
        assertNotNull(point);
        assertEquals(overworld(), point.dimension());
        assertEquals(new Vec3(12.5, 64.0, -7.25), point.position());
        assertEquals(135f, point.yaw(), 1e-6);
    }
}
