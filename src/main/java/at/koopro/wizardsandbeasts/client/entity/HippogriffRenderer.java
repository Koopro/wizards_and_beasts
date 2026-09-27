package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.HippogriffEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The hippogriff's coat: the tinted beast renderer with a model whose texture follows the synced {@link
 * HippogriffEntity.Coat}. Geometry and animation stay on the base asset — the five coats share one rig, as the
 * goblin's roles do ({@link GoblinVariantGeoModel}).
 */
public class HippogriffRenderer<R extends EntityRenderState & GeoRenderState> extends TintedBeastRenderer<R> {

    static final DataTicket<HippogriffEntity.Coat> TICKET_COAT =
            DataTicket.create("hippogriff_coat", HippogriffEntity.Coat.class);

    public HippogriffRenderer(EntityRendererProvider.Context context) {
        super(context, new CoatModel());
    }

    @Override
    public void addRenderData(@NonNull GenericBeastEntity beast, Void unused, @NonNull R renderState, float partialTick) {
        super.addRenderData(beast, unused, renderState, partialTick);
        if (beast instanceof HippogriffEntity hippogriff) {
            renderState.addGeckolibData(TICKET_COAT, hippogriff.coat());
        }
    }

    private static final class CoatModel extends DefaultedEntityGeoModel<GenericBeastEntity> {

        CoatModel() {
            super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hippogriff"), "head");
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            String name = renderState.getOrDefaultGeckolibData(TICKET_COAT, HippogriffEntity.Coat.STORM_GREY).textureName();
            return name == null ? super.getTextureResource(renderState)
                    : buildFormattedTexturePath(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name));
        }
    }
}
