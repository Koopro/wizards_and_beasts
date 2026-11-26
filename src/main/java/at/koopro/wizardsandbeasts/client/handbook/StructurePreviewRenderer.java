package at.koopro.wizardsandbeasts.client.handbook;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.NullMarked;

/**
 * Draws a handbook build guide's blocks into their own texture each frame (vanilla's picture-in-picture path, as the
 * wand restyle preview does): the build turned on its turntable, every placed block through the real block renderer,
 * the current layer hanging a little above its place until it lands.
 */
@NullMarked
public final class StructurePreviewRenderer extends PictureInPictureRenderer<StructurePreviewRenderState> {

    public StructurePreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<StructurePreviewRenderState> getRenderStateClass() {
        return StructurePreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(StructurePreviewRenderState state, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        pose.scale(1.0f, -1.0f, -1.0f);
        pose.mulPose(Axis.XP.rotationDegrees(state.pitchDeg()));
        pose.mulPose(Axis.YP.rotationDegrees(state.yawDeg()));

        FeatureRenderDispatcher dispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage storage = dispatcher.getSubmitNodeStorage();
        for (StructurePreviewRenderState.Placed placed : state.blocks()) {
            pose.pushPose();
            pose.translate(placed.pos().getX() - 0.5f, placed.pos().getY() - 0.5f + (placed.current() ? state.drop() : 0.0f),
                    placed.pos().getZ() - 0.5f);
            storage.submitBlock(pose, placed.state(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        dispatcher.renderAllFeatures();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    @Override
    protected String getTextureLabel() {
        return "handbook build guide";
    }
}
