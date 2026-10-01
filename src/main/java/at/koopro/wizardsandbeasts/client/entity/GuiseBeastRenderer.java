package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * {@link TintedBeastRenderer} for shapeshifters: feeds the synced guise into {@link GuiseBeastGeoModel}, so a Boggart
 * is drawn as whatever it has become for the person facing it.
 */
public class GuiseBeastRenderer<R extends EntityRenderState & GeoRenderState> extends TintedBeastRenderer<R> {

    public GuiseBeastRenderer(EntityRendererProvider.Context context, String ownModelName) {
        super(context, new GuiseBeastGeoModel(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, ownModelName)));
    }

    @Override
    public void addRenderData(@NonNull GenericBeastEntity beast, Void unused, @NonNull R renderState, float partialTick) {
        super.addRenderData(beast, unused, renderState, partialTick);
        renderState.addGeckolibData(GuiseBeastGeoModel.TICKET_GUISE, beast.getGuise());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider(String ownModelName) {
        return context -> new GuiseBeastRenderer(context, ownModelName);
    }
}
