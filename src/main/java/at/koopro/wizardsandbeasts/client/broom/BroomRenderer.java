package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class BroomRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<BroomEntity, R> {

    public static final DataTicket<Float> PITCH_TILT =
            DataTicket.create("broom_pitch_tilt", Float.class);
    public static final DataTicket<Float> ROLL_TILT =
            DataTicket.create("broom_roll_tilt", Float.class);
    public static final DataTicket<Float> FORWARD_LEAN =
            DataTicket.create("broom_forward_lean", Float.class);

    public BroomRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom")));
    }

    @Override
    public void addRenderData(BroomEntity broom, Void unused, R renderState, float partialTick) {
        super.addRenderData(broom, unused, renderState, partialTick);

        renderState.addGeckolibData(PITCH_TILT,
                Mth.lerp(partialTick, broom.getPrevPitchTilt(), broom.getPitchTilt()));
        renderState.addGeckolibData(ROLL_TILT,
                Mth.lerp(partialTick, broom.getPrevRollTilt(), broom.getRollTilt()));
        renderState.addGeckolibData(FORWARD_LEAN,
                Mth.lerp(partialTick, broom.getPrevForwardLean(), broom.getForwardLean()));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);

        float pitchTilt   = info.getOrDefaultGeckolibData(PITCH_TILT, 0f);
        float rollTilt    = info.getOrDefaultGeckolibData(ROLL_TILT, 0f);
        float forwardLean = info.getOrDefaultGeckolibData(FORWARD_LEAN, 0f);

        bones.ifPresent("broom_body", snapshot -> {
            snapshot.setRotX((pitchTilt + forwardLean) * Mth.DEG_TO_RAD);
            snapshot.setRotZ(rollTilt * Mth.DEG_TO_RAD);
        });
    }
}
