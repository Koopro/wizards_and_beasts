package at.koopro.wizardsandbeasts.azkaban.worldgen;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AzkabanWorldgenRegistries {

    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<AzkabanFixedPlacement>>
            AZKABAN_FIXED_PLACEMENT = STRUCTURE_PLACEMENT_TYPES.register(
                    "azkaban_fixed", () -> () -> AzkabanFixedPlacement.CODEC);

    private AzkabanWorldgenRegistries() {}
}
