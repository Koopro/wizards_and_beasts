package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The five built-in {@link SettingType}s. Each is a small record so it compares by value, which is what
 * lets a test build the same type twice and treat the two as one.
 */
@NullMarked
public final class SettingTypes {

    /**
     * Plain decimal notation only. {@link Double#parseDouble} also accepts hex floats ({@code 0x1p3}),
     * trailing type suffixes ({@code 1.5d}) and {@code NaN}/{@code Infinity}; none of those is a thing an
     * administrator means, and NaN compares false against every bound, so it would slip past a range check.
     */
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final MathContext DISPLAY_PRECISION = new MathContext(10);

    private SettingTypes() {}

    public static SettingType<Boolean> bool() {
        return BoolType.INSTANCE;
    }

    public static SettingType<Integer> integer(int min, int max) {
        return new IntType(min, max);
    }

    public static SettingType<Double> decimal(double min, double max) {
        return new DoubleType(min, max, niceStep(min, max));
    }

    public static SettingType<Double> decimal(double min, double max, double step) {
        return new DoubleType(min, max, step);
    }

    public static <E extends Enum<E>> SettingType<E> enumeration(Class<E> type) {
        return new EnumType<>(type);
    }

    public static SettingType<String> text(int maxLength, @Nullable Pattern pattern) {
        return new StringType(maxLength, pattern);
    }

    /**
     * About a hundred slider positions across the range, rounded down to a power of ten, so a 0.25–2.0
     * range steps by 0.01 and a 0.01–1.0 range by 0.001 without anyone choosing it per setting.
     */
    static double niceStep(double min, double max) {
        double span = max - min;
        if (!(span > 0.0) || Double.isInfinite(span)) {
            return 1.0;
        }
        return Math.pow(10.0, Math.floor(Math.log10(span / 100.0)));
    }

    // ── types ──

    public enum BoolType implements SettingType<Boolean> {
        INSTANCE;

        @Override
        public SettingKind kind() {
            return SettingKind.BOOLEAN;
        }

        @Override
        public @Nullable Boolean parse(String raw) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                default -> null;
            };
        }

        @Override
        public boolean inBounds(Boolean value) {
            return true;
        }

        @Override
        public String format(Boolean value) {
            return value.toString();
        }
    }

    public record IntType(int minValue, int maxValue) implements SettingType<Integer> {
        public IntType {
            if (minValue > maxValue) {
                throw new IllegalArgumentException("min > max: " + minValue + " > " + maxValue);
            }
        }

        @Override
        public SettingKind kind() {
            return SettingKind.INTEGER;
        }

        @Override
        public @Nullable Integer parse(String raw) {
            String value = raw.trim();
            if (!INTEGER.matcher(value).matches()) {
                return null;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException overflow) {
                return null;
            }
        }

        @Override
        public boolean inBounds(Integer value) {
            return value >= minValue && value <= maxValue;
        }

        @Override
        public String format(Integer value) {
            return value.toString();
        }

        @Override
        public double min() {
            return minValue;
        }

        @Override
        public double max() {
            return maxValue;
        }

        @Override
        public double step() {
            return 1.0;
        }
    }

    public record DoubleType(double minValue, double maxValue, double stepValue) implements SettingType<Double> {
        public DoubleType {
            if (!(minValue <= maxValue)) {
                throw new IllegalArgumentException("min > max: " + minValue + " > " + maxValue);
            }
        }

        @Override
        public SettingKind kind() {
            return SettingKind.DOUBLE;
        }

        @Override
        public @Nullable Double parse(String raw) {
            String value = raw.trim();
            if (!DECIMAL.matcher(value).matches()) {
                return null;
            }
            try {
                double parsed = Double.parseDouble(value);
                return Double.isFinite(parsed) ? parsed : null;
            } catch (NumberFormatException malformed) {
                return null;
            }
        }

        @Override
        public boolean inBounds(Double value) {
            return value >= minValue && value <= maxValue;
        }

        @Override
        public String format(Double value) {
            // valueOf goes through Double.toString, the shortest text that reads back to the same double,
            // so 0.6 prints as "0.6" rather than its exact binary expansion; the precision cap then keeps
            // an arithmetic result (a slider step) from printing seventeen digits.
            return BigDecimal.valueOf(value).round(DISPLAY_PRECISION).stripTrailingZeros().toPlainString();
        }

        @Override
        public double min() {
            return minValue;
        }

        @Override
        public double max() {
            return maxValue;
        }

        @Override
        public double step() {
            return stepValue;
        }
    }

    public record EnumType<E extends Enum<E>>(Class<E> type) implements SettingType<E> {
        @Override
        public SettingKind kind() {
            return SettingKind.ENUM;
        }

        @Override
        public @Nullable E parse(String raw) {
            String value = raw.trim();
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
            return null;
        }

        @Override
        public boolean inBounds(E value) {
            return true;
        }

        @Override
        public String format(E value) {
            return value.name();
        }

        @Override
        public List<String> options() {
            return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        }
    }

    public record StringType(int maxLengthValue, @Nullable Pattern pattern) implements SettingType<String> {
        @Override
        public SettingKind kind() {
            return SettingKind.STRING;
        }

        @Override
        public String parse(String raw) {
            return raw;
        }

        @Override
        public boolean inBounds(String value) {
            return value.length() <= maxLengthValue
                    && (pattern == null || pattern.matcher(value).matches());
        }

        @Override
        public String format(String value) {
            return value;
        }

        @Override
        public int maxLength() {
            return maxLengthValue;
        }
    }
}
