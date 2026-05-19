package at.koopro.wizardsandbeasts.event.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.util.ChatHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Handles automatic skill point awarding from gameplay events.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class SkillEvents {

    private SkillEvents() {}

    /**
     * Award skill points when a player gains an XP level.
     * 1 SP per level gained.
     */
    @SubscribeEvent
    public static void onLevelUp(PlayerXpEvent.LevelChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int levels = event.getLevels();
        if (levels <= 0) return;

        SkillSystemAPI.awardPoints(player, levels);
        SkillDataSyncS2CPayload.syncToPlayer(player);
        ChatHelper.sendActionBar(player, "\u00A76+" + levels + " Skill Point" + (levels > 1 ? "s" : "") + "!");
    }

    /**
     * Award skill points when a player first locks in their heritage.
     * 3 SP for initial heritage selection.
     */
    @SubscribeEvent
    public static void onHeritageSelected(HeritageEvents.PlayerHeritageSelectedEvent event) {
        ServerPlayer player = event.getPlayer();
        SkillSystemAPI.awardPoints(player, 3);
        SkillDataSyncS2CPayload.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "You received 3 Skill Points for choosing your path!");
    }

    public static void checkProficiencyMilestone(ServerPlayer player, String spellId, int oldCount, int newCount) {
        Proficiency oldProf = Proficiency.fromCastCount(oldCount);
        Proficiency newProf = Proficiency.fromCastCount(newCount);

        if (oldProf != newProf) {
            int points = switch (newProf) {
                case PROFICIENT -> 1;
                case MASTERED -> 2;
                default -> 0;
            };
            if (points > 0) {
                SkillSystemAPI.awardPoints(player, points);
                SkillDataSyncS2CPayload.syncToPlayer(player);
                ChatHelper.sendActionBar(player,
                        "\u00A76+" + points + " SP \u00A77(reached " + newProf.name().toLowerCase() + ")");
            }
        }
    }
}
