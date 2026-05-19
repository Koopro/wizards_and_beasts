package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Factory for simple GeoEntityRenderers that use DefaultedEntityGeoModel
 * with no custom render logic. Eliminates one-class-per-entity for simple mobs.
 */
public final class GeoRendererHelper {

    private GeoRendererHelper() {}

    /**
     * Creates a renderer factory for a simple GeckoLib entity.
     * <p>
     * Usage in ClientSetup:
     * <pre>
     * event.registerEntityRenderer(ModEntities.NIFFLER.get(),
     *     GeoRendererHelper.simple("niffler"));
     * </pre>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> simple(String modelName) {
        return context -> new GeoEntityRenderer(context,
                new DefaultedEntityGeoModel<>(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
    }
}
