package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What Wolfsbane looks like while it is working, and what it looks like when it stops.
 *
 * <p>The potion's <em>mechanics</em> live elsewhere and deliberately so: {@code WerewolfRules.hasWolfsbane}
 * is the single question, {@code WerewolfMoonHandler.refreshControl} re-derives the loss-of-control flag
 * from it every scan, and {@code WerewolfTransformService} decides what a medicated change means. This
 * class adds only the two things a player can actually see.
 *
 * <h2>The haze</h2>
 * Pale drifting motes at chest height — aconite steam still coming off a wizard who drank it. Spawned
 * server-side so <b>other players see it too</b>, which is the point: on a server where somebody is about
 * to become a wolf, whether they took their potion is information everyone around them wants. Denser once
 * the drinker is actually in wolf shape, because that is when it is load-bearing.
 *
 * <p>No screen effect. A tint would have to be gated on {@code Config.reduceScreenEffects} like every
 * other one in the mod, which means the accessibility setting would switch off the only indicator that
 * the thing keeping you in control is about to stop. Particles are visible to everyone under every
 * setting.
 *
 * <h2>The failure warning</h2>
 * The last {@link #WARNING_TICKS} are called out, escalating, because of what happens at zero: a werewolf
 * under a full moon whose Wolfsbane runs out loses control of their character inside one scan. That is
 * the single harshest transition in the mod and it must never arrive unannounced. Warned only for actual
 * werewolves — anybody else holding the effect has nothing to lose by it.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class WolfsbaneHandler {

    /** Ticks between haze puffs. Slow: this is a lingering vapour, not a fountain. */
    private static final int HAZE_INTERVAL_TICKS = 8;
    /** How long before the potion fails that the warning starts. Five seconds. */
    public static final int WARNING_TICKS = 100;
    /** Ticks between warning pulses inside that window. */
    private static final int WARNING_INTERVAL_TICKS = 20;

    private WolfsbaneHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        MobEffectInstance wolfsbane = player.getEffect(ModEffects.WOLFSBANE);
        if (wolfsbane == null) {
            return;
        }

        if (player.tickCount % HAZE_INTERVAL_TICKS == 0) {
            haze(player, level);
        }

        int remaining = wolfsbane.getDuration();
        if (remaining <= WARNING_TICKS && WerewolfRules.isWerewolf(player)) {
            warnOfFailure(player, level, remaining);
        }
    }

    /**
     * The steam that has not finished coming off.
     *
     * <p>Placed at the chest and given almost no velocity, so it hangs on the drinker rather than
     * trailing behind them. {@code SPORE_BLOSSOM_AIR} drifts downward on its own, which reads as a
     * settling vapour; the occasional smoke wisp keeps it from looking like a decorative aura.
     */
    private static void haze(ServerPlayer player, ServerLevel level) {
        boolean wolf = WerewolfRules.inWolfForm(WerewolfState.data(player));
        int count = wolf ? 3 : 1;
        double chestY = player.getY() + player.getBbHeight() * 0.6;

        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                player.getX(), chestY, player.getZ(),
                count, 0.32, 0.28, 0.32, 0.0);
        if (wolf && player.tickCount % (HAZE_INTERVAL_TICKS * 3) == 0) {
            level.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), chestY, player.getZ(),
                    2, 0.2, 0.15, 0.2, 0.005);
        }
    }

    /**
     * The potion running out, said plainly.
     *
     * <p>Pitch climbs across the window so the warning gets more insistent rather than merely repeating —
     * the same shape {@code GillyweedHandler.fadeWarning} uses for the same reason. The action bar rather
     * than a toast: this is a countdown, and it is worthless the moment it is over.
     */
    private static void warnOfFailure(ServerPlayer player, ServerLevel level, int remaining) {
        if (player.tickCount % WARNING_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                8, 0.3, 0.3, 0.3, 0.02);

        float progress = 1.0f - remaining / (float) WARNING_TICKS;
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.PLAYERS, 0.5f, 0.8f + progress * 0.5f);

        PlayerFeedback.actionBar(player, Component.translatable(
                "message.wizards_and_beasts.werewolf.wolfsbane_failing", remaining / 20 + 1));
    }
}
