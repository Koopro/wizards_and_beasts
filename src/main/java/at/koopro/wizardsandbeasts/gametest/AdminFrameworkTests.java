package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.admin.config.catalog.ConfigSettingCatalog;
import at.koopro.wizardsandbeasts.command.AdminAccess;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminResetSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingResultS2CPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSnapshotS2CPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Control Center's server boundary, driven through the same entry points its packets use
 * ({@link AdminNetworkService}) against the live {@code Config}.
 *
 * <p>Each scenario that changes a real config value does so and restores it inside one synchronous step
 * (try/finally), so no scenario running beside it ever observes the changed value — the 2026-09-17 rule for
 * global switches in parallel game tests.
 */
public final class AdminFrameworkTests {

    private static final Identifier DAYS_PER_YEAR = id("ministry_days_per_year");
    private static final Identifier WIND_VOLUME = id("broom_wind_volume");

    private AdminFrameworkTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_packet_from_a_non_admin_is_refused",
                "admin: a change packet from a player who is not an administrator changes nothing and reveals nothing",
                AdminFrameworkTests::nonAdminIsRefused);
        tests.add("admin_packet_from_an_admin_is_applied_and_echoed",
                "admin: an administrator's change is validated, stored in Config, and echoed with the authoritative value",
                AdminFrameworkTests::adminChangeIsAppliedAndEchoed);
        tests.add("admin_catalog_binds_to_config",
                "admin: every catalog entry binds to a real Config key, with its range read from the spec",
                AdminFrameworkTests::catalogBindsToConfig);
    }

    private static void nonAdminIsRefused(GameTestHelper helper) {
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "AdminOutsider", GameType.SURVIVAL);
        try {
            WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, !AdminAccess.allows(outsider.createCommandSourceStack()),
                    () -> "setup: a fresh mock player already counts as an administrator");
            int before = Config.ministryDaysPerYear;

            AdminResult result = AdminNetworkService.change(outsider,
                    new AdminChangeSettingC2SPayload(7, DAYS_PER_YEAR, Integer.toString(before + 1), true));
            check(helper, result.rejected() && result.rejection() == AdminRejection.UNAUTHORIZED,
                    () -> "a non-admin change was not refused as unauthorised: " + result);
            check(helper, result.value().isEmpty(), () -> "a refusal disclosed the setting's value to a non-admin");
            check(helper, Config.ministryDaysPerYear == before, () -> "a non-admin packet changed the config");

            AdminResult reset = AdminNetworkService.reset(outsider, new AdminResetSettingC2SPayload(8, DAYS_PER_YEAR, true));
            check(helper, reset.rejection() == AdminRejection.UNAUTHORIZED, () -> "a non-admin reset was not refused: " + reset);

            // An unknown id must read exactly like a known one, or the refusal becomes an id oracle.
            AdminResult probe = AdminNetworkService.change(outsider,
                    new AdminChangeSettingC2SPayload(9, id("no_such_setting"), "1", true));
            check(helper, probe.rejection() == AdminRejection.UNAUTHORIZED,
                    () -> "a non-admin could tell an unknown id from a known one: " + probe);

            check(helper, !AdminNetworkService.openFor(outsider), () -> "the Control Center opened for a non-admin");
            check(helper, !AdminNetworkService.refreshFor(outsider, null), () -> "a non-admin was sent a snapshot");
            List<CustomPacketPayload> sent = WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, sent.stream().noneMatch(AdminSnapshotS2CPayload.class::isInstance),
                    () -> "a snapshot reached a non-admin");
            check(helper, sent.stream().filter(AdminSettingResultS2CPayload.class::isInstance)
                            .map(AdminSettingResultS2CPayload.class::cast)
                            .allMatch(p -> p.result().rejected() && p.result().value().isEmpty()),
                    () -> "a non-admin was told something other than a bare refusal");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
        }
    }

    private static void adminChangeIsAppliedAndEchoed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID listed = firstAllowListedUuid();
        ServerPlayer admin = listed == null
                ? WizardTestSupport.placeMockPlayer(helper, "AdminOperator", GameType.SURVIVAL)
                : WizardTestSupport.placeMockPlayer(helper, "AdminListed", listed, GameType.SURVIVAL);
        boolean opped = false;
        int original = Config.ministryDaysPerYear;
        try {
            if (listed == null) {
                // No allow-list configured: operator permission is the rule, so make this player one.
                server.getPlayerList().op(admin.nameAndId());
                opped = true;
            }
            check(helper, AdminAccess.allows(admin.createCommandSourceStack()), () -> "setup: the admin is not an admin");
            WizardTestSupport.drainClientboundPayloads(admin);

            int target = original == 3650 ? original - 1 : original + 1;
            // Dangerous: an unconfirmed change is held back, not applied, and says why.
            AdminResult held = AdminNetworkService.change(admin,
                    new AdminChangeSettingC2SPayload(40, DAYS_PER_YEAR, Integer.toString(target), false));
            check(helper, held.needsConfirmation() && held.detailKey() != null && Config.ministryDaysPerYear == original,
                    () -> "an unconfirmed dangerous change was not held for confirmation: " + held);
            AdminResult applied = AdminNetworkService.change(admin,
                    new AdminChangeSettingC2SPayload(41, DAYS_PER_YEAR, Integer.toString(target), true));
            check(helper, applied.applied(), () -> "an admin's in-range change was not applied: " + applied);
            // Config.onLoad ran synchronously from the save, so gameplay already reads the new value.
            check(helper, Config.ministryDaysPerYear == target,
                    () -> "Config did not pick the change up: " + Config.ministryDaysPerYear + " != " + target);
            check(helper, applied.value().equals(Integer.toString(target)) && applied.previousValue().equals(Integer.toString(original)),
                    () -> "the result did not carry the authoritative before/after: " + applied);

            List<CustomPacketPayload> sent = WizardTestSupport.drainClientboundPayloads(admin);
            check(helper, sent.stream().anyMatch(p -> p instanceof AdminSettingResultS2CPayload r
                            && r.requestId() == 41 && r.result().value().equals(Integer.toString(target))),
                    () -> "the admin was not sent the result for request 41: " + sent);

            AdminResult outOfRange = AdminNetworkService.change(admin, new AdminChangeSettingC2SPayload(42, DAYS_PER_YEAR, "0", true));
            check(helper, outOfRange.rejection() == AdminRejection.OUT_OF_RANGE && Config.ministryDaysPerYear == target,
                    () -> "an out-of-range value was not refused cleanly: " + outOfRange);
            check(helper, outOfRange.value().equals(Integer.toString(target)),
                    () -> "a refusal did not carry the value the row must snap back to: " + outOfRange);

            AdminResult garbage = AdminNetworkService.change(admin, new AdminChangeSettingC2SPayload(43, DAYS_PER_YEAR, "12; drop", true));
            check(helper, garbage.rejection() == AdminRejection.INVALID_VALUE, () -> "malformed text was not refused: " + garbage);

            AdminResult unknown = AdminNetworkService.change(admin, new AdminChangeSettingC2SPayload(44, id("no_such_setting"), "1", true));
            check(helper, unknown.rejection() == AdminRejection.UNKNOWN_SETTING, () -> "an unknown id was not refused: " + unknown);

            float wind = Config.broomWindVolume;
            AdminResult client = AdminNetworkService.change(admin, new AdminChangeSettingC2SPayload(45, WIND_VOLUME, "0", true));
            check(helper, client.rejection() == AdminRejection.CLIENT_ONLY && Config.broomWindVolume == wind,
                    () -> "a client-read setting was written from the server: " + client);

            AdminResult reset = AdminNetworkService.reset(admin, new AdminResetSettingC2SPayload(46, DAYS_PER_YEAR, true));
            AdminSetting<?> days = AdminSettings.registry().get(DAYS_PER_YEAR);
            check(helper, days != null && reset.value().equals(days.defaultText())
                            && Integer.toString(Config.ministryDaysPerYear).equals(days.defaultText()),
                    () -> "reset did not restore the default: " + reset);
            helper.succeed();
        } finally {
            // Put the operator's configured value back whatever happened above, with the console's authority.
            AdminSettings.service().change(AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class)),
                    DAYS_PER_YEAR, Integer.toString(original), true);
            if (opped) {
                server.getPlayerList().deop(admin.nameAndId());
            }
            WizardTestSupport.retire(helper, admin);
        }
    }

    private static void catalogBindsToConfig(GameTestHelper helper) {
        for (ConfigSettingCatalog.Entry entry : ConfigSettingCatalog.ENTRIES) {
            AdminSetting<?> setting = AdminSettings.registry().get(id(entry.path()));
            check(helper, setting != null, () -> "catalog entry " + entry.path() + " is not registered");
            if (setting == null) {
                continue;
            }
            check(helper, setting.binding().available(), () -> entry.path() + " is not readable");
            check(helper, setting.writable() == (entry.scope() == SettingScope.SERVER),
                    () -> entry.path() + " writability disagrees with its scope");
            check(helper, setting.dangerous() == entry.dangerous(), () -> entry.path() + " lost its dangerous flag");
        }
        AdminSetting<?> days = AdminSettings.registry().get(DAYS_PER_YEAR);
        check(helper, days != null && days.type().kind() == SettingKind.INTEGER
                        && days.type().min() == 1 && days.type().max() == 3650,
                () -> "ministry_days_per_year did not take its range from Config's defineInRange(1, 3650)");
        AdminSetting<?> profile = AdminSettings.registry().get(id("perf_profile"));
        check(helper, profile != null && profile.type().kind() == SettingKind.ENUM
                        && profile.type().options().equals(List.of("LOW", "MEDIUM", "HIGH")),
                () -> "perf_profile did not become a choice over Config.PerfProfile");
        // The Config catalog, the module-state settings, the Apparition rules, one rule
        // per heritage and applicable property, and the creature rules (natural spawn + two per variant); spell values are resolved by a provider and are deliberately not in
        // the static registry.
        int heritageRules = 0;
        for (at.koopro.wizardsandbeasts.heritage.Heritage heritage : at.koopro.wizardsandbeasts.heritage.Heritage.values()) {
            heritageRules += at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings.applicable(heritage).size();
        }
        int creatureRules = 0;
        for (String creature : at.koopro.wizardsandbeasts.registry.ModCreatures.ROSTER) {
            creatureRules += 1 + 2 * at.koopro.wizardsandbeasts.creature.variant.CreatureVariants.of(creature).size();
        }
        // The module switches and the Apparition rules, counted by building them, so this formula never restates
        // which modules a section exposes.
        at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry modules = new at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry();
        at.koopro.wizardsandbeasts.admin.config.catalog.ModuleStateSettings.contribute(modules);
        at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry apparition = new at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry();
        at.koopro.wizardsandbeasts.admin.travel.ApparitionRuleSettings.contribute(apparition);
        int expected = ConfigSettingCatalog.ENTRIES.size() + modules.size() + apparition.size() + heritageRules + creatureRules;
        check(helper, AdminSettings.registry().size() == expected
                        && AdminSettings.registry().get(id("module_dark_arts")) != null
                        && AdminSettings.registry().get(id("module_heritage")) != null,
                () -> "registry size " + AdminSettings.registry().size() + " != " + expected
                        + " (catalog + module settings + heritage rules)");
        helper.succeed();
    }

    /** The first valid UUID on the admin allow-list, or null when none is configured. */
    static UUID firstAllowListedUuid() {
        for (String raw : Config.adminUuids()) {
            try {
                return UUID.fromString(raw.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException notAUuid) {
                // AdminAccess ignores junk entries; so does this.
            }
        }
        return null;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }
}
