package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.entity.broom.BroomFlightRules;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Sound, particles and field of view for broom flight — the part of "going fast" that is not motion.
 *
 * <p>Strictly cosmetic and strictly client-side. Nothing here is read by the server, nothing here
 * changes where the broom goes, and every effect reads the same {@link BroomFlightRules#speedRatio}
 * the physics does, so what the player sees and hears agrees with what the broom is doing.
 *
 * <h2>Everything is configurable, and off is a supported setting</h2>
 * {@code broomWindVolume}, {@code broomFovEffect} and {@code broomSpeedParticles} each go to zero or
 * false independently. The FOV knob in particular defaults low and is documented for motion sickness:
 * a field of view that moves on its own is the single most common cause of it, and a player who
 * cannot turn that off simply cannot use the broom.
 *
 * <h2>Allocation</h2>
 * The wind is one gust every {@link #WIND_INTERVAL_TICKS} ticks rather than a looping
 * {@code SoundInstance}, so nothing is retained between ticks and there is no loop to leak when the
 * player dismounts, dies, or switches {@link Module#BROOM_FLIGHT} off mid-flight. Particles are
 * emitted at most twice a tick and only above a speed threshold. The FOV hook allocates nothing.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class BroomFlightFx {

    /** Ticks between wind gusts at full speed. Slow enough to read as wind, not as a stutter. */
    private static final int WIND_INTERVAL_TICKS = 5;
    /** Speed ratio below which there is no wind at all — hovering is quiet. */
    private static final float WIND_THRESHOLD = 0.25f;
    /**
     * Speed ratio below which no slipstream is drawn — unless the boost is firing, which always
     * draws one. A boost that produces no visible change is a boost the player cannot tell fired.
     */
    private static final float PARTICLE_THRESHOLD = 0.4f;
    /** Particles per tick at the threshold, and at the ceiling. The rate is what reads as speed. */
    private static final int PARTICLES_MIN = 1;
    private static final int PARTICLES_MAX = 5;
    /** Extra particles per tick while boosting, on top of whatever the speed already earns. */
    private static final int PARTICLES_BOOST_BONUS = 3;
    /** Spread of the seeded positions, in blocks. Small: a trail, not a cloud. */
    private static final double PARTICLE_JITTER = 0.18;
    /** Per-frame smoothing on the FOV multiplier, so it eases in and out rather than snapping. */
    private static final float FOV_SMOOTHING = 0.08f;

    private static int windCooldown;
    /** Smoothed FOV bonus, kept across frames so dismounting eases back instead of snapping. */
    private static float smoothedFov;
    /** Whether the boost was firing last tick, so the cue is played on the edge and not every tick. */
    private static boolean boostWasFiring;

    private BroomFlightFx() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BroomEntity broom = riddenBroom(mc);
        if (broom == null || mc.player == null || mc.level == null) {
            windCooldown = 0;
            boostWasFiring = false;
            return;
        }
        float ratio = BroomFlightRules.speedRatio(broom.getCurrentSpeed(), broom.getCruiseSpeed());
        BroomDefinition def = broom.resolveDefinition();
        playWind(mc, def, ratio);
        playBoostCue(mc, broom, def);
        spawnSlipstream(mc.player, broom, def, ratio);
    }

    /**
     * One gust whose volume and pitch both climb with speed. Pitch is the part that reads as speed —
     * a louder version of the same note sounds like a bigger broom, not a faster one.
     */
    private static void playWind(Minecraft mc, BroomDefinition def, float speedRatio) {
        if (Config.broomWindVolume <= 0.0f || speedRatio < WIND_THRESHOLD || mc.player == null) {
            return;
        }
        if (windCooldown > 0) {
            windCooldown--;
            return;
        }
        windCooldown = WIND_INTERVAL_TICKS;
        // Remap [threshold, 1] onto [0, 1] so the wind starts from silence at the threshold rather
        // than arriving already a quarter loud.
        float intensity = (speedRatio - WIND_THRESHOLD) / (1.0f - WIND_THRESHOLD);
        // idleLoopSound is the broom's own flight note; the elytra rush is what every broom used
        // to share and is still what an unauthored one gets.
        SoundEvent wind = def.audio().resolveIdleLoopSound().orElse(SoundEvents.ELYTRA_FLYING);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                wind, SoundSource.PLAYERS,
                Config.broomWindVolume * (0.25f + intensity * 0.75f),
                0.7f + intensity * 0.5f,
                false);
    }

    /**
     * One shot when the boost starts, not a loop while it runs.
     *
     * <p>Edge-triggered off the boost's own remaining-ticks counter rather than off the input, so
     * mashing the key while the boost is on cooldown is silent — the cue means "the boost fired",
     * and a cue that plays when nothing happened is worse than none.
     */
    private static void playBoostCue(Minecraft mc, BroomEntity broom, BroomDefinition def) {
        boolean boosting = broom.isBoostFiring();
        boolean started = boosting && !boostWasFiring;
        boostWasFiring = boosting;
        if (!started || mc.player == null || mc.level == null) return;

        def.audio().resolveBoostSound().ifPresent(sound ->
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        sound, SoundSource.PLAYERS, Config.broomWindVolume, 1.0f, false));
    }

    /**
     * The slipstream, shed from the bristle tips rather than from the rider.
     *
     * <p>It used to seed behind the <em>player</em>, offset along the direction of travel, which put
     * the trail inside the rider on anything flying nose-up and detached it entirely from the broom
     * they were sitting on. {@code tailPosition()} is the rig's own {@code fx_tail} anchor, so the
     * trail leaves the twigs.
     *
     * <p>Rate carries the speed, not size or colour: a Firebolt at its ceiling sheds eight particles
     * a tick where a Cleansweep at its own ceiling sheds five, and both are unmistakably faster than
     * the same broom at half throttle. Colour is the broom's identity and stays constant, which is
     * what makes a gold trail read as "a Nimbus" and not as "something going a particular speed".
     */
    private static void spawnSlipstream(LocalPlayer player, BroomEntity broom, BroomDefinition def,
                                        float speedRatio) {
        if (!Config.broomSpeedParticles || player.level() == null) {
            return;
        }
        boolean boosting = broom.isBoostFiring();
        if (speedRatio < PARTICLE_THRESHOLD && !boosting) {
            return;
        }

        // Remap [threshold, 1] onto [0, 1] so the trail starts thin at the threshold rather than
        // arriving already half its full density.
        float intensity = Mth.clamp(
                (speedRatio - PARTICLE_THRESHOLD) / (1.0f - PARTICLE_THRESHOLD), 0.0f, 1.0f);
        int count = PARTICLES_MIN + Math.round(intensity * (PARTICLES_MAX - PARTICLES_MIN));
        if (boosting) {
            count += PARTICLES_BOOST_BONUS;
        }

        Vec3 tail = broom.tailPosition();
        var particle = def.audio().trailParticleOrDefault();
        var random = player.level().random;
        for (int i = 0; i < count; i++) {
            player.level().addParticle(particle,
                    tail.x + (random.nextDouble() - 0.5) * PARTICLE_JITTER,
                    tail.y + (random.nextDouble() - 0.5) * PARTICLE_JITTER,
                    tail.z + (random.nextDouble() - 0.5) * PARTICLE_JITTER,
                    0.0, 0.0, 0.0);
        }
    }

    /**
     * Widens the view a little at speed.
     *
     * <p>Uses {@link ComputeFovModifierEvent}, which multiplies the player's configured FOV rather
     * than replacing it, so a player who flies with FOV 30 or 110 keeps their own setting and simply
     * gets a proportional amount of extra at speed. Smoothed every frame and targeted at 1 whenever
     * the player is not on a broom, which is what makes dismounting and dying ease back rather than
     * snap.
     */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        float target = 0.0f;
        if (Config.broomFovEffect > 0.0f) {
            BroomEntity broom = riddenBroom(mc);
            if (broom != null) {
                float ratio = BroomFlightRules.speedRatio(broom.getCurrentSpeed(), broom.getCruiseSpeed());
                target = ratio * Config.broomFovEffect;
                // boostFovPunch is extra widening while the boost is actually firing, on top of the
                // speed ramp rather than instead of it. Scaled by the same config knob, so a player
                // who turned the FOV effect off for motion sickness gets no punch either — that
                // setting is the one thing in here that must stay absolute.
                if (broom.isBoostFiring()) {
                    target += broom.resolveDefinition().handling().boostFovPunch()
                            * Config.broomFovEffect;
                }
            }
        }
        smoothedFov = Mth.lerp(FOV_SMOOTHING, smoothedFov, target);
        if (smoothedFov < 0.001f) {
            // Settled: stop writing to an event we have no reason to touch, so a player who is not
            // flying is left with exactly the FOV every other system computed.
            smoothedFov = 0.0f;
            return;
        }
        event.setNewFovModifier(event.getNewFovModifier() * (1.0f + smoothedFov));
    }

    /** The broom the local player is riding, or null — including when the module is switched off. */
    @Nullable
    private static BroomEntity riddenBroom(Minecraft mc) {
        if (mc.player == null || !ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
            return null;
        }
        return mc.player.getVehicle() instanceof BroomEntity broom ? broom : null;
    }
}
