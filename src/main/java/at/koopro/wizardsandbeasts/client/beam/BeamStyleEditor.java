package at.koopro.wizardsandbeasts.client.beam;

/**
 * Live-editable override for what a beam looks like, plus the working copy the editor screen binds
 * its sliders to.
 *
 * <p>{@link BeamAppearance} derives a look from the spell and hands it to the entity once, at spawn.
 * That is right for play and useless for tuning — you cannot see a colour change without recasting.
 * So while {@link #active} is set, {@link BeamEntityRenderer} reads the style and shape from here
 * instead of from the entity, and every slider drag shows up on the next frame.
 *
 * <p>Mutable statics on purpose: this is the same shape as the legacy {@code BeamSettings} and it is
 * a debug affordance for one local client, never gameplay state.
 */
public final class BeamStyleEditor {

    /** Which shape the editor builds. */
    public enum ShapeType { LASER, LIGHTNING }

    /** While true, every beam on this client draws with the values below. */
    public static boolean active = false;

    // ── BeamStyle fields (sizes in pixels, matching BeamStyle) ────────────────
    public static float width = 2.0f;
    public static float height = 2.0f;
    public static int coreColor = 0xFFFFFF;
    public static int glowColor = 0x44CCFF;
    public static float coreOpacity = 1.0f;
    public static float glowOpacity = 0.55f;
    public static int bloomLayers = 3;
    public static float spin = 0f;
    public static boolean additive = true;

    // ── Shape fields ─────────────────────────────────────────────────────────
    public static ShapeType shapeType = ShapeType.LASER;
    public static int segments = 6;
    public static float spread = 4f;
    public static int frequency = 2;

    /** Reach of the preview beam, in blocks. Real beams get theirs from the server. */
    public static float previewRange = 24f;

    private BeamStyleEditor() {}

    public static BeamStyle style() {
        return new BeamStyle(width, height, coreColor, glowColor,
                coreOpacity, glowOpacity, bloomLayers, spin, additive);
    }

    public static BeamShape shape() {
        return shapeType == ShapeType.LIGHTNING
                ? new Lightning(segments, spread, frequency)
                : new Laser();
    }

    /** Seeds the working copy from a real appearance, so editing starts where the spell left off. */
    public static void loadFrom(BeamStyle from, BeamShape fromShape) {
        width = from.width();
        height = from.height();
        coreColor = from.coreColor();
        glowColor = from.glowColor();
        coreOpacity = from.coreOpacity();
        glowOpacity = from.glowOpacity();
        bloomLayers = from.bloomLayers();
        spin = from.spin();
        additive = from.additive();

        if (fromShape instanceof Lightning bolt) {
            shapeType = ShapeType.LIGHTNING;
            segments = bolt.segments();
            spread = bolt.spread();
            frequency = bolt.frequency();
        } else {
            shapeType = ShapeType.LASER;
        }
    }

    /** Seeds from a spell's authored look; falls back to the laser preset for non-beam spells. */
    public static void loadFromSpell(at.koopro.wizardsandbeasts.spell.core.Spell spell) {
        BeamAppearance.forSpell(spell).ifPresentOrElse(
                look -> loadFrom(look.style(), look.shape()),
                () -> loadFrom(BeamStyle.laser(spell == null ? 0x44CCFF : spell.getColor()), new Laser()));
    }

    public static void reset() {
        loadFrom(BeamStyle.laser(0x44CCFF), new Laser());
        previewRange = 24f;
    }

    // ── colour helpers, so the screen can drive R/G/B on separate sliders ─────

    public static float channel(int rgb, int shift) {
        return ((rgb >> shift) & 0xFF) / 255f;
    }

    public static int withChannel(int rgb, int shift, float value) {
        int clamped = Math.max(0, Math.min(255, Math.round(value * 255f)));
        return (rgb & ~(0xFF << shift)) | (clamped << shift);
    }
}
