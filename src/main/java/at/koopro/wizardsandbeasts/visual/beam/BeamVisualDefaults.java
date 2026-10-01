package at.koopro.wizardsandbeasts.visual.beam;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The authored look of every beam spell — the "default" that a reset returns to and no administrator can overwrite,
 * because it lives in code, not in world data.
 *
 * <p>Moved here from {@code client.beam.BeamAppearance} unchanged, so the server can know it too (setting defaults,
 * built-in presets, impact intensity). The numbers are the ones the renderer drew before the Visuals section existed;
 * {@code BeamVisualDefaultsTest} pins them. The only normalisation: bloom layers above two are written as two, which
 * is all {@code BeamGeometry} ever drew.
 *
 * <p>Nothing about appearance lives in spell data except {@code color}, so the glow is derived from it and a
 * datapack recolour still reaches a beam nobody has customised. One table to extend when a new beam spell lands; it
 * must list exactly the spells the beam channel accepts ({@code BeamSpellCoverageTest}).
 */
@NullMarked
public final class BeamVisualDefaults {

    public static final String CRUCIO = "crucio";
    public static final String AVADA = "avada_kedavra";
    public static final String AGUAMENTI = "aguamenti";
    public static final String LEVIOSA = "wingardium_leviosa";

    /** The beam spells, by bare id, in browser order. */
    public static final List<String> SPELLS = List.of(CRUCIO, AVADA, AGUAMENTI, LEVIOSA);

    private BeamVisualDefaults() {}

    /** The bare id of a beam spell ({@code crucio} for {@code crucio} or {@code wizards_and_beasts:crucio}), or null. */
    public static @Nullable String key(@Nullable String spellId) {
        if (spellId == null) {
            return null;
        }
        String bare = spellId.startsWith(WizardsAndBeastsMod.MODID + ":")
                ? spellId.substring(WizardsAndBeastsMod.MODID.length() + 1) : spellId;
        return SPELLS.contains(bare) ? bare : null;
    }

    public static boolean isBeamSpell(@Nullable String spellId) {
        return key(spellId) != null;
    }

    /**
     * The authored look of {@code spellId} with its current colour {@code color}, or null when it is not a beam spell.
     */
    public static @Nullable BeamVisual forSpell(@Nullable String spellId, int color) {
        String key = key(spellId);
        if (key == null) {
            return null;
        }
        return switch (key) {
            // Jagged and thin: a fat, heavily-bloomed core smears a zig-zag back into a solid bar.
            case CRUCIO -> visual(true, BeamShapeKind.LIGHTNING, 0xFFFFFF, glowFor(color), 1, 1.0, 0.5, 2);
            // "A jet of green light": additive, pale green core, saturated green halo, wider than a normal beam.
            case AVADA -> visual(true, BeamShapeKind.LASER, 0xCCFFCC, color, 3, 0.9, 0.6, 2);
            case AGUAMENTI -> visual(true, BeamShapeKind.LASER, 0xFFFFFF, glowFor(color), 2, 1.0, 0.55, 2);
            // Leviosa channels but has never drawn a beam: the levitation is the feedback, and a beam to a floating
            // block read as an attack. Off by default; an administrator may turn it on.
            default -> visual(false, BeamShapeKind.LASER, 0xFFFFFF, glowFor(color), 2, 1.0, 0.55, 2);
        };
    }

    private static BeamVisual visual(boolean enabled, BeamShapeKind shape, int core, int glow, int size,
                                     double coreBrightness, double glowBrightness, int shells) {
        return new BeamVisual(enabled, shape, core, glow, size, size, coreBrightness, glowBrightness, shells,
                BeamVisual.DEFAULT_SPARK_DENSITY, 0.0, true,
                6, 4.0, 2, // the bolt Crucio has always drawn; inert for a laser
                0, 0, 1.0);
    }

    /**
     * Lifts a spell colour into a glow colour without dragging it toward grey (the curve {@code BeamAppearance}
     * shipped with).
     */
    static int glowFor(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return ((int) (brighten(r) * 255f) << 16) | ((int) (brighten(g) * 255f) << 8) | (int) (brighten(b) * 255f);
    }

    private static float brighten(float channel) {
        final float amount = 0.32f;
        return Math.min(1f, channel + (1f - channel) * amount * channel + amount * 0.15f);
    }
}
