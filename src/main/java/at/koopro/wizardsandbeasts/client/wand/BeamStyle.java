package at.koopro.wizardsandbeasts.client.wand;

import net.minecraft.util.Mth;

/**
 * The complete visual identity of one beam: three nested layers plus the path, pulse and endpoint
 * behaviour that drive them. Immutable — a style is art direction, not state.
 *
 * <p>The {@code client.beam} shapes own the <em>technique</em> (jagged path, volumetric quads,
 * layering); a style owns everything that makes one spell's beam look unlike another's. That split is
 * what lets Avada Kedavra read as a curse and Aguamenti as a water jet without either spell touching
 * the renderer.
 *
 * <p>Layer colours are derived from a single spell hue via {@link #withHue(int)} rather than authored
 * per layer. Each layer stores a {@code whiteness} instead: the wide halo keeps the spell's hue at
 * full chroma and the narrow core is pushed toward white. That split is the one that survives additive
 * blending — a saturated core under a whitened halo sums to flat pastel with no visible filament.
 *
 * @see BeamStyles for the per-spell instances
 */
public record BeamStyle(Layer outer, Layer mid, Layer core, Path path, Pulse pulse, Flash flash) {

    /** How a layer's segments are turned into triangles. */
    public enum Geometry {
        /**
         * One camera-facing quad per segment. No silhouette from any angle, so it is the right choice
         * for the soft outer halo; degenerate when looked at straight down the beam axis, which the
         * muzzle bloom covers.
         */
        BILLBOARD(1.0f),
        /**
         * A closed prism around the segment — real volume, so the layer keeps a consistent thickness
         * as the camera orbits and the bolt still looks solid seen end-on. Front and back faces both
         * draw (culling is off), so the additive sum doubles; {@link #alphaScale()} compensates.
         */
        PRISM(0.55f);

        private final float alphaScale;

        Geometry(float alphaScale) {
            this.alphaScale = alphaScale;
        }

        /** Per-vertex alpha correction so swapping geometry does not change apparent brightness. */
        public float alphaScale() {
            return alphaScale;
        }
    }

    /**
     * One nested beam layer. {@code width} is a half-width in blocks (billboard half-span / prism
     * radius). {@code whiteness} is how far this layer is pushed from the spell hue toward white;
     * {@code r}/{@code g}/{@code b} are the resolved product of that, filled in by {@link #withHue}.
     */
    public record Layer(float width, float r, float g, float b, float alpha, float whiteness,
                        Geometry geometry) {

        /** Declares a layer in hue-relative terms; the concrete colour arrives with {@link #withHue}. */
        public static Layer of(float width, float alpha, float whiteness, Geometry geometry) {
            return new Layer(width, 1f, 1f, 1f, alpha, whiteness, geometry);
        }

        /** Declares a layer with an explicit colour — used by the debug override, which edits raw RGB. */
        public static Layer explicit(float width, float r, float g, float b, float alpha, Geometry geometry) {
            return new Layer(width, r, g, b, alpha, 0f, geometry);
        }

        Layer tinted(float hueR, float hueG, float hueB) {
            return new Layer(width,
                    Mth.lerp(whiteness, hueR, 1f),
                    Mth.lerp(whiteness, hueG, 1f),
                    Mth.lerp(whiteness, hueB, 1f),
                    alpha, whiteness, geometry);
        }

        Layer withGeometry(Geometry replacement) {
            return new Layer(width, r, g, b, alpha, whiteness, replacement);
        }
    }

    /**
     * Path construction.
     *
     * @param jag            lateral displacement in blocks at the arc's widest point
     * @param bow            whole-arc lean as a fraction of {@code jag}; without it the bolt jitters along a ruler
     * @param forkChance     per-node chance of spawning a branch
     * @param maxForks       branches per shape frame; {@code 0} disables forking
     * @param forkLength     branch length as a fraction of the beam length
     * @param reseedTicks    ticks between shape keyframes
     * @param morph          {@code 0} = hard snap between keyframes (electrical crackle),
     *                       {@code 1} = continuous interpolation (a writhing or flowing beam)
     * @param segmentDensity multiplier on the global segments-per-block quality setting
     */
    public record Path(float jag, float bow, float forkChance, int maxForks, float forkLength,
                       int reseedTicks, float morph, float segmentDensity) {

        Path withMaxForks(int replacement) {
            return new Path(jag, bow, forkChance, replacement, forkLength, reseedTicks, morph, segmentDensity);
        }
    }

    /**
     * Brightness and texture animation.
     *
     * @param amount          depth of the continuous brightness shimmer, {@code 0}..{@code 1}
     * @param speed           shimmer frequency
     * @param scrollSpeed     texture tiles scrolled along the beam per tick
     * @param uvBlocksPerTile blocks of beam per texture tile; lower reads as denser detail
     */
    public record Pulse(float amount, float speed, float scrollSpeed, float uvBlocksPerTile) {}

    /**
     * Endpoint bloom.
     *
     * @param muzzleScale  muzzle bloom radius as a multiple of the outer halo width
     * @param impactScale  impact bloom radius as a multiple of the outer halo width
     * @param impactSparks scattered spark quads at the impact point; {@code 0} disables them
     */
    public record Flash(float muzzleScale, float impactScale, int impactSparks) {}

    /**
     * Resolves every layer's colour from one ARGB spell colour. The hue is normalised to full chroma
     * first so a dim spell colour still throws a saturated halo — brightness is the layer alphas' job,
     * not the hue's.
     */
    public BeamStyle withHue(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float peak = Math.max(0.001f, Math.max(r, Math.max(g, b)));
        float hueR = Math.min(1f, r / peak);
        float hueG = Math.min(1f, g / peak);
        float hueB = Math.min(1f, b / peak);
        return new BeamStyle(outer.tinted(hueR, hueG, hueB), mid.tinted(hueR, hueG, hueB),
                core.tinted(hueR, hueG, hueB), path, pulse, flash);
    }

    /** Drops every layer to billboards — the LOW quality preset's cheap path. */
    public BeamStyle flattened() {
        return new BeamStyle(outer.withGeometry(Geometry.BILLBOARD), mid.withGeometry(Geometry.BILLBOARD),
                core.withGeometry(Geometry.BILLBOARD), path, pulse, flash);
    }

    /** Clamps branch count to a quality budget without touching the style's art direction. */
    public BeamStyle cappedForks(int limit) {
        int capped = Math.min(path.maxForks(), Math.max(0, limit));
        return capped == path.maxForks()
                ? this
                : new BeamStyle(outer, mid, core, path.withMaxForks(capped), pulse, flash);
    }
}
