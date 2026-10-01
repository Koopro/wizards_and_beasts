package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.heritage.veela.VeelaAllure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The three moments the Veela allure needs to hear about ({@link VeelaAllure}).
 *
 * <ul>
 *   <li>An entranced mob tries to take up a target — it cannot. (An entranced <em>player</em>'s blows are already
 *       stopped by the Amortentia rule in {@code SignatureBrewHandler}; nothing is duplicated here.)</li>
 *   <li>The Veela lands a blow — the allure breaks on everyone they had entranced.</li>
 *   <li>The Veela logs out — whoever they entranced is forgotten; the effect runs out on its own.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class VeelaAllureEvents {

    private VeelaAllureEvents() {}

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.hasEffect(ModEffects.INFATUATION)
                && event.getNewAboutToBeSetTarget() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getEntity() != attacker) {
            VeelaAllure.breakOnAnger(attacker);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VeelaAllure.forget(event.getEntity().getUUID());
    }
}
