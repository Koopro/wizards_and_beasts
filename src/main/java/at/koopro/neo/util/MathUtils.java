package at.koopro.neo.util;

public final class MathUtils {

    private MathUtils() {}

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
