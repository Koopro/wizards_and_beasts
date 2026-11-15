package at.koopro.wizardsandbeasts.client.wand.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.NullMarked;

/**
 * Draws the restyle screen's wand as a 3D model on a turntable, into its own texture each frame
 * (vanilla's picture-in-picture path, the one the inventory's player model uses).
 *
 * <p>The wand goes through the real item renderer in the item-frame pose, which centres it on its own length; that
 * pose lays it on the diagonal, and this stands it back up, leans it a little and turns it. The core's light is
 * drawn at the point as a small glow with motes falling off it, facing the viewer whichever way the wand is turned:
 * the wand model itself has no lit tip since the tips were retired.
 */
@NullMarked
public final class WandPreviewRenderer extends PictureInPictureRenderer<WandPreviewRenderState> {

    /** The item-frame pose lays the wand at -45 degrees; this stands it upright again. */
    private static final float UPRIGHT = 45.0f;
    /** How far the wand leans off upright, so turning it shows its length as well as its girth. */
    private static final float LEAN = 18.0f;
    /**
     * The frame pose leaves the wand's middle this far (blocks, along its length) below the centre of the picture;
     * moved back so the wand sits in the middle of its stage. Measured off the drawn wand.
     */
    private static final float CENTRE_SHIFT = 0.10f;

    public WandPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<WandPreviewRenderState> getRenderStateClass() {
        return WandPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(WandPreviewRenderState state, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        pose.scale(1.0f, -1.0f, -1.0f);
        pose.mulPose(Axis.XP.rotationDegrees(state.pitchDeg()));
        pose.mulPose(Axis.YP.rotationDegrees(state.yawDeg()));
        pose.mulPose(Axis.ZP.rotationDegrees(UPRIGHT - LEAN));
        pose.mulPose(Axis.ZP.rotationDegrees(-UPRIGHT));
        pose.translate(0.0f, CENTRE_SHIFT, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(UPRIGHT));

        FeatureRenderDispatcher dispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage storage = dispatcher.getSubmitNodeStorage();
        ItemStackRenderState item = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(item, state.wand(), ItemDisplayContext.FIXED, mc.level, null, 0);
        item.submit(pose, storage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);

        if ((state.glint() & 0xFFFFFF) != 0) {
            submitCoreLight(pose, storage, state);
        }
        dispatcher.renderAllFeatures();
    }

    /** A glow at the point, turned to face the viewer, and three motes drifting down from it. */
    private static void submitCoreLight(PoseStack pose, SubmitNodeStorage storage, WandPreviewRenderState state) {
        pose.pushPose();
        // Up the wand to its point (the wand stands along +Y here before its lean is undone).
        pose.mulPose(Axis.ZP.rotationDegrees(-(UPRIGHT - LEAN)));
        pose.mulPose(Axis.ZP.rotationDegrees(-LEAN));
        // The frame was moved up the wand to centre it, so the point is CENTRE_SHIFT nearer in this frame.
        pose.translate(0.0f, state.tipHeight() - CENTRE_SHIFT, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(LEAN));
        // Face the viewer: undo the turn and the tilt around the point.
        pose.mulPose(Axis.YP.rotationDegrees(-state.yawDeg()));
        pose.mulPose(Axis.XP.rotationDegrees(-state.pitchDeg()));
        int rgb = state.glint() & 0xFFFFFF;
        long now = Util.getMillis();
        float pulse = 0.8f + 0.2f * (float) Math.sin(now / 300.0);
        storage.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buffer) -> {
            float[] radius = {0.055f, 0.035f, 0.02f, 0.009f};
            int[] alpha = {35, 70, 130, 255};
            for (int i = 0; i < radius.length; i++) {
                quad(p, buffer, 0.0f, 0.0f, radius[i], rgb, Math.round(alpha[i] * pulse));
            }
            for (int i = 0; i < 3; i++) {
                float t = ((now + i * 900L) % 2700L) / 2700.0f;
                float x = (float) Math.sin(i * 2.1 + t * 3.0) * 0.06f;
                float y = -0.03f - t * 0.3f;
                quad(p, buffer, x, y, 0.009f, rgb, Math.round(230 * (1.0f - t)));
            }
        });
        pose.popPose();
    }

    private static void quad(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer buffer, float cx, float cy,
                             float r, int rgb, int alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        buffer.addVertex(p, cx - r, cy - r, 0.0f).setColor(red, green, blue, alpha);
        buffer.addVertex(p, cx + r, cy - r, 0.0f).setColor(red, green, blue, alpha);
        buffer.addVertex(p, cx + r, cy + r, 0.0f).setColor(red, green, blue, alpha);
        buffer.addVertex(p, cx - r, cy + r, 0.0f).setColor(red, green, blue, alpha);
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    @Override
    protected String getTextureLabel() {
        return "wand restyle preview";
    }
}
