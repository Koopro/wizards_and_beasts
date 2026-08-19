package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Renders a Gringotts goblin in whichever of the four roles it is wearing.
 *
 * <p>All this adds over {@code GeoRendererHelper.simple} is copying the synced variant onto the
 * render state so {@link GoblinVariantGeoModel} can branch on it — the same one-ticket shape
 * {@link ScaledBeastRenderer} uses for render scale and {@link DisguisableBeastRenderer} for the
 * Kelpie's guise. The live entity is never read at render time (GeckoLib-5 contract).
 *
 * <p>No glow layer: nothing on a goblin is emissive, so there is no {@code _glowmask} to probe for.
 */
public class GoblinRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<GoblinTellerEntity, R> {

    public GoblinRenderer(EntityRendererProvider.Context context, String baseAsset) {
        super(context, new GoblinVariantGeoModel(baseAsset));
    }

    @Override
    public void addRenderData(@NonNull GoblinTellerEntity goblin, Void unused,
                              @NonNull R renderState, float partialTick) {
        super.addRenderData(goblin, unused, renderState, partialTick);
        renderState.addGeckolibData(GoblinVariantGeoModel.TICKET_VARIANT, goblin.variant());
    }

    /** Provider mirroring {@code ScaledBeastRenderer.provider}, for {@code ClientSetup}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static EntityRendererProvider<GoblinTellerEntity> provider(String baseAsset) {
        return context -> new GoblinRenderer(context, baseAsset);
    }
}
