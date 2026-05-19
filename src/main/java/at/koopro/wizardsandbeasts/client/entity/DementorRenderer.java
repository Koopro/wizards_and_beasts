package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
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
 * GeckoLib renderer for {@link DementorEntity}.
 *
 * <p>Heritage-aware: Muggles see no model (environmental effects still apply server-side).
 * All entity state is passed via {@link DataTicket} — no direct entity access at render time.
 */
public class DementorRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<DementorEntity, R> {

    /** True when the local player is a Muggle (no magical heritage selected). */
    public static final DataTicket<Boolean> TICKET_MUGGLE_VIEW =
            DataTicket.create("dementor_muggle_view", Boolean.class);

    public DementorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dementor")));
    }

    @Override
    public void addRenderData(@NonNull DementorEntity dementor, Void unused,
                              @NonNull R renderState, float partialTick) {
        super.addRenderData(dementor, unused, renderState, partialTick);
        renderState.addGeckolibData(TICKET_MUGGLE_VIEW, isMuggleView());
    }

    @Override
    public void adjustModelBonesForRender(@NonNull RenderPassInfo<R> info, @NonNull BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        if (Boolean.TRUE.equals(info.getOrDefaultGeckolibData(TICKET_MUGGLE_VIEW, false))) {
            // Scale root to zero — Muggles cannot perceive the Dementor's form
            bones.ifPresent("root", b -> {
                b.setScaleX(0f);
                b.setScaleY(0f);
                b.setScaleZ(0f);
            });
        }
    }

    /**
     * Heritage enum has no MUGGLE entry; non-magical humans are represented by
     * an absence of selected heritage. We treat "no heritage selected" as Muggle.
     *
     * // TODO: update once a proper Muggle heritage type is added to the Heritage enum
     */
    private static boolean isMuggleView() {
        PlayerHeritageData data = ClientHeritageDataState.get();
        Heritage h = data.getSelectedHeritage();
        return h == null;
    }
}
