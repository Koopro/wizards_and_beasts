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
    /** Vanilla's output per part, captured at layer entry. Reused between frames to avoid churn. */
    private final Map<PlayerModelPart, float[]> captured = new EnumMap<>(PlayerModelPart.class);

    /** The targets a {@code ModelPart} can actually hold, in capture order. */
    private static final PoseTarget[] CAPTURED_TARGETS = {
            PoseTarget.X, PoseTarget.Y, PoseTarget.Z,
            PoseTarget.X_ROT, PoseTarget.Y_ROT, PoseTarget.Z_ROT,
            PoseTarget.X_SCALE, PoseTarget.Y_SCALE, PoseTarget.Z_SCALE,
    };

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
     * finished posing — see schema §3.7. The incoming transforms are captured first, because they
     * are vanilla's output for this frame and {@code RESET} blends back toward them.
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
        captureVanillaPose(model);

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

        return stackResults.get(PlayerModelPart.BODY);
    }

    /**
     * Runs every pass and returns only the whole-avatar transform, touching no {@code ModelPart}.
     *
     * <p>A second entry point because the two halves of a pose are consumed at different moments.
     * {@code setupRotations} runs <em>before</em> {@code setupAnim} — see the call order in
     * {@code LivingEntityRenderer.submit} — and it is the only place the pose stack is in the frame a
     * whole-body pitch needs: yawed to the player's facing, origin at their feet. The limb half
     * cannot be computed there, because vanilla has not posed the model yet and there would be
     * nothing to capture. So the body half is evaluated here and the limb half in {@link #run}.
     *
     * <p>Running the passes twice per frame is safe: {@code pose} is a pure function of the render
     * state, the partial tick and each pass's own tick-advanced timers, none of which this touches.
     * The timers advance on the client tick, never from a render pass, precisely so that a pose
     * evaluated twice in one frame gives the same answer both times.
     */
    public @Nullable PoseStackResult runBody(PlayerModel model, AvatarRenderState state,
                                             float partialTicks) {
        if (!active() || passes.isEmpty()) {
            return null;
        }

        PoseContext context = new PoseContext(model, state, FirstPersonContext.NONE, partialTicks);
        stackResults.clear();

        for (PosePass pass : passes) {
            builder.clear();
            pass.pose(builder, context);
            if (builder.isEmpty()) {
                continue;
            }
            for (Map.Entry<PlayerModelPart, PartPoseData> entry : builder.parts().entrySet()) {
                // Virtual parts only. The limb ops in this same builder are deliberately dropped —
                // they are applied by run(), and writing them here would apply them twice.
                if (entry.getKey().isVirtual()) {
                    applyToStackResult(entry.getKey(), entry.getValue());
                }
            }
        }

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
        applyToModelPart(part, target, data);
    }

    private void applyToModelPart(PlayerModelPart part, ModelPart target, PartPoseData data) {
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
            float captured = capturedValue(part, op.target());
            write(target, op.target(),
                    op.type().apply(op.target(), current, op.value(), captured, multiplier));
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
            case Z_SCALE -> part.zScale = value;
            case X2, Y2, Z2 -> { /* unreachable: filtered above */ }
        }
    }

    /**
     * Records what vanilla left on every part, before any pass has touched it.
     *
     * <p>The layer runs at {@code TAIL} of {@code setupAnim}, so the values sitting on the model at
     * this moment <em>are</em> vanilla's authored output for this frame — the walk cycle, the swing,
     * the crouch. Capturing them is what gives {@code RESET} something worth returning to.
     *
     * <p>The layer deliberately does <b>not</b> reset parts to their initial pose here. Doing that
     * would discard vanilla's animation wholesale on every frame the layer runs, so a pose that
     * touched only the arms would still freeze the legs mid-stride.
     */
    private void captureVanillaPose(PlayerModel model) {
        for (PlayerModelPart part : PlayerModelPart.values()) {
            ModelPart modelPart = part.resolve(model);
            if (modelPart == null) {
                continue;
            }
            float[] row = captured.computeIfAbsent(part, p -> new float[CAPTURED_TARGETS.length]);
            for (int i = 0; i < CAPTURED_TARGETS.length; i++) {
                row[i] = read(modelPart, CAPTURED_TARGETS[i]);
            }
        }
    }

    /**
     * The value vanilla had on this target at layer entry — {@code RESET}'s destination.
     *
     * <p>Not {@code ModelPart.getInitialPose()}: that is the authored rest pose, near a T-pose, so
     * blending toward it during a fade-out snaps the player upright before the next frame's
     * {@code setupAnim} puts them back in the walk cycle. What a blend-out means is "hand this limb
     * back to vanilla mid-stride", which is exactly the captured value.
     */
    private float capturedValue(PlayerModelPart part, PoseTarget target) {
        float[] row = captured.get(part);
        if (row == null) {
            return target.identity();
        }
        for (int i = 0; i < CAPTURED_TARGETS.length; i++) {
            if (CAPTURED_TARGETS[i] == target) {
                return row[i];
            }
        }
        return target.identity();
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
