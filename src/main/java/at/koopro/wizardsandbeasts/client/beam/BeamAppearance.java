package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.visual.beam.BeamShapeKind;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;

import java.util.Optional;

/**
 * Decides what a spell's beam <em>looks</em> like: a {@link BeamStyle} plus a {@link BeamShape}, and how it fades.
 *
 * <p>The look is a {@link BeamVisual}: the spell's authored default ({@code visual.beam.BeamVisualDefaults}, which
 * used to be the table in this class, numbers unchanged) with the server's overrides from the Control Center laid on
 * top ({@link ClientBeamVisuals}). This class only translates it into the renderer's own records.
 */
public final class BeamAppearance {

    /** What to draw for one caster, and over how many ticks it fades in and out. */
    public record Appearance(BeamStyle style, BeamShape shape, int fadeInTicks, int fadeOutTicks) {
        public Appearance(BeamStyle style, BeamShape shape) {
            this(style, shape, 0, 0);
        }
    }

    private BeamAppearance() {}

    /**
     * @return empty when this spell draws no beam at all — it is not a beam spell, or its beam is switched off (Leviosa
     *         by default: the levitation is the feedback, and a beam to a floating block read as an attack).
     */
    public static Optional<Appearance> forSpell(Spell spell) {
        BeamVisual visual = ClientBeamVisuals.effective(spell);
        if (visual == null || !visual.enabled()) {
            return Optional.empty();
        }
        return Optional.of(of(visual));
    }

    /** The renderer's records for a look, whether or not it is enabled (a preview draws a disabled beam too). */
    public static Appearance of(BeamVisual visual) {
        BeamStyle style = new BeamStyle(visual.coreWidth(), visual.coreHeight(), visual.coreColor(), visual.glowColor(),
                (float) visual.coreBrightness(), (float) visual.glowBrightness(), visual.glowShells(),
                (float) visual.spin(), visual.additive(), (float) visual.sparkDensity());
        BeamShape shape = visual.shape() == BeamShapeKind.LIGHTNING
                ? new Lightning(visual.segments(), (float) visual.jitter(), visual.crackleTicks())
                : new Laser();
        return new Appearance(style, shape, visual.fadeInTicks(), visual.fadeOutTicks());
    }
}
