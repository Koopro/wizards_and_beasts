package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;

import java.util.Optional;

/**
 * Decides what a spell's beam <em>looks</em> like: a {@link BeamStyle} plus a {@link BeamShape}.
 *
 * <p>Nothing about appearance lives in spell data — a {@code SpellDefinition} carries exactly one
 * visual field, {@code color}. Rather than grow the spell JSON schema for a client-only concern,
 * the style is derived from that colour here and the shape comes from a small table keyed on the
 * four ids the beam channel accepts at all (see {@code WandBeamSpellIds}). One file to extend when
 * a new beam spell lands.
 *
 * <p>Ids are compared with {@link SpellIds#matches} so both {@code crucio} and
 * {@code wizards_and_beasts:crucio} resolve.
 */
public final class BeamAppearance {

    /** What to draw for one caster. */
    public record Appearance(BeamStyle style, BeamShape shape) {}

    private static final String CRUCIO = "crucio";
    private static final String AVADA = "avada_kedavra";
    private static final String AGUAMENTI = "aguamenti";
    private static final String LEVIOSA = "wingardium_leviosa";

    private BeamAppearance() {}

    /**
     * @return empty when this spell draws no beam at all — either it is not a beam spell, or it is
     *         Leviosa, which channels but has never rendered one (the levitation is the feedback;
     *         a beam to a floating block read as an attack).
     */
    public static Optional<Appearance> forSpell(Spell spell) {
        if (spell == null) {
            return Optional.empty();
        }
        String id = spell.getId();
        int color = spell.getColor();

        if (SpellIds.matches(id, LEVIOSA)) {
            return Optional.empty();
        }
        if (SpellIds.matches(id, CRUCIO)) {
            // Jagged and thin: a fat, heavily-bloomed core smears a zig-zag back into a solid bar.
            return Optional.of(new Appearance(
                    BeamStyle.lightning(glowFor(color)),
                    new Lightning(6, 4f, 2)));
        }
        if (SpellIds.matches(id, AVADA)) {
            // "A jet of green light" — the spell's own doc. Dark magic, but not a dark colour, so
            // the alpha-blended dark style was wrong: at core opacity 1 it drew a flat, opaque
            // green bar with no glow at all. Additive with a pale green core and a saturated green
            // halo, wider than a normal beam because this one is supposed to be frightening.
            return Optional.of(new Appearance(
                    new BeamStyle(3.0f, 3.0f, 0xCCFFCC, color, 0.9f, 0.6f, 4, 0f, true),
                    new Laser()));
        }
        if (SpellIds.matches(id, AGUAMENTI)) {
            return Optional.of(new Appearance(
                    BeamStyle.laser(glowFor(color)),
                    new Laser()));
        }
        return Optional.empty();
    }

    /**
     * Lifts a spell colour into a glow colour without dragging it toward grey.
     *
     * <p>Same curve the old renderer uses ({@code BeamSettings.applySpellColor}), and for the same
     * reason: the glow layers used to lerp hard toward white, which made every beam read as a pale
     * wash with a thin coloured thread inside. Copied rather than shared so the new path carries no
     * dependency on the old one — that whole package is meant to go away.
     */
    private static int glowFor(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return pack(brighten(r), brighten(g), brighten(b));
    }

    private static float brighten(float channel) {
        final float amount = 0.32f;
        return Math.min(1f, channel + (1f - channel) * amount * channel + amount * 0.15f);
    }

    private static int pack(float r, float g, float b) {
        return ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }
}
