package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Falling and landing.
 *
 * <p>Vanilla draws a player falling from a tower exactly as one standing still: arms at the sides, legs together.
 * Past a short drop the arms now go up and out and flail a little, the legs part, the head looks down; and a hard
 * landing bends the knees and the waist for a moment, deeper for a longer fall.
 *
 * <p>Purely client-side and derived: every client sees the same positions and ground flag for every player, so there
 * is nothing to send. The vertical speed comes from the entity's own position change, put on the render state by
 * {@link #registerModifiers} (a remote player's motion vector is not kept up to date on the client, its position is).
 *
 * <p>Locomotion band, under the cast: a wizard can cast while falling, and the cast arm reads over the flail.
 */
@NullMarked
public final class MovementPosePass extends ProceduralPosePass {

    public static final int PRIORITY = 110;

    /** Vertical speed and ground contact, per frame. Blocks per tick; negative is down. */
    public record Motion(float verticalSpeed, boolean onGround, boolean exempt) {}

    public static final ContextKey<Motion> MOTION =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pose_motion"));

    /** Falling faster than this is a fall rather than a hop or a step down (a jump peaks near 0.42 up). */
    static final float FALL_FROM = 0.55f;
    /** The flail is full by this speed. */
    static final float FALL_FULL = 0.95f;
    /** Ticks the flail takes to come on and to let go once grounded. */
    static final int FALL_RISE = 6;
    static final int FALL_DROP = 2;
    /** The landing: ticks it lasts, and its deepest bend at full intensity. */
    static final float LAND_TICKS = 9f;
    static final float LAND_LEAN = 0.42f;

    private final KindFade<Fall> fade = new KindFade<>(FALL_RISE, FALL_DROP);
    private final Map<Integer, Landing> landings = new HashMap<>();

    private enum Fall { FALLING }

    private static final class Landing {
        boolean airborne;
        float peak;
        double landedAt = Double.NEGATIVE_INFINITY;
        float intensity;
    }

    public MovementPosePass() {
        super("movement", PRIORITY);
    }

    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class, (entity, renderState) -> {
            if (entity instanceof Player player && renderState instanceof LivingEntityRenderState state) {
                boolean exempt = player.isPassenger() || player.isFallFlying() || player.isInWater()
                        || player.onClimbable() || player.isSleeping()
                        || (player.getAbilities().flying);
                state.setRenderData(MOTION, new Motion((float) (player.getY() - player.yo), player.onGround(), exempt));
            }
        });
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        if (context.firstPerson().firstPerson()) {
            return;
        }
        AvatarRenderState state = context.state();
        Motion motion = state.getRenderData(MOTION);
        if (motion == null) {
            return;
        }
        double time = ClientCastAnimationState.clientTick() + context.partialTicks();
        float speed = -motion.verticalSpeed();
        boolean falling = !motion.exempt() && !motion.onGround() && speed > FALL_FROM;

        Landing landing = landings.computeIfAbsent(state.id, id -> new Landing());
        if (!motion.onGround() && !motion.exempt()) {
            landing.airborne = true;
            landing.peak = Math.max(landing.peak, speed);
        } else if (landing.airborne) {
            landing.airborne = false;
            if (motion.onGround() && landing.peak > FALL_FROM) {
                landing.landedAt = time;
                landing.intensity = Mth.clamp((landing.peak - FALL_FROM) / (FALL_FULL - FALL_FROM), 0.25f, 1f);
            }
            landing.peak = 0f;
        }

        KindFade.Shares<Fall> shares = fade.observe(state.id, falling ? Fall.FALLING : null, time);
        float fallShare = shares.current() == Fall.FALLING ? shares.weight() : shares.fall();
        if (fallShare > 0f) {
            float depth = Mth.clamp((Math.max(speed, landing.peak) - FALL_FROM) / (FALL_FULL - FALL_FROM), 0.7f, 1f);
            builder.opWeight(fallShare * depth);
            poseFall(builder, state.ageInTicks);
            builder.opWeight(1f);
        }

        float since = (float) (time - landing.landedAt);
        if (since >= 0f && since < LAND_TICKS) {
            float bend = landBend(since) * landing.intensity;
            if (bend > 0f) {
                poseLanding(builder, bend);
            }
        }
    }

    /** Arms up and out with a flail, legs apart, head down to the ground coming up. */
    private static void poseFall(PoseBuilder builder, float age) {
        float flail = Mth.sin(age * 0.9f) * 9f;
        builder.get(PlayerModelPart.RIGHT_ARM)
                .setRotDeg(PoseTarget.X_ROT, -150f + flail)
                .setRotDeg(PoseTarget.Y_ROT, 0f)
                .setRotDeg(PoseTarget.Z_ROT, 28f + flail * 0.5f);
        builder.get(PlayerModelPart.LEFT_ARM)
                .setRotDeg(PoseTarget.X_ROT, -150f - flail)
                .setRotDeg(PoseTarget.Y_ROT, 0f)
                .setRotDeg(PoseTarget.Z_ROT, -28f + flail * 0.5f);
        builder.get(PlayerModelPart.RIGHT_LEG)
                .setRotDeg(PoseTarget.X_ROT, -14f - flail * 0.6f)
                .setRotDeg(PoseTarget.Z_ROT, 9f);
        builder.get(PlayerModelPart.LEFT_LEG)
                .setRotDeg(PoseTarget.X_ROT, 10f + flail * 0.6f)
                .setRotDeg(PoseTarget.Z_ROT, -9f);
        builder.get(PlayerModelPart.HEAD).addRotDeg(PoseTarget.X_ROT, 14f);
    }

    /** Knees and waist taking the landing: arms forward for balance, the body bent, legs pushed back. */
    private static void poseLanding(PoseBuilder builder, float bend) {
        builder.opWeight(bend);
        builder.get(PlayerModelPart.RIGHT_ARM).setRotDeg(PoseTarget.X_ROT, -40f).setRotDeg(PoseTarget.Z_ROT, 12f);
        builder.get(PlayerModelPart.LEFT_ARM).setRotDeg(PoseTarget.X_ROT, -40f).setRotDeg(PoseTarget.Z_ROT, -12f);
        builder.opWeight(1f);
        GesturePosePass.lean(builder, LAND_LEAN * bend);
    }

    /** 0..1..0 over the landing: a fast drop into the bend, a slower rise out of it. */
    static float landBend(float since) {
        if (since < 0f || since >= LAND_TICKS) {
            return 0f;
        }
        float down = 2f;
        return since < down
                ? ClientCastAnimationState.smoothstep(since / down)
                : 1f - ClientCastAnimationState.smoothstep((since - down) / (LAND_TICKS - down));
    }

    public void sweep(double time) {
        fade.sweep(time);
        landings.entrySet().removeIf(e -> !e.getValue().airborne && time - e.getValue().landedAt > 200.0);
    }

    public void clear() {
        fade.clear();
        landings.clear();
    }
}
