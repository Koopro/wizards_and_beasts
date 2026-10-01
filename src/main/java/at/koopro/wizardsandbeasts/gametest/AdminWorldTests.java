package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.travel.ApparitionRuleSettings;
import at.koopro.wizardsandbeasts.apparition.ApparitionRules;
import at.koopro.wizardsandbeasts.apparition.ApparitionRulesData;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.ApparitionStartResult;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.FineSchedule;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Travel, Ministry, Economy and World sections' server boundary: authority (packets and the vault command),
 * runtime changes reaching their single readers, apply-mode flags, economy integrity (no setting moves money), the
 * Ministry rules the law reads, and the travel gates. Every change is restored through the same service; module
 * states are restored through the cache, never through a datapack reload mid-batch.
 */
public final class AdminWorldTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    private static final AdminContext NO_WORLD = AdminContext.detached(null, "game-test-no-world",
            EnumSet.complementOf(EnumSet.of(AdminCapability.WORLD)));
    private static final String NS = "wizards_and_beasts";

    private AdminWorldTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_world_unauthorised_refused",
                "admin world/economy/travel: non-admins and admins without the area's capability are refused; the vault money commands refuse non-admins",
                AdminWorldTests::unauthorisedRefused);
        tests.add("admin_world_runtime_changes_reach_readers",
                "admin travel/ministry: Floo, Apparition and notoriety settings reach their single readers at once and persist in the world",
                AdminWorldTests::runtimeChangesReachReaders);
        tests.add("admin_world_apply_mode_flags",
                "admin world: structure switches are NEW CHUNKS ONLY on the wire, everything else in these sections is runtime",
                AdminWorldTests::applyModeFlags);
        tests.add("admin_economy_integrity",
                "admin economy: changing every economy setting moves no money; an administrator's vault command still works",
                AdminWorldTests::economyIntegrity);
        tests.add("admin_ministry_rules",
                "admin ministry: opening the Ministry asks for confirmation; fine scale and notoriety cooling drive the law's arithmetic",
                AdminWorldTests::ministryRules);
        tests.add("admin_travel_restrictions",
                "admin travel: the Apparition switch gates the start, the wind-up rule reaches the splinch floor, severity 0 never splinches",
                AdminWorldTests::travelRestrictions);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static void restore(String... paths) {
        for (String path : paths) {
            service().reset(CONSOLE, id(path), true);
        }
    }

    private static int command(MinecraftServer server, net.minecraft.commands.CommandSourceStack source, String command) {
        int[] result = {0};
        server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result[0] = success ? 1 : 0),
                command);
        return result[0];
    }

    // ── authority ──

    private static void unauthorisedRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "WorldAdminOutsider", GameType.SURVIVAL);
        try {
            List<String> paths = List.of("floo_travel_cooldown_ticks", ApparitionRuleSettings.BLINK_COOLDOWN,
                    "ollivander_wand_price_knuts", "module_azkaban", "dummy_scarecrow", "module_ministry",
                    "ministry_notoriety_decay_per_second");
            for (String path : paths) {
                AdminResult result = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(1, id(path), "1", true));
                check(helper, result.rejection() == AdminRejection.UNAUTHORIZED, () -> "a non-admin changed " + path + ": " + result);
            }
            for (String path : List.of("module_azkaban", "dummy_scarecrow")) {
                AdminResult result = service().change(NO_WORLD, id(path), path.startsWith("module_") ? "ENABLED" : "false", true);
                check(helper, result.rejection() == AdminRejection.UNAUTHORIZED,
                        () -> "an admin without the world capability changed " + path + ": " + result);
            }

            // The vault money verbs: a non-admin may read their own vault, nothing else.
            PlayerVaultData vault = outsider.getData(ModAttachments.VAULT_DATA.get());
            long before = vault.getTotalInKnuts();
            net.minecraft.commands.CommandSourceStack own = outsider.createCommandSourceStack();
            check(helper, command(server, own, "wandb player vault deposit galleons 100") == 0,
                    () -> "a non-admin minted Galleons with /wandb player vault deposit");
            check(helper, command(server, own, "wandb player vault clear") == 0, () -> "a non-admin ran vault clear");
            check(helper, vault.getTotalInKnuts() == before, () -> "the non-admin's vault changed: " + before + " → "
                    + vault.getTotalInKnuts());
            check(helper, command(server, own, "wandb player vault") == 1, () -> "a player can no longer read their own vault");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
        }
    }

    // ── runtime ──

    private static void runtimeChangesReachReaders(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            check(helper, service().change(CONSOLE, id("floo_travel_cooldown_ticks"), "200", true).applied()
                    && FlooTravelHandler.cooldownTicks() == 200, () -> "Floo cooldown did not reach FlooTravelHandler");
            check(helper, service().change(CONSOLE, id("floo_misfire_chance_percent"), "0", true).applied()
                    && Config.flooMisfireChancePercent == 0, () -> "Floo misfire chance did not reach Config");

            check(helper, service().change(CONSOLE, id(ApparitionRuleSettings.BLINK_COOLDOWN), "80", true).applied()
                    && ApparitionRules.cooldownTicks(ApparitionTier.BLINK) == 80, () -> "blink cooldown did not reach ApparitionRules");
            check(helper, service().change(CONSOLE, id(ApparitionRuleSettings.LICENCE_PROFICIENCY), "50", true).applied()
                    && Math.abs(ApparitionRules.requiredLicenceProficiency() - 0.5f) < 1e-6,
                    () -> "licence proficiency did not reach ApparitionRules");
            check(helper, ApparitionRulesData.get(server).tuning().blinkCooldownTicks() == 80,
                    () -> "the Apparition rule was not stored in the world");
            check(helper, ApparitionRules.cooldownTicks(ApparitionTier.ANCHORED) == ApparitionTier.ANCHORED.cooldownTicks(),
                    () -> "changing the blink cooldown moved the anchored one");

            AdminResult unconfirmed = service().change(CONSOLE, id(ApparitionRuleSettings.ANCHORED_COOLDOWN), "100", false);
            check(helper, unconfirmed.needsConfirmation() && ApparitionRules.cooldownTicks(ApparitionTier.ANCHORED)
                            == ApparitionTier.ANCHORED.cooldownTicks(),
                    () -> "cutting the anchored cooldown below half did not ask for confirmation: " + unconfirmed);

            check(helper, service().change(CONSOLE, id("ministry_notoriety_decay_per_second"), "0.5", true).applied()
                    && Config.ministryNotorietyDecayPerSecond == 0.5, () -> "notoriety cooling did not reach Config");
            helper.succeed();
        } finally {
            restore("floo_travel_cooldown_ticks", "floo_misfire_chance_percent", ApparitionRuleSettings.BLINK_COOLDOWN,
                    ApparitionRuleSettings.LICENCE_PROFICIENCY, "ministry_notoriety_decay_per_second");
        }
        check(helper, ApparitionRules.current().equals(ApparitionRules.Tuning.AUTHORED),
                () -> "reset did not return Apparition to its authored rules: " + ApparitionRules.current());
    }

    // ── restart / new-chunk flags ──

    private static void applyModeFlags(GameTestHelper helper) {
        Map<String, ApplyMode> expected = new LinkedHashMap<>();
        expected.put("module_azkaban", ApplyMode.NEW_CHUNKS);
        expected.put("module_chamber_of_secrets", ApplyMode.NEW_CHUNKS);
        expected.put("module_pocket_dimensions", ApplyMode.RUNTIME);
        expected.put("module_ministry", ApplyMode.RUNTIME);
        expected.put("module_gringotts", ApplyMode.RUNTIME);
        expected.put("floo_travel_cooldown_ticks", ApplyMode.RUNTIME);
        expected.put(ApparitionRuleSettings.WINDUP_DAMAGE_MODE, ApplyMode.RUNTIME);
        expected.put("dummy_scarecrow", ApplyMode.RUNTIME);
        expected.put("dragot_galleon_rate", ApplyMode.RUNTIME);
        for (Map.Entry<String, ApplyMode> entry : expected.entrySet()) {
            AdminSetting<?> setting = AdminSettings.registry().get(id(entry.getKey()));
            check(helper, setting != null, () -> "missing setting " + entry.getKey());
            AdminSettingDescriptor descriptor = AdminSettingDescriptor.of(setting, CONSOLE);
            check(helper, descriptor.applyMode() == entry.getValue() && setting.applyMode() == entry.getValue(),
                    () -> entry.getKey() + " is " + descriptor.applyMode() + ", expected " + entry.getValue());
            check(helper, !descriptor.restartRequired(), () -> entry.getKey() + " claims a restart it does not need");
        }
        // Every setting in the new sections is filed where its panel looks for it.
        for (AdminCategory category : List.of(AdminCategory.ECONOMY, AdminCategory.WORLD)) {
            check(helper, !AdminSettings.registry().inCategory(category).isEmpty(), () -> category + " has no settings");
        }
        helper.succeed();
    }

    // ── economy ──

    private static void economyIntegrity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer saver = WizardTestSupport.placeMockPlayer(helper, "EconomySaver", GameType.SURVIVAL);
        List<String> economy = List.of("ollivander_wand_price_knuts", "floo_registration_fee_knuts", "skill_respec_cost_knuts",
                "dragot_galleon_rate");
        try {
            PlayerVaultData vault = saver.getData(ModAttachments.VAULT_DATA.get());
            vault.resetAll();
            vault.depositGalleons(3);
            vault.depositKnuts(7);
            long before = vault.getTotalInKnuts();
            String[] values = {"0", "99999", "1", "50"};
            for (int i = 0; i < economy.size(); i++) {
                String path = economy.get(i);
                AdminResult result = service().change(CONSOLE, id(path), values[i], true);
                check(helper, result.applied(), () -> path + " refused: " + result);
            }
            check(helper, vault.getTotalInKnuts() == before,
                    () -> "an economy setting moved money: " + before + " → " + vault.getTotalInKnuts());
            for (AdminSetting<?> setting : AdminSettings.registry().inCategory(AdminCategory.ECONOMY)) {
                String path = setting.id().getPath();
                check(helper, !path.contains("deposit") && !path.contains("give") && !path.contains("balance"),
                        () -> "a money-moving setting slipped into Economy: " + path);
            }

            // The administrator's tool still works, from the console, and only moves what it says.
            net.minecraft.commands.CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
            check(helper, command(server, console, "wandb player vault deposit galleons 2 EconomySaver") == 1
                            && vault.getTotalInKnuts() == before + 2L * at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper.KNUTS_PER_GALLEON,
                    () -> "the admin vault deposit did not work: " + vault.getTotalInKnuts());
            check(helper, command(server, console, "wandb player vault withdraw galleons 2 EconomySaver") == 1
                    && vault.getTotalInKnuts() == before, () -> "the admin vault withdraw did not restore the balance");
            helper.succeed();
        } finally {
            restore(economy.toArray(String[]::new));
            WizardTestSupport.retire(helper, saver);
        }
    }

    // ── ministry ──

    private static void ministryRules(GameTestHelper helper) {
        ServerPlayer suspect = WizardTestSupport.placeMockPlayer(helper, "MinistrySuspect", GameType.SURVIVAL);
        Map<Module, ModuleState> modules = new EnumMap<>(ModuleManager.snapshot());
        try {
            if (!ModuleManager.isEnabled(Module.MINISTRY)) {
                AdminResult open = service().change(CONSOLE, id("module_ministry"), "ENABLED", false);
                check(helper, open.needsConfirmation() && !ModuleManager.isEnabled(Module.MINISTRY),
                        () -> "opening the Ministry did not ask for confirmation: " + open);
            }
            check(helper, service().change(CONSOLE, id("ministry_fine_scale_percent"), "200", true).applied()
                    && FineSchedule.assess(986, 1.0f, Config.ministryFineScalePercent) == 1972,
                    () -> "the fine scale did not reach the fine arithmetic");

            // Notoriety cooling, read by TraceService.decay while the Ministry watches.
            ModuleManager.setState(Module.MINISTRY, ModuleManager.State.ENABLED);
            check(helper, TraceService.isActive(), () -> "the Ministry switch does not drive TraceService.isActive");
            MinistryRecords.mutate(suspect, r -> r.withNotoriety(20.0f));
            service().change(CONSOLE, id("ministry_notoriety_decay_per_second"), "0.5", true);
            TraceService.decay(suspect, 200);
            float after = MinistryRecords.get(suspect).notoriety();
            check(helper, Math.abs(after - 15.0f) < 1e-3, () -> "10 s at 0.5/s should cool 20 to 15, got " + after);

            ModuleManager.setState(Module.MINISTRY, ModuleManager.State.DISABLED);
            check(helper, !TraceService.isActive(), () -> "closing the Ministry left the Trace running");
            TraceService.decay(suspect, 200);
            check(helper, Math.abs(MinistryRecords.get(suspect).notoriety() - 15.0f) < 1e-3,
                    () -> "the law ran while the Ministry was closed");
            helper.succeed();
        } finally {
            ModuleManager.acceptAuthoritative(modules);
            restore("ministry_fine_scale_percent", "ministry_notoriety_decay_per_second");
            WizardTestSupport.retire(helper, suspect);
        }
    }

    // ── travel ──

    private static void travelRestrictions(GameTestHelper helper) {
        ServerPlayer traveller = WizardTestSupport.placeMockPlayer(helper, "TravelRestricted", GameType.SURVIVAL);
        Map<Module, ModuleState> modules = new EnumMap<>(ModuleManager.snapshot());
        try {
            ModuleManager.setState(Module.APPARITION, ModuleManager.State.DISABLED);
            check(helper, ApparitionServerLogic.evaluateStart(traveller) == ApparitionStartResult.REJECTED_MODULE_OFF,
                    () -> "Apparition started with its module closed");
            ModuleManager.setState(Module.APPARITION, ModuleManager.State.ENABLED);
            ModuleManager.setState(Module.PLAYER_ABILITIES, ModuleManager.State.ENABLED);
            ApparitionStartResult open = ApparitionServerLogic.evaluateStart(traveller);
            check(helper, open != ApparitionStartResult.REJECTED_MODULE_OFF, () -> "Apparition stayed refused once opened: " + open);

            // The combat rule: a hit during an anchored wind-up.
            check(helper, service().change(CONSOLE, id(ApparitionRuleSettings.WINDUP_DAMAGE_MODE), "LENIENT", true).applied()
                    && ApparitionRules.windupDamageMode() == WindupDamageMode.LENIENT, () -> "wind-up rule not stored");
            SplinchTier lenient = SplinchResolver.floorForWindupDamage(SplinchTier.CLEAN, 1, true, false,
                    ApparitionRules.windupDamageMode());
            service().change(CONSOLE, id(ApparitionRuleSettings.WINDUP_DAMAGE_MODE), "HYBRID", true);
            SplinchTier hybrid = SplinchResolver.floorForWindupDamage(SplinchTier.CLEAN, 1, true, false,
                    ApparitionRules.windupDamageMode());
            check(helper, lenient == SplinchTier.CLEAN && hybrid != SplinchTier.CLEAN,
                    () -> "the wind-up rule did not change the outcome of a hit: lenient " + lenient + ", hybrid " + hybrid);

            // Severity 0: no careless release splinches; 100 is exactly the authored ladder.
            check(helper, service().change(CONSOLE, id(ApparitionRuleSettings.SPLINCH_SEVERITY), "0", true).applied()
                    && SplinchResolver.resolve(ApparitionRules.scaleMiss(30)) == SplinchTier.CLEAN,
                    () -> "severity 0 still splinched");
            service().change(CONSOLE, id(ApparitionRuleSettings.SPLINCH_SEVERITY), "100", true);
            check(helper, ApparitionRules.scaleMiss(30) == 30, () -> "severity 100 changed the miss");
            helper.succeed();
        } finally {
            ModuleManager.acceptAuthoritative(modules);
            restore(ApparitionRuleSettings.WINDUP_DAMAGE_MODE, ApparitionRuleSettings.SPLINCH_SEVERITY);
            WizardTestSupport.retire(helper, traveller);
        }
    }
}
