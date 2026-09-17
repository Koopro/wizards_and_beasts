package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drives {@link MinistryTrace} from the server clock, and gives a brand-new character their configured age.
 *
 * <p>No per-player hooks for death, respawn, logout or dimension change: case state is world data keyed by
 * UUID and timed by the shared clock, so none of those events changes anything about it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MinistryTraceEvents {

    /** Reports and cases move in seconds and minutes; once a second is plenty. */
    private static final int INTERVAL_TICKS = 20;

    private MinistryTraceEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0 || !TraceService.isActive()) {
            return;
        }
        MinistryTrace.process(server, MinistryTrace.now(server));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || Config.ministryNewCharacterAge <= 0) {
            return;
        }
        // Only a character that has never played: an existing one keeps being of age whatever the config says.
        boolean newCharacter = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0;
        if (newCharacter && MinistryRecords.get(player).ageYears() == WizardingAge.UNSET) {
            MinistryTrace.setAge(player, Math.max(WizardingAge.YOUNGEST, Config.ministryNewCharacterAge));
        }
    }
}
