package at.koopro.wizardsandbeasts.visual.beam;

/**
 * Everything about a spell beam's <em>look</em> that the beam renderer can actually draw — and nothing else.
 *
 * <p>Deliberately missing: length (a beam reaches as far as the server's spell range × wand range, which is gameplay),
 * trails and taper (the renderer draws whole-pixel boxes with no history), a beam sound (beam casts play none) and an
 * impact effect type (the burst's family comes from the spell). A property is added here only together with the
 * renderer code that reads it.
 *
 * <p>Pure data: no client classes, so the server can store, validate and sync it. Decimal fields are {@code double} so
 * their text form round-trips without float noise ({@code 0.55}, not {@code 0.550000011920929}).
 *
 * @param enabled         whether the beam is drawn at all (gameplay runs either way)
 * @param shape           straight or jagged
 * @param coreColor       RGB of the solid rod
 * @param glowColor       RGB of the glow shells and sparks
 * @param coreWidth       rod width in whole pixels (1/16 block)
 * @param coreHeight      rod height in whole pixels
 * @param coreBrightness  rod opacity; under additive blending, its brightness
 * @param glowBrightness  glow opacity; under additive blending, its brightness
 * @param glowShells      glow shells around the rod (the renderer draws at most two)
 * @param sparkDensity    share of spark candidates lit at any moment, 0..1
 * @param spin            rotation of the rod about its axis, degrees per tick
 * @param additive        additive (bright magic) or alpha (dark magic that must read against a bright sky)
 * @param segments        lightning only: straight pieces in the bolt
 * @param jitter          lightning only: how far interior joints stray, in pixels
 * @param crackleTicks    lightning only: the bolt re-rolls its shape every this many ticks
 * @param fadeInTicks     ticks over which a new beam brightens to full
 * @param fadeOutTicks    ticks over which an ended beam dims away (its reach is never extended)
 * @param impactIntensity scale on the beam's impact bursts (particles and camera kick), read by the server
 */
public record BeamVisual(
        boolean enabled,
        BeamShapeKind shape,
        int coreColor,
        int glowColor,
        int coreWidth,
        int coreHeight,
        double coreBrightness,
        double glowBrightness,
        int glowShells,
        double sparkDensity,
        double spin,
        boolean additive,
        int segments,
        double jitter,
        int crackleTicks,
        int fadeInTicks,
        int fadeOutTicks,
        double impactIntensity) {

    /** The spark share the renderer shipped with (96 of 256 candidates). */
    public static final double DEFAULT_SPARK_DENSITY = 0.375;

    public BeamVisual {
        coreColor &= 0xFFFFFF;
        glowColor &= 0xFFFFFF;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A mutable copy, for {@link BeamVisualProperty} to set one field on. */
    public static final class Builder {
        boolean enabled;
        BeamShapeKind shape;
        int coreColor;
        int glowColor;
        int coreWidth;
        int coreHeight;
        double coreBrightness;
        double glowBrightness;
        int glowShells;
        double sparkDensity;
        double spin;
        boolean additive;
        int segments;
        double jitter;
        int crackleTicks;
        int fadeInTicks;
        int fadeOutTicks;
        double impactIntensity;

        Builder(BeamVisual from) {
            enabled = from.enabled;
            shape = from.shape;
            coreColor = from.coreColor;
            glowColor = from.glowColor;
            coreWidth = from.coreWidth;
            coreHeight = from.coreHeight;
            coreBrightness = from.coreBrightness;
            glowBrightness = from.glowBrightness;
            glowShells = from.glowShells;
            sparkDensity = from.sparkDensity;
            spin = from.spin;
            additive = from.additive;
            segments = from.segments;
            jitter = from.jitter;
            crackleTicks = from.crackleTicks;
            fadeInTicks = from.fadeInTicks;
            fadeOutTicks = from.fadeOutTicks;
            impactIntensity = from.impactIntensity;
        }

        public BeamVisual build() {
            return new BeamVisual(enabled, shape, coreColor, glowColor, coreWidth, coreHeight, coreBrightness,
                    glowBrightness, glowShells, sparkDensity, spin, additive, segments, jitter, crackleTicks,
                    fadeInTicks, fadeOutTicks, impactIntensity);
        }
    }
}
