package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.GeoRendererHelper;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixEntity;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixRebirth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * The phoenix's renderer: the plain GeckoLib one plus its glowmask, and one rule — the ash pile under the bird
 * exists only while it burns. The rebirth clips ({@code burst}, {@code ashes}, {@code rise}) scale the pile in and
 * out; outside them nothing keys it, so here it is held at zero. The phase comes from the entity's synced data,
 * never decided on the client.
 */
public class PhoenixRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<PhoenixEntity, R> {

    private static final DataTicket<Boolean> TICKET_BURNING = DataTicket.create("phoenix_burning", Boolean.class);

    public PhoenixRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "phoenix")));
        GeoRendererHelper.applyGlowIfPresent(this, "phoenix");
    }

    @Override
    public void addRenderData(@NonNull PhoenixEntity phoenix, Void unused, @NonNull R renderState, float partialTick) {
        super.addRenderData(phoenix, unused, renderState, partialTick);
        renderState.addGeckolibData(TICKET_BURNING, phoenix.syncedPhase() != PhoenixRebirth.Phase.ALIVE);
    }

    @Override
    public void adjustModelBonesForRender(@NonNull RenderPassInfo<R> info, @NonNull BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        if (!Boolean.TRUE.equals(info.getOrDefaultGeckolibData(TICKET_BURNING, false))) {
            bones.ifPresent("ash", b -> {
                b.setScaleX(0f);
                b.setScaleY(0f);
                b.setScaleZ(0f);
            });
        }
    }
}
