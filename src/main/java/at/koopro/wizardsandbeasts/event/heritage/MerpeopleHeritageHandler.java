package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The tide calling a Selkie home.
 *
 * <p>"Seals who shrug off skin to dance on land until the tide calls them home again" — so the change is
 * not a choice at all, it is where they happen to be. Under water they are {@code merfolk_water}; out of
 * it they are {@code merfolk_land}. No key, no ability, no cooldown.
 *
 * <p>Only Selkies. Merrow and Siren carry {@code water_breathing} but not {@code "transformation"} — they
 * are at home in the water without changing shape, and {@link HeritageTransformService} enforces that.
 *
 * <h2>The grace period is the whole difficulty</h2>
 * A player swimming at the surface crosses the waterline several times a second, and a transformation
 * fired on the raw {@code isUnderWater()} reading would strobe between two forms — each one starting a
 * transition, freezing them, and playing a screen effect. So surfacing has to <em>persist</em> for
 * {@link #SURFACE_GRACE_TICKS} before it counts, while submerging counts immediately: getting into the
 * water should feel instant, and getting out should feel like a decision.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MerpeopleHeritageHandler {

    /** Every half-second. Fine enough that entering water feels immediate. */
    private static final int SCAN_INTERVAL_TICKS = 10;

    /**
     * How long a Selkie must stay out of the water before the land shape takes them back.
     *
     * <p>Two seconds, which comfortably outlasts a swimming stroke at the surface and a jump off a boat.
     */
    private static final int SURFACE_GRACE_TICKS = 40;

    /** Game tick at which each surfaced Selkie becomes eligible to change back. */
    private static final PlayerScopedState<Long> SURFACED_AT =
            PlayerScopedState.create("merpeople-surfaced-at");

    private MerpeopleHeritageHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.getServer().getTickCount() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (data.getSelectedHeritage() != Heritage.MERPEOPLE
                    || !HeritageTransformService.canTransform(data)) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            scan(player, level, data);
        }
    }

    private static void scan(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        boolean submerged = player.isUnderWater();
        boolean transformed = HeritageTransformService.isTransformed(data);

        if (submerged) {
            SURFACED_AT.remove(player.getUUID());
            if (!transformed && HeritageTransformService.enter(player, data)) {
                splash(player, level, ParticleTypes.BUBBLE_COLUMN_UP, 1.2f);
            }
            return;
        }

        if (!transformed) {
            SURFACED_AT.remove(player.getUUID());
            return;
        }

        long now = level.getGameTime();
        Long eligibleAt = SURFACED_AT.get(player.getUUID());
        if (eligibleAt == null) {
            SURFACED_AT.put(player.getUUID(), now + SURFACE_GRACE_TICKS);
            return;
        }
        if (now < eligibleAt) {
            return;
        }
        SURFACED_AT.remove(player.getUUID());
        if (HeritageTransformService.exit(player, data)) {
            splash(player, level, ParticleTypes.SPLASH, 0.9f);
        }
    }

    private static void splash(ServerPlayer player, ServerLevel level,
                               net.minecraft.core.particles.SimpleParticleType particle, float pitch) {
        level.sendParticles(particle, player.getX(), player.getY() + 0.6, player.getZ(),
                24, 0.4, 0.5, 0.4, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH,
                SoundSource.PLAYERS, 0.7f, pitch);
    }
}
