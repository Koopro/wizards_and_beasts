package at.koopro.wizardsandbeasts.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RgbHexTest {

    @Test
    void normalize_accepts_hash_prefix_uppercases() {
        assertEquals("AABBCC", RgbHex.normalizeRgbHex("#AaBbCc"));
    }

    @Test
    void normalize_accepts_plain_six_digits() {
        assertEquals("001122", RgbHex.normalizeRgbHex("001122"));
    }

    @Test
    void normalize_rejects_wrong_length() {
        assertNull(RgbHex.normalizeRgbHex("abc"));
        assertNull(RgbHex.normalizeRgbHex("00112233"));
    }

    @Test
    void normalize_rejects_non_hex() {
        assertNull(RgbHex.normalizeRgbHex("GGGGBB"));
    }
}
