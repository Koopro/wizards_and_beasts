package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side wet/shake state for the Animagus dog (wolf) form — purely cosmetic.
 * <p>
 * While the dog stands in water or rain its coat darkens ({@link #wetShade}); the moment it
 * steps out it performs the vanilla wolf shake — a body-roll wiggle (driven by
 * {@link net.minecraft.client.renderer.entity.state.WolfRenderState#shakeAnim}) plus a burst of
 * water droplets — and dries off. Ticked once per client tick for every visible dog-form player.
 */
public final class WetShakeTracker {

    /** Matches the {@code 1.8} divisor in {@code WolfRenderState.getBodyRollAngle}; a full shake ends here. */
    private static final float SHAKE_MAX = 2.0f;
    private static final float SHAKE_SPEED = 0.08f;
    private static final float SOAKED_SHADE = 0.75f;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private WetShakeTracker() {}

    private static final class State {
        boolean wasWet;
        boolean shaking;
        float shakeAnim;
        float wetAmount; // 1 = soaked, 0 = dry
    }

    public static void tick(Level level) {
        STATES.keySet().removeIf(uuid -> level.getPlayerByUUID(uuid) == null);

        for (Player player : level.players()) {
            if (!isDogForm(player)) {
                STATES.remove(player.getUUID());
                continue;
            }
            State s = STATES.computeIfAbsent(player.getUUID(), u -> new State());
            boolean wet = player.isInWaterOrRain();

            if (wet) {
                s.wetAmount = 1.0f;
                s.shaking = false;
                s.shakeAnim = 0.0f;
            } else if (s.wasWet && s.wetAmount > 0.0f && !s.shaking) {
                // Just left the water — kick off a shake.
                s.shaking = true;
                s.shakeAnim = 0.0f;
            }

            if (s.shaking) {
                s.shakeAnim += SHAKE_SPEED;
                spawnDroplets(level, player);
                s.wetAmount = Math.max(0.0f, 1.0f - s.shakeAnim / SHAKE_MAX);
                if (s.shakeAnim >= SHAKE_MAX) {
                    s.shaking = false;
                    s.shakeAnim = 0.0f;
                    s.wetAmount = 0.0f;
                }
            }
            s.wasWet = wet;
        }
    }

    /** Current shake progress (0 when not shaking), fed to {@code WolfRenderState.shakeAnim}. */
    public static float shakeAnim(UUID uuid) {
        State s = STATES.get(uuid);
        return s != null ? s.shakeAnim : 0.0f;
    }

    /** Coat-darkening multiplier: 1.0 dry, down to {@value #SOAKED_SHADE} when fully soaked. */
    public static float wetShade(UUID uuid) {
        State s = STATES.get(uuid);
        if (s == null) return 1.0f;
        return 1.0f - (1.0f - SOAKED_SHADE) * s.wetAmount;
    }

    private static void spawnDroplets(Level level, Player player) {
        var rnd = level.random;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.SPLASH,
                    player.getX() + (rnd.nextDouble() - 0.5) * player.getBbWidth(),
                    player.getY() + 0.3 + rnd.nextDouble() * 0.3,
                    player.getZ() + (rnd.nextDouble() - 0.5) * player.getBbWidth(),
                    (rnd.nextDouble() - 0.5) * 0.3, 0.1, (rnd.nextDouble() - 0.5) * 0.3);
        }
    }

    private static boolean isDogForm(Player player) {
        ClientFormDataState.FormData data = ClientFormDataState.get(player.getUUID());
        return data != null && "animagus_dog".equals(data.formId());
    }
}
