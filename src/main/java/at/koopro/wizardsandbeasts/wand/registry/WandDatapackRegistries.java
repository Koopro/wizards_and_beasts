package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public final class WandDatapackRegistries {
    public static final ResourceKey<Registry<WandWoodDefinition>> WAND_WOOD_REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wand_woods"));
    public static final ResourceKey<Registry<WandCoreDefinition>> WAND_CORE_REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wand_cores"));
    public static final ResourceKey<Registry<BenchEnhancerDefinition>> BENCH_ENHANCER_REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bench_enhancers"));

    private WandDatapackRegistries() {
    }

    public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(WAND_WOOD_REGISTRY, WandWoodDefinition.CODEC, WandWoodDefinition.CODEC);
        event.dataPackRegistry(WAND_CORE_REGISTRY, WandCoreDefinition.CODEC, WandCoreDefinition.CODEC);
        event.dataPackRegistry(BENCH_ENHANCER_REGISTRY, BenchEnhancerDefinition.CODEC, BenchEnhancerDefinition.CODEC);
    }
}
