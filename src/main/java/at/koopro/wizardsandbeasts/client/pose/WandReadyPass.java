package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.broom.BroomRiderRenderer;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * The wand at the ready: a witch or wizard with a wand drawn carries it, not dangles it.
 *
 * <p>Vanilla holds any item with the arm hanging and the item jutting forward at a right angle, so a drawn wand read
 * as a stick carried to the shed. This lifts the wand arm a little forward and in, with a slow breath in it, and keeps
 * part of vanilla's walk swing so the arm still belongs to a walking body. Drawing the wand plays a short raise that
 * overshoots and settles; putting it away lets go a little faster.
 *
 * <p>Posture band, under everything that does something with the wand: the item holds, the gestures and the cast all
 * blend <em>from</em> this hold, so a cast now starts from a raised wand instead of from a hanging arm.
 *
 * <p>Third person. In first person vanilla's own equip animation already raises the item into view.
 */
@NullMarked
public final class WandReadyPass extends ProceduralPosePass {

    public static final int PRIORITY = 60;

    private enum Hold { READY }

    /** The draw: rising into the ready hold. Long enough for the overshoot to read. */
    static final int DRAW_TICKS = 7;
    static final int SHEATHE_TICKS = 4;

    /** Ready hold, degrees, right-handed. Inward turn is negative {@code yRot}. */
    static final float READY_X = -26f;
    static final float READY_Y = -9f;
    /** Share of vanilla's arm left under the hold, so the walk swing is damped rather than frozen. */
    static final float HOLD_SHARE = 0.72f;
    /** Breath: degrees and radians per tick. */
    static final float BREATH_DEG = 1.4f;
    static final float BREATH_RATE = 0.075f;
    /** Extra lift at the top of the draw. */
    static final float DRAW_OVERSHOOT_DEG = 34f;
    /** Walk speed (vanilla's walkAnimationSpeed) from which the hold gives way to the running swing. */
    static final float RUN_FROM = 0.55f;
    static final float RUN_SHARE = 0.3f;
    /**
     * Share of the wand's cast tilt the ready hold asks for: enough to bring the point level with the ground once the
     * arm is lifted, instead of jutting up at the sky.
     */
    public static final float READY_WAND_TILT = 0.28f;

    private final KindFade<Hold> fade = new KindFade<>(DRAW_TICKS, SHEATHE_TICKS);

    /** Last frame's share per entity, read by the wand renderer for its tilt (one frame late, by design). */
    private static final Map<Integer, Float> SHOWN = new HashMap<>();

    public WandReadyPass() {
        super("wand_ready", PRIORITY);
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        if (context.firstPerson().firstPerson()) {
            return;
        }
        AvatarRenderState state = context.state();
        double time = ClientCastAnimationState.clientTick() + context.partialTicks();
        KindFade.Shares<Hold> shares = fade.observe(state.id, ready(state) ? Hold.READY : null, time);
        float weight = shares.current() == Hold.READY ? shares.weight() : shares.fall();
        if (weight <= 0f) {
            SHOWN.remove(state.id);
            return;
        }
        boolean drawing = shares.current() == Hold.READY && weight < 1f;
        // The overshoot only on the way up: a sheathe just lowers.
        float overshoot = drawing ? Mth.sin(weight * Mth.PI) * DRAW_OVERSHOOT_DEG : 0f;
        // A swing (an attack, a placed block) owns the arm while it lasts.
        float swing = state.attackTime > 0f ? Mth.sin(state.attackTime * Mth.PI) : 0f;
        float running = Mth.clamp((state.walkAnimationSpeed - RUN_FROM) / (1f - RUN_FROM), 0f, 1f);
        float share = weight * (1f - swing) * Mth.lerp(running, 1f, RUN_SHARE);
        SHOWN.put(state.id, share);
        if (share <= 0f) {
            return;
        }

        HumanoidArm arm = state.mainArm;
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        PlayerModelPart part = arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_ARM : PlayerModelPart.LEFT_ARM;
        float breath = Mth.sin(state.ageInTicks * BREATH_RATE) * BREATH_DEG;
        builder.get(part)
                .multiplier(share * HOLD_SHARE)
                .setRotDeg(PoseTarget.X_ROT, READY_X - overshoot + breath)
                .setRotDeg(PoseTarget.Y_ROT, READY_Y * side)
                // The idle bob writes zRot; a held wand does not tremble with it.
                .setRotDeg(PoseTarget.Z_ROT, 0f);
    }

    /** Whether the player is carrying a drawn wand at rest. */
    static boolean ready(AvatarRenderState state) {
        return state.getMainHandItemStack().getItem() instanceof WandItem
                && !state.isUsingItem
                && !state.isFallFlying
                && !state.isVisuallySwimming
                && state.swimAmount <= 0f
                && state.bedOrientation == null
                && BroomRiderRenderer.getRide(state) == null;
    }

    /** The ready hold's share for an entity, last frame; zero when it is not carrying a wand at rest. */
    public static float shown(int entityId) {
        return SHOWN.getOrDefault(entityId, 0f);
    }

    public void sweep(double time) {
        fade.sweep(time);
    }

    public static void clear() {
        SHOWN.clear();
    }
}
