package at.koopro.wizardsandbeasts.azkaban.biome;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import terrablender.api.Regions;

/**
 * Registers the {@link AzkabanRegion} with TerraBlender so {@code azkaban_sea}
 * is injected into the overworld biome source.
 *
 * <p>Must be called from {@code FMLCommonSetupEvent} via {@code enqueueWork(...)}
 * (TerraBlender region registration is not thread-safe and must run on the main
 * setup thread). Registration is unconditional — the {@code Module.AZKABAN} gate
 * controls structure <em>generation</em>, not biome registration, so the biome
 * stays available even when the module is disabled.
 */
public final class AzkabanTerraBlender {

    private static final Identifier REGION_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "overworld");

    private AzkabanTerraBlender() {}

    public static void register() {
        Regions.register(new AzkabanRegion(REGION_ID));
    }
}
