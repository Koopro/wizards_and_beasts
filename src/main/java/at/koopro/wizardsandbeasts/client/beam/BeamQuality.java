package at.koopro.wizardsandbeasts.client.beam;

import org.jspecify.annotations.NullMarked;

/**
 * This client's beam render budget — a per-player GPU preference, never server state.
 *
 * <p>Set by {@code /wandb debug beam preset low|medium|high} (relayed by {@code BeamPresetS2CPayload}) and by the
 * Visuals → Debug tab. It used to land only in the legacy {@code client.wand.BeamSettings}, which the live renderer
 * never reads, so the command changed nothing on screen. {@code LOW} drops the sparks, keeps one glow shell and caps a
 * bolt at four segments; {@code MEDIUM} and {@code HIGH} both draw the full look — this renderer has nothing beyond it.
 *
 * <p>Applied to beams in the world only; the Control Center's preview always shows the look as authored.
 */
@NullMarked
public final class BeamQuality {

    public enum Level { LOW, MEDIUM, HIGH }

    private static final int LOW_MAX_SEGMENTS = 4;

    private static volatile Level level = Level.MEDIUM;

    private BeamQuality() {}

    public static Level level() {
        return level;
    }

    public static void set(Level next) {
        level = next;
    }

    public static BeamStyle apply(BeamStyle style) {
        if (level != Level.LOW) {
            return style;
        }
        return new BeamStyle(style.width(), style.height(), style.coreColor(), style.glowColor(), style.coreOpacity(),
                style.glowOpacity(), Math.min(1, style.bloomLayers()), style.spin(), style.additive(), 0f);
    }

    public static BeamShape apply(BeamShape shape) {
        if (level == Level.LOW && shape instanceof Lightning bolt && bolt.segments() > LOW_MAX_SEGMENTS) {
            return new Lightning(LOW_MAX_SEGMENTS, bolt.spread(), bolt.frequency());
        }
        return shape;
    }
}
