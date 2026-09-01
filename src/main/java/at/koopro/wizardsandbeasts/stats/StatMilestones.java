package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class StatMilestones {

    private static final Logger LOGGER = LogUtils.getLogger();

    private StatMilestones() {}

    /**
     * Entry point for milestone-triggered stat bumps. Each milestone grants a <em>one-shot</em> bump
     * to a thematically-appropriate stat via {@link PlayerStatsAPI#grantMilestoneBump}: the achievement
     * is recorded in {@link PlayerSkillData#markMilestoneAchieved} so callers may fire the trigger every
     * time the underlying event recurs without re-granting. Server-side only; a client-side call is a
     * no-op (the bump is also rejected by the API's server guard).
     */
    public static void onMilestoneTriggered(Player player, MilestoneType type) {
        if (player.level().isClientSide()) return;

        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        if (!skillData.markMilestoneAchieved(type.name())) {
            return; // already achieved — one-shot
        }

        PlayerStat stat;
        int amount;
        switch (type) {
            case FIRST_PATRONUS_CORPOREAL          -> { stat = PlayerStat.WILLPOWER; amount = 2; }
            case FIRST_IMPERIUS_RESISTED           -> { stat = PlayerStat.WILLPOWER; amount = 2; }
            case FIRST_OCCLUMENCY_DEFENCE_SUCCESS  -> { stat = PlayerStat.WILLPOWER; amount = 1; }
            case FIRST_ANIMAGUS_TRANSFORMATION     -> { stat = PlayerStat.PRECISION; amount = 2; }
            case NEAR_DEATH_SURVIVED               -> { stat = PlayerStat.POWER;     amount = 2; }
            default -> { return; }
        }

        LOGGER.debug("[WizardsAndBeasts] Milestone {} for {} → +{} {}",
                type, player.getName().getString(), amount, stat.getId());

        // The reason travels with the grant rather than as a toast of its own. Both used to fire, so
        // a near-death survival raised one panel saying the milestone happened and — once POWER
        // learned to announce itself — a second saying the number changed, for one event.
        int before = PlayerStatsAPI.getStat(player, stat);
        PlayerStatsAPI.grantMilestoneBump(player, stat, amount, sourceKey(type));
        int after = PlayerStatsAPI.getStat(player, stat);

        // A milestone is one-shot whether or not the stat could take the points, and a player whose
        // Power is already on its heritage band would otherwise have earned it, spent it and been
        // told nothing at all — the level-up channel only speaks when a number actually moves. Say
        // that the milestone happened and why it bought nothing.
        if (after <= before && player instanceof ServerPlayer serverPlayer) {
            PlayerFeedback.toast(serverPlayer, NoticeKind.DISCOVERY,
                    Component.translatable(sourceKey(type)),
                    Component.translatable("message.wizards_and_beasts.milestone.no_room",
                            stat.displayName()));
        }
    }

    /** Lang key describing the milestone, shown as the body of the level-up notice. */
    static String sourceKey(MilestoneType type) {
        return "stat.wizards_and_beasts.milestone." + type.name().toLowerCase(java.util.Locale.ROOT);
    }
}
