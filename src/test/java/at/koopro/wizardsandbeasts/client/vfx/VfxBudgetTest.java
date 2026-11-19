package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spell-VFX budget: MEDIUM up close changes nothing, distance and the tick cap thin, a shape keeps its floor. */
class VfxBudgetTest {

    @Test
    void mediumUpCloseIsTheDesignedCount() {
        assertEquals(1.0f, VfxBudget.qualityScale(Config.VfxQuality.MEDIUM));
        assertEquals(1.0f, VfxBudget.distanceScale(4.0));
        assertEquals(24, VfxBudget.scaled(24, 3, 1.0f, 1.0f, false));
    }

    @Test
    void qualityOrdersTheCounts() {
        float low = VfxBudget.qualityScale(Config.VfxQuality.LOW);
        float high = VfxBudget.qualityScale(Config.VfxQuality.HIGH);
        assertTrue(low < 1.0f && high > 1.0f);
        assertTrue(VfxBudget.tickCap(Config.VfxQuality.LOW) < VfxBudget.tickCap(Config.VfxQuality.MEDIUM));
        assertTrue(VfxBudget.tickCap(Config.VfxQuality.MEDIUM) < VfxBudget.tickCap(Config.VfxQuality.HIGH));
        assertEquals(12, VfxBudget.scaled(24, 3, low, 1.0f, false));
    }

    @Test
    void distanceThinsFromSixteenToAQuarterAtThirtyTwo() {
        assertEquals(1.0f, VfxBudget.distanceScale(VfxBudget.NEAR));
        assertEquals(VfxBudget.FAR_SCALE, VfxBudget.distanceScale(VfxBudget.FAR));
        assertEquals(VfxBudget.FAR_SCALE, VfxBudget.distanceScale(100));
        float mid = VfxBudget.distanceScale(24.0);
        assertTrue(mid < 1.0f && mid > VfxBudget.FAR_SCALE);
    }

    @Test
    void aShapeKeepsItsFloorFarAwayAndOverTheCap() {
        assertEquals(4, VfxBudget.scaled(16, 4, 0.5f, 0.25f, false));
        assertEquals(4, VfxBudget.scaled(200, 4, 1.4f, 1.0f, true));
    }

    @Test
    void theFloorNeverExceedsWhatWasAskedFor() {
        assertEquals(2, VfxBudget.scaled(2, 4, 1.0f, 1.0f, true));
        assertEquals(0, VfxBudget.scaled(0, 4, 1.0f, 1.0f, false));
    }
}
