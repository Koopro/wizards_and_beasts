package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.LumosFieldEffect;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.wand.DebugWandState;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            PlayerStateSyncService.syncFullLoginState(player, false);
        }
    }

    @SubscribeEvent
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
            PlayerStateSyncService.syncFullLoginState(player, false);
        }
    }
}
