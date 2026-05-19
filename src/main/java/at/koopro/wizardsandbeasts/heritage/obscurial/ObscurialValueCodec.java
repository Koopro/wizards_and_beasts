package at.koopro.wizardsandbeasts.heritage.obscurial;

public final class ObscurialValueCodec {

    private ObscurialValueCodec() {}

    public static float clampPercent(float value, float max) {
        return Math.max(0f, Math.min(max, value));
    }

    public static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
