package at.koopro.wizardsandbeasts.client.beam;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Pure look of a beam — colour, thickness, bloom. No shape and no rendering state; a
 * {@link BeamShape} decides <em>where</em> the beam goes, a {@code BeamStyle} decides how it
 * looks. Keeping the two apart means a new shape is a record plus a registry line and never
 * has to re-declare colour/bloom fields.
 *
 * <p>All sizes are in <strong>pixels</strong> (1px = 1/16 block) so they line up with
 * Blockbench model dimensions. {@link BeamGeometry} converts to blocks.
 *
 * <p>Data-safe: this record has no client-only imports, so it loads on a dedicated server even
 * though only the client ever renders with it.
 */
public record BeamStyle(
        float width,
        float height,
        int coreColor,
        int glowColor,
        float coreOpacity,
        float glowOpacity,
        int bloomLayers,
        float spin,
        boolean additive) {

    public static final Codec<BeamStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("width", 2.0f).forGetter(BeamStyle::width),
            Codec.FLOAT.optionalFieldOf("height", 2.0f).forGetter(BeamStyle::height),
            Codec.INT.optionalFieldOf("core_color", 0xFFFFFF).forGetter(BeamStyle::coreColor),
            Codec.INT.optionalFieldOf("glow_color", 0x44CCFF).forGetter(BeamStyle::glowColor),
            Codec.floatRange(0f, 1f).optionalFieldOf("core_opacity", 1.0f).forGetter(BeamStyle::coreOpacity),
            Codec.floatRange(0f, 1f).optionalFieldOf("glow_opacity", 0.55f).forGetter(BeamStyle::glowOpacity),
            Codec.intRange(0, 8).optionalFieldOf("bloom_layers", 3).forGetter(BeamStyle::bloomLayers),
            Codec.FLOAT.optionalFieldOf("spin", 0.0f).forGetter(BeamStyle::spin),
            Codec.BOOL.optionalFieldOf("additive", true).forGetter(BeamStyle::additive)
    ).apply(instance, BeamStyle::new));

    /** Bright, wide, well-bloomed additive beam. White core, coloured glow. */
    public static BeamStyle laser(int glowColor) {
        return new BeamStyle(2.0f, 2.0f, 0xFFFFFF, glowColor, 1.0f, 0.55f, 3, 0.0f, true);
    }

    /**
     * Thinner beam with fewer bloom layers — a fat, heavily-bloomed core smears a zig-zag back
     * into a solid bar, so lightning wants less of both.
     */
    public static BeamStyle lightning(int glowColor) {
        return new BeamStyle(1.0f, 1.0f, 0xFFFFFF, glowColor, 1.0f, 0.5f, 2, 0.0f, true);
    }

    /**
     * Dark-magic beam: {@code additive = false}. Additive blending can only ever brighten the
     * background, so a dark beam has to use normal alpha blending to read against a bright sky.
     */
    public static BeamStyle dark(int coreColor, int glowColor) {
        return new BeamStyle(2.0f, 2.0f, coreColor, glowColor, 1.0f, 0.55f, 3, 0.0f, false);
    }
}
