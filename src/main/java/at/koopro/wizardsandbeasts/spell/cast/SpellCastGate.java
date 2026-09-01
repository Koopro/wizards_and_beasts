package at.koopro.wizardsandbeasts.spell.cast;

import org.jspecify.annotations.Nullable;

/**
 * Pure decision logic for the deterministic middle of the cast-resolution chain in
 * {@link SpellCastService#completeWandCastRelease} — the contiguous run from "no active spell" through
 * the global cooldown. These gates are ordered <em>conditions</em> with no side effects; their player
 * feedback (messages / stress / packets) lives in the service's switch, which reproduces each gate's
 * behavior exactly. Keeping the decision here (and free of Minecraft types) makes the precedence
 * unit-testable and gives it a single source of truth, so a future reordering is caught by a test
 * rather than only in-game.
 *
 * <p>Gates that are side-effecting or order-sensitive stay inline in the service and are intentionally
 * <em>not</em> modelled here: the earlier bond / {@code canUseWand} guards (the latter self-sends its
 * denial) and the later random misfires (mental instability, obscurus collapse, Gamp's Law).
 */
public enum SpellCastGate {
    NO_ACTIVE_SPELL,
    UNKNOWN_SPELL,
    SPELL_NOT_IMPLEMENTED,
    SPELL_NOT_KNOWN,
    OBSCURIAL_ABILITY_INPUT,
    REQUIREMENTS_UNMET,
    OBSCURIAL_DARK_ONLY,
    OBSCURIAL_DARK_RESTRICTED,
    TOO_DRUNK,
    ON_COOLDOWN,
    GLOBAL_COOLDOWN;

    /**
     * Side-effect-free facts about a cast attempt, all computed by the caller before evaluation. Fields
     * are read in precedence order and short-circuit at the first failure, so any field logically
     * downstream of an earlier failure is a don't-care (callers may pass {@code false} / a guarded value).
     *
     * @param activeSpellPresent    a spell id is set in the active loadout slot
     * @param spellResolved         that id resolves to a registered spell
     * @param spellImplemented      that spell's behaviour is written — see
     *                              {@link at.koopro.wizardsandbeasts.spell.def.SpellImplementationState}
     * @param spellKnown            the caster has learned the spell
     * @param obscurialAbility      the spell is an Obscurial ability (cast via ability keys, not the wand)
     * @param requirementSatisfied  the spell's requirement is met (or enforcement is off)
     * @param darkFormOnlyOutsideForm  a dark-form-only spell cast while not in obscurus form
     * @param darkRestrictedInForm  a spell the obscurus form rejects, cast while in form
     * @param tooDrunk              three Firewhiskies in two minutes; see {@code Firewhisky}
     * @param onCooldown            the spell's own cooldown is still active
     * @param globalCooldownActive  the shared global cooldown is still active
     */
    public record Inputs(boolean activeSpellPresent,
                         boolean spellResolved,
                         boolean spellImplemented,
                         boolean spellKnown,
                         boolean obscurialAbility,
                         boolean requirementSatisfied,
                         boolean darkFormOnlyOutsideForm,
                         boolean darkRestrictedInForm,
                         boolean tooDrunk,
                         boolean onCooldown,
                         boolean globalCooldownActive) {}

    /** The first failing gate in cast-precedence order, or {@code null} when the cast may proceed. */
    @Nullable
    public static SpellCastGate evaluate(Inputs in) {
        if (!in.activeSpellPresent()) return NO_ACTIVE_SPELL;
        if (!in.spellResolved()) return UNKNOWN_SPELL;
        // Ahead of SPELL_NOT_KNOWN on purpose: whether a spell works at all is a property of the
        // spell, not of the caster, so it outranks every gate that describes the player's progress.
        // Telling someone to go learn a spell that cannot be cast by anyone would be a lie.
        if (!in.spellImplemented()) return SPELL_NOT_IMPLEMENTED;
        if (!in.spellKnown()) return SPELL_NOT_KNOWN;
        if (in.obscurialAbility()) return OBSCURIAL_ABILITY_INPUT;
        if (!in.requirementSatisfied()) return REQUIREMENTS_UNMET;
        if (in.darkFormOnlyOutsideForm()) return OBSCURIAL_DARK_ONLY;
        if (in.darkRestrictedInForm()) return OBSCURIAL_DARK_RESTRICTED;
        // After every gate that describes the spell, before the two that describe timing. Telling a
        // drunk wizard they have not learned the spell is more useful than telling them they are
        // drunk; telling them about a cooldown they cannot reach is not useful at all.
        if (in.tooDrunk()) return TOO_DRUNK;
        if (in.onCooldown()) return ON_COOLDOWN;
        if (in.globalCooldownActive()) return GLOBAL_COOLDOWN;
        return null;
    }

    /**
     * Final cooldown ticks for a cast: the modifier/scaling-adjusted value, floored at 50% of the base
     * so cooldown reductions can never drop a spell below half its listed cooldown (the invariant
     * documented in {@code documentation/PIPELINE_AUDIT.md} §5). Both terms clamp to a minimum of 1 tick.
     */
    public static int resolveCooldownTicks(int baseCooldown, float cooldownMult) {
        int scaled = Math.max(1, Math.round(baseCooldown * cooldownMult));
        int floor = Math.max(1, Math.round(baseCooldown * 0.5f));
        return Math.max(scaled, floor);
    }
}
