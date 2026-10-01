package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.beast.StreelerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The Streeler in this hour's colour.
 *
 * <p>The colour ({@link StreelerEntity#colour()}) comes from the day clock and the UUID, which the client already has,
 * so nothing is synced for it. It multiplies the painted skin; lifting it part-way to white keeps the shell's painted
 * bands and the foot's shading legible under every hue instead of flooding them with one colour.
 */
public class StreelerRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<StreelerEntity, R> {

    /** How much of the hour's colour reaches the skin; the rest is white. */
    static final float TINT_STRENGTH = 0.6f;

    public StreelerRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "streeler")));
    }

    @Override
    public int getRenderColor(@NonNull StreelerEntity streeler, Void unused, float partialTick) {
        int base = super.getRenderColor(streeler, unused, partialTick);
        int tint = ARGB.srgbLerp(TINT_STRENGTH, 0xFFFFFFFF, streeler.colour());
        return ARGB.multiply(base, tint);
    }
}
