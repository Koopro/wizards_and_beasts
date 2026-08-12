package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The pose stack: a priority-ordered list of passes, run once per render, blended onto the model.
 *
 * <p>Client-side singleton. Passes are registered once at client setup and never conditionally —
 * {@link Module#PLAYER_ANIMATION} gates whether the layer <em>runs</em>, never whether a pass
 * exists, so switching the module off leaves nothing half-registered to go wrong on the way back.
 *
 * <p><b>No global mutable state.</b> Every per-frame value travels in a {@link PoseContext}. The
 * accumulators here are instance fields reused between frames purely to avoid allocation, and they
 * are cleared at the top of every run rather than trusted to be empty.
 */
@NullMarked
public final class PlayerPoseLayer {

    private static final PlayerPoseLayer INSTANCE = new PlayerPoseLayer();

    public static PlayerPoseLayer get() {
        return INSTANCE;
    }

    private final List<PosePass> passes = new ArrayList<>();

    /** Reused per frame. Cleared on entry to {@link #run}, never assumed clean. */
    private final PoseBuilder builder = new PoseBuilder();
    private final Map<PlayerModelPart, PoseStackResult> stackResults = new EnumMap<>(PlayerModelPart.class);

    private PlayerPoseLayer() {}

    /**
     * Registers a pass and re-sorts.
     *
     * @throws IllegalArgumentException if the priority falls in no {@link PoseBand} — a hard error
     *         by ruling, because a pass with a nonsense priority still runs, just somewhere
     *         arbitrary, and surfaces later as a pose nobody can trace to a number
     */
    public void register(PosePass pass) {
        PoseBand.require(pass.priority(), pass.name());
        passes.add(pass);
        passes.sort(Comparator.comparingInt(PosePass::priority));
    }

    public List<PosePass> passes() {
        return List.copyOf(passes);
    }

    /** True when the module allows the layer to pose anything. PREVIEW counts as enabled. */
    public boolean active() {
        return ModuleManager.isEnabled(Module.PLAYER_ANIMATION);
    }

    /**
     * Runs every pass and applies the result to the model.
     *
     * <p>Called from the existing {@code PlayerModelMixin} delegate chain, after vanilla has
     * finished posing — see schema §3.7. Vanilla's own {@code setupAnim} is the reset: it rewrites
     * every part from the model's initial pose each frame, so the layer starts from a clean pose
     * without having to restore anything itself.
     *
     * @return the whole-avatar transform, or null when nothing addressed {@link PlayerModelPart#BODY}
     */
    public @Nullable PoseStackResult run(PlayerModel model, AvatarRenderState state,
                                         FirstPersonContext firstPerson, float partialTicks) {
        if (!active() || passes.isEmpty()) {
            return null;
        }

        PoseContext context = new PoseContext(model, state, firstPerson, partialTicks);
        stackResults.clear();

        for (PosePass pass : passes) {
            builder.clear();
            pass.pose(builder, context);
            if (builder.isEmpty()) {
                continue;
            }
            for (Map.Entry<PlayerModelPart, PartPoseData> entry : builder.parts().entrySet()) {
                applyPart(model, entry.getKey(), entry.getValue());
            }
        }

        PlayerModelPart.copyOverlays(model);
        return stackResults.get(PlayerModelPart.BODY);
    }

    private void applyPart(PlayerModel model, PlayerModelPart part, PartPoseData data) {
        if (data.isEmpty()) {
            return;
        }
        if (part.isVirtual()) {
            applyToStackResult(part, data);
            return;
        }
        ModelPart target = part.resolve(model);
        if (target == null) {
            return;
        }
        applyToModelPart(target, data);
    }

    private void applyToModelPart(ModelPart target, PartPoseData data) {
        float multiplier = data.multiplier();
        for (PartPoseData.Op op : data.ops()) {
            if (op.target().isPostRotation()) {
                // A ModelPart has one translation and one rotation in a fixed order, so there is
                // nowhere for a post-rotation offset to go. Documented no-op rather than a silent
                // approximation that would put the part somewhere subtly wrong.
                PostRotationWarning.once(op.target());
                continue;
            }
            float current = read(target, op.target());
            float initial = initial(target, op.target());
            write(target, op.target(),
                    op.type().apply(op.target(), current, op.value(), initial, multiplier));
        }
    }

    private void applyToStackResult(PlayerModelPart part, PartPoseData data) {
        PoseStackResult result = stackResults.computeIfAbsent(part, p -> new PoseStackResult());
        float multiplier = data.multiplier();
        for (PartPoseData.Op op : data.ops()) {
            float current = result.get(op.target());
            result.set(op.target(),
                    op.type().apply(op.target(), current, op.value(), op.target().identity(), multiplier));
        }
    }

    private static float read(ModelPart part, PoseTarget target) {
        return switch (target) {
            case X -> part.x;
            case Y -> part.y;
            case Z -> part.z;
            case X_ROT -> part.xRot;
            case Y_ROT -> part.yRot;
            case Z_ROT -> part.zRot;
            case X_SCALE -> part.xScale;
            case Y_SCALE -> part.yScale;
            case Z_SCALE -> part.zScale;
            case X2, Y2, Z2 -> target.identity();
        };
    }

    private static void write(ModelPart part, PoseTarget target, float value) {
        switch (target) {
            case X -> part.x = value;
            case Y -> part.y = value;
            case Z -> part.z = value;
            case X_ROT -> part.xRot = value;
            case Y_ROT -> part.yRot = value;
            case Z_ROT -> part.zRot = value;
            case X_SCALE -> part.xScale = value;
            case Y_SCALE -> part.yScale = value;
            case X2, Y2, Z2 -> { /* unreachable: filtered above */ }
        }
    }

    /**
     * The part's pose as vanilla left it this frame.
     *
     * <p>{@code RESET} blends toward what {@code setupAnim} produced, not toward the model's
     * authored rest pose. That is the useful meaning: "hand this limb back to vanilla" during a
     * blend-out, so a pose releasing mid-stride returns to the walk cycle rather than snapping to a
     * T-pose and then back into the walk.
     */
    private static float initial(ModelPart part, PoseTarget target) {
        var initial = part.getInitialPose();
        return switch (target) {
            case X -> initial.x();
            case Y -> initial.y();
            case Z -> initial.z();
            case X_ROT -> initial.xRot();
            case Y_ROT -> initial.yRot();
            case Z_ROT -> initial.zRot();
            case X_SCALE, Y_SCALE, Z_SCALE -> 1.0f;
            case X2, Y2, Z2 -> target.identity();
        };
    }

    /** Debug-level, once per target, so a mistake is findable without spamming a render loop. */
    private static final class PostRotationWarning {
        private static final java.util.Set<PoseTarget> SEEN = java.util.EnumSet.noneOf(PoseTarget.class);
        private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

        static void once(PoseTarget target) {
            if (SEEN.add(target)) {
                LOGGER.debug("[W&B] Pose target {} was requested on a ModelPart, which cannot express a "
                        + "post-rotation offset. Ignored. Use it on BODY or a first-person arm.", target);
            }
        }
    }
}
