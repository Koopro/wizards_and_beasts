package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Applies the "root" debug transform (PoseStack-level scale + translate)
 * during player rendering. The "root" entry is not a real ModelPart —
 * it controls whole-model scale and offset via the PoseStack.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class DebugRenderHandler {

    /** Whether we pushed a pose in Pre that needs popping in Post. */
    private static boolean pushedPose = false;

    private DebugRenderHandler() {}

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        ModelDebugEditor editor = ModelDebugEditor.get();
        if (!editor.isActive()) return;
        if (editor.getModelMode() != ModelDebugEditor.ModelMode.VANILLA) return;

        DebugTransformData root = editor.getData("root");
        if (root.isDefault()) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(root.offX, root.offY, root.offZ);
        poseStack.scale(root.scaleX, root.scaleY, root.scaleZ);
        pushedPose = true;
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (pushedPose) {
            event.getPoseStack().popPose();
            pushedPose = false;
        }
    }
}
