package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.IdentityHashMap;
import java.util.Map;

public class BroomRiderRenderer {

    public record BroomTiltData(float roll, float lean, float pitchTilt) {
    }

    private static final Map<Object, BroomTiltData> TILT_MAP = new IdentityHashMap<>();

    public static BroomTiltData getTilt(LivingEntityRenderState state) {
        return TILT_MAP.get(state);
    }

    public static void removeTilt(LivingEntityRenderState state) {
        TILT_MAP.remove(state);
    }

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class) LivingEntityRenderer.class,
                (entity, renderState) -> {
                    if (entity instanceof LivingEntity living
                            && living.getVehicle() instanceof BroomEntity broom
                            && renderState instanceof LivingEntityRenderState) {
                        float pt = Minecraft.getInstance()
                                .getDeltaTracker().getGameTimeDeltaPartialTick(false);
                        TILT_MAP.put(renderState, new BroomTiltData(
                                Mth.lerp(pt, broom.getPrevRollTilt(), broom.getRollTilt()),
                                Mth.lerp(pt, broom.getPrevForwardLean(), broom.getForwardLean()),
                                Mth.lerp(pt, broom.getPrevPitchTilt(), broom.getPitchTilt())
                        ));
                    }
                });
    }
}
