package at.koopro.wizardsandbeasts.client.diagon;

import at.koopro.wizardsandbeasts.diagon.DiagonArchwayMovingBlock;
import at.koopro.wizardsandbeasts.diagon.DiagonArchwayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Draws a brick of the archway into Diagon Alley, and plays its part when the arch opens or closes.
 *
 * <p>Opening, ring by ring out from the tapped brick: a brick wriggles in its place, then folds back into the wall, out
 * along it away from the tap, shrinking to nothing; the arch stands open behind them. Closing plays it backwards, the
 * outer ring first, each brick growing back out of the wall and settling with a last shiver. A brick at rest is a plain
 * brick, drawn as the alley's own brick block so it cannot be told from its neighbours.
 */
public final class DiagonArchwayRenderer implements BlockEntityRenderer<DiagonArchwayBlockEntity, DiagonArchwayRenderer.State> {

    /** Seconds between one ring starting and the next. */
    private static final float RING_DELAY = 0.12f;
    private static final float WRIGGLE = 0.35f;
    private static final float FOLD = 0.5f;
    private static final float SETTLE = 0.2f;
    /** How far along the wall a brick slides as it folds away, in blocks. */
    private static final float SLIDE = 0.6f;
    /** Rings an arch can have; closing starts at the outside. */
    private static final int OUTER_RING = 4;

    public static final class State extends BlockEntityRenderState {
        boolean open;
        /** Seconds into this brick's own part; below zero it has not started. */
        float local;
        boolean resting;
        float outX;
        float outY;
        float outZ;
        float seed;
    }

    @Override
    public @NonNull State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(@NonNull DiagonArchwayBlockEntity brick, @NonNull State state, float partialTick,
                                   @NonNull Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(brick, state, partialTick, cameraPos, breakProgress);
        state.open = state.blockState.getValueOrElse(DiagonArchwayMovingBlock.OPEN, false);
        Level level = brick.getLevel();
        state.resting = brick.changedAt() == Long.MIN_VALUE || level == null;
        if (!state.resting) {
            float seconds = (level.getGameTime() - brick.changedAt() + partialTick) / 20f;
            int order = state.open ? brick.ring() : Math.max(0, OUTER_RING - brick.ring());
            state.local = seconds - order * RING_DELAY;
        }
        state.outX = brick.outX();
        state.outY = brick.outY();
        state.outZ = brick.outZ();
        state.seed = (brick.getBlockPos().hashCode() & 0xFF) / 255f * Mth.TWO_PI;
        // Lit like the wall's faces, from the air in front of it: the light inside a solid wall is near nothing, and a
        // brick lit from inside showed darker than its neighbours.
        if (level != null) {
            int sky = 0;
            int block = 0;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                int packed = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, brick.getBlockPos().relative(side));
                sky = Math.max(sky, net.minecraft.client.renderer.LightTexture.sky(packed));
                block = Math.max(block, net.minecraft.client.renderer.LightTexture.block(packed));
            }
            state.lightCoords = net.minecraft.client.renderer.LightTexture.pack(block, sky);
        }
    }

    @Override
    public void submit(@NonNull State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        float scale = 1f;
        float slide = 0f;
        float shiver = 0f;
        if (state.resting) {
            if (state.open) {
                return;
            }
        } else if (state.open) {
            if (state.local >= WRIGGLE + FOLD) {
                return;                                    // folded away: the arch is open here
            }
            if (state.local > 0f && state.local < WRIGGLE) {
                shiver = 1f - state.local / WRIGGLE * 0.3f;
            } else if (state.local >= WRIGGLE) {
                float f = ease((state.local - WRIGGLE) / FOLD);
                scale = 1f - f;
                slide = f * SLIDE;
                shiver = 0.4f * (1f - f);
            }
        } else {
            if (state.local <= 0f) {
                return;                                    // not back yet: still open here
            }
            if (state.local < FOLD) {
                float f = ease(state.local / FOLD);
                scale = f;
                slide = (1f - f) * SLIDE;
            } else if (state.local < FOLD + SETTLE) {
                shiver = 1f - (state.local - FOLD) / SETTLE;
            }
        }
        if (scale <= 0.01f) {
            return;
        }
        float time = state.local + state.seed;
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.translate(0.5f + state.outX * slide, 0.5f + state.outY * slide, 0.5f + state.outZ * slide);
        if (shiver > 0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 45f) * 5f * shiver));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 38f + 1.3f) * 5f * shiver));
            poseStack.translate(Mth.sin(time * 51f) * 0.02f * shiver, 0f, Mth.cos(time * 47f) * 0.02f * shiver);
        }
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        // The alley's own brick block, drawn as a block: same model, same face shading as the wall round it, so a
        // brick at rest cannot be told from its neighbours (a hand-built cube lit like an entity showed darker).
        collector.submitBlock(poseStack, at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks.DIAGON_BRICK.base()
                .get().defaultBlockState(), light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static float ease(float f) {
        f = Mth.clamp(f, 0f, 1f);
        return f * f * (3f - 2f * f);
    }
}
