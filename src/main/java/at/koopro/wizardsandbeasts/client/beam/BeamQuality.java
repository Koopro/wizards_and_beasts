package at.koopro.wizardsandbeasts.client.beam;

import org.jspecify.annotations.NullMarked;

/**
 * This client's beam render budget — a per-player GPU preference, never server state.
 *
 * <p>Backed by {@code VfxBudget} (config {@code vfxQuality}); overridden for the session by {@code /wandb debug beam preset low|medium|high} (relayed by {@code BeamPresetS2CPayload}) and by the
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

    private BeamQuality() {}

    /** The client's spell-VFX quality ({@code VfxBudget}); beams and particles share one setting since 2026-11-15. */
    public static Level level() {
        return Level.valueOf(at.koopro.wizardsandbeasts.client.vfx.VfxBudget.quality().name());
    }

    /** Session override; also turns every other spell effect up or down, which is what a player asking expects. */
    public static void set(Level next) {
        at.koopro.wizardsandbeasts.client.vfx.VfxBudget.override(
                at.koopro.wizardsandbeasts.Config.VfxQuality.valueOf(next.name()));
    }

    public static BeamStyle apply(BeamStyle style) {
        if (level() != Level.LOW) {
            return style;
        }
        return new BeamStyle(style.width(), style.height(), style.coreColor(), style.glowColor(), style.coreOpacity(),
                style.glowOpacity(), Math.min(1, style.bloomLayers()), style.spin(), style.additive(), 0f);
    }

    public static BeamShape apply(BeamShape shape) {
        if (level() == Level.LOW && shape instanceof Lightning bolt && bolt.segments() > LOW_MAX_SEGMENTS) {
            return new Lightning(LOW_MAX_SEGMENTS, bolt.spread(), bolt.frequency());
        }
        return shape;
    }
}
