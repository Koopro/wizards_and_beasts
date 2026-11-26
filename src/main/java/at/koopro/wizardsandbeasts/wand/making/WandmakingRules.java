package at.koopro.wizardsandbeasts.wand.making;

import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every wood pairs with every core (owner, 2026-11-26). This is a rule, not a file per pair.
 *
 * <ul>
 *   <li>difficulty = the wood's difficulty + the core's</li>
 *   <li>length = the wood's range, shifted by the core's {@code length_shift}</li>
 *   <li>affinity = what the core says of this wood (default 0)</li>
 * </ul>
 * A pairing exists as soon as both definitions do. Whether it may be made is a separate question, answered by the
 * withdrawal rules ({@code WandRules.mayMake}).
 */
@NullMarked
public final class WandmakingRules {

    private WandmakingRules() {}

    public static WoodMaking woodMaking(WandWoodDefinition wood) {
        return wood.making().orElseGet(() -> WoodMaking.forRarity(wood.rarity()));
    }

    /** The pure rule. */
    public static WandmakingSpec spec(Identifier woodId, WandWoodDefinition wood, Identifier coreId, WandCoreDefinition core) {
        WoodMaking w = woodMaking(wood);
        CoreMaking c = core.making();
        return new WandmakingSpec(woodId, coreId,
                round(w.difficulty() + c.difficulty()),
                round(w.lengthMin() + c.lengthShift()),
                round(w.lengthMax() + c.lengthShift()),
                c.affinityWith(woodId));
    }

    /** This pair from the live registries, or empty when the wood or the core is not defined. */
    public static Optional<WandmakingSpec> spec(HolderLookup.Provider registries, Identifier wood, Identifier core) {
        Optional<WandWoodDefinition> w = registries.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY)
                .get(ResourceKey.create(WandDatapackRegistries.WAND_WOOD_REGISTRY, wood)).map(h -> h.value());
        Optional<WandCoreDefinition> c = registries.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY)
                .get(ResourceKey.create(WandDatapackRegistries.WAND_CORE_REGISTRY, core)).map(h -> h.value());
        if (w.isEmpty() || c.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(spec(wood, w.get(), core, c.get()));
    }

    /** Every wood with every core, ordered by wood, then core. */
    /** A registered wood's making values, or empty for a wood the registry does not know. */
    public static Optional<WoodMaking> woodMaking(HolderLookup.Provider registries, Identifier wood) {
        return registries.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY)
                .get(ResourceKey.create(WandDatapackRegistries.WAND_WOOD_REGISTRY, wood)).map(h -> woodMaking(h.value()));
    }

    /** A registered core's making values, or {@link CoreMaking#DEFAULT} for a core the registry does not know. */
    public static CoreMaking coreMaking(HolderLookup.Provider registries, Identifier core) {
        return registries.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY)
                .get(ResourceKey.create(WandDatapackRegistries.WAND_CORE_REGISTRY, core))
                .map(h -> h.value().making()).orElse(CoreMaking.DEFAULT);
    }

    public static List<WandmakingSpec> all(HolderLookup.Provider registries) {
        List<WandmakingSpec> out = new ArrayList<>();
        var woods = registries.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).listElements()
                .sorted(Comparator.comparing(h -> h.key().identifier().toString())).toList();
        var cores = registries.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).listElements()
                .sorted(Comparator.comparing(h -> h.key().identifier().toString())).toList();
        for (var w : woods) {
            for (var c : cores) {
                out.add(spec(w.key().identifier(), w.value(), c.key().identifier(), c.value()));
            }
        }
        return out;
    }

    /** Rounds to two places, as authored. Float sums would otherwise show values like 2.8000002 in a tooltip. */
    static float round(float v) {
        return Math.round(v * 100.0f) / 100.0f;
    }
}
