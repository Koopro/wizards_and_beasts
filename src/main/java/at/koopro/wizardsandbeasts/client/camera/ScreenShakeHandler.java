package at.koopro.wizardsandbeasts.client.camera;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Camera kick on spell impact.
 *
 * <p><b>Costs nothing on the wire.</b> {@code SpellImpactBurstS2CPayload} already reaches every client
 * tracking an impact and already carries the position and particle count, so the shake is derived
 * entirely on the client from data that was being sent anyway. No new payload, no protocol change, and
 * every existing impact call site gains a kick without being touched.
 *
 * <p><b>Conservative by construction.</b> Only yaw and pitch are nudged and only while a shake is
 * live; once {@link ScreenShakeMath#decay} returns zero this stops writing to the viewport event
 * entirely, so a resting camera is exactly vanilla's. A single shake slot is kept rather than a list:
 * overlapping impacts take the stronger of the two, which means a firefight cannot stack itself into
 * an unplayable screen.
 *
 * <p>The arithmetic lives in {@link ScreenShakeMath} so the caps, the distance falloff and the return
 * to zero can be tested without a render context, mirroring {@code BroomCameraMath}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class ScreenShakeHandler {

    /** Ticks a single impact shake runs for. Short: this is a jolt, not a rumble. */
    private static final float DURATION_TICKS = 7.0F;
    /** Pitch runs a quarter-cycle behind yaw so the view arcs instead of sliding on one diagonal. */
    private static final float PITCH_PHASE = 1.57F;

    private static float amplitude;
    private static float elapsedTicks;

    private ScreenShakeHandler() {}

    /**
     * Registers an impact felt at {@code pos}, sized by the burst's particle count.
     *
     * <p>Called from the client handler for {@code SpellImpactBurstS2CPayload}.
     */
    public static void impact(Vec3 pos, int particleCount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        float peak = ScreenShakeMath.amplitude(particleCount, mc.player.position().distanceTo(pos));
        if (peak <= 0.0F) {
            return;
        }
        // Take the stronger of the running shake and the new one rather than summing: a burst of
        // impacts should not compound into a view the player cannot aim through.
        float current = ScreenShakeMath.decay(amplitude, elapsedTicks, DURATION_TICKS);
        if (peak <= current) {
            return;
        }
        amplitude = peak;
        elapsedTicks = 0.0F;
    }

    /** Clears any running shake — used when the world changes out from under the camera. */
    public static void reset() {
        amplitude = 0.0F;
        elapsedTicks = 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (amplitude <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        elapsedTicks += 1.0F;
        if (ScreenShakeMath.decay(amplitude, elapsedTicks, DURATION_TICKS) <= 0.0F) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (amplitude <= 0.0F) {
            return;
        }
        float time = elapsedTicks + (float) event.getPartialTick();
        float current = ScreenShakeMath.decay(amplitude, time, DURATION_TICKS);
        if (current <= 0.0F) {
            return;
        }
        event.setYaw(event.getYaw() + ScreenShakeMath.offset(current, time, 0.0F));
        event.setPitch(event.getPitch() + ScreenShakeMath.offset(current, time, PITCH_PHASE));
    }
}
