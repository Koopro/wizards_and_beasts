package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.DebugModeService;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.effect.LumosFieldEffect;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.wand.DebugWandState;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.render.outline.EntityOutlineService;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.network.debug.DebugModeS2CPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class WizardsAndBeastsCommands {

    private WizardsAndBeastsCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WandbCommands.register(event);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasEffect(ModEffects.LUMOS_FIELD)) {
            LumosFieldEffect.placeOrUpdateLight(player);
        } else {
            LumosFieldEffect.removeLight(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean needsSelection = !HeritageAPI.hasHeritageSelected(player);
            PlayerStateSyncService.syncFullLoginState(player, needsSelection);
            OWLExaminationHandler.syncToPlayer(player);
            PlayerStatsSyncPayload.syncToPlayer(player);
            DarkCorruptionService.syncDisplay(player);
            // Outlines are broadcast on change, so a player joining afterwards would otherwise never
            // learn about anyone already outlined.
            EntityOutlineService.syncToPlayer(player);
            // Same reasoning for debug mode: it is toggled by a command, and a reconnecting operator
            // would otherwise have the flag on the server and a client that never starts polling.
            DebugModeS2CPayload.sendTo(player, DebugModeService.isEnabled(player));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Vanilla's stopUsingItem() on death halts WandItem.onUseTick without routing through any hook
        // this mod listens to, so a held beam channel (Imperio control, Crucio, Leviosa, ...) would keep
        // its victim under the effect for as long as the caster sits on the death screen. End it here.
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            // The client releases the wand as it dies and sends the release packet regardless; dropping
            // the session is what stops that packet from resolving into a cast from a corpse.
            WandCastSessions.abort(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            WandCastSessions.abort(player);
            PlayerStateSyncService.syncFullLoginState(player, false);
            PlayerStatsSyncPayload.syncToPlayer(player);
            DarkCorruptionService.syncDisplay(player);
        }
    }

    /**
     * HIGHEST because {@link at.koopro.wizardsandbeasts.util.PlayerScopedState}'s own logout listener
     * wipes every per-player holder — including the beam session this method needs to read. Both ran at
     * NORMAL, so which went first depended on mod-class scanning order; when the generic sweep won,
     * {@code endChannel} found no session and returned early, and the caster's Crucio victim kept its
     * effects and the Leviosa target kept {@code noGravity} for good. Alt-F4 mid-channel was a way to
     * make a beam permanent.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            DebugWandState.cleanup(player.getUUID(), (ServerLevel) player.level());
            LumosFieldEffect.removeLight(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            WandCastSessions.abort(player);
            PlayerStateSyncService.syncFullLoginState(player, false);
            PlayerStatsSyncPayload.syncToPlayer(player);
        }
    }
}
