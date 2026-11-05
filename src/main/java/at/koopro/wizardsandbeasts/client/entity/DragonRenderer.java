package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * GeckoLib renderer for {@link DragonEntity}. One renderer drives all ten breeds; each breed's model is the id-named
 * {@code DefaultedEntityGeoModel}.
 *
 * <p>Size is not this class's: a breed's size is the definition's {@code scale}, applied through
 * {@code Attributes.SCALE}, which moves model and hitbox together. A render-only root-bone scale from
 * {@code dragon.scale} and a flame-colour ticket used to live here; no data set the one and nothing read the other
 * (VR-0 D12, Beast Renaissance D28), and with definitions now synced to clients a datapack setting {@code dragon.scale}
 * would have scaled the model a second time over the attribute.
 */
public class DragonRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<DragonEntity, R> {

    public DragonRenderer(EntityRendererProvider.Context context, String modelName) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
    }

    /**
     * Raw provider mirroring {@code GeoRendererHelper.simple}: lets {@code ClientSetup} register this
     * renderer against the manifest's {@code EntityType<GenericBeastEntity>} holders (DragonEntity is a
     * subclass) without fighting the generic bounds.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider(String modelName) {
        return context -> at.koopro.wizardsandbeasts.client.GeoRendererHelper.applyGlowIfPresent(
                new DragonRenderer(context, modelName), modelName);
    }
}
