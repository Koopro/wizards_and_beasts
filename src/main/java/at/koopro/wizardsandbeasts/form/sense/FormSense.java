package at.koopro.wizardsandbeasts.form.sense;

/**
 * A sense a transformed body has and a human one does not.
 *
 * <p>The counterpart to {@link at.koopro.wizardsandbeasts.form.constraint.FormConstraint}: that enum is
 * what a form takes away, this is what it gives. Both are declared by the system that owns the form and
 * applied by shared machinery, so the werewolf gets a wolf's nose without depending on the Animagus
 * package and vice versa.
 *
 * <p>Deliberately not the same enum as {@code AnimagusCapability}. That one is a datapack vocabulary
 * with a codec, a validation pass and entries about flight physics and hitboxes; this is the small
 * subset that is <em>sensory</em> and therefore shareable. {@code AnimagusSenseSource} maps one onto the
 * other, and a werewolf — which has no {@code AnimagusFormDefinition} and never will — answers directly.
 */
public enum FormSense {

    /**
     * Sees in the dark.
     *
     * <p>Applied as vanilla Night Vision, but not naively: vanilla strobes the screen when the effect
     * has under 200 ticks left, so the obvious "re-apply a short effect every tick" produces a constant
     * flicker. {@link FormSenseService} keeps it well clear of that edge. This is the bug that made
     * Animagus cat form unpleasant to actually play.
     */
    NIGHT_EYES,

    /**
     * Smells what it cannot see: nearby living things are outlined through blocks, for this player alone.
     *
     * <p>The rule lives on the server; the drawing is a client render-state field with no packet behind
     * it. They are joined by a marker effect ({@code ModEffects.SCENT_TRACKING}) rather than by two
     * copies of the same condition — see {@link FormSenseService}.
     */
    SCENT_TRACK,

    /**
     * Long sight: extended entity-highlight range and cleared distance fog.
     *
     * <p><b>Declared, not yet implemented.</b> It is purely a client render-tuning change and has no
     * server behaviour to hang on, so nothing applies it today. Named here so a form can state the
     * intent and so the gap is visible rather than silently absent.
     */
    KEEN_SIGHT,

    /**
     * Breathes water as easily as air.
     *
     * <p>Applied as vanilla Water Breathing, on the same ambient, no-icon terms as the rest: a body
     * that simply has gills does not belong in the potion tray. Gillyweed is the one interaction
     * worth knowing about and it already works — {@code GillyweedHandler} refuses vanilla Water
     * Breathing outright while its own {@code GILLS} effect is up, so a Merperson who chews a sprig
     * keeps the gills and this grant is quietly declined rather than fighting it.
     *
     * <p>Unlike the other senses this one is not owned by a transformation. Merpeople are aquatic
     * whatever shape they are in: only the Selkie changes form at the waterline, and a Merrow or a
     * Siren never changes at all, so keying it to a form would have left two of the three lineages
     * drowning. It hangs off the {@code water_dwelling} lineage trait instead.
     */
    WATER_BREATHING
}
