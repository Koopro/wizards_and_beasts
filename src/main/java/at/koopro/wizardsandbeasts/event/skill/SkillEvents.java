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
        ChatHelper.send(player, "§6Skill trees have been reworked into a web — your "
                + data.getSkillPoints() + " skill points were refunded. Open the skill screen to re-allocate.");
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
