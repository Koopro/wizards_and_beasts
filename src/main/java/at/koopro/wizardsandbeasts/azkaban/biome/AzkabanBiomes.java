package at.koopro.wizardsandbeasts.azkaban.biome;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class AzkabanBiomes {

    public static final ResourceKey<Biome> AZKABAN_SEA = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "azkaban_sea"));

    private AzkabanBiomes() {}
}
