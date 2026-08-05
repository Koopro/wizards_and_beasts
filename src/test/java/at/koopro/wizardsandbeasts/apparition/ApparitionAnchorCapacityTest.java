package at.koopro.wizardsandbeasts.apparition;

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

/** Anchor capacity: how it scales, and that mastery still reaches the old flat cap. */
class ApparitionAnchorCapacityTest {

    private static ResourceKey<Level> overworld() {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    }

    private static ApparitionPoint point(String name) {
        return new ApparitionPoint(name, overworld(), new Vec3(1.5, 64.0, -2.5), 90f);
    }

    @Test
    void capacityRunsFromFourToTwelve() {
        assertEquals(4, ApparitionAnchors.capacity(0.0f));
        assertEquals(12, ApparitionAnchors.capacity(1.0f));
    }

    @Test
    void masteryReachesTheOldFlatCapSoNobodyLosesAnchors() {
        assertEquals(PlayerApparitionPoints.MAX_POINTS, ApparitionAnchors.capacity(1.0f));
    }

    @Test
    void proficiencyIsClampedRatherThanTrusted() {
        assertEquals(4, ApparitionAnchors.capacity(-2.0f));
        assertEquals(12, ApparitionAnchors.capacity(7.0f));
    }

    @Test
    void aNoviceIsHeldToFour() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY;
        int capacity = ApparitionAnchors.capacity(0.0f);
        for (int i = 0; i < capacity; i++) {
            points = points.with(point("Point " + i), capacity);
        }
        assertTrue(points.isFull(capacity));

        PlayerApparitionPoints overflowed = points.with(point("One Too Many"), capacity);
        assertSame(points, overflowed);
        assertNull(overflowed.byName("One Too Many"));
    }

    @Test
    void reusingANameStillReplacesAtAFullSet() {
        int capacity = ApparitionAnchors.capacity(0.0f);
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY;
        for (int i = 0; i < capacity; i++) {
            points = points.with(point("Point " + i), capacity);
        }
        PlayerApparitionPoints updated = points.with(
                new ApparitionPoint("Point 0", overworld(), new Vec3(5, 5, 5), 0f), capacity);

        assertEquals(capacity, updated.points().size());
        assertNotNull(updated.byName("Point 0"));
        assertEquals(5.0, updated.byName("Point 0").position().x, 1e-9);
    }

    @Test
    void theAbsoluteCeilingStillBindsAboveTheEarnedCapacity() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY;
        for (int i = 0; i < PlayerApparitionPoints.MAX_POINTS; i++) {
            points = points.with(point("Point " + i), 999);
        }
        assertSame(points, points.with(point("Overflow"), 999),
                "a skill node must not lift the list past its hard ceiling");
    }

    @Test
    void aNoviceIsNotFullAtThree() {
        PlayerApparitionPoints points = PlayerApparitionPoints.EMPTY
                .with(point("A"), 4)
                .with(point("B"), 4)
                .with(point("C"), 4);
        assertFalse(points.isFull(4));
    }
}
