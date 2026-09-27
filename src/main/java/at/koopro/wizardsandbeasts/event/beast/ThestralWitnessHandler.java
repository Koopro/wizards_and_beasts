package at.koopro.wizardsandbeasts.event.beast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Grants {@link ThestralEntity#WITNESSED_DEATH_FLAG} to those who see a person die.
 *
 * <p>Canon: thestrals are visible to "people who have seen death" (Order of the Phoenix) — Harry after Cedric, Neville
 * after his grandfather, Luna after her mother. The game cannot know whether anyone has understood what they saw and
 * does not try; it records the explicit fact. So the rule is narrow on purpose:
 *
 * <ul>
 *   <li><b>a person</b> — a player, a villager or trader ({@link Npc}), or an illager or witch ({@link Raider}). Not a
 *       chicken, not a zombie. The old rule counted any death at all within 24 blocks, so a single cow at a farm
 *       granted it.</li>
 *   <li><b>seen</b> — within {@link #WITNESS_RADIUS} and in line of sight, alive, and not a spectator.</li>
 *   <li>not one's own death; nobody witnesses that.</li>
 * </ul>
 *
 * <p>The flag lives in the player's ability data: saved, copied across death, and synced to the client at login,
 * respawn and dimension change, which is where the renderer reads it. Once set it is never cleared.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ThestralWitnessHandler {

    static final double WITNESS_RADIUS = 16.0;

    private ThestralWitnessHandler() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !isPerson(victim)) {
            return;
        }
        AABB area = victim.getBoundingBox().inflate(WITNESS_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && p != victim)) {
            if (player.distanceTo(victim) <= WITNESS_RADIUS && player.hasLineOfSight(victim)) {
                PlayerAbilityHelper.addAbilityFlag(player, ThestralEntity.WITNESSED_DEATH_FLAG);
            }
        }
    }

    /** Whose death counts: a person's. */
    static boolean isPerson(LivingEntity entity) {
        return entity instanceof Player || entity instanceof Npc || entity instanceof Raider;
    }
}
