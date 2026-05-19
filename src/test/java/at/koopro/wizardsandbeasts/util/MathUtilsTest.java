package at.koopro.wizardsandbeasts.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MathUtilsTest {

    @Test
    void clampInt_insideRange() {
        assertEquals(5, MathUtils.clampInt(5, 0, 10));
    }

    @Test
    void clampInt_belowMin() {
        assertEquals(0, MathUtils.clampInt(-3, 0, 10));
    }

    @Test
    void clampInt_aboveMax() {
        assertEquals(10, MathUtils.clampInt(99, 0, 10));
    }
}
