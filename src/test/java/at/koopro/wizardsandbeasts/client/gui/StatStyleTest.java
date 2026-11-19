package at.koopro.wizardsandbeasts.client.gui;

import at.koopro.wizardsandbeasts.client.gui.character.StatStyle;
import at.koopro.wizardsandbeasts.client.gui.ui.UiMotion;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each stat's ink is its name colour on the parchment sheet, so it has to read as text there. */
class StatStyleTest {

    @Test
    void everyStatInkReadsOnTheSheet() {
        for (PlayerStat stat : PlayerStat.values()) {
            int ink = StatStyle.ink(stat);
            assertTrue(UiContrast.ratio(ink, WizardsPalette.PAGE) >= UiContrast.AA_TEXT,
                    () -> stat + " ink is under AA on PAGE");
            // A card's wash sits between PAGE and PAGE_SHADE; the shade is the darker bound.
            assertTrue(UiContrast.ratio(ink, WizardsPalette.PAGE_SHADE) >= 4.0,
                    () -> stat + " ink is under 4:1 on PAGE_SHADE");
        }
    }

    @Test
    void inksAreDistinctAndNotThePageMeaningColours() {
        Set<Integer> seen = new HashSet<>();
        for (PlayerStat stat : PlayerStat.values()) {
            int ink = StatStyle.ink(stat);
            assertTrue(seen.add(ink), () -> stat + " shares an ink");
            assertTrue(ink != WizardsPalette.PAGE_RUBRIC && ink != WizardsPalette.PAGE_GOOD
                    && ink != WizardsPalette.PAGE_BAD, () -> stat + " reuses a page meaning colour");
        }
    }

    @Test
    void washKeepsTheInkAndSetsAlpha() {
        int wash = StatStyle.wash(PlayerStat.KNOWLEDGE, 0x14);
        assertEquals(0x14, wash >>> 24);
        assertEquals(StatStyle.ink(PlayerStat.KNOWLEDGE) & 0xFFFFFF, wash & 0xFFFFFF);
    }

    @Test
    void easeOutIsMonotoneAndPinned() {
        assertEquals(0f, UiMotion.easeOut(0f), 1e-6);
        assertEquals(1f, UiMotion.easeOut(1f), 1e-6);
        assertEquals(1f, UiMotion.easeOut(2f), 1e-6);
        float last = -1f;
        for (int i = 0; i <= 20; i++) {
            float v = UiMotion.easeOut(i / 20f);
            assertTrue(v >= last);
            last = v;
        }
        // Ease-out: past the midpoint by half time.
        assertTrue(UiMotion.easeOut(0.5f) > 0.5f);
    }

    @Test
    void measureSpokeZeroPointsUpAndFractionsScale() {
        int[] top = at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart.point(50, 50, 30, 0, 5, 1f);
        assertEquals(50, top[0]);
        assertEquals(20, top[1]);
        int[] half = at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart.point(50, 50, 30, 0, 5, 0.5f);
        assertEquals(35, half[1]);
        // Clamped: a stat over its maximum stays on the outer ring.
        int[] over = at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart.point(50, 50, 30, 2, 5, 3f);
        int[] full = at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart.point(50, 50, 30, 2, 5, 1f);
        assertEquals(full[0], over[0]);
        assertEquals(full[1], over[1]);
        // Spokes 1 and 4 mirror each other about the vertical axis.
        int[] right = at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart.point(50, 50, 30, 1, 5, 1f);
        int[] left = at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart.point(50, 50, 30, 4, 5, 1f);
        assertEquals(right[1], left[1]);
        assertEquals(50 - right[0], left[0] - 50);
    }

    @Test
    void fadeScalesOnlyAlpha() {
        assertEquals(0x80FFFFFF, UiMotion.fade(0xFFFFFFFF, 128 / 255f));
        assertEquals(0x00123456, UiMotion.fade(0xFF123456, 0f));
        assertEquals(0x58123456, UiMotion.fade(0xB0123456, 0.5f));
    }
}
