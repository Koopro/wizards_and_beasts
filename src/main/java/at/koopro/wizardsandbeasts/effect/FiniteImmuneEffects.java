package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/** Effects Finite Incantatem must never strip (lore / gameplay exclusions). */
public final class FiniteImmuneEffects {

    /**
     * Effects that Finite Incantatem cannot remove.
     * Lore reasons are documented per entry.
     */
    private static final Set<DeferredHolder<MobEffect, ?>> IMMUNE_SET = Set.of(
            ModEffects.SECTUMSEMPRA_BLEED,   // requires Vulnera Sanentur (sung counter-curse)
            ModEffects.CRUCIATUS_PAIN,       // Unforgivable — Finite has no purchase on it
            ModEffects.DEMENTOR_CHILL        // creature aura, not a cast spell
    );

    public static boolean isImmune(@NonNull MobEffect effect) {
        return IMMUNE_SET.stream()
                .anyMatch(holder -> holder.value() == effect);
    }

    private FiniteImmuneEffects() {}
}
