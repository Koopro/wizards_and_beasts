package at.koopro.wizardsandbeasts.client.furniture;

import at.koopro.wizardsandbeasts.block.furniture.FurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.MovingPropBlock;
import at.koopro.wizardsandbeasts.block.furniture.MovingPropBlockEntity;
import at.koopro.wizardsandbeasts.block.furniture.PropPartBlock;
import at.koopro.wizardsandbeasts.registry.FurnitureBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Draws the moving part of a clock, a globe or a floating book; the still part is the block's own baked model.
 *
 * <p>Every part is a {@link PropPartBlock} state, a plain JSON block model, so this is a few pose-stack turns and a
 * {@code submitBlock}: no model loading, no per-frame geometry. Nothing ticks; the time comes from the level. Parts
 * are authored in the block frame front to the north (pivots in {@code tools/furniture_models.py}), so the pose is
 * first turned like the blockstate's {@code y}.
 */
public final class MovingPropRenderer implements BlockEntityRenderer<MovingPropBlockEntity, MovingPropRenderer.State> {

    /** Moving parts beyond this are not drawn: a clock's hands are not legible further off anyway. */
    private static final int VIEW_DISTANCE = 32;
    /** Pivot of the clock hands (block units): the dial's centre, as the tool draws it. */
    private static final float CLOCK_X = 8f, CLOCK_Y = 8f;
    /** Centre of the globe's sphere above its stand, and its tilt. */
    private static final float GLOBE_X = 8f, GLOBE_Y = 9.5f, GLOBE_Z = 8f, GLOBE_TILT = 23.5f;
    /** Seconds per turn of the globe; seconds between two page turns of a floating book. */
    private static final float GLOBE_TURN = 24f, PAGE_EVERY = 5f, PAGE_TURN = 1.4f;
    /** Height of the book's spine hinge (block units): a turning page swings about the line x = 8 at this height. */
    private static final float PAGE_HINGE_Y = 9f;

    public static final class State extends BlockEntityRenderState {
        MovingPropBlock.@Nullable Kind kind;
        float facingDegrees;
        float seconds;
        long dayTime;
        float phase;
    }

    @Override
    public @NonNull State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(@NonNull MovingPropBlockEntity prop, @NonNull State state, float partialTick,
                                   @NonNull Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(prop, state, partialTick, cameraPos, breakProgress);
        BlockState block = prop.getBlockState();
        state.kind = block.getBlock() instanceof MovingPropBlock moving ? moving.kind() : null;
        Direction facing = block.getValueOrElse(FurnitureBlock.FACING, Direction.NORTH);
        state.facingDegrees = (facing.toYRot() + 180f) % 360f;   // the blockstate's y for this facing
        Level level = prop.getLevel();
        state.seconds = level == null ? 0f : (level.getGameTime() % 240_000L + partialTick) / 20f;
        state.dayTime = level == null ? 6000L : level.getDayTime();
        state.phase = (prop.getBlockPos().hashCode() & 0xFF) / 255f;
    }

    @Override
    public void submit(@NonNull State state, @NonNull PoseStack pose, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        if (state.kind == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facingDegrees));
        pose.translate(-0.5f, -0.5f, -0.5f);
        switch (state.kind) {
            case CLOCK -> clock(state, pose, collector);
            case GLOBE -> globe(state, pose, collector);
            case BOOK -> book(state, pose, collector);
        }
        pose.popPose();
    }

    /** Minecraft's day starts at six in the morning; the hour hand goes round twice a day, the minute hand 24 times. */
    private static void clock(State state, PoseStack pose, SubmitNodeCollector collector) {
        float day = Math.floorMod(state.dayTime, 24_000L) / 1000f;
        float hours = (day + 6f) % 12f;
        float minutes = day % 1f;
        hand(state, pose, collector, PropPartBlock.Part.CLOCK_HOUR, hours / 12f * 360f);
        hand(state, pose, collector, PropPartBlock.Part.CLOCK_MINUTE, minutes * 360f);
    }

    /** Turned about the dial's normal; a positive turn about +z reads clockwise from the front (north). */
    private static void hand(State state, PoseStack pose, SubmitNodeCollector collector, PropPartBlock.Part part,
                             float degrees) {
        pose.pushPose();
        pose.translate(CLOCK_X / 16f, CLOCK_Y / 16f, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        pose.translate(-CLOCK_X / 16f, -CLOCK_Y / 16f, 0f);
        part(state, pose, collector, part);
        pose.popPose();
    }

    private static void globe(State state, PoseStack pose, SubmitNodeCollector collector) {
        pose.translate(GLOBE_X / 16f, GLOBE_Y / 16f, GLOBE_Z / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(GLOBE_TILT));
        pose.mulPose(Axis.YP.rotationDegrees((state.seconds / GLOBE_TURN + state.phase) * 360f));
        pose.translate(-0.5f, -0.5f, -0.5f);
        part(state, pose, collector, PropPartBlock.Part.GLOBE);
    }

    /** Hovers and turns slowly; every few seconds a page lifts off the right-hand stack and settles on the left. */
    private static void book(State state, PoseStack pose, SubmitNodeCollector collector) {
        float t = state.seconds + state.phase * 10f;
        pose.translate(0.5f, 0.5f + Mth.sin(t * 1.3f) * 0.05f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(t * 12f));
        pose.mulPose(Axis.XP.rotationDegrees(-18f + Mth.sin(t * 0.9f) * 4f));
        pose.translate(-0.5f, -0.5f, -0.5f);
        part(state, pose, collector, PropPartBlock.Part.BOOK);
        float cycle = t % PAGE_EVERY;
        if (cycle < PAGE_TURN) {
            float f = cycle / PAGE_TURN;
            float eased = f * f * (3f - 2f * f);
            pose.translate(0.5f, PAGE_HINGE_Y / 16f, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(eased * 172f));
            pose.translate(-0.5f, -PAGE_HINGE_Y / 16f, 0f);
            part(state, pose, collector, PropPartBlock.Part.BOOK_PAGE);
        }
    }

    private static void part(State state, PoseStack pose, SubmitNodeCollector collector, PropPartBlock.Part part) {
        collector.submitBlock(pose, FurnitureBlocks.PROP_PART.get().defaultBlockState().setValue(PropPartBlock.PART, part),
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
