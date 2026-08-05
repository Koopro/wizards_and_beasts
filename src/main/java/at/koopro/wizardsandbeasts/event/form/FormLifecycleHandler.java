package at.koopro.wizardsandbeasts.event.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Keeps form state alive across the player lifecycle.
 * <p>
 * {@link FormSystemAPI} only syncs on <em>change</em>, which leaves two holes: an observer who
 * starts tracking an already-transformed player is never told about the form and draws a human,
 * and the size profile's attribute modifiers are transient, so respawning or changing dimension
 * silently drops the form's hitbox and scale.
 * <p>
 * Mirrors the tracking/login/respawn/dimension quartet in {@code CloakEffectsHandler}, which
 * solves the same visibility problem for equipment masking.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FormLifecycleHandler {

    private FormLifecycleHandler() {}

    /** An observer just started tracking someone — tell them what that someone looks like. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        FormSyncS2CPayload.syncTo(observer, target);
    }

    /**
     * A joining client knows nothing about anyone. Send it every online player's form, including
     * its own — tracking has not started yet, so {@link #onStartTracking} would miss them all.
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) return;

        FormSystemAPI.reapplyCurrentForm(observer);

        if (!(observer.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            FormSyncS2CPayload.syncTo(observer, target);
        }
    }

    /** Respawn builds a fresh player entity — transient size modifiers are gone with the old one. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPayload.syncToTracking(player);
    }

    /** Changing dimension re-creates the tracking set, so re-announce the form to it. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPayload.syncToTracking(player);
    }
}
