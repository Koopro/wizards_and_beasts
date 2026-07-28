package at.koopro.wizardsandbeasts.client.camera;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
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
    private static final float DURATION_TICKS = 6.0F;

    private static float amplitude;
    private static float elapsedTicks;
    /** Per-impact axis weights, so consecutive hits do not shake along the same line. */
    private static float yawDirection = 1.0F;
    private static float pitchDirection = 1.0F;

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
        // A fixed axis pairing makes every impact shake the same way, which the eye learns after
        // about three casts and then reads as a glitch. Randomising the weights keeps each hit
        // feeling like its own event; pitch stays the weaker axis because vertical camera movement
        // is the part players notice as nausea.
        float angle = (float) (mc.level != null ? mc.level.random.nextFloat() : 0.5f) * Mth.TWO_PI;
        yawDirection = Mth.cos(angle);
        pitchDirection = Mth.sin(angle) * 0.6F;
    }

    /** Clears any running shake — used when the world changes out from under the camera. */
    public static void reset() {
        amplitude = 0.0F;
        elapsedTicks = 0.0F;
        yawDirection = 1.0F;
        pitchDirection = 1.0F;
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
        event.setYaw(event.getYaw()
                + ScreenShakeMath.offset(current, time, 1.0F, yawDirection));
        event.setPitch(event.getPitch()
                + ScreenShakeMath.offset(current, time, ScreenShakeMath.PITCH_FREQUENCY_RATIO,
                        pitchDirection));
    }
}
