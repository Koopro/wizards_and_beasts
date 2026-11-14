package at.koopro.wizardsandbeasts.client.diagon;

import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.diagon.DiagonArchwayBlockEntity;
import at.koopro.wizardsandbeasts.diagon.DiagonArchwayMovingBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Draws a brick of the archway into Diagon Alley while it moves, and plays its part.
 *
 * <p>Opening, ring by ring out from the tapped brick: a brick wriggles in its place, then folds into the side of the
 * arch. It turns edge-on like a card, squeezing thin, as it slides into the nearer jamb, or, in the top course and
 * the middle column, up into the lintel. It slides into the standing wall, which hides it, and the arch is open behind
 * it. Closing plays it backwards, the outer ring first: each brick unfolds out of the side and settles with a shiver.
 * Drawn as the alley's own brick block, lit from the air in front of the wall, so it matches the bricks round it.
 */
public final class DiagonArchwayRenderer implements BlockEntityRenderer<DiagonArchwayBlockEntity, DiagonArchwayRenderer.State> {

    /** Seconds between one ring starting and the next. */
    private static final float RING_DELAY = 0.12f;
    private static final float WRIGGLE = 0.3f;
    private static final float FOLD = 0.7f;
    private static final float SETTLE = 0.2f;
    /** How thin a brick squeezes as it turns edge-on, as a share of its depth. */
    private static final float SQUEEZE = 0.85f;
    /** Rings an arch can have; closing starts at the outside. */
    private static final int OUTER_RING = 4;

    public static final class State extends BlockEntityRenderState {
        boolean open;
        /** Seconds into this brick's own part; below zero it has not started. */
        float local;
        boolean resting;
        float foldX;
        float foldY;
        float foldZ;
        float slide;
        float normalX;
        float normalZ;
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
        state.foldX = brick.outX();
        state.foldY = brick.outY();
        state.foldZ = brick.outZ();
        state.slide = brick.slide();
        state.normalX = brick.normalX();
        state.normalZ = brick.normalZ();
        state.seed = (brick.getBlockPos().hashCode() & 0xFF) / 255f * Mth.TWO_PI;
        // Lit like the wall's faces, from the air in front of it: the light inside a solid wall is near nothing.
        if (level != null) {
            int sky = 0;
            int block = 0;
            for (Direction side : Direction.values()) {
                int packed = LevelRenderer.getLightColor(level, brick.getBlockPos().relative(side));
                sky = Math.max(sky, LightTexture.sky(packed));
                block = Math.max(block, LightTexture.block(packed));
            }
            state.lightCoords = LightTexture.pack(block, sky);
        }
    }

    @Override
    public void submit(@NonNull State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        float fold = 0f;                                   // 0 in the wall, 1 folded away into the side
        float shiver = 0f;
        if (state.resting) {
            if (state.open) {
                return;
            }
        } else if (state.open) {
            if (state.local >= WRIGGLE + FOLD) {
                return;                                    // folded into the side: the arch is open here
            }
            if (state.local > 0f && state.local < WRIGGLE) {
                shiver = 1f - state.local / WRIGGLE * 0.4f;
            } else if (state.local >= WRIGGLE) {
                fold = ease((state.local - WRIGGLE) / FOLD);
                shiver = 0.5f * (1f - fold);
            }
        } else {
            if (state.local <= 0f) {
                return;                                    // not back yet: still open here
            }
            if (state.local < FOLD) {
                fold = 1f - ease(state.local / FOLD);
            } else if (state.local < FOLD + SETTLE) {
                shiver = 1f - (state.local - FOLD) / SETTLE;
            }
        }
        if (fold >= 0.995f) {
            return;
        }
        float time = state.local + state.seed;
        poseStack.pushPose();
        // Slide toward the side, turning about the line in the wall square to the slide, like a card turning edge-on.
        float travel = fold * state.slide;
        poseStack.translate(0.5f + state.foldX * travel, 0.5f + state.foldY * travel, 0.5f + state.foldZ * travel);
        Vector3f axis = new Vector3f(state.foldX, state.foldY, state.foldZ).cross(state.normalX, 0f, state.normalZ);
        if (fold > 0f && axis.lengthSquared() > 1.0e-4f) {
            axis.normalize();
            poseStack.mulPose(new Quaternionf().rotateAxis(fold * Mth.HALF_PI, axis.x, axis.y, axis.z));
        }
        if (shiver > 0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 45f) * 5f * shiver));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 38f + 1.3f) * 5f * shiver));
            poseStack.translate(Mth.sin(time * 51f) * 0.02f * shiver, 0f, Mth.cos(time * 47f) * 0.02f * shiver);
        }
        // Squeezed thin along its slide as it folds, so it tucks into the side rather than poking out of it.
        float squeeze = 1f - SQUEEZE * fold;
        poseStack.scale(Math.abs(state.foldX) > 0.5f ? squeeze : 1f, Math.abs(state.foldY) > 0.5f ? squeeze : 1f,
                Math.abs(state.foldZ) > 0.5f ? squeeze : 1f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        collector.submitBlock(poseStack, DiagonAlleyBlocks.DIAGON_BRICK.base().get().defaultBlockState(),
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static float ease(float f) {
        f = Mth.clamp(f, 0f, 1f);
        return f * f * (3f - 2f * f);
    }
}
