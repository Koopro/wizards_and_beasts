package at.koopro.wizardsandbeasts.event.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Handles automatic skill point awarding from gameplay events, plus the one-time
 * web rework migration (schema v1 → v2) at login.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class SkillEvents {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private SkillEvents() {}

    /**
     * Web rework migration (v1 → v2): unconditional full refund + earned clamp to
     * {@link SkillSystemAPI#MAX_SKILL_POINTS}, exactly once per pre-rework player. The version
     * stamp is persisted with the attachment, so relogs never re-fire.
     */
    @SubscribeEvent
    public static void onLoginMigrateToWeb(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = SkillSystemAPI.getSkillData(player);
        if (!data.needsWebMigration()) return;

        int cleared = data.applyWebMigration();
        SkillSystemAPI.reconcileDerivedEffects(player); // strips attribute modifiers from cleared nodes
        SkillDataSyncS2CPayload.syncToPlayer(player);
        LOGGER.info(
                "Skill web migration for {}: cleared {} allocations, refunded to {} points (earned clamped to cap {})",
                player.getName().getString(), cleared, data.getSkillPoints(), SkillSystemAPI.MAX_SKILL_POINTS);
        PlayerFeedback.toast(player, NoticeKind.WARN,
                Component.translatable(L + "migration.title"),
                Component.translatable(L + "migration.body", data.getSkillPoints()));
    }

    /**
     * Vocation reframe migration: the secondary slot no longer exists in the schema. The codec
     * reads a legacy {@code secondary} key into a transient marker; here (player identity in hand)
     * we log the clear once, rebuild the declared profile without the secondary's modifiers, and
     * persist the marker-free state — the key is gone on the next save, so this never re-fires.
     */
    @SubscribeEvent
    public static void onLoginClearLegacySecondaryVocation(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = at.koopro.wizardsandbeasts.skill.vocation.VocationHelper.getData(player);
        if (!data.legacySecondaryPresent()) return;

        player.setData(at.koopro.wizardsandbeasts.registry.ModAttachments.VOCATION_DATA.get(),
                data.withoutLegacySecondary());
        // Strip all vocation modifiers (including the old secondary's scaled profile) and
        // re-apply only the surviving primary declaration.
        at.koopro.wizardsandbeasts.skill.vocation.VocationEffectApplicator.removeAll(player);
        data.primary().map(at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry::get)
                .ifPresent(v -> at.koopro.wizardsandbeasts.skill.vocation.VocationEffectApplicator.apply(player, v));
        at.koopro.wizardsandbeasts.sync.PlayerStateSyncService.syncVocations(player);
        LOGGER.info("Vocation reframe migration for {}: cleared legacy secondary vocation declaration",
                player.getName().getString());
    }

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
        // Action bar, not a toast: levelling happens constantly, and a floating panel per level would
        // be the chat problem again in a new place.
        PlayerFeedback.actionBar(player, Component.translatable(L + "points_awarded", levels)
                .withStyle(ChatFormatting.GOLD));
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
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable(L + "heritage_award.title"),
                Component.translatable(L + "heritage_award.body", 3));
    }

    private static final String L = "skill.wizards_and_beasts.award.";

    /** "+2 Skill Points" / "+1 Profession Point" / both, as one line. */
    private static Component awardLine(int skillPoints, boolean professionPoint) {
        if (skillPoints > 0 && professionPoint) {
            return Component.translatable(L + "both", skillPoints);
        }
        return professionPoint
                ? Component.translatable(L + "profession_point")
                : Component.translatable(L + "skill_points", skillPoints);
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
            }
            // Career progress. Profession points were granted once \u2014 3 at heritage selection \u2014 and by no
            // other path, so a profession tree (8 points to fill) could never be finished in survival.
            // Mastering a spell is the natural earn: proven practice is what a career is built on.
            boolean mastered = newProf == Proficiency.MASTERED;
            if (mastered) {
                ProfessionSystemAPI.awardPoints(player, 1);
            }

            // One toast, not two action-bar writes. Reaching MASTERED awards both a skill point and a
            // profession point in the same tick, and the action bar has a single slot \u2014 the second
            // message overwrote the first before it was ever drawn, so the profession point looked
            // like it had not been granted.
            if (points > 0 || mastered) {
                PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                        Component.translatable(L + "milestone." + newProf.name().toLowerCase(Locale.ROOT)),
                        awardLine(points, mastered));
            }
        }
    }
}
