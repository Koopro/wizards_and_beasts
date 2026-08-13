package at.koopro.wizardsandbeasts.client.form.geo;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Resolves which rig, texture and animation file a transformed player draws.
 *
 * <p>{@code getModelResource} and {@code getTextureResource} take the render state rather than the
 * animatable in this GeckoLib version, so the rig has to travel as a {@link DataTicket} — the same
 * mechanism {@code DisguisableBeastGeoModel} uses to switch a Kelpie between its true form and its
 * horse guise, and for the same reason: one model object serves every player, and what it draws
 * differs per render.
 *
 * <p>{@code getAnimationResource} does take the animatable, which is why
 * {@link PlayerFormAnimatable} carries its rig.
 */
@NullMarked
public final class PlayerFormGeoModel extends GeoModel<PlayerFormAnimatable> {

    /** The rig this render pass is drawing. Placed on the state by {@link PlayerFormGeoRenderer}. */
    public static final DataTicket<PlayerFormRig> TICKET_RIG =
            DataTicket.create("player_form_rig", PlayerFormRig.class);

    /**
     * Fallback when the ticket is missing.
     *
     * <p>Should be unreachable — the renderer sets the ticket before every pass — but a null here
     * becomes a crash inside GeckoLib's asset loader rather than a visible mistake, so it resolves to
     * a rig that certainly exists instead.
     */
    private static final PlayerFormRig FALLBACK = PlayerFormRig.forForm("werewolf_wolf");

    private static PlayerFormRig rigOf(GeoRenderState renderState) {
        PlayerFormRig rig = renderState.getGeckolibData(TICKET_RIG);
        return rig != null ? rig : FALLBACK;
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return rigOf(renderState).modelResource();
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return rigOf(renderState).textureResource();
    }

    @Override
    public Identifier getAnimationResource(PlayerFormAnimatable animatable) {
        return animatable.rig().animationResource();
    }
}
