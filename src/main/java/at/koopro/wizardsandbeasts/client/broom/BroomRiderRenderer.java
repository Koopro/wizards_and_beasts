package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Attaches the ridden broom's interpolated tilt to the rider's render state, for
 * {@link BroomRiderRenderHandler} (which banks the whole body) and {@link BroomRiderPoseHandler}
 * (which poses the limbs).
 *
 * <p>The data rides on the render state itself via {@link EntityRenderState#setRenderData}, not in a
 * side map keyed by state. {@code EntityRenderer.createRenderState} allocates a fresh state every
 * frame, so a state with no entry is simply a rider who is not on a broom — there is nothing to
 * clear afterwards, and no way for an early-returning render pass to strand an entry.
 */
@NullMarked
public final class BroomRiderRenderer {

    public record BroomRideData(float roll, float forwardLean, float pitchTilt) {}

    /** Present on the render state of a player riding a broom; absent otherwise. */
    public static final ContextKey<BroomRideData> BROOM_RIDE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom_ride"));

    private BroomRiderRenderer() {}

    public static @Nullable BroomRideData getRide(EntityRenderState state) {
        return state.getRenderData(BROOM_RIDE);
    }

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class) LivingEntityRenderer.class,
                (entity, renderState) -> {
                    if (entity instanceof LivingEntity living
                            && living.getVehicle() instanceof BroomEntity broom
                            && renderState instanceof LivingEntityRenderState state) {
                        float pt = Minecraft.getInstance()
                                .getDeltaTracker().getGameTimeDeltaPartialTick(false);
                        state.setRenderData(BROOM_RIDE, new BroomRideData(
                                Mth.lerp(pt, broom.getPrevRollTilt(), broom.getRollTilt()),
                                Mth.lerp(pt, broom.getPrevForwardLean(), broom.getForwardLean()),
                                Mth.lerp(pt, broom.getPrevPitchTilt(), broom.getPitchTilt())
                        ));
                    }
                });
    }
}
