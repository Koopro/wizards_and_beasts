package at.koopro.wizardsandbeasts.brew.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One entry in a brew's {@code components} list: a {@link BrewEffect} plus an optional {@code phase}.
 *
 * <p>The phase rides beside the component's own fields in the same flat JSON object, so a component
 * written before phases existed parses unchanged and defaults to {@link BrewEffectPhase#ON_DRINK}.
 * Same trick, and the same reason, as {@code SpellEffectEntry}'s cadence.
 */
public record BrewEffectEntry(BrewEffect component, BrewEffectPhase phase) {

    public static final Codec<BrewEffectEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BrewEffect.MAP_CODEC.forGetter(BrewEffectEntry::component),
            BrewEffectPhase.CODEC.optionalFieldOf("phase", BrewEffectPhase.ON_DRINK)
                    .forGetter(BrewEffectEntry::phase)
    ).apply(inst, BrewEffectEntry::new));

    /** Convenience for the wrapping that {@code BrewDefinition} does to legacy effect lists. */
    public static BrewEffectEntry onDrink(BrewEffect component) {
        return new BrewEffectEntry(component, BrewEffectPhase.ON_DRINK);
    }

    /**
     * Run every entry whose phase matches, in authored order.
     *
     * <p>Deliberately dumb, like {@code SpellEffectRunner}: no gating and no reordering. A component
     * that needs a module check does it inside {@code apply}, because only the component knows which
     * module it belongs to.
     */
    public static void run(@Nullable List<BrewEffectEntry> entries, BrewEffectContext ctx) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (BrewEffectEntry entry : entries) {
            if (entry.phase() == ctx.phase()) {
                entry.component().apply(ctx);
            }
        }
    }

    /** Whether any entry would run in {@code phase} — lets callers skip building a context. */
    public static boolean hasPhase(@Nullable List<BrewEffectEntry> entries, BrewEffectPhase phase) {
        if (entries == null) {
            return false;
        }
        for (BrewEffectEntry entry : entries) {
            if (entry.phase() == phase) {
                return true;
            }
        }
        return false;
    }
}
