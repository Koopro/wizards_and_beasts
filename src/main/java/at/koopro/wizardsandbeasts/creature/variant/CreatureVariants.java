package at.koopro.wizardsandbeasts.creature.variant;

import at.koopro.wizardsandbeasts.creature.rules.CreatureRules;
import at.koopro.wizardsandbeasts.entity.creature.HippogriffEntity;
import at.koopro.wizardsandbeasts.entity.creature.KelpieEntity;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Which creatures have variants, and the one variant roll they all use.
 *
 * <p>The variants themselves are the creatures' enums; this map only says which enum belongs to which creature id,
 * so the admin layer can find them. A creature gains variants by giving its entity a {@link CreatureVariant} enum,
 * a {@link VariantHolder} implementation, a {@link #roll} call in {@code finalizeSpawn}, and an entry here.
 *
 * <p>The roll honours the server's {@link CreatureRules}: a disabled variant is never rolled, and an overridden
 * weight replaces the authored one. With no rules it is exactly the roll each creature had before — uniform for the
 * Hippogriff and Kelpie, 70/12/12/6 for the Niffler.
 */
@NullMarked
public final class CreatureVariants {

    private static final Map<String, List<CreatureVariant>> BY_CREATURE = Map.of(
            "hippogriff", List.of(HippogriffEntity.Coat.values()),
            "kelpie", List.of(KelpieEntity.Coat.values()),
            "niffler", List.of(NifflerEntity.Coat.values()));

    private CreatureVariants() {}

    /** This creature's variants in their saved order, or an empty list when it has none. */
    public static List<CreatureVariant> of(String creatureId) {
        return BY_CREATURE.getOrDefault(creatureId, List.of());
    }

    public static boolean hasVariants(String creatureId) {
        return BY_CREATURE.containsKey(creatureId);
    }

    public static @Nullable CreatureVariant byId(String creatureId, String variantId) {
        for (CreatureVariant variant : of(creatureId)) {
            if (variant.variantId().equals(variantId)) {
                return variant;
            }
        }
        return null;
    }

    /** The spawn roll under the server's rules. Server side. */
    public static <V extends CreatureVariant> V roll(String creatureId, V[] values, RandomSource random) {
        return rollWeighted(values,
                v -> CreatureRules.variantEnabled(creatureId, v) ? Math.max(0, CreatureRules.variantWeight(creatureId, v)) : 0,
                random);
    }

    /**
     * A weighted pick. Zero weights are never picked. If every weight is zero — which the admin validator prevents,
     * but a hand-edited world file could produce — the first value is returned rather than failing a spawn.
     */
    public static <V> V rollWeighted(V[] values, ToIntFunction<V> weight, RandomSource random) {
        int total = 0;
        for (V value : values) {
            total += Math.max(0, weight.applyAsInt(value));
        }
        if (total <= 0) {
            return values[0];
        }
        int r = random.nextInt(total);
        for (V value : values) {
            r -= Math.max(0, weight.applyAsInt(value));
            if (r < 0) {
                return value;
            }
        }
        return values[values.length - 1];
    }
}
