package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.KelpieEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The Kelpie: the disguise renderer (guise or true form, same skeleton) with the true form's coat.
 *
 * <p>The guise is one dark horse; the coats are the true form's — black, blue-black, green-black — and only show once
 * it reveals itself.
 */
public class KelpieRenderer<R extends EntityRenderState & GeoRenderState> extends TintedBeastRenderer<R> {

    static final DataTicket<KelpieEntity.Coat> TICKET_COAT = DataTicket.create("kelpie_coat", KelpieEntity.Coat.class);

    public KelpieRenderer(EntityRendererProvider.Context context) {
        super(context, new CoatModel());
    }

    @Override
    public void addRenderData(@NonNull GenericBeastEntity beast, Void unused, @NonNull R renderState, float partialTick) {
        super.addRenderData(beast, unused, renderState, partialTick);
        renderState.addGeckolibData(DisguisableBeastGeoModel.TICKET_DISGUISED, beast.isDisguised());
        if (beast instanceof KelpieEntity kelpie) {
            renderState.addGeckolibData(TICKET_COAT, kelpie.coat());
        }
    }

    private static final class CoatModel extends DisguisableBeastGeoModel {

        CoatModel() {
            super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "kelpie"),
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "kelpie_disguise"));
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            if (renderState.getOrDefaultGeckolibData(TICKET_DISGUISED, Boolean.FALSE)) {
                return super.getTextureResource(renderState);
            }
            String coat = renderState.getOrDefaultGeckolibData(TICKET_COAT, KelpieEntity.Coat.BLACK).texture();
            return coat == null ? super.getTextureResource(renderState)
                    : buildFormattedTexturePath(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, coat));
        }
    }
}
