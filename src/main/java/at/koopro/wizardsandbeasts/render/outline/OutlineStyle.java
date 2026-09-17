package at.koopro.wizardsandbeasts.render.outline;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * How a temporary outline looks and how long it lasts — the one value a spell hands to {@link SpellOutlines},
 * so its entity outlines and block highlights cannot drift apart in colour.
 *
 * @param argb          packed ARGB. An alpha byte of {@code 0} is read as opaque: spell colours authored as
 *                      bare RGB would otherwise be a fully transparent outline that draws nothing and reports
 *                      nothing. A low non-zero alpha is honoured.
 * @param durationTicks game ticks; zero or negative outlines nothing
 */
@NullMarked
public record OutlineStyle(int argb, int durationTicks) {

    /**
     * Saturation and brightness floors for {@link #forSpell}. The outline post-effect and the thin block
     * lines both wash a pale colour towards white — Revelio's {@code 0xFFFFAA} read as a grey smudge against
     * daylight. Lifting saturation to 0.6 keeps the hue, so it is still visibly the spell's colour.
     */
    public static final float MIN_SATURATION = 0.6f;
    public static final float MIN_BRIGHTNESS = 0.8f;

    /** Below this a colour is grey, its hue meaningless, and saturating it would invent one. */
    private static final float ACHROMATIC = 0.05f;

    public OutlineStyle {
        if (ARGB.alpha(argb) == 0) {
            argb = ARGB.opaque(argb);
        }
    }

    /** A spell's colour made {@link #vivid} and opaque, for {@code durationTicks}. */
    public static OutlineStyle forSpell(int spellColour, int durationTicks) {
        return new OutlineStyle(ARGB.opaque(vivid(spellColour)), durationTicks);
    }

    /**
     * Same hue, with saturation and brightness raised to the floors; greys only have their brightness raised.
     *
     * <p>Done in RGB rather than through an HSV round trip. In HSV every channel is {@code V × (1 − S × k)}
     * with {@code k} fixed by the hue, so scaling saturation is scaling each channel's distance below the
     * maximum, and scaling brightness is scaling every channel. That keeps the hue exactly and leaves a colour
     * that needs no change bit-for-bit untouched — {@code Mth.hsvToRgb} truncates, and turned Revelio's
     * {@code 0xFF} red into {@code 0xFE}.
     */
    public static int vivid(int rgb) {
        float r = ARGB.red(rgb) / 255.0f;
        float g = ARGB.green(rgb) / 255.0f;
        float b = ARGB.blue(rgb) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float saturation = max == 0.0f ? 0.0f : (max - min) / max;

        if (saturation >= ACHROMATIC && saturation < MIN_SATURATION) {
            float stretch = MIN_SATURATION / saturation;
            r = max - (max - r) * stretch;
            g = max - (max - g) * stretch;
            b = max - (max - b) * stretch;
        }
        if (max < MIN_BRIGHTNESS) {
            if (max == 0.0f) {
                r = g = b = MIN_BRIGHTNESS;
            } else {
                float lift = MIN_BRIGHTNESS / max;
                r *= lift;
                g *= lift;
                b *= lift;
            }
        }
        return ARGB.color(channel(r), channel(g), channel(b)) & 0xFFFFFF;
    }

    private static int channel(float value) {
        return Mth.clamp(Math.round(value * 255.0f), 0, 255);
    }
}
