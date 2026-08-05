package at.koopro.wizardsandbeasts.event.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry;
import at.koopro.wizardsandbeasts.apparition.sidealong.SideAlongService;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionWardsSyncS2CPayload;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ApparitionEvents {
    private ApparitionEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ApparitionWardRegistry.load(event.getServer());
        ApparitionWardsSyncS2CPayload.syncToAllPlayers(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ApparitionWardRegistry.save(event.getServer());
    }

    /**
     * Sneak-interacting another player offers to take them along. An offer only — nobody travels until they
     * accept, and the offer lapses after {@link SideAlongService#OFFER_TICKS}.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)
                || !(event.getTarget() instanceof ServerPlayer invitee)
                || !caster.isShiftKeyDown()
                || caster == invitee) {
            return;
        }
        if (!ApparitionServerLogic.canApparate(caster)) {
            return;
        }
        SideAlongService.offer(caster, invitee);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ApparitionServerLogic.tick(player);
        SideAlongService.tick(player);
        if (player.tickCount % 1200 == 0 && HeritageAPI.getPlayerHeritage(player) == Heritage.WIZARDKIND) {
            float next = Math.min(1.0f, PlayerAbilityHelper.getOcclumencyLevel(player) + 0.001f);
            PlayerAbilityHelper.setOcclumencyLevel(player, next);
            // TODO: hook Occlumency into future Dark Arts Trace resistance calculations.
        }
        int legiCd = PlayerAbilityHelper.getLegilimencyCooldownTicks(player);
        if (legiCd > 0) {
            PlayerAbilityHelper.setLegilimencyCooldownTicks(player, legiCd - 1);
        }
    }
}
