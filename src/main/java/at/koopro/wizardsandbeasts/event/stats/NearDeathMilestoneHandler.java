package at.koopro.wizardsandbeasts.event.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.stats.MilestoneType;
import at.koopro.wizardsandbeasts.stats.StatMilestones;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Fires the {@link MilestoneType#NEAR_DEATH_SURVIVED} milestone the first time a player takes a real
 * hit that leaves them alive but critically low (≤15% of max health, floor 3 HP). Uses the post-damage
 * event so the survival check reads the player's actual remaining health after all reductions.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class NearDeathMilestoneHandler {

    private NearDeathMilestoneHandler() {}

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getNewDamage() < 1.0f) {
            return; // trivial chip damage doesn't count as a brush with death
        }
        float health = player.getHealth();
        if (health <= 0.0f) {
            return; // died — not survived
        }
        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0.0f) {
            return;
        }
        if (health <= Math.max(3.0f, maxHealth * 0.15f)) {
            StatMilestones.onMilestoneTriggered(player, MilestoneType.NEAR_DEATH_SURVIVED);
        }
    }
}
