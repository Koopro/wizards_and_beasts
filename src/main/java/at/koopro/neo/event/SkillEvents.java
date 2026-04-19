package at.koopro.neo.event;

import at.koopro.neo.Neo;
import at.koopro.neo.network.SkillDataSyncS2CPacket;
import at.koopro.neo.skill.SkillSystemAPI;
import at.koopro.neo.spell.Proficiency;
import at.koopro.neo.util.ChatHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Handles automatic skill point awarding from gameplay events.
 */
@EventBusSubscriber(modid = Neo.MODID)
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
        SkillDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendActionBar(player, "\u00A76+" + levels + " Skill Point" + (levels > 1 ? "s" : "") + "!");
    }

    /**
     * Award skill points when a player first selects their wizard type.
     * 3 SP for initial type selection.
     */
    @SubscribeEvent
    public static void onTypeSelected(TypeEvents.PlayerTypeSelectedEvent event) {
        ServerPlayer player = event.getPlayer();
        SkillSystemAPI.awardPoints(player, 3);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "You received 3 Skill Points for choosing your path!");
    }

    /**
     * Check for proficiency milestones after spell casts.
     * Call this from SpellCastC2SPacket after incrementing cast count.
     *
     * @param player   the caster
     * @param spellId  the spell cast
     * @param oldCount the cast count before this cast
     */
    public static void checkProficiencyMilestone(ServerPlayer player, String spellId, int oldCount) {
        int newCount = oldCount + 1;
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
                SkillDataSyncS2CPacket.syncToPlayer(player);
                ChatHelper.sendActionBar(player,
                        "\u00A76+" + points + " SP \u00A77(reached " + newProf.name().toLowerCase() + ")");
            }
        }
    }
}
