package at.koopro.wizardsandbeasts.client.furniture;

import at.koopro.wizardsandbeasts.block.furniture.DisplayCaseBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** The thing in a display case, turning slowly over its plinth. Nothing is drawn for an empty case. */
public final class DisplayCaseRenderer implements BlockEntityRenderer<DisplayCaseBlockEntity, DisplayCaseRenderer.State> {

    /** Where the item floats (block units): over the plinth, inside the glass. */
    private static final float ITEM_Y = 7.5f;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean empty = true;
        float spin;
    }

    @Override
    public @NonNull State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(@NonNull DisplayCaseBlockEntity display, @NonNull State state, float partialTick,
                                   @NonNull Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTick, cameraPos, breakProgress);
        state.empty = display.shown().isEmpty();
        if (state.empty || display.getLevel() == null) {
            state.item.clear();
            return;
        }
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.item, display.shown(),
                ItemDisplayContext.FIXED, display.getLevel(), null, (int) display.getBlockPos().asLong());
        state.spin = ((display.getLevel().getGameTime() % 7200L) + partialTick) * 1.5f;
    }

    @Override
    public void submit(@NonNull State state, @NonNull PoseStack pose, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        if (state.empty) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, ITEM_Y / 16f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.spin));
        pose.scale(0.5f, 0.5f, 0.5f);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
