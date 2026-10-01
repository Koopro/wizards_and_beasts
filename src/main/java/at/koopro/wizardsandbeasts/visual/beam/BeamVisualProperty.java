package at.koopro.wizardsandbeasts.visual.beam;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * One editable field of a {@link BeamVisual}: its stable id (setting id segment, storage key, wire key), its kind and
 * bounds, and how it reads and writes as text.
 *
 * <p>Text is the one representation shared by the admin setting, world storage and the sync payload, so there is one
 * parser: {@link #with} refuses anything malformed or out of bounds rather than clamping it — a refused override is
 * dropped, never bent into a value nobody asked for. The bounds are the renderer's: {@link #GLOW_SHELLS} stops at two
 * because {@code BeamGeometry} draws two, widths are whole pixels because it rounds to them.
 */
@NullMarked
public enum BeamVisualProperty {
    ENABLED("enabled", Kind.BOOL, 0, 1, 1, v -> v.enabled(), (b, t) -> b.enabled = (Boolean) t),
    SHAPE("shape", Kind.CHOICE, 0, 0, 0, v -> v.shape(), (b, t) -> b.shape = (BeamShapeKind) t),
    CORE_COLOR("core_color", Kind.COLOR, 0, 0xFFFFFF, 1, v -> v.coreColor(), (b, t) -> b.coreColor = (Integer) t),
    GLOW_COLOR("glow_color", Kind.COLOR, 0, 0xFFFFFF, 1, v -> v.glowColor(), (b, t) -> b.glowColor = (Integer) t),
    CORE_WIDTH("core_width", Kind.INT, 1, 12, 1, v -> v.coreWidth(), (b, t) -> b.coreWidth = (Integer) t),
    CORE_HEIGHT("core_height", Kind.INT, 1, 12, 1, v -> v.coreHeight(), (b, t) -> b.coreHeight = (Integer) t),
    CORE_BRIGHTNESS("core_brightness", Kind.DECIMAL, 0, 1, 0.05, v -> v.coreBrightness(),
            (b, t) -> b.coreBrightness = (Double) t),
    GLOW_BRIGHTNESS("glow_brightness", Kind.DECIMAL, 0, 1, 0.05, v -> v.glowBrightness(),
            (b, t) -> b.glowBrightness = (Double) t),
    GLOW_SHELLS("glow_shells", Kind.INT, 0, 2, 1, v -> v.glowShells(), (b, t) -> b.glowShells = (Integer) t),
    SPARK_DENSITY("spark_density", Kind.DECIMAL, 0, 1, 0.05, v -> v.sparkDensity(),
            (b, t) -> b.sparkDensity = (Double) t),
    SPIN("spin", Kind.DECIMAL, -20, 20, 0.5, v -> v.spin(), (b, t) -> b.spin = (Double) t),
    ADDITIVE("additive", Kind.BOOL, 0, 1, 1, v -> v.additive(), (b, t) -> b.additive = (Boolean) t),
    SEGMENTS("segments", Kind.INT, 1, 32, 1, v -> v.segments(), (b, t) -> b.segments = (Integer) t),
    JITTER("jitter", Kind.DECIMAL, 0, 24, 0.5, v -> v.jitter(), (b, t) -> b.jitter = (Double) t),
    CRACKLE_TICKS("crackle_ticks", Kind.INT, 1, 40, 1, v -> v.crackleTicks(), (b, t) -> b.crackleTicks = (Integer) t),
    FADE_IN_TICKS("fade_in_ticks", Kind.INT, 0, 40, 1, v -> v.fadeInTicks(), (b, t) -> b.fadeInTicks = (Integer) t),
    FADE_OUT_TICKS("fade_out_ticks", Kind.INT, 0, 40, 1, v -> v.fadeOutTicks(), (b, t) -> b.fadeOutTicks = (Integer) t),
    IMPACT_INTENSITY("impact_intensity", Kind.DECIMAL, 0, 2, 0.05, v -> v.impactIntensity(),
            (b, t) -> b.impactIntensity = (Double) t);

    /** How a property's text is read. */
    public enum Kind { BOOL, INT, DECIMAL, CHOICE, COLOR }

    /** {@code #RRGGBB}, case-insensitive, the hash optional on input. */
    public static final Pattern COLOR_TEXT = Pattern.compile("(?i)#?[0-9a-f]{6}");
    private static final MathContext PRECISION = new MathContext(6);

    private final String id;
    private final Kind kind;
    private final double min;
    private final double max;
    private final double step;
    private final Function<BeamVisual, Object> getter;
    private final BiConsumer<BeamVisual.Builder, Object> setter;

    BeamVisualProperty(String id, Kind kind, double min, double max, double step,
                       Function<BeamVisual, Object> getter, BiConsumer<BeamVisual.Builder, Object> setter) {
        this.id = id;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    /** Only meaningful while the shape is {@link BeamShapeKind#LIGHTNING}. */
    public boolean lightningOnly() {
        return this == SEGMENTS || this == JITTER || this == CRACKLE_TICKS;
    }

    /** Read on the server where impact bursts are sent, not by the renderer. */
    public boolean serverRead() {
        return this == IMPACT_INTENSITY;
    }

    /** This property's value in {@code visual}, as canonical text. */
    public String text(BeamVisual visual) {
        return format(getter.apply(visual));
    }

    /** {@code visual} with this property set from {@code text}, or empty when the text is malformed or out of bounds. */
    public Optional<BeamVisual> with(BeamVisual visual, String text) {
        Object parsed = parse(text);
        if (parsed == null) {
            return Optional.empty();
        }
        BeamVisual.Builder builder = visual.toBuilder();
        setter.accept(builder, parsed);
        return Optional.of(builder.build());
    }

    /** Whether {@code text} is a legal value of this property. */
    public boolean accepts(String text) {
        return parse(text) != null;
    }

    /** {@code text} in canonical form, or null when it is not a legal value. */
    public @Nullable String canonical(String text) {
        Object parsed = parse(text);
        return parsed == null ? null : format(parsed);
    }

    private @Nullable Object parse(String raw) {
        String text = raw.trim();
        switch (kind) {
            case BOOL -> {
                if (text.equalsIgnoreCase("true")) {
                    return Boolean.TRUE;
                }
                return text.equalsIgnoreCase("false") ? Boolean.FALSE : null;
            }
            case INT -> {
                try {
                    int value = Integer.parseInt(text);
                    return value >= min && value <= max ? value : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case DECIMAL -> {
                try {
                    double value = Double.parseDouble(text);
                    return Double.isFinite(value) && value >= min && value <= max ? value : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case CHOICE -> {
                for (BeamShapeKind shape : BeamShapeKind.values()) {
                    if (shape.name().equalsIgnoreCase(text)) {
                        return shape;
                    }
                }
                return null;
            }
            case COLOR -> {
                if (!COLOR_TEXT.matcher(text).matches()) {
                    return null;
                }
                return Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16);
            }
        }
        return null;
    }

    private String format(Object value) {
        return switch (kind) {
            case BOOL, INT -> value.toString();
            case DECIMAL -> BigDecimal.valueOf((Double) value).round(PRECISION).stripTrailingZeros().toPlainString();
            case CHOICE -> ((BeamShapeKind) value).name();
            case COLOR -> String.format(Locale.ROOT, "#%06X", ((Integer) value) & 0xFFFFFF);
        };
    }

    public static @Nullable BeamVisualProperty byId(String id) {
        for (BeamVisualProperty property : values()) {
            if (property.id.equals(id)) {
                return property;
            }
        }
        return null;
    }

    /** In editor order. */
    public static List<BeamVisualProperty> all() {
        return List.of(values());
    }
}
