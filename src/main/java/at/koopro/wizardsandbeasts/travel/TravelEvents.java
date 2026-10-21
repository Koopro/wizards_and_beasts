package at.koopro.wizardsandbeasts.travel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooTolls;
import at.koopro.wizardsandbeasts.travel.aerial.AerialSurvey;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player ticks travel needs (time trials, aerial survey), the clean-up death owes them, and the login work:
 * the one-time Floo address-book seeding and paying out Floo tolls earned while away.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class TravelEvents {

    private TravelEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TimeTrialService.tick(player);
            AerialSurvey.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A player with play time has been here before discovery existed; a first login has none.
            boolean playedBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0;
            FlooAccess.seedOnce(player, playedBefore);
            FlooTolls.collect(player);
        }
    }

    /**
     * A running course dies with its flyer. {@code PlayerScopedState} survives death (a respawn is the same UUID), so
     * without this the respawned player would be judged as having teleported off their broom.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TimeTrialService.clear(player);
        }
    }
}
