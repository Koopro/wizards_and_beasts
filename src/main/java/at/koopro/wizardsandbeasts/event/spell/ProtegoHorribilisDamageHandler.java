package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.effect.ProtegoShieldEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ProtegoHorribilisDamageHandler {

    private ProtegoHorribilisDamageHandler() {}

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        var inst = victim.getEffect(ModEffects.PROTEGO_SHIELD);
        if (inst == null) {
            return;
        }
        int tier = ProtegoShieldEffect.tierFromAmplifier(inst.getAmplifier());
        if (tier < 3) {
            return;
        }
        if (!event.getSource().is(DamageTypes.INDIRECT_MAGIC) && !event.getSource().is(DamageTypes.MAGIC)) {
            return;
        }
        var container = event.getContainer();
        container.setNewDamage(container.getNewDamage() * 0.5f);
    }
}
