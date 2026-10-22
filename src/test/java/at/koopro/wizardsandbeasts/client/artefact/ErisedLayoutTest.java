package at.koopro.wizardsandbeasts.client.artefact;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErisedLayoutTest {

    /** GUI sizes from a 1280x720 window at scales 1-4, a 854x480 window at 2, and a 320x240 minimum. */
    @ParameterizedTest
    @CsvSource({"1280,720", "640,360", "427,240", "320,180", "427,240", "320,240"})
    void theMirrorFitsAndTheGlassIsInsideIt(int w, int h) {
        ErisedLayout l = ErisedLayout.of(w, h);
        assertTrue(l.x() >= 0 && l.y() >= 0 && l.x() + l.w() <= w && l.y() + l.h() <= h, "off screen: " + l);
        assertTrue(l.glassX() > l.x() && l.glassY() > l.y() && l.glassX() + l.glassW() < l.x() + l.w()
                && l.glassY() + l.glassH() < l.y() + l.h(), "glass outside the frame: " + l);
        assertTrue(l.glassW() >= 100, "too narrow to read: " + l);
        assertTrue(l.overRail(l.railX() + 1, l.railY() + 1));
    }

    @ParameterizedTest
    @CsvSource({"1280,720", "640,360"})
    void drawnAtItsOwnSizeWhenItFits(int w, int h) {
        ErisedLayout l = ErisedLayout.of(w, h);
        assertEquals(1.0f, l.scale());
        assertEquals(ErisedLayout.TEX_W, l.w());
        assertEquals(152, l.glassW());
    }
}
