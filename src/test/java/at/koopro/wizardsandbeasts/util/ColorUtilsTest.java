package at.koopro.wizardsandbeasts.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilsTest {

    @Test
    void withAlpha_int() {
        int c = ColorUtils.withAlpha(0x00FF8040, 128);
        assertEquals(128, ColorUtils.getAlpha(c));
        assertEquals(0xFF8040, ColorUtils.getRGB(c));
    }

    @Test
    void pack_roundTrip() {
        int argb = ColorUtils.pack(10, 20, 30, 200);
        assertEquals(200, ColorUtils.getAlpha(argb));
        assertEquals(10, ColorUtils.getRed(argb));
        assertEquals(20, ColorUtils.getGreen(argb));
        assertEquals(30, ColorUtils.getBlue(argb));
    }

    @Test
    void lerp_endpoints() {
        int a = ColorUtils.pack(0, 0, 0, 255);
        int b = ColorUtils.pack(100, 100, 100, 255);
        assertEquals(a, ColorUtils.lerp(a, b, 0f));
        assertEquals(b, ColorUtils.lerp(a, b, 1f));
    }
}
