package at.koopro.wizardsandbeasts.spell.effect;

import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Executes a spell's {@link SpellEffectComponent} list against a {@link SpellEffectContext}.
 *
 * <p>The runner is intentionally dumb: it iterates the list in order and calls {@code apply(ctx)} on
 * each component. <b>No gating lives here</b> — every component self-gates inside {@code apply()}
 * ({@code WANDS_AND_SPELLS}, and {@code DARK_ARTS} for dark effects), so the runner executes at the
 * existing effect-site layer rather than introducing a new gating path.
 *
 * <p>Only {@link JsonSpell}s carry an {@code effects} list; Java spells return an empty list and the
 * runner is a no-op for them — which is how an un-migrated spell keeps its legacy behavior unchanged.
 */
public final class SpellEffectRunner {

    private SpellEffectRunner() {}

    /** The spell's authored effect components, or an empty list for non-JSON / effect-less spells. */
    public static List<SpellEffectComponent> effectsOf(@Nullable Spell spell) {
        if (spell instanceof JsonSpell json) {
            return json.definition().effectComponents();
        }
        return List.of();
    }

    /** Runs {@code effects} in order. Null/empty list = no-op. */
    public static void run(@Nullable List<SpellEffectComponent> effects, SpellEffectContext ctx) {
        if (effects == null || effects.isEmpty()) return;
        for (SpellEffectComponent component : effects) {
            component.apply(ctx);
        }
    }

    /** Convenience: resolve {@code spell}'s effects and run them. No-op when there are none. */
    public static void run(@Nullable Spell spell, SpellEffectContext ctx) {
        run(effectsOf(spell), ctx);
    }
}
