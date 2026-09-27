package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Draws a Dementor for those who can see one.
 *
 * <p>Canon: Muggles cannot see Dementors, though they feel them — Dudley felt the cold and relived his worst in
 * Little Whinging and saw nothing. So seeing is decided per viewer in {@link #shouldRender}: the aura, the Kiss and
 * the AI are the same for everyone (server-side), and a Muggle simply draws nothing — no model, no shadow, no name.
 * A player with no heritage chosen is the Muggle case (the heritage enum has no Muggle entry). Spectators see it.
 *
 * <p>This replaces zero-scaling the {@code root} bone, which still ran the whole render pass to draw nothing.
 *
 * <p>Dissipation is the {@code dissipate} clip, not vanilla's death roll, so the death tilt is off.
 */
public class DementorRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<DementorEntity, R> {

    public DementorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dementor")));
        this.shadowRadius = 0.4f;
    }

    @Override
    public boolean shouldRender(@NonNull DementorEntity dementor, @NonNull Frustum frustum, double x, double y, double z) {
        return canPerceive() && super.shouldRender(dementor, frustum, x, y, z);
    }

    @Override
    protected float getDeathMaxRotation(@NonNull GeoRenderState renderState) {
        return 0f;
    }

    /** Whether the local player can see Dementors: anyone with magical heritage, or a spectator. */
    public static boolean canPerceive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isSpectator()) {
            return true;
        }
        return ClientHeritageDataState.get().getSelectedHeritage() != null;
    }
}
