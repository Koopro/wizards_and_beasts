package at.koopro.wizardsandbeasts.wand.visual;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The rhythm of a core's light. Colour says which creature gave the core; rhythm says what kind of
 * thing it is — a heart beats, a feather smoulders, a hair just shines.
 *
 * <p>Every pattern is a pure function of time and a per-wand phase, so two wands of one core do not
 * pulse in lockstep and nothing needs ticking. Values stay inside {@code [0.35, 1]}: a pattern varies
 * the light, it never puts it out (that is the bond's job).
 */
public enum WandGlintPattern implements StringRepresentable {
    /** Unwavering. Unicorn hair: the most consistent core. */
    STEADY,
    /** A slow swell and fade. */
    PULSE,
    /** A double beat, then rest. Dragon heartstring. */
    HEARTBEAT,
    /** A smoulder with uneven flickers. Phoenix feather. */
    EMBER,
    /** Quick small glitters over a steady base. Veela hair: temperamental, beautiful. */
    SHIMMER,
    /** Mostly low, with a sharp flash now and then. Thunderbird tail feather. */
    STORM,
    /** A long, dim breath. Thestral tail hair; the Elder Wand. */
    SMOKE;

    public static final Codec<WandGlintPattern> CODEC = StringRepresentable.fromEnum(WandGlintPattern::values);

    private static final float TAU = (float) (Math.PI * 2.0);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * How bright the light is at this moment, {@code [0.35, 1]}.
     *
     * @param seconds client time in seconds
     * @param phase   the wand's own offset, {@code [0, 1)}
     */
    public float intensity(float seconds, float phase) {
        float t = seconds + phase * 17.0f;
        float v = switch (this) {
            case STEADY -> 0.9f;
            case PULSE -> 0.675f + 0.325f * (float) Math.sin(t * TAU / 3.2f);
            case HEARTBEAT -> {
                float c = fract(t / 1.4f);
                float beat = bump(c, 0.06f, 0.07f) + 0.7f * bump(c, 0.26f, 0.07f);
                yield 0.45f + 0.55f * Math.min(1.0f, beat);
            }
            case EMBER -> 0.62f + 0.18f * (float) Math.sin(t * 2.3f)
                    + 0.20f * noise(t * 7.0f);
            case SHIMMER -> 0.70f + 0.30f * Math.max(0.0f, noise(t * 11.0f) * 2.0f - 1.0f);
            case STORM -> {
                float c = fract(t / 2.7f);
                yield 0.45f + 0.55f * (bump(c, 0.10f, 0.025f) + 0.6f * bump(c, 0.16f, 0.02f));
            }
            case SMOKE -> 0.55f + 0.30f * (float) Math.sin(t * TAU / 6.5f);
        };
        return Math.max(0.35f, Math.min(1.0f, v));
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /** A smooth hump of half-width {@code w} centred on {@code at}, peak 1. */
    private static float bump(float x, float at, float w) {
        float d = (x - at) / w;
        return d * d >= 1.0f ? 0.0f : (1.0f - d * d) * (1.0f - d * d);
    }

    /** Smooth value noise in {@code [0, 1]}, deterministic in its argument. */
    private static float noise(float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = hash(i);
        float b = hash(i + 1);
        float s = f * f * (3.0f - 2.0f * f);
        return a + (b - a) * s;
    }

    private static float hash(int n) {
        int h = n * 0x27D4EB2D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535.0f;
    }
}
