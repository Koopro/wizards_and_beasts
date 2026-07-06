package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.niffler.HappinessAttachment;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HappinessTickHandler {
    private HappinessTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }

        float happiness = player.getData(ModAttachments.HAPPINESS.get());
        if (player.hasEffect(ModEffects.DEMENTOR_CHILL)) {
            happiness = HappinessAttachment.clamp(happiness - 2.0f);
        } else {
            happiness = HappinessAttachment.clamp(happiness + 0.5f);
        }

        if (happiness <= 10.0f) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, true, true));
        }
        // >= HappinessSpellPower.THRESHOLD: spell-power bonus, consumed at cast time
        // by HappinessSpellPower.applyCastModifiers (SpellExecutor hook block).
        player.setData(ModAttachments.HAPPINESS.get(), happiness);
    }
}
