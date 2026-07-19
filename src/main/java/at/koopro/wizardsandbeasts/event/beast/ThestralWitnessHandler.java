package at.koopro.wizardsandbeasts.event.beast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Grants {@link ThestralEntity#WITNESSED_DEATH_FLAG} to every player near a death — proximity-only,
 * no line-of-sight requirement, mirroring {@link at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler}'s
 * cheap-scan style. Unlike that handler (which only credits the killer), this fires for any nearby
 * death — canon is "witnessed a death," not "caused one."
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ThestralWitnessHandler {

    private static final double WITNESS_RADIUS = 24.0;

    private ThestralWitnessHandler() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        AABB area = event.getEntity().getBoundingBox().inflate(WITNESS_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            PlayerAbilityHelper.addAbilityFlag(player, ThestralEntity.WITNESSED_DEATH_FLAG);
        }
    }
}
