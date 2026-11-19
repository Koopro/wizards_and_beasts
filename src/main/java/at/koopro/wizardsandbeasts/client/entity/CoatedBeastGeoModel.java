package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.event.entity.CompileEntityRenderStateEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The model of a generic creature that may wear a coat from its definition ({@code Coats}): the base texture
 * unless the render state carries a coat, then {@code textures/entity/<creature>/<coat>.png}. GeckoLib's glow layer
 * derives its mask from the texture in use, so a coat on a glowing creature ships its own {@code _glowmask} beside it
 * ({@code tools/coat_variants.py} copies it).
 *
 * <p>The coat reaches the render state through {@link Stamp}, once per frame for every generic creature, so the
 * renderers ({@code TintedBeastRenderer}, {@code DragonRenderer}) need nothing beyond using this model.
 */
@NullMarked
public class CoatedBeastGeoModel<T extends GenericBeastEntity> extends DefaultedEntityGeoModel<T> {

    public static final DataTicket<String> COAT = DataTicket.create("wizards_and_beasts_coat", String.class);

    public CoatedBeastGeoModel(Identifier asset) {
        super(asset);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        String coat = renderState.getGeckolibData(COAT);
        return coat == null ? super.getTextureResource(renderState)
                : buildFormattedTexturePath(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, coat));
    }

    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
    public static final class Stamp {

        private Stamp() {}

        @SubscribeEvent
        public static void onCompileRenderState(CompileEntityRenderStateEvent<?, ?> event) {
            if (event.getAnimatable() instanceof GenericBeastEntity beast) {
                String coat = beast.coatTexture();
                if (coat != null) {
                    event.addData(COAT, coat);
                }
            }
        }
    }
}
