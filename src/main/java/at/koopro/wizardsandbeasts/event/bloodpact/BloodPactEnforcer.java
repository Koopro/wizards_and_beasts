package at.koopro.wizardsandbeasts.event.bloodpact;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bloodpact.BloodPactRecord;
import at.koopro.wizardsandbeasts.item.bloodpact.BloodPactVialItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BloodPactEnforcer {

    private BloodPactEnforcer() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;

        List<BloodPactRecord> pacts = victim.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get());
        boolean pactActive = pacts.stream().anyMatch(r -> r.partnerUUID().equals(attacker.getUUID()));
        if (!pactActive) return;

        event.setCanceled(true);

        attacker.displayClientMessage(
                Component.literal("The blood pact prevents you from harming " + victim.getScoreboardName() + ".")
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_RED),
                true);
        victim.displayClientMessage(
                Component.literal("The blood pact protected you from " + attacker.getScoreboardName() + ".")
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD),
                true);

        victim.level().playSound(null, victim.blockPosition(),
                ModSounds.BLOOD_PACT_BLOCK.get(), SoundSource.PLAYERS, 0.9f, 1.2f);

        if (victim.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    12, 0.3, 0.3, 0.3, 0.1);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        long currentTick = event.getServer().getTickCount();
        BloodPactVialItem.PENDING_PROPOSALS.entrySet()
                .removeIf(entry -> entry.getValue().expiryGameTick() < currentTick);
    }
}
