package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings;
import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritageNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritagePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.heritage.HeritageRulesSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Heritages section's server boundary: rules the gate enforces, the player tools' authority and confirmation,
 * and that the tools read and preserve the character rather than a copy of it.
 *
 * <p>Rule changes are shared state; every scenario that makes one restores it inside the same synchronous step,
 * through the same service, so parallel scenarios never see it.
 */
public final class AdminHeritageTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    /** Everything but the players capability: a config administrator who may not touch characters. */
    private static final AdminContext NO_PLAYERS = AdminContext.detached(null, "game-test-config",
            EnumSet.complementOf(EnumSet.of(AdminCapability.PLAYERS)));

    private AdminHeritageTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_heritage_closed_heritage_cannot_be_selected",
                "admin heritage: a heritage closed by the rules is refused at the server gate whatever the client sends; reopening restores onboarding",
                AdminHeritageTests::closedHeritageCannotBeSelected);
        tests.add("admin_heritage_last_selectable_is_protected",
                "admin heritage: closing the last selectable heritage is refused as a conflict",
                AdminHeritageTests::lastSelectableIsProtected);
        tests.add("admin_heritage_unauthorised_is_rejected",
                "admin heritage: without the players capability nobody can inspect, assign or reset; unconfirmed requests change nothing",
                AdminHeritageTests::unauthorisedIsRejected);
        tests.add("admin_heritage_assignment_preserves_progression",
                "admin heritage: assigning and resetting keep trained stats and pay no second set of starting skill points",
                AdminHeritageTests::assignmentPreservesProgression);
        tests.add("admin_heritage_inspection_reads_live_attributes",
                "admin heritage: the inspector's derived values are the player's live attribute values",
                AdminHeritageTests::inspectionReadsLiveAttributes);
        tests.add("admin_heritage_transformation_rule",
                "admin heritage: closing a heritage's transformation removes the way in and keeps the way out; the rule reaches clients",
                AdminHeritageTests::transformationRule);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static Identifier selectable(Heritage heritage) {
        return HeritageRuleSettings.id(heritage, HeritageRuleSettings.Property.SELECTABLE);
    }

    private static HeritageVariant firstLineage(Heritage heritage) {
        return heritage.getSubtypes().get(0);
    }

    // ── gate ──

    private static void closedHeritageCannotBeSelected(GameTestHelper helper) {
        ServerPlayer newcomer = WizardTestSupport.placeMockPlayer(helper, "HeritageNewcomer", GameType.SURVIVAL);
        Heritage heritage = Heritage.WIZARDKIND;
        HeritageVariant lineage = firstLineage(heritage);
        try {
            HeritageAPI.clear(newcomer, false);
            // Keep another heritage open first: closing the only selectable one is refused (see the next scenario).
            // Opening an unfinished heritage is itself a dangerous change, so it must arrive confirmed.
            AdminResult unconfirmedOpen = service().change(CONSOLE, selectable(Heritage.GOBLIN), "true");
            check(helper, Heritage.GOBLIN.isAlphaAvailable() || !unconfirmedOpen.applied(),
                    () -> "opening an unfinished heritage did not ask for confirmation: " + unconfirmedOpen);
            service().change(CONSOLE, selectable(Heritage.GOBLIN), "true", true);
            AdminResult closed = service().change(CONSOLE, selectable(heritage), "false");
            check(helper, closed.applied() && !HeritageRules.selectable(heritage), () -> "closing the heritage was not applied: " + closed);

            // Exactly what a (possibly modified) client would send: the server refuses it.
            HeritageSelectC2SPayload.apply(newcomer, new HeritageSelectC2SPayload(heritage.getId(), lineage.getId(), ""));
            PlayerHeritageData data = HeritageAPI.getData(newcomer);
            check(helper, data.getSelectedHeritage() == null && !data.isLocked(),
                    () -> "a closed heritage was committed at the gate: " + data.getSelectedHeritage());

            AdminResult reopened = service().reset(CONSOLE, selectable(heritage), false);
            check(helper, reopened.applied() && HeritageRules.selectable(heritage), () -> "reopening did not apply: " + reopened);

            // Onboarding still works: the same packet now commits.
            HeritageSelectC2SPayload.apply(newcomer, new HeritageSelectC2SPayload(heritage.getId(), lineage.getId(), ""));
            check(helper, data.getSelectedHeritage() == heritage && data.getSelectedHeritageVariant() == lineage && data.isLocked(),
                    () -> "a selectable heritage was not committed at the gate");

            // A committed character cannot re-choose, and a lineage from another heritage is refused.
            HeritageSelectC2SPayload.apply(newcomer, new HeritageSelectC2SPayload(Heritage.GOBLIN.getId(),
                    firstLineage(Heritage.GOBLIN).getId(), ""));
            check(helper, data.getSelectedHeritage() == heritage, () -> "a locked character re-selected at the gate");
            helper.succeed();
        } finally {
            service().reset(CONSOLE, selectable(heritage), false);
            service().reset(CONSOLE, selectable(Heritage.GOBLIN), false);
            WizardTestSupport.retire(helper, newcomer);
        }
    }

    private static void lastSelectableIsProtected(GameTestHelper helper) {
        List<Heritage> closedHere = new ArrayList<>();
        Heritage keep = Heritage.WIZARDKIND;
        try {
            for (Heritage heritage : Heritage.values()) {
                if (heritage != keep && HeritageRules.selectable(heritage)) {
                    AdminResult result = service().change(CONSOLE, selectable(heritage), "false");
                    check(helper, result.applied(), () -> "closing " + heritage + " was refused: " + result);
                    closedHere.add(heritage);
                }
            }
            AdminResult last = service().change(CONSOLE, selectable(keep), "false");
            check(helper, last.rejection() == AdminRejection.CONFLICT && HeritageRules.selectable(keep),
                    () -> "closing the last selectable heritage was not refused as a conflict: " + last);
            helper.succeed();
        } finally {
            for (Heritage heritage : closedHere) {
                service().reset(CONSOLE, selectable(heritage), false);
            }
        }
    }

    // ── authority ──

    private static void unauthorisedIsRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "HeritageOutsider", GameType.SURVIVAL);
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "HeritageSubject", GameType.SURVIVAL);
        try {
            HeritageAPI.commit(subject, Heritage.WIZARDKIND, firstLineage(Heritage.WIZARDKIND));

            // A non-admin's packets: a rule change, an inspection, an assignment and a reset, all "confirmed".
            AdminResult rule = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(1,
                    selectable(Heritage.WIZARDKIND), "false", true));
            check(helper, rule.rejection() == AdminRejection.UNAUTHORIZED && HeritageRules.selectable(Heritage.WIZARDKIND),
                    () -> "a non-admin changed a heritage rule: " + rule);
            WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, !AdminHeritageNetworkService.sendInspection(outsider, subject.getUUID()),
                    () -> "a non-admin was sent another player's character");
            check(helper, !AdminHeritageNetworkService.sendPlayers(outsider), () -> "a non-admin was sent the player list");
            HeritageAdminService.Outcome spoofed = AdminHeritageNetworkService.assign(outsider, new AdminHeritagePayloads.AssignRequest(
                    subject.getUUID(), Heritage.GOBLIN.getId(), firstLineage(Heritage.GOBLIN).getId(), true));
            HeritageAdminService.Outcome wiped = AdminHeritageNetworkService.resetOnboarding(outsider,
                    new AdminHeritagePayloads.ResetOnboardingRequest(subject.getUUID(), true));
            check(helper, !spoofed.success() && !wiped.success(), () -> "a non-admin assigned or reset: " + spoofed + " / " + wiped);
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(outsider)) {
                check(helper, !(payload instanceof AdminHeritagePayloads.InspectReply)
                                && !(payload instanceof AdminHeritagePayloads.PlayersReply),
                        () -> "a non-admin received " + payload.type().id());
            }

            // A config administrator without the players capability: same answer.
            check(helper, HeritageAdminService.inspect(NO_PLAYERS, server, subject.getUUID()) == null
                            && HeritageAdminService.players(NO_PLAYERS, server).isEmpty(),
                    () -> "an administrator without the players capability read a character");
            HeritageAdminService.Outcome configAdmin = HeritageAdminService.resetOnboarding(NO_PLAYERS, server, subject.getUUID(), true);
            check(helper, !configAdmin.success(), () -> "an administrator without the players capability reset a player");

            // Authorised but unconfirmed: nothing happens.
            HeritageAdminService.Outcome unconfirmedAssign = HeritageAdminService.assign(CONSOLE, server, subject.getUUID(),
                    Heritage.GOBLIN.getId(), firstLineage(Heritage.GOBLIN).getId(), false);
            HeritageAdminService.Outcome unconfirmedReset = HeritageAdminService.resetOnboarding(CONSOLE, server, subject.getUUID(), false);
            check(helper, !unconfirmedAssign.success() && unconfirmedAssign.messageKey().endsWith("confirm_required")
                            && !unconfirmedReset.success() && unconfirmedReset.messageKey().endsWith("confirm_required"),
                    () -> "an unconfirmed request was not held: " + unconfirmedAssign + " / " + unconfirmedReset);

            // Malformed: a lineage of another heritage is refused even when confirmed.
            HeritageAdminService.Outcome mismatched = HeritageAdminService.assign(CONSOLE, server, subject.getUUID(),
                    Heritage.WIZARDKIND.getId(), firstLineage(Heritage.GOBLIN).getId(), true);
            check(helper, !mismatched.success(), () -> "a mismatched lineage was assigned: " + mismatched);

            PlayerHeritageData data = HeritageAPI.getData(subject);
            check(helper, data.getSelectedHeritage() == Heritage.WIZARDKIND && data.isLocked(),
                    () -> "the subject's character changed: " + data.getSelectedHeritage());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
            WizardTestSupport.retire(helper, subject);
        }
    }

    // ── progression ──

    private static void assignmentPreservesProgression(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "HeritageProgress", GameType.SURVIVAL);
        try {
            Heritage wizard = Heritage.WIZARDKIND;
            HeritageAPI.clear(subject, false);
            HeritageSelectC2SPayload.apply(subject, new HeritageSelectC2SPayload(wizard.getId(), firstLineage(wizard).getId(), ""));
            PlayerStatsAPI.setStat(subject, PlayerStat.PRECISION, 7);
            PlayerStatsAPI.setStat(subject, PlayerStat.REFLEXES, 5);
            int earned = SkillSystemAPI.getSkillData(subject).getTotalPointsEarned();

            HeritageVariant other = wizard.getSubtypes().size() > 1 ? wizard.getSubtypes().get(1) : firstLineage(wizard);
            HeritageAdminService.Outcome assigned = HeritageAdminService.assign(CONSOLE, server, subject.getUUID(),
                    wizard.getId(), other.getId(), true);
            check(helper, assigned.success() && HeritageAPI.getData(subject).getSelectedHeritageVariant() == other,
                    () -> "a confirmed assignment did not apply: " + assigned);
            check(helper, PlayerStatsAPI.getStat(subject, PlayerStat.PRECISION) == 7
                            && PlayerStatsAPI.getStat(subject, PlayerStat.REFLEXES) == 5,
                    () -> "assignment lost trained stats");
            check(helper, SkillSystemAPI.getSkillData(subject).getTotalPointsEarned() == earned,
                    () -> "assignment paid the starting skill points again");

            HeritageAdminService.Outcome reset = HeritageAdminService.resetOnboarding(CONSOLE, server, subject.getUUID(), true);
            PlayerHeritageData data = HeritageAPI.getData(subject);
            check(helper, reset.success() && data.getSelectedHeritage() == null && !data.isLocked(),
                    () -> "a confirmed onboarding reset did not clear the heritage: " + reset);
            check(helper, PlayerStatsAPI.getStat(subject, PlayerStat.PRECISION) == 7,
                    () -> "resetting onboarding lost trained stats");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }

    // ── derived stats ──

    private static void inspectionReadsLiveAttributes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "HeritageInspect", GameType.SURVIVAL);
        try {
            // A lineage with a health modifier if the mod has one, so the value is not simply the vanilla 20.
            HeritageVariant lineage = firstLineage(Heritage.WIZARDKIND);
            for (HeritageVariant candidate : HeritageVariant.values()) {
                if (candidate.getTotalHealth() != 0.0) {
                    lineage = candidate;
                    break;
                }
            }
            HeritageAPI.commit(subject, lineage.getParentHeritage(), lineage);
            HeritageAdminService.Inspection inspection = HeritageAdminService.inspect(CONSOLE, server, subject.getUUID());
            check(helper, inspection != null, () -> "an administrator could not inspect an online player");
            AdminSpellFact health = inspection.derived().get(0);
            double live = subject.getAttribute(Attributes.MAX_HEALTH).getValue();
            String expected = String.format(java.util.Locale.ROOT, "%.3f", live).replace(".000", "");
            final HeritageVariant used = lineage;
            check(helper, health.value().startsWith(expected),
                    () -> "the inspector showed max health " + health.value() + ", the player has " + live + " (" + used + ")");
            check(helper, inspection.heritageId().equals(lineage.getParentHeritage().getId())
                            && inspection.variantId().equals(lineage.getId()),
                    () -> "the inspector named the wrong heritage");
            // Wand Affinity is not an attribute players carry yet: the inspector must say so, not invent a number.
            AdminSpellFact affinity = inspection.derived().get(3);
            check(helper, affinity.valueTranslatable() && affinity.value().endsWith("untracked"),
                    () -> "the inspector invented a Wand Affinity value: " + affinity.value());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }

    // ── transformation ──

    private static void transformationRule(GameTestHelper helper) {
        ServerPlayer veela = WizardTestSupport.placeMockPlayer(helper, "HeritageVeela", GameType.SURVIVAL);
        Identifier rule = HeritageRuleSettings.id(Heritage.VEELA, HeritageRuleSettings.Property.TRANSFORMATION);
        try {
            HeritageVariant lineage = Heritage.VEELA.getSubtypes().stream()
                    .filter(v -> v.hasTag(HeritageTransformService.TAG_TRANSFORMATION)).findFirst().orElse(null);
            check(helper, lineage != null, () -> "no Veela lineage transforms");
            HeritageAPI.commit(veela, Heritage.VEELA, lineage);
            List<String> open = new ArrayList<>();
            HeritageTransformService.grantsFor(veela, open);
            check(helper, open.contains(AbilityIds.VEELA_FORM.toString()), () -> "an open transformation offered no form ability");

            WizardTestSupport.drainClientboundPayloads(veela);
            AdminResult closed = service().change(CONSOLE, rule, "false");
            check(helper, closed.applied() && !HeritageRules.transformationAllowed(Heritage.VEELA),
                    () -> "closing the transformation was not applied: " + closed);
            boolean synced = WizardTestSupport.drainClientboundPayloads(veela).stream().anyMatch(p ->
                    p instanceof HeritageRulesSyncS2CPayload sync
                            && sync.rules().get(Heritage.VEELA.getId()) != null
                            && sync.rules().get(Heritage.VEELA.getId()).transformation().equals(java.util.Optional.of(false)));
            check(helper, synced, () -> "the closed transformation was not synced to the client");

            List<String> shut = new ArrayList<>();
            HeritageTransformService.grantsFor(veela, shut);
            check(helper, !shut.contains(AbilityIds.VEELA_FORM.toString()), () -> "a closed transformation still offered the form");
            check(helper, !HeritageTransformService.enter(veela, HeritageAPI.getData(veela)),
                    () -> "a closed transformation could still be entered");
            helper.succeed();
        } finally {
            service().reset(CONSOLE, rule, false);
            WizardTestSupport.retire(helper, veela);
        }
    }

}
