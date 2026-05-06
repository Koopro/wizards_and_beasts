package at.koopro.wizardsandbeasts.wand.ollivander;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

@EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
public final class OllivanderInteractHandler {

    private OllivanderInteractHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }
        if (!villager.getVillagerData().profession().is(ModVillager.WANDMAKER.getKey())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<OllivanderPoolEntry> picks = OllivanderTrialMenu.pickTrials(player);
        float[] scores = OllivanderTrialMenu.computeInitialScores(player, picks);
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.wizards_and_beasts.ollivander_trial");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                return new OllivanderTrialMenu(syncId, inv, picks);
            }
        }, buf -> {
            buf.writeVarInt((int) (WandResonanceConfigLoader.getConfig(player.registryAccess()).matchThreshold() * 100.0f));
            for (int i = 0; i < 3; i++) {
                OllivanderPoolEntry e = picks.get(i);
                buf.writeUtf(e.woodKey().toString(), 320);
                buf.writeUtf(e.coreKey().toString(), 320);
                buf.writeUtf(e.flexibility(), 64);
            }
            buf.writeFloat(scores[0]);
            buf.writeFloat(scores[1]);
            buf.writeFloat(scores[2]);
        });
    }
}
