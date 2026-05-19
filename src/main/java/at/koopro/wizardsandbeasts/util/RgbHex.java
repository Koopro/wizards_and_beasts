package at.koopro.wizardsandbeasts.util;

import javax.annotation.Nullable;

/**
 * Normalizes user RGB hex input for glow debug and similar (6 hex digits, optional leading #).
 */
public final class RgbHex {
    private RgbHex() {}

    /**
     * @return upper-case 6-char hex without #, or null if invalid
     */
    @Nullable
    public static String normalizeRgbHex(String input) {
        String value = input.startsWith("#") ? input.substring(1) : input;
        if (value.length() != 6) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                return null;
            }
        }
        return value.toUpperCase();
    }
}
