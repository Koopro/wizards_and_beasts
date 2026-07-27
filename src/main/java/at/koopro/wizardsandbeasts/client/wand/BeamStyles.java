package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.client.wand.BeamStyle.Flash;
import at.koopro.wizardsandbeasts.client.wand.BeamStyle.Geometry;
import at.koopro.wizardsandbeasts.client.wand.BeamStyle.Layer;
import at.koopro.wizardsandbeasts.client.wand.BeamStyle.Path;
import at.koopro.wizardsandbeasts.client.wand.BeamStyle.Pulse;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-spell beam looks. A spell contributes a {@link BeamStyle}; it never implements geometry.
 *
 * <p>Adding a beam to a new spell needs nothing here — the fallback derives an arc style from the
 * spell's own colour. Entries exist only where a spell wants to read as something other than an
 * electrical arc.
 */
public final class BeamStyles {

    // Layer alphas are budgeted against the additive sum, not read in isolation: a PRISM layer draws
    // its front and back faces over the same pixels and carries Geometry.PRISM's 0.55 correction, so
    // its contribution at the beam's centre is alpha * 1.1. Anything summing much past 1.0 across the
    // three layers clips to flat white and throws the hue away — which is exactly what a first pass at
    // these numbers did. Only a deliberately white-hot filament should cross it, and only while narrow.

    /**
     * Default: an electrical arc. Hard crackle (near-zero morph, fast re-seed), plenty of forks, a
     * saturated halo around a white-hot filament.
     */
    public static final BeamStyle ARC = new BeamStyle(
            Layer.of(0.145f, 0.26f, 0.00f, Geometry.BILLBOARD),
            Layer.of(0.055f, 0.32f, 0.30f, Geometry.PRISM),
            Layer.of(0.022f, 0.62f, 0.78f, Geometry.PRISM),
            new Path(0.30f, 0.55f, 0.18f, 4, 0.12f, 2, 0.15f, 1.00f),
            new Pulse(0.14f, 2.70f, 0.16f, 1.6f),
            new Flash(1.6f, 2.2f, 6));

    /**
     * The Cruciatus Curse: a lash of raw pain. Same electrical family as {@link #ARC} but angrier —
     * one-tick re-seed, no morphing at all, more and longer branches, deeper strobe.
     */
    private static final BeamStyle TORTURE = new BeamStyle(
            Layer.of(0.155f, 0.28f, 0.00f, Geometry.BILLBOARD),
            Layer.of(0.058f, 0.34f, 0.28f, Geometry.PRISM),
            Layer.of(0.024f, 0.65f, 0.72f, Geometry.PRISM),
            new Path(0.34f, 0.60f, 0.22f, 5, 0.15f, 1, 0.00f, 1.00f),
            new Pulse(0.22f, 3.40f, 0.20f, 1.4f),
            new Flash(1.7f, 2.3f, 7));

    /**
     * The Killing Curse. Deliberately <em>not</em> electricity: a broad sickly haze around a heavy,
     * slow-writhing jet of green. High morph and a long re-seed make the shape crawl instead of
     * snapping, the pulse is nearly steady (it does not flicker — it just arrives), and the core keeps
     * a green tint rather than burning out to white, so it reads as unnatural light rather than heat.
     * Two short branches survive so the jet still looks unstable, not like a laser pointer.
     */
    private static final BeamStyle KILLING_CURSE = new BeamStyle(
            Layer.of(0.200f, 0.24f, 0.00f, Geometry.BILLBOARD),
            Layer.of(0.072f, 0.30f, 0.22f, Geometry.PRISM),
            Layer.of(0.028f, 0.44f, 0.50f, Geometry.PRISM),
            new Path(0.20f, 0.85f, 0.09f, 2, 0.08f, 4, 0.85f, 1.15f),
            new Pulse(0.10f, 1.60f, 0.10f, 2.4f),
            new Flash(1.9f, 2.6f, 8));

    /**
     * Aguamenti: a jet of water, not a discharge. No branches, fully morphed (the shape flows rather
     * than snaps), fast UV scroll downrange, and a dim core — water has no hot filament.
     */
    private static final BeamStyle WATER_JET = new BeamStyle(
            Layer.of(0.150f, 0.16f, 0.05f, Geometry.BILLBOARD),
            Layer.of(0.070f, 0.20f, 0.35f, Geometry.PRISM),
            Layer.of(0.028f, 0.26f, 0.70f, Geometry.PRISM),
            new Path(0.16f, 0.30f, 0.00f, 0, 0.00f, 3, 1.00f, 1.30f),
            new Pulse(0.08f, 1.00f, 0.55f, 1.0f),
            new Flash(0.9f, 1.4f, 5));

    /** Hue for the debug force-beam, which has no spell to take a colour from. */
    private static final int DEBUG_HUE = 0xFF59A0FF;

    /** Keyed by spell id; the colour is kept so a datapack reload that recolours a spell is picked up. */
    private record Cached(int color, BeamStyle style) {}

    private static final Map<String, Cached> CACHE = new HashMap<>();

    private BeamStyles() {}

    /**
     * The style for the spell currently being channelled, hue-resolved from the spell's own colour.
     * Cached per spell id — this runs every frame and the result only changes when the spell does.
     */
    public static BeamStyle forSpell(@Nullable Spell spell) {
        String id = spell == null ? "" : spell.getId();
        int color = spell == null ? DEBUG_HUE : spell.getColor();

        Cached cached = CACHE.get(id);
        if (cached != null && cached.color() == color) {
            return cached.style();
        }
        BeamStyle style = base(id).withHue(color);
        CACHE.put(id, new Cached(color, style));
        return style;
    }

    private static BeamStyle base(String spellId) {
        if (SpellIds.matches(spellId, "avada_kedavra")) {
            return KILLING_CURSE;
        }
        if (SpellIds.matches(spellId, "crucio")) {
            return TORTURE;
        }
        if (SpellIds.matches(spellId, "aguamenti")) {
            return WATER_JET;
        }
        return ARC;
    }
}
