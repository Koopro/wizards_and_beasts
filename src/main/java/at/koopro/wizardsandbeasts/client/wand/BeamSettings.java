package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;

/**
 * Holds all adjustable beam parameters. The renderer reads from this
 * instead of hardcoded values. Purely client-side, never serialized.
 */
public final class BeamSettings {

    public enum PerformancePreset {
        LOW,
        MEDIUM,
        HIGH
    }

    // ── Global ──────────────────────────────────────────────────────
    public static float range = 50.0f;
    /** Subtle alpha flicker (higher = faster shimmer, vanilla-like energy). */
    public static float speed = 0.08f;
    /**
     * Path subdivisions along the beam (higher = smoother polyline, more tube faces).
     * Scaled against beam length; clamped by {@link #minPathSegments}/{@link #maxPathSegments}.
     */
    public static int segmentsPerUnit = 4;

    /**
     * Textured scrolling strip + flashes ({@code true}), or legacy untextured lightning tube ({@code false}).
     * {@link PerformancePreset#LOW} disables this for FPS.
     */
    public static boolean useTextured = true;

    public static int minPathSegments = 8;
    public static int maxPathSegments = 28;

    // ── Per-layer ───────────────────────────────────────────────────
    public static final int OUTER = 0;
    public static final int MID = 1;
    public static final int CORE = 2;

    public static final String[] LAYER_NAMES = {"Outer Glow", "Mid Glow", "Core"};

    public static final LayerSettings[] layers = {
            new LayerSettings(0.15f, 1.0f, 0.25f, 0.08f, 0.20f, 1.5f),   // outer
            new LayerSettings(0.08f, 1.0f, 0.55f, 0.15f, 0.40f, 1.0f),   // mid
            new LayerSettings(0.03f, 1.0f, 0.95f, 0.90f, 0.85f, 0.35f),  // core
    };

    private static final LayerSettings[] DEFAULTS = {
            new LayerSettings(0.15f, 1.0f, 0.25f, 0.08f, 0.20f, 1.5f),
            new LayerSettings(0.08f, 1.0f, 0.55f, 0.15f, 0.40f, 1.0f),
            new LayerSettings(0.03f, 1.0f, 0.95f, 0.90f, 0.85f, 0.35f),
    };

    private static PerformancePreset activePreset = PerformancePreset.MEDIUM;

    public static void resetLayer(int index) {
        layers[index].copyFrom(DEFAULTS[index]);
    }

    /**
     * Derives 3-layer beam colors from a single spell color (ARGB int). The core stays the pure spell
     * colour; mid and outer brighten it for glow.
     *
     * <p>These used to lerp hard toward white (outer was {@code c * 0.35 + 0.65}), which on an additive
     * render type meant the two widest, most visible layers were nearly white regardless of the spell —
     * every beam read as a pale wash with a thin coloured thread inside. They now keep their hue and only
     * lift brightness, so a red spell renders as a red beam.
     */
    public static void applySpellColor(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Core: pure spell color
        layers[CORE].r = r;
        layers[CORE].g = g;
        layers[CORE].b = b;

        // Mid: same hue, lifted
        layers[MID].r = brighten(r, 0.20f);
        layers[MID].g = brighten(g, 0.20f);
        layers[MID].b = brighten(b, 0.20f);

        // Outer: same hue, lifted a little further for a diffuse halo
        layers[OUTER].r = brighten(r, 0.32f);
        layers[OUTER].g = brighten(g, 0.32f);
        layers[OUTER].b = brighten(b, 0.32f);
    }

    /** Lifts a channel toward full without dragging it toward grey, preserving the spell's hue. */
    private static float brighten(float channel, float amount) {
        return Math.min(1.0f, channel + (1.0f - channel) * amount * channel + amount * 0.15f);
    }

    public static void resetAll() {
        range = 50.0f;
        speed = 0.08f;
        segmentsPerUnit = 4;
        useTextured = true;
        minPathSegments = 8;
        maxPathSegments = 28;
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
                useTextured = false;
            }
            case MEDIUM -> {
                segmentsPerUnit = 4;
                minPathSegments = 8;
                maxPathSegments = 28;
                speed = 0.08f;
                useTextured = true;
            }
            case HIGH -> {
                segmentsPerUnit = 6;
                minPathSegments = 10;
                maxPathSegments = 40;
                speed = 0.10f;
                useTextured = true;
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
