package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Niffler and Baby Niffler: one rig, the coat's texture, and head tracking on the cube-less {@code look} bone (GeckoLib
 * sets the rotation of the bone it turns, so tracking {@code head} would erase every sniff and dig).
 */
public class NifflerRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<NifflerEntity, R> {

    static final DataTicket<NifflerEntity.Coat> TICKET_COAT = DataTicket.create("niffler_coat", NifflerEntity.Coat.class);

    public NifflerRenderer(EntityRendererProvider.Context context) {
        super(context, new CoatModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    public void addRenderData(@NonNull NifflerEntity niffler, Void unused, @NonNull R renderState, float partialTick) {
        super.addRenderData(niffler, unused, renderState, partialTick);
        renderState.addGeckolibData(TICKET_COAT, niffler.coat());
    }

    private static final class CoatModel extends DefaultedEntityGeoModel<NifflerEntity> {

        CoatModel() {
            super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "niffler"), "look");
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            String name = renderState.getOrDefaultGeckolibData(TICKET_COAT, NifflerEntity.Coat.CLASSIC).texture();
            return name == null ? super.getTextureResource(renderState)
                    : buildFormattedTexturePath(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name));
        }
    }
}
