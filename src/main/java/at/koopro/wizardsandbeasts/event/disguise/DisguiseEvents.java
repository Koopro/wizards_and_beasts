package at.koopro.wizardsandbeasts.event.disguise;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.disguise.DisguiseSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The disguise clock, and every way one ends or has to be re-announced.
 *
 * <h2>Death reverts</h2>
 * <p>Offered as revert-or-keep and this takes revert. Keeping it would mean a corpse and a respawn
 * wearing a face the player was not seen to put back on, and — the deciding argument — a player who
 * could die to keep a disguise would have a free way to reset the timer. It applies to admin
 * disguises too: an operator who wants one back after dying can type four words, and the alternative
 * is a rule that behaves differently depending on where the disguise came from.
 *
 * <h2>Why a new tracker needs telling</h2>
 * <p>The sync is broadcast to whoever is tracking the disguised player at the moment it changes. A
 * client that starts tracking them <em>afterwards</em> — walking into range, or joining the server —
 * was not there for that packet and would draw the real face. {@link #onStartTracking} is what makes a
 * disguise hold up for somebody who arrives late, which in multiplayer is most people.
 *
 * <h2>Why the wearer needs telling too</h2>
 * <p>{@code StartTracking} never fires for a player tracking themselves, so the three events that
 * rebuild a player's own client view — login, respawn, dimension change — are handled explicitly.
 * Without them the wearer is the only person on the server who cannot see their own disguise, and a
 * player who cannot see it cannot tell whether it is still on.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DisguiseEvents {

    private DisguiseEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DisguiseSystemAPI.tick(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Silent: the death screen is not the place for a note about a face coming off.
            DisguiseSystemAPI.clear(player, false);
        }
    }

    /**
     * A client that has just started seeing somebody needs to know whose face they are wearing.
     *
     * <p>{@code getEntity()} is the <b>viewer</b> and {@code getTarget()} is what they have begun
     * tracking — an easy pair to swap, and swapping them here would send every viewer their own
     * disguise instead of the one they need to draw.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer
                && event.getTarget() instanceof ServerPlayer subject) {
            DisguiseSystemAPI.resyncTo(viewer, subject);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DisguiseSystemAPI.resyncSelf(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DisguiseSystemAPI.resyncSelf(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DisguiseSystemAPI.resyncSelf(player);
        }
    }
}
