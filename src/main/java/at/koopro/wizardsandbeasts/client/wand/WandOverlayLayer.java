package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the wand's model a second time over itself, with another sheet laid on the same UVs.
 *
 * <p>Two such passes make a wand's look ({@link WandRenderer}): the <b>trim</b>, the wand's metal,
 * stones and leather drawn untinted so the wood's colour does not muddy a silver ferrule; and the
 * <b>glint</b>, the core's light at the point (and pommel), drawn additively at full brightness in the
 * core's colour. Each reads its sheet and colour from the render state, so one layer type serves both
 * and a wand that needs neither pass (no trim sheet, a black glint) costs nothing.
 *
 * <p>Same geometry, same bone poses and visibility as the main pass -- it renders through the main
 * pass's own {@link RenderPassInfo} -- so the overlay can never drift off the wand it decorates.
 */
@NullMarked
final class WandOverlayLayer extends GeoRenderLayer<WandItem, GeoItemRenderer.RenderData, GeoRenderState> {

    enum Kind { TRIM, GLINT }

    private final Kind kind;
    private final DataTicket<Identifier> sheet;
    private final DataTicket<Integer> colour;

    WandOverlayLayer(GeoItemRenderer<WandItem> renderer, Kind kind,
                     DataTicket<Identifier> sheet, DataTicket<Integer> colour) {
        super(renderer);
        this.kind = kind;
        this.sheet = sheet;
        this.colour = colour;
    }

    @Override
    public void submitRenderTask(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector collector) {
        if (!info.willRender()) {
            return;
        }
        GeoRenderState state = info.renderState();
        Identifier texture = state.getGeckolibData(sheet);
        Integer argb = state.getGeckolibData(colour);
        if (texture == null || argb == null || (argb & 0xFFFFFF) == 0) {
            return;
        }
        RenderType type = kind == Kind.GLINT ? RenderTypes.eyes(texture) : RenderTypes.entityCutoutNoCull(texture);
        int light = kind == Kind.GLINT ? LightTexture.FULL_BRIGHT : info.packedLight();
        int overlay = kind == Kind.GLINT ? OverlayTexture.NO_OVERLAY : info.packedOverlay();
        int packedColour = argb;
        collector.order(1).submitCustomGeometry(info.poseStack(), type, (pose, buffer) -> {
            PoseStack poseStack = info.poseStack();
            poseStack.pushPose();
            poseStack.last().set(pose);
            info.renderPosed(() -> info.model().render(info, buffer, light, overlay, packedColour));
            poseStack.popPose();
        });
    }
}
