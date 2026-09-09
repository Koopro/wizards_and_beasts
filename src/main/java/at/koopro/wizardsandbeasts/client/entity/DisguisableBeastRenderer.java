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
 * Drop-in extension of {@link TintedBeastRenderer} (keeps the tint working) for creatures with a
 * {@code LureDisguise} ability — feeds the synced disguise flag into {@link DisguisableBeastGeoModel}
 * via a {@code DataTicket}, exactly mirroring how the parent already threads the tint.
 */
public class DisguisableBeastRenderer<R extends EntityRenderState & GeoRenderState>
        extends TintedBeastRenderer<R> {

    public DisguisableBeastRenderer(EntityRendererProvider.Context context,
                                     Identifier trueFormAssetSubpath, Identifier disguiseAssetSubpath) {
        super(context, new DisguisableBeastGeoModel(trueFormAssetSubpath, disguiseAssetSubpath));
    }

    @Override
    public void addRenderData(@NonNull GenericBeastEntity beast, Void unused,
                               @NonNull R renderState, float partialTick) {
        super.addRenderData(beast, unused, renderState, partialTick);
        renderState.addGeckolibData(DisguisableBeastGeoModel.TICKET_DISGUISED, beast.isDisguised());
    }

    /** Provider mirroring {@code TintedBeastRenderer.provider}, taking both the true-form and disguise asset names. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider(
            String trueFormModelName, String disguiseModelName) {
        return context -> new DisguisableBeastRenderer(context,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, trueFormModelName),
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, disguiseModelName));
    }
}
