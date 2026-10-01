package at.koopro.wizardsandbeasts.event.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Ends an Apparition attempt on every path that invalidates the wizard holding it.
 *
 * <p>The attempt lived in a {@code PlayerScopedState}, which only a logout clears. It is keyed by UUID, so it survived
 * a death and came back attached to the respawned player: releasing then Apparated them from the respawn point to an
 * anchored destination chosen before they died. It also survived a dimension change, leaving an anchored destination
 * resolved in one world to be applied in another (2026-09-29, documentation/MULTIPLAYER_AUDIT.md). The wand's cast
 * session has always been dropped on these same paths; this gives the Apparition charge the same treatment.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ApparitionChargeLifecycle {

    private ApparitionChargeLifecycle() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ApparitionChargeManager.abort(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ApparitionChargeManager.abort(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ApparitionChargeManager.abort(player);
        }
    }
}
