package at.koopro.wizardsandbeasts.spell.effect;

import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Executes a spell's {@link SpellEffectEntry} list against a {@link SpellEffectContext}.
 *
 * <p>The runner is intentionally dumb: it iterates the list in order and calls {@code apply(ctx)} on
 * each entry. <b>No gating lives here</b> — every component self-gates inside {@code apply()}
 * ({@code WANDS_AND_SPELLS}, and {@code DARK_ARTS} for dark effects), so the runner executes at the
 * existing effect-site layer rather than introducing a new gating path.
 *
 * <p><b>Cadence (F2).</b> The phase-less {@link #run(List, SpellEffectContext)} overload runs every
 * entry and ignores cadence — that is the non-channel invocation contract (self/projectile/cone/
 * targeted fire the whole list once at their existing point, so cadence stays inert there). Only the
 * BEAM_CHANNEL dispatch ({@code WandBeamChannelLogic}) uses the phase-filtered
 * {@link #run(List, SpellEffectContext, EffectCadence)} overload.
 *
 * <p>Only {@link JsonSpell}s carry an {@code effects} list; Java spells return an empty list and the
 * runner is a no-op for them — which is how an un-migrated spell keeps its legacy behavior unchanged.
 */
public final class SpellEffectRunner {

    private SpellEffectRunner() {}

    /** The spell's authored effect entries, or an empty list for non-JSON / effect-less spells. */
    public static List<SpellEffectEntry> effectsOf(@Nullable Spell spell) {
        if (spell instanceof JsonSpell json) {
            return json.definition().effectComponents();
        }
        return List.of();
    }

    /** Runs every entry in order, cadence-inert (the non-channel sites). Null/empty list = no-op. */
    public static void run(@Nullable List<SpellEffectEntry> effects, SpellEffectContext ctx) {
        if (effects == null || effects.isEmpty()) return;
        for (SpellEffectEntry entry : effects) {
            entry.apply(ctx);
        }
    }

    /** Runs only the entries tagged with {@code phase} — the BEAM_CHANNEL dispatch. */
    public static void run(@Nullable List<SpellEffectEntry> effects, SpellEffectContext ctx,
                           EffectCadence phase) {
        if (effects == null || effects.isEmpty()) return;
        for (SpellEffectEntry entry : effects) {
            if (entry.cadence() == phase) {
                entry.apply(ctx);
            }
        }
    }

    /** Runs a nested, cadence-less component list ({@code aoe_apply} children). */
    public static void runComponents(List<SpellEffectComponent> components, SpellEffectContext ctx) {
        for (SpellEffectComponent component : components) {
            component.apply(ctx);
        }
    }

    /** Convenience: resolve {@code spell}'s effects and run them cadence-inert. No-op when none. */
    public static void run(@Nullable Spell spell, SpellEffectContext ctx) {
        run(effectsOf(spell), ctx);
    }
}
