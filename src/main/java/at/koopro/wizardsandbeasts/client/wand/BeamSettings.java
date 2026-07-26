package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;

/**
 * Client-side beam quality budget and debug override. Purely client-side, never serialized.
 *
 * <p>Two distinct jobs live here, and keeping them apart is the point:
 * <ul>
 *   <li><b>Quality caps</b> — segment density, path segment clamps, whether layers get volumetric
 *       geometry, whether endpoint blooms draw, a fork budget. These always apply, on top of whatever
 *       the spell asked for, and are what the performance presets move.</li>
 *   <li><b>Debug override</b> — while {@link #debugOverride} is set (the beam debug screen sets it
 *       for as long as it is open) the layer colours/widths/alphas below replace the spell's
 *       {@link BeamStyle} entirely, so sliders preview live.</li>
 * </ul>
 * A spell's own look is <em>not</em> stored here — see {@link BeamStyles}.
 */
public final class BeamSettings {

    public enum PerformancePreset {
        LOW,
        MEDIUM,
        HIGH
    }

    /** Blocks of lateral jag per unit of the debug {@code noiseAmp} slider. */
    private static final float LATERAL_NOISE_BLOCKS = 0.30f;

    // ── Global ──────────────────────────────────────────────────────
    /** Beam length for the debug force-beam only; real casts use the spell's range. */
    public static float range = 50.0f;
    /** Drives both the debug beam's shimmer frequency and its UV scroll rate. */
    public static float speed = 0.08f;
    /**
     * Path subdivisions per two blocks of beam, before the style's own density multiplier.
     * Clamped by {@link #minPathSegments}/{@link #maxPathSegments}.
     */
    public static int segmentsPerUnit = 4;

    public static int minPathSegments = 8;
    public static int maxPathSegments = 28;

    /** Whether the inner layers get prism geometry. {@link PerformancePreset#LOW} flattens them. */
    public static boolean volumetric = true;

    /** Whether the muzzle/impact bloom quads draw. */
    public static boolean endpointFlashes = true;

    /** Hard cap on fork branches, whatever the style asks for. */
    public static int maxForks = 4;

    /** While set, the fields below replace the spell's style so the debug sliders preview live. */
    public static boolean debugOverride = false;

    // ── Debug override values ───────────────────────────────────────
    /** Ticks between shape keyframes for the debug override. */
    public static int arcRefreshTicks = 2;

    /** Per-node fork chance for the debug override. */
    public static float forkChance = 0.18f;

    /** Blocks of beam per texture tile for the debug override. */
    public static float uvBlocksPerTile = 1.6f;

    /** Keyframe interpolation for the debug override: {@code 0} snaps, {@code 1} morphs smoothly. */
    public static float morph = 0.15f;

    // ── Per-layer ───────────────────────────────────────────────────
    public static final int OUTER = 0;
    public static final int MID = 1;
    public static final int CORE = 2;

    public static final String[] LAYER_NAMES = {"Outer Glow", "Mid Glow", "Core"};

    // Widths/alphas are tuned for ADDITIVE blending: a wide, dim, saturated halo that the narrow,
    // near-opaque white core burns through. Under alpha blending these numbers would look washed out;
    // under additive the layers sum, so the core is what goes white-hot.
    public static final LayerSettings[] layers = {
            new LayerSettings(0.145f, 0.35f, 0.60f, 1.00f, 0.26f, 1.00f), // outer halo
            new LayerSettings(0.055f, 0.65f, 0.85f, 1.00f, 0.32f, 1.00f), // mid glow
            new LayerSettings(0.022f, 0.95f, 0.98f, 1.00f, 0.62f, 1.00f), // white-hot core
    };

    private static final LayerSettings[] DEFAULTS = {
            new LayerSettings(0.145f, 0.35f, 0.60f, 1.00f, 0.26f, 1.00f),
            new LayerSettings(0.055f, 0.65f, 0.85f, 1.00f, 0.32f, 1.00f),
            new LayerSettings(0.022f, 0.95f, 0.98f, 1.00f, 0.62f, 1.00f),
    };

    private static PerformancePreset activePreset = PerformancePreset.MEDIUM;

    public static void resetLayer(int index) {
        layers[index].copyFrom(DEFAULTS[index]);
    }

    /**
     * Derives the 3 debug layers from a single spell colour (ARGB int), the same way
     * {@link BeamStyle#withHue(int)} does: the wide halo carries the hue at full chroma and the narrow
     * core is pushed toward white. Only the debug screen's spell-preset buttons call this.
     */
    public static void applySpellColor(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float peak = Math.max(0.001f, Math.max(r, Math.max(g, b)));
        layers[OUTER].r = Math.min(1f, r / peak);
        layers[OUTER].g = Math.min(1f, g / peak);
        layers[OUTER].b = Math.min(1f, b / peak);

        layers[MID].r = toWhite(layers[OUTER].r, 0.30f);
        layers[MID].g = toWhite(layers[OUTER].g, 0.30f);
        layers[MID].b = toWhite(layers[OUTER].b, 0.30f);

        layers[CORE].r = toWhite(layers[OUTER].r, 0.78f);
        layers[CORE].g = toWhite(layers[OUTER].g, 0.78f);
        layers[CORE].b = toWhite(layers[OUTER].b, 0.78f);
    }

    private static float toWhite(float channel, float amount) {
        return channel + (1.0f - channel) * amount;
    }

    /**
     * Applies this client's quality budget — and, if it is active, the debug override — to the style
     * the spell asked for. The renderer never reads the raw fields; it renders whatever comes back.
     */
    public static BeamStyle resolve(BeamStyle spellStyle) {
        BeamStyle style = debugOverride ? debugStyle() : spellStyle;
        if (!volumetric) {
            style = style.flattened();
        }
        return style.cappedForks(maxForks);
    }

    /** Builds a style straight from the mutable debug fields so the sliders preview live. */
    private static BeamStyle debugStyle() {
        BeamStyle.Geometry inner = volumetric ? BeamStyle.Geometry.PRISM : BeamStyle.Geometry.BILLBOARD;
        float jag = LATERAL_NOISE_BLOCKS * Math.max(layers[OUTER].noiseAmp,
                Math.max(layers[MID].noiseAmp, layers[CORE].noiseAmp));
        return new BeamStyle(
                explicit(layers[OUTER], BeamStyle.Geometry.BILLBOARD),
                explicit(layers[MID], inner),
                explicit(layers[CORE], inner),
                new BeamStyle.Path(jag, 0.55f, forkChance, maxForks, 0.12f,
                        Math.max(1, arcRefreshTicks), morph, 1.0f),
                new BeamStyle.Pulse(0.14f, speed * 34f, speed * 2f, uvBlocksPerTile),
                new BeamStyle.Flash(1.6f, 2.2f, endpointFlashes ? 6 : 0));
    }

    private static BeamStyle.Layer explicit(LayerSettings layer, BeamStyle.Geometry geometry) {
        return BeamStyle.Layer.explicit(layer.width, layer.r, layer.g, layer.b, layer.alpha, geometry);
    }

    public static void resetAll() {
        range = 50.0f;
        speed = 0.08f;
        segmentsPerUnit = 4;
        minPathSegments = 8;
        maxPathSegments = 28;
        volumetric = true;
        endpointFlashes = true;
        arcRefreshTicks = 2;
        forkChance = 0.18f;
        maxForks = 4;
        uvBlocksPerTile = 1.6f;
        morph = 0.15f;
        debugOverride = false;
        BeamRayResolver.setExtensionBlocksPerTick(BeamRayResolver.DEFAULT_EXTENSION_BLOCKS_PER_TICK);
        for (int i = 0; i < layers.length; i++) {
            resetLayer(i);
        }
        activePreset = PerformancePreset.MEDIUM;
    }

    public static PerformancePreset getActivePreset() {
        return activePreset;
    }

    public static void applyPerformancePreset(PerformancePreset preset) {
        activePreset = preset;
        switch (preset) {
            case LOW -> {
                segmentsPerUnit = 2;
                minPathSegments = 4;
                maxPathSegments = 14;
                speed = 0.05f;
                volumetric = false;
                endpointFlashes = false;
                arcRefreshTicks = 3;
                maxForks = 0;
            }
            case MEDIUM -> {
                segmentsPerUnit = 4;
                minPathSegments = 8;
                maxPathSegments = 28;
                speed = 0.08f;
                volumetric = true;
                endpointFlashes = true;
                arcRefreshTicks = 2;
                maxForks = 4;
            }
            case HIGH -> {
                segmentsPerUnit = 6;
                minPathSegments = 10;
                maxPathSegments = 40;
                speed = 0.10f;
                volumetric = true;
                endpointFlashes = true;
                arcRefreshTicks = 1;
                maxForks = 7;
            }
        }
    }

    public static final class LayerSettings {
        public float width;
        public float r, g, b;
        public float alpha;
        public float noiseAmp;

        public LayerSettings(float width, float r, float g, float b, float alpha, float noiseAmp) {
            this.width = width;
            this.r = r;
            this.g = g;
            this.b = b;
            this.alpha = alpha;
            this.noiseAmp = noiseAmp;
        }

        public void copyFrom(LayerSettings other) {
            this.width = other.width;
            this.r = other.r;
            this.g = other.g;
            this.b = other.b;
            this.alpha = other.alpha;
            this.noiseAmp = other.noiseAmp;
        }
    }

    private BeamSettings() {}
}
