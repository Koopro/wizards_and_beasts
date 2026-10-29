package at.koopro.wizardsandbeasts.journey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * When the path looks: once a second per player, and at login.
 *
 * <p>Polling rather than a hook in each owning system, because the facts live in six systems with more than six
 * writers each (the trial, the bench, a pickup, a trade for the wand alone), and a missed hook would leave a step
 * unnoticed for good. A walked or switched-off path costs one attachment read per second; an open one reads only the
 * facts of its unfinished steps.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ApprenticePathEvents {

    /** Ticks between looks: a step is noticed within a second, which reads as "at once". */
    static final int INTERVAL = 20;

    private ApprenticePathEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                // Offset by entity id so a full server does not look at everyone on the same tick.
                && (player.tickCount + player.getId()) % INTERVAL == 0) {
            ApprenticePathService.evaluate(player);
        }
    }

    /**
     * HIGHEST, because the old-save check asks whether the player came in with a path record at all, and other login
     * handlers (the Education sync among them) read the record, which attaches an empty one.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ApprenticePathService.login(player, player.hasData(ModAttachments.APPRENTICE_PATH.get()));
        }
    }
}
