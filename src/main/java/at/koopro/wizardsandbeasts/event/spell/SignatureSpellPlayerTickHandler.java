package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SignatureSpellPlayerTickHandler {

    private SignatureSpellPlayerTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        ImperioServerLogic.tickVictim(player);

        ImperioControlState st = player.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        float will = player.getData(ModAttachments.WILLPOWER.get());
        if (!st.isControlled() && will < 100f) {
            player.setData(ModAttachments.WILLPOWER.get(), Math.min(100f, will + 1.0f / 20f));
        }

        float mental = player.getData(ModAttachments.MENTAL_STABILITY.get());
        float corruption = player.getData(ModAttachments.DARK_CORRUPTION.get());
        float maxMental = corruption >= 60f ? 80f : 100f;
        if (!player.hasEffect(ModEffects.CRUCIO_SANITY_DRAIN)
                && !player.hasEffect(ModEffects.DEMENTOR_CHILL)
                && mental < maxMental) {
            player.setData(ModAttachments.MENTAL_STABILITY.get(), Math.min(maxMental, mental + 0.05f / 20f));
        }

        if (mental <= 30f && player.tickCount % 200 == 0) {
            player.displayClientMessage(
                    Component.literal("Your mind fractures.").withStyle(ChatFormatting.DARK_PURPLE), true);
        }

        int crucioExp = player.getData(ModAttachments.CRUCIO_EXPOSURE_TICKS.get());
        if (crucioExp > 2400 && !player.getTags().contains("neo_crucio_broken_applied")) {
            player.addTag("neo_crucio_broken_applied");
            float ms = player.getData(ModAttachments.MENTAL_STABILITY.get());
            player.setData(ModAttachments.MENTAL_STABILITY.get(), Math.min(ms, 50f));
            player.sendSystemMessage(Component.literal("Something inside you has broken.").withStyle(ChatFormatting.DARK_RED));
        }

        if (corruption >= 30f && player.tickCount % 6000 == 0) {
            player.displayClientMessage(
                    Component.literal("You feel the darkness seeping into your thoughts.").withStyle(ChatFormatting.DARK_GRAY),
                    true);
            player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(), player.getZ(),
                    6, 0.3, 0.05, 0.3, 0.01);
        }
    }
}
