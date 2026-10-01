package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.history.AdminHistoryData;
import at.koopro.wizardsandbeasts.admin.profile.ProfileApplier;
import at.koopro.wizardsandbeasts.admin.profile.ProfileCodec;
import at.koopro.wizardsandbeasts.admin.profile.ProfileDocument;
import at.koopro.wizardsandbeasts.admin.profile.ProfileService;
import at.koopro.wizardsandbeasts.admin.profile.ProfileValidator;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import at.koopro.wizardsandbeasts.module.data.ModuleStateData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Profiles against the live settings: apply a shipped preset and revert its group, all-or-nothing on a partial failure,
 * import validation (malformed, incompatible, invalid, unknown), snapshots, persistent history with revert-one, apply
 * modes, and authority. Every test leaves the server where it found it: values through the same service, module states
 * through the world data with the datapack reload held back.
 */
public final class AdminProfileTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    private static final AdminContext NOBODY = AdminContext.detached(null, "game-test-nobody", EnumSet.noneOf(AdminCapability.class));
    private static final String NS = "wizards_and_beasts";

    private AdminProfileTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        // These three apply whole profiles in replace mode (and restore a snapshot afterwards): alone, or they would
        // reset settings other scenarios in the same batch are relying on (a broom test's speed scale, for one).
        tests.addAlone("admin_profile_apply_preset_and_revert_group",
                "admin profiles: the Hardcore preset applies as one group and the group reverts every value it changed",
                AdminProfileTests::applyAndRevertGroup);
        tests.addAlone("admin_profile_partial_failure_rolls_back",
                "admin profiles: a batch with one change that can never pass, or one invalid value, or a stale value, leaves nothing changed",
                AdminProfileTests::partialFailureRollsBack);
        tests.add("admin_profile_import_validation",
                "admin profiles: malformed, incompatible, invalid and path-escaping imports are refused; a usable one is kept with its warnings",
                AdminProfileTests::importValidation);
        tests.addAlone("admin_profile_snapshot_restore",
                "admin profiles: a snapshot captures the configuration and restoring it brings it back",
                AdminProfileTests::snapshotRestore);
        tests.add("admin_profile_history_persists_and_reverts",
                "admin profiles: changes are written to the world's history with actor and value; one change reverts, a stale revert is refused",
                AdminProfileTests::historyPersistsAndReverts);
        tests.add("admin_profile_flags_and_authority",
                "admin profiles: a worldgen module change is flagged NEW CHUNKS before applying; actors without authority are refused",
                AdminProfileTests::flagsAndAuthority);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static <T> T quietly(Supplier<T> body) {
        return ModuleStateService.withoutDatapackReload(body);
    }

    /** Takes a full snapshot of the server's configuration; the returned runnable restores it exactly. */
    private static Runnable remember(MinecraftServer server) {
        Map<Module, ModuleState> modules = new EnumMap<>(ModuleStateData.get(server.overworld()).allStates());
        ProfileService.Result snapshot = ProfileService.saveAs(server, CONSOLE, "gametest restore point",
                ProfileDocument.Kind.SNAPSHOT);
        return () -> {
            if (snapshot.ok()) {
                quietly(() -> ProfileService.apply(server, CONSOLE, snapshot.id()));
                ProfileService.delete(server, CONSOLE, snapshot.id());
            }
            quietly(() -> {
                ModuleStateData data = ModuleStateData.get(server.overworld());
                modules.forEach(data::setState);
                ModuleStateService.refreshAndBroadcast(server);
                return null;
            });
        };
    }

    private static void applyAndRevertGroup(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Runnable restore = remember(server);
        try {
            ProfileDocument hardcore = ProfileService.find(server, "hardcore");
            check(helper, hardcore != null && hardcore.meta().kind() == ProfileDocument.Kind.PRESET, () -> "no hardcore preset");
            ProfileValidator.Plan plan = ProfileService.preview(server, CONSOLE, hardcore);
            check(helper, plan.applicable() && !plan.changes().isEmpty(), () -> "preview: " + plan.errors());
            double damageBefore = Config.spellDamageMultiplier;

            ProfileService.Result applied = quietly(() -> ProfileService.apply(server, CONSOLE, "hardcore"));
            check(helper, applied.ok() && applied.changed() == plan.changes().size(), () -> "apply: " + applied);
            check(helper, Config.spellDamageMultiplier == 1.5 && ModuleManager.isEnabled(Module.MINISTRY)
                    && Config.ministryFineScalePercent == 200, () -> "the preset's values did not land");

            List<AdminChangeRecord> recent = service().history().recent(plan.changes().size());
            String group = recent.get(0).group();
            check(helper, group != null && group.startsWith("profile:hardcore:")
                            && service().history().inGroup(group).size() == plan.changes().size()
                            && recent.stream().allMatch(r -> r.kind() == AdminChangeRecord.Kind.PROFILE),
                    () -> "the batch was not recorded as one group: " + group);

            ProfileService.Result reverted = quietly(() -> ProfileService.revertGroup(server, CONSOLE, group));
            check(helper, reverted.ok() && Config.spellDamageMultiplier == damageBefore,
                    () -> "reverting the group did not restore: " + reverted);
            check(helper, service().history().inGroup(group).stream().allMatch(AdminChangeRecord::undone),
                    () -> "the reverted group can still be reverted");
            helper.succeed();
        } finally {
            restore.run();
        }
    }

    private static void partialFailureRollsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Runnable restore = remember(server);
        try {
            String damage = service().registry().get(id("spell_damage_multiplier")).currentText();
            // Wizardkind is the only selectable heritage: closing it is refused in every pass ("last selectable").
            Identifier wizardkind = HeritageRuleSettings.id(Heritage.WIZARDKIND, HeritageRuleSettings.Property.SELECTABLE);
            List<ProfileValidator.Change> conflict = List.of(
                    new ProfileValidator.Change(id("spell_damage_multiplier"), damage, "2", ApplyMode.RUNTIME, null),
                    new ProfileValidator.Change(wizardkind, "true", "false", ApplyMode.RUNTIME, null));
            ProfileApplier.Outcome refused = quietly(() -> ProfileApplier.apply(service(), CONSOLE, conflict,
                    "gametest:conflict", AdminChangeRecord.Kind.PROFILE, server));
            check(helper, !refused.applied() && refused.rollbackFailed().isEmpty()
                            && service().registry().get(id("spell_damage_multiplier")).currentText().equals(damage),
                    () -> "a rule conflict left the batch half applied: " + refused);

            List<ProfileValidator.Change> invalid = List.of(
                    new ProfileValidator.Change(id("spell_damage_multiplier"), damage, "2", ApplyMode.RUNTIME, null),
                    new ProfileValidator.Change(id("brew_failure_multiplier"), "1", "not-a-number", ApplyMode.RUNTIME, null));
            ProfileApplier.Outcome bad = quietly(() -> ProfileApplier.apply(service(), CONSOLE, invalid,
                    "gametest:invalid", AdminChangeRecord.Kind.PROFILE, server));
            check(helper, !bad.applied() && service().registry().get(id("spell_damage_multiplier")).currentText().equals(damage),
                    () -> "an invalid value left the batch half applied: " + bad);

            List<ProfileValidator.Change> stale = List.of(
                    new ProfileValidator.Change(id("spell_damage_multiplier"), "9.5", "2", ApplyMode.RUNTIME, null));
            ProfileApplier.Outcome staleOutcome = ProfileApplier.apply(service(), CONSOLE, stale, "gametest:stale",
                    AdminChangeRecord.Kind.PROFILE, server);
            check(helper, !staleOutcome.applied() && "stale".equals(staleOutcome.failures().get(0).code()),
                    () -> "a stale plan was applied: " + staleOutcome);
            check(helper, service().history().inGroup("gametest:conflict").stream().noneMatch(AdminChangeRecord::undoable),
                    () -> "a rolled-back batch is still revertible");
            helper.succeed();
        } finally {
            restore.run();
        }
    }

    private static String file(int version, String settings) {
        return "{\"schema\":\"" + ProfileCodec.SCHEMA_ID + "\",\"schema_version\":" + version
                + ",\"profile\":{\"id\":\"imported\",\"name\":\"Gametest Import\"},\"mode\":\"merge\",\"settings\":{"
                + settings + "},\"modules\":{}}";
    }

    private static void importValidation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String keptId = null;
        try {
            ProfileService.Result malformed = ProfileService.importText(server, CONSOLE, "{ not json", "malformed");
            check(helper, "invalid_file".equals(malformed.outcome()), () -> "malformed: " + malformed);
            ProfileService.Result version = ProfileService.importText(server, CONSOLE, file(99, ""), "future");
            check(helper, "invalid_file".equals(version.outcome())
                    && "incompatible_version".equals(version.issues().get(0).code()), () -> "future version: " + version);
            ProfileService.Result invalid = ProfileService.importText(server, CONSOLE,
                    file(1, "\"" + NS + ":spell_damage_multiplier\":\"eleven\""), "invalid");
            check(helper, "refused".equals(invalid.outcome()), () -> "invalid value: " + invalid);
            ProfileService.Result range = ProfileService.importText(server, CONSOLE,
                    file(1, "\"" + NS + ":spell_damage_multiplier\":\"500\""), "range");
            check(helper, "refused".equals(range.outcome())
                    && range.issues().stream().anyMatch(i -> i.code().equals("out_of_range")), () -> "out of range: " + range);

            ProfileService.Result usable = ProfileService.importText(server, CONSOLE, file(1,
                    "\"" + NS + ":spell_damage_multiplier\":\"1.25\",\"" + NS + ":setting_from_the_future\":\"1\""), "usable");
            keptId = usable.id();
            String kept = keptId;
            check(helper, usable.ok() && usable.issues().stream().anyMatch(i -> i.code().equals("unknown_setting"))
                            && ProfileService.find(server, kept) != null
                            && ProfileService.find(server, kept).meta().kind() == ProfileDocument.Kind.CUSTOM,
                    () -> "a usable import was not kept with its warning: " + usable);
            check(helper, Config.spellDamageMultiplier != 1.25, () -> "importing applied the profile");

            // The folder door: a file in the profile folder imports; names that escape it are refused.
            Files.createDirectories(ProfileService.folder(server));
            Files.writeString(ProfileService.folder(server).resolve("gametest_bad.json"), "[[[", StandardCharsets.UTF_8);
            check(helper, "invalid_file".equals(ProfileService.importFile(server, CONSOLE, "gametest_bad.json").outcome()),
                    () -> "a malformed file imported");
            for (String escape : List.of("../level.dat", "..\\level.json", "sub/x.json")) {
                check(helper, "invalid_file".equals(ProfileService.importFile(server, CONSOLE, escape).outcome()),
                        () -> "an escaping file name was read: " + escape);
            }
            Files.deleteIfExists(ProfileService.folder(server).resolve("gametest_bad.json"));
            helper.succeed();
        } catch (java.io.IOException e) {
            helper.fail("io: " + e);
        } finally {
            if (keptId != null) {
                ProfileService.delete(server, CONSOLE, keptId);
            }
        }
    }

    private static void snapshotRestore(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Runnable restore = remember(server);
        String snapshot = null;
        try {
            service().change(CONSOLE, id("brew_failure_multiplier"), "2", true);
            // No label: the snapshot names itself (the panel's default).
            ProfileService.Result taken = ProfileService.saveAs(server, CONSOLE, "", ProfileDocument.Kind.SNAPSHOT);
            snapshot = taken.id();
            check(helper, taken.ok(), () -> "snapshot: " + taken);
            service().change(CONSOLE, id("brew_failure_multiplier"), "0.5", true);
            check(helper, Config.brewFailureMultiplier == 0.5, () -> "setup");
            String snapshotId = snapshot;
            ProfileService.Result restored = quietly(() -> ProfileService.apply(server, CONSOLE, snapshotId));
            check(helper, restored.ok() && Config.brewFailureMultiplier == 2.0, () -> "restore: " + restored);
            check(helper, "builtin".equals(ProfileService.delete(server, CONSOLE, "default").outcome()),
                    () -> "a built-in preset was deletable");
            helper.succeed();
        } finally {
            if (snapshot != null) {
                ProfileService.delete(server, CONSOLE, snapshot);
            }
            restore.run();
        }
    }

    private static void historyPersistsAndReverts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String before = service().registry().get(id("dummy_scare_radius")).currentText();
        try {
            AdminResult first = service().change(CONSOLE, id("dummy_scare_radius"), "20", true);
            check(helper, first.applied(), () -> "setup " + first);
            AdminChangeRecord record = service().history().recent(1).get(0);
            check(helper, record.settingId().getPath().equals("dummy_scare_radius") && record.newValue().equals("20")
                    && record.oldValue().equals(before) && record.actorName().equals("game-test") && record.timestampMillis() > 0,
                    () -> "record: " + record);
            check(helper, AdminHistoryData.get(server).records().stream().anyMatch(r -> r.sequence() == record.sequence()),
                    () -> "the change was not written to the world's history");

            service().change(CONSOLE, id("dummy_scare_radius"), "30", true);
            AdminChangeRecord latest = service().history().recent(1).get(0);
            AdminResult stale = service().revert(CONSOLE, record.sequence());
            check(helper, stale.rejection() == AdminRejection.CONFLICT, () -> "a stale revert went through: " + stale);

            AdminResult reverted = service().revert(CONSOLE, latest.sequence());
            check(helper, reverted.applied() && Config.dummyScareRadius == 20, () -> "revert one: " + reverted);
            check(helper, service().history().find(latest.sequence()).map(AdminChangeRecord::undone).orElse(false)
                    && AdminHistoryData.get(server).records().stream()
                    .anyMatch(r -> r.sequence() == latest.sequence() && r.undone()),
                    () -> "the revert was not recorded in the world's history");
            helper.succeed();
        } finally {
            service().change(CONSOLE, id("dummy_scare_radius"), before, true);
        }
    }

    private static void flagsAndAuthority(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ModuleState azkaban = ModuleManager.state(Module.AZKABAN);
        String target = azkaban == ModuleState.ENABLED ? "disabled" : "enabled";
        ProfileDocument worldgen = new ProfileDocument(1, "", new ProfileDocument.Meta("w", "W", "", "", 0,
                ProfileDocument.Kind.CUSTOM), ProfileDocument.Mode.MERGE, Map.of(), Map.of("azkaban", target));
        ProfileValidator.Plan plan = ProfileService.preview(server, CONSOLE, worldgen);
        check(helper, plan.applicable() && plan.touchesWorldgen() && !plan.needsRestart()
                        && plan.changes().get(0).applyMode() == ApplyMode.NEW_CHUNKS,
                () -> "the worldgen change was not flagged: " + plan.changes());

        check(helper, "unauthorized".equals(ProfileService.saveAs(server, NOBODY, "x", ProfileDocument.Kind.CUSTOM).outcome())
                        && "unauthorized".equals(ProfileService.apply(server, NOBODY, "default").outcome())
                        && "unauthorized".equals(ProfileService.importText(server, NOBODY, "{}", "x").outcome())
                        && "unauthorized".equals(ProfileService.delete(server, NOBODY, "default").outcome())
                        && "unauthorized".equals(ProfileService.export(server, NOBODY, "default").outcome()),
                () -> "an actor without authority used profiles");
        AdminContext noConfig = AdminContext.detached(null, "no-config", EnumSet.complementOf(EnumSet.of(AdminCapability.CONFIG)));
        ProfileValidator.Plan limited = ProfileService.preview(server, noConfig, ProfileService.find(server, "hardcore"));
        check(helper, !limited.applicable() && limited.errors().stream().anyMatch(i -> i.code().equals("unauthorized")),
                () -> "a profile touching settings the actor may not change was applicable");
        helper.succeed();
    }
}
