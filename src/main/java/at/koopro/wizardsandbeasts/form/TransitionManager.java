package at.koopro.wizardsandbeasts.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.event.form.FormEvents;
import at.koopro.wizardsandbeasts.network.form.TransitionEndS2CPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionStartS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for transformation transitions.
 * <p>
 * Handles the lifecycle: freeze player → tick duration → apply new form → unfreeze.
 * Max invulnerability is capped at 30 ticks to prevent exploits.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class TransitionManager {

    private static final int MAX_DURATION_TICKS = 30;
    private static final Map<UUID, ActiveTransition> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Starts a transformation transition for a player.
     *
     * @param player    the player transforming
     * @param toFormId  the target form ID
     * @return true if the transition was started, false if already transitioning or invalid
     */
    public static boolean startTransition(ServerPlayer player, String toFormId) {
        UUID uuid = player.getUUID();
        if (ACTIVE.containsKey(uuid)) return false;

        String fromFormId = FormSystemAPI.getPlayerFormId(player);
        if (fromFormId == null) fromFormId = "human_default";
        if (fromFormId.equals(toFormId)) return false;

        PlayerForm targetForm = FormRegistry.get(toFormId);
        if (targetForm == null) return false;

        TransformationConfig config = TransformationConfigRegistry.getOrDefault(fromFormId, toFormId);
        int duration = Math.min(config.durationTicks(), MAX_DURATION_TICKS);

        ACTIVE.put(uuid, new ActiveTransition(
                fromFormId, toFormId, duration,
                config.screenEffect(), config.freezeMovement(),
                0, player.isInvulnerable()));

        // Make player invulnerable during transition
        player.setInvulnerable(true);

        // Fire event
        NeoForge.EVENT_BUS.post(new FormEvents.PlayerFormTransitionStartEvent(
                player, fromFormId, toFormId, duration));

        // Notify clients
        TransitionStartS2CPayload.sendToTracking(
                player, fromFormId, toFormId, duration, config.screenEffect().ordinal());

        return true;
    }

    /**
     * Cancels an active transition, restoring the player's previous state.
     */
    public static void cancelTransition(ServerPlayer player) {
        ActiveTransition transition = ACTIVE.remove(player.getUUID());
        if (transition != null) {
            player.setInvulnerable(transition.wasInvulnerable);
            TransitionEndS2CPayload.sendToTracking(player, transition.fromFormId);
        }
    }

    /**
     * Returns whether a player is currently transitioning.
     */
    public static boolean isTransitioning(UUID playerUUID) {
        return ACTIVE.containsKey(playerUUID);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;

        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID uuid = entry.getKey();
            ActiveTransition t = entry.getValue();

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                // Player disconnected — clean up
                it.remove();
                continue;
            }

            int newTick = t.elapsedTicks + 1;

            // Freeze movement if configured
            if (t.freezeMovement) {
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true; // sync to client
            }

            if (newTick >= t.durationTicks) {
                // Transition complete — apply the new form
                it.remove();
                player.setInvulnerable(t.wasInvulnerable);

                FormSystemAPI.setPlayerForm(player, t.toFormId);

                NeoForge.EVENT_BUS.post(
                        new FormEvents.PlayerFormTransitionEndEvent(player, t.toFormId));
                TransitionEndS2CPayload.sendToTracking(player, t.toFormId);
            } else {
                entry.setValue(new ActiveTransition(
                        t.fromFormId, t.toFormId, t.durationTicks,
                        t.screenEffect, t.freezeMovement,
                        newTick, t.wasInvulnerable));
            }
        }
    }

    /**
     * Removes a player from all active transitions (e.g. on disconnect).
     */
    public static void onPlayerDisconnect(UUID playerUUID) {
        ACTIVE.remove(playerUUID);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private TransitionManager() {}

    private record ActiveTransition(
            String fromFormId,
            String toFormId,
            int durationTicks,
            TransformationConfig.ScreenEffect screenEffect,
            boolean freezeMovement,
            int elapsedTicks,
            boolean wasInvulnerable
    ) {}
}
