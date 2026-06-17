package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.model.PatronusStagModel;
import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Patronus renderer. The drifting particle trail carries most of the look; on top of it we draw an
 * ethereal translucent stag — placeholder geometry standing in until per-form Patronus models exist.
 */
public class PatronusRenderer extends EntityRenderer<PatronusEntity, EntityRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/form/placeholder.png");
    /** Icy translucent silver-blue. */
    private static final int TINT = 0x99B8E6FF;
    private static final int FULL_BRIGHT = 0xF000F0;

    private final PatronusStagModel model = new PatronusStagModel();

    public PatronusRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);

        float age = state.ageInTicks;
        poseStack.pushPose();
        // Entity model space is inverted; lift so the legs rest near the entity origin.
        poseStack.translate(0.0, 1.4, 0.0);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        // Slow drift-spin and a gentle vertical bob.
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5f));
        poseStack.translate(0.0, (float) Math.sin(age * 0.12f) * 0.04f, 0.0);

        RenderType renderType = RenderTypes.entityTranslucent(TEXTURE);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack temp = new PoseStack();
            temp.last().pose().set(pose.pose());
            temp.last().normal().set(pose.normal());
            model.render(temp, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, TINT);
        });

        poseStack.popPose();
    }
}
