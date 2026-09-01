package at.koopro.wizardsandbeasts.event.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the departure window: the countdown, and the two ways it has to end early.
 *
 * <p>Separate from {@link FlooCallEvents} rather than folded into its player tick. The two systems
 * share a keyword and nothing else — a call is a conversation held from where you are standing, a
 * departure is a journey about to happen — and one subscriber ticking both would mean a change to
 * either one's cadence silently changing the other's.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FlooDepartureEvents {

    private FlooDepartureEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlooDeparture.tick(player);
        }
    }

    /**
     * A traveller who logs out mid-windup does not arrive.
     *
     * <p>{@code PlayerScopedState} clears its own entry on logout, so the pending departure is gone
     * either way; this exists so the cancel runs while the player object is still valid and the
     * bookkeeping happens in one place rather than being inferred from a map that quietly emptied.
     * Without an explicit end, a player who disconnected inside the window and reconnected somewhere
     * else would be the only case where a hop neither happened nor was refused.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Silent for the same reason death is: a player who has just disconnected cannot read a
            // message, and the sputter would play into an empty room.
            FlooDeparture.cancelSilently(player);
        }
    }

    /**
     * Dying in the fire ends the journey.
     *
     * <p>Without this the departure survives the death, because {@code PlayerScopedState} is keyed on
     * UUID and a respawn is the same player. What happened next was not a teleport — the next tick
     * saw the corpse's replacement standing at a spawn point, decided they had stepped out of the
     * fire, and cancelled with "you step back out of the flames" plus a sputter at the respawn
     * point. Self-healing, but it narrated a fireplace that was not there to somebody who had just
     * died, and it left one tick in which a dead player still had a hop in flight.
     *
     * <p>Silent cancel: the death screen is not the place for a note about Floo Powder, and nothing
     * was charged.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlooDeparture.cancelSilently(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FlooDeparture.clearAll();
    }
}
