package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text round-trip every entry point relies on: what a type prints, it must parse back unchanged. */
class SettingTypesTest {

    @Test
    void decimalsPrintShortAndParseBack() {
        SettingType<Double> type = SettingTypes.decimal(0.0, 1.0);
        assertEquals("0.6", type.format(0.6));
        assertEquals("1", type.format(1.0));
        assertEquals(0.6, type.parse(type.format(0.6)));
        assertEquals(1.0e-3, type.parse("1e-3"));
    }

    @Test
    void decimalStepIsAboutAHundredthOfTheRange() {
        assertEquals(0.01, SettingTypes.decimal(0.25, 2.0).step(), 1e-12);
        assertEquals(0.001, SettingTypes.decimal(0.01, 1.0).step(), 1e-12);
        assertEquals(0.1, SettingTypes.decimal(0.0, 20.0).step(), 1e-12);
    }

    @Test
    void integersRejectOverflowAndFractions() {
        SettingType<Integer> type = SettingTypes.integer(0, Integer.MAX_VALUE);
        assertNull(type.parse("2147483648"));
        assertNull(type.parse("1.0"));
        assertEquals(Integer.MAX_VALUE, type.parse("2147483647"));
        assertTrue(type.inBounds(Integer.MAX_VALUE));
        assertFalse(type.inBounds(-1));
    }

    @Test
    void stringsHonourLengthAndPattern() {
        SettingType<String> type = SettingTypes.text(8, Pattern.compile("[a-z]*"));
        assertTrue(type.inBounds("abc"));
        assertFalse(type.inBounds("abcdefghi"));
        assertFalse(type.inBounds("ABC"));
    }
}
