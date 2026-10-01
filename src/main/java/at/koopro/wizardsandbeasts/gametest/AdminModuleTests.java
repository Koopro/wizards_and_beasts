package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.module.ModuleAdminInfo;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.ApparitionStartResult;
import at.koopro.wizardsandbeasts.loot.BestiaryHarvestLootModifier;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import at.koopro.wizardsandbeasts.module.data.ModuleStateData;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.module.ModuleStateSyncPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Supplier;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Modules section against the live module system: dependency validation and cascade through the admin service and
 * the command, sync to players, authority, apply-mode flags for every module, and that the gates the dependencies cite
 * really behave so. Every change runs with the datapack reload held back (a reload mid-batch would re-run every reload
 * listener under other tests) and the world's module states are restored exactly afterwards.
 */
public final class AdminModuleTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    private static final AdminContext NO_CONFIG = AdminContext.detached(null, "game-test-no-config",
            EnumSet.complementOf(EnumSet.of(AdminCapability.CONFIG)));

    private AdminModuleTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_module_dependencies_enforced",
                "admin modules: enabling Apparition without Player abilities is refused with the reason; disabling Player abilities asks first and then takes Apparition with it",
                AdminModuleTests::dependenciesEnforced);
        tests.add("admin_module_command_dependencies",
                "admin modules: /wandb admin module set refuses a missing dependency and needs confirm before a cascade",
                AdminModuleTests::commandDependencies);
        tests.add("admin_module_sync_and_authority",
                "admin modules: a change reaches players in ModuleStateSyncPayload; non-admins and admins without the config capability are refused",
                AdminModuleTests::syncAndAuthority);
        tests.add("admin_module_flags_and_gates",
                "admin modules: every module has a switch with the right apply mode; the gates the dependency graph cites behave as it says",
                AdminModuleTests::flagsAndGates);
    }

    private static <T> T quietly(Supplier<T> body) {
        return ModuleStateService.withoutDatapackReload(body);
    }

    private static Map<Module, ModuleState> saved(MinecraftServer server) {
        return new EnumMap<>(ModuleStateData.get(server.overworld()).allStates());
    }

    private static void restore(MinecraftServer server, Map<Module, ModuleState> states) {
        quietly(() -> {
            ModuleStateData data = ModuleStateData.get(server.overworld());
            states.forEach(data::setState);
            ModuleStateService.refreshAndBroadcast(server);
            return null;
        });
    }

    private static void set(MinecraftServer server, Module module, ModuleState state) {
        quietly(() -> {
            ModuleStateData.get(server.overworld()).setState(module, state);
            ModuleStateService.refreshAndBroadcast(server);
            return null;
        });
    }

    private static AdminResult change(Module module, ModuleState state, boolean confirmed) {
        return quietly(() -> AdminSettings.service().change(CONSOLE, ModuleAdminInfo.settingId(module), state.name(), confirmed));
    }

    // ── dependencies through the admin service ──

    private static void dependenciesEnforced(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<Module, ModuleState> before = saved(server);
        try {
            set(server, Module.PLAYER_ABILITIES, ModuleState.DISABLED);
            set(server, Module.APPARITION, ModuleState.DISABLED);

            AdminResult refused = change(Module.APPARITION, ModuleState.ENABLED, true);
            ModuleDependencies.Edge edge = ModuleDependencies.dependenciesOf(Module.APPARITION).get(0);
            check(helper, refused.rejection() == AdminRejection.CONFLICT && edge.blockedKey().equals(refused.detailKey())
                            && ModuleManager.state(Module.APPARITION) == ModuleState.DISABLED,
                    () -> "Apparition opened without Player abilities: " + refused);

            check(helper, change(Module.PLAYER_ABILITIES, ModuleState.ENABLED, true).applied(), () -> "abilities");
            check(helper, change(Module.APPARITION, ModuleState.ENABLED, true).applied()
                    && ModuleManager.isEnabled(Module.APPARITION), () -> "Apparition did not open once its base was on");

            AdminResult asks = change(Module.PLAYER_ABILITIES, ModuleState.DISABLED, false);
            check(helper, asks.needsConfirmation() && edge.effectKey().equals(asks.detailKey())
                            && ModuleManager.isEnabled(Module.PLAYER_ABILITIES),
                    () -> "closing Player abilities did not warn about Apparition first: " + asks);

            AdminResult closed = change(Module.PLAYER_ABILITIES, ModuleState.DISABLED, true);
            check(helper, closed.applied() && !ModuleManager.isEnabled(Module.PLAYER_ABILITIES)
                            && ModuleManager.state(Module.APPARITION) == ModuleState.DISABLED
                            && ModuleStateData.get(server.overworld()).state(Module.APPARITION) == ModuleState.DISABLED,
                    () -> "the cascade did not close and store Apparition: " + ModuleManager.state(Module.APPARITION));

            // A partial dependency warns and lets the change through, without touching the dependant.
            set(server, Module.MINISTRY, ModuleState.ENABLED);
            AdminResult partial = change(Module.GRINGOTTS, ModuleState.DISABLED, false);
            check(helper, partial.needsConfirmation(), () -> "closing Gringotts under the Ministry did not warn: " + partial);
            check(helper, change(Module.GRINGOTTS, ModuleState.DISABLED, true).applied()
                    && ModuleManager.isEnabled(Module.MINISTRY), () -> "a partial dependency cascaded");
            helper.succeed();
        } finally {
            restore(server, before);
        }
    }

    // ── dependencies through the command ──

    /** The command's own return value: 1 done, 0 refused (a refusal still "succeeds" as an execution). */
    private static int command(MinecraftServer server, String command) {
        int[] result = {0};
        CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput()
                .withCallback((success, value) -> result[0] = success ? value : 0);
        quietly(() -> {
            server.getCommands().performPrefixedCommand(console, command);
            return null;
        });
        return result[0];
    }

    private static void commandDependencies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<Module, ModuleState> before = saved(server);
        try {
            set(server, Module.PLAYER_ABILITIES, ModuleState.DISABLED);
            set(server, Module.APPARITION, ModuleState.DISABLED);
            check(helper, command(server, "wandb admin module set apparition enabled") == 0
                    && !ModuleManager.isEnabled(Module.APPARITION), () -> "the command opened Apparition without its base");

            set(server, Module.PLAYER_ABILITIES, ModuleState.ENABLED);
            set(server, Module.APPARITION, ModuleState.ENABLED);
            check(helper, command(server, "wandb admin module set player_abilities disabled") == 0
                    && ModuleManager.isEnabled(Module.PLAYER_ABILITIES) && ModuleManager.isEnabled(Module.APPARITION),
                    () -> "the command cascaded without 'confirm'");
            check(helper, command(server, "wandb admin module set player_abilities disabled confirm") == 1
                    && !ModuleManager.isEnabled(Module.PLAYER_ABILITIES) && !ModuleManager.isEnabled(Module.APPARITION),
                    () -> "the confirmed command did not close both");
            helper.succeed();
        } finally {
            restore(server, before);
        }
    }

    // ── sync and authority ──

    private static void syncAndAuthority(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<Module, ModuleState> before = saved(server);
        ServerPlayer watcher = WizardTestSupport.placeMockPlayer(helper, "ModuleWatcher", GameType.SURVIVAL);
        try {
            WizardTestSupport.drainClientboundPayloads(watcher);
            ModuleState target = ModuleManager.state(Module.HANDBOOK) == ModuleState.ENABLED ? ModuleState.PREVIEW : ModuleState.ENABLED;
            AdminResult applied = change(Module.HANDBOOK, target, true);
            check(helper, applied.applied(), () -> "handbook change refused: " + applied);
            ModuleStateSyncPayload last = null;
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(watcher)) {
                if (payload instanceof ModuleStateSyncPayload sync) {
                    last = sync;
                }
            }
            ModuleStateSyncPayload synced = last;
            check(helper, synced != null && synced.resolvedStates().get(Module.HANDBOOK) == target
                            && synced.resolvedStates().size() == Module.values().length
                            && synced.resolvedStates().equals(ModuleManager.snapshot()),
                    () -> "the player was not sent the new module states: " + synced);

            ModuleState now = ModuleManager.state(Module.HANDBOOK);
            AdminResult outsider = quietly(() -> AdminNetworkService.change(watcher, new AdminChangeSettingC2SPayload(1,
                    ModuleAdminInfo.settingId(Module.HANDBOOK), "DISABLED", true)));
            check(helper, outsider.rejection() == AdminRejection.UNAUTHORIZED && ModuleManager.state(Module.HANDBOOK) == now,
                    () -> "a non-admin changed a module: " + outsider);
            AdminResult noConfig = quietly(() -> AdminSettings.service().change(NO_CONFIG,
                    ModuleAdminInfo.settingId(Module.HANDBOOK), "DISABLED", true));
            check(helper, noConfig.rejection() == AdminRejection.UNAUTHORIZED,
                    () -> "an admin without the config capability changed a module: " + noConfig);
            helper.succeed();
        } finally {
            restore(server, before);
            WizardTestSupport.retire(helper, watcher);
        }
    }

    // ── flags and the gates behind the graph ──

    private static void flagsAndGates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Module module : Module.values()) {
            AdminSetting<?> setting = AdminSettings.registry().get(ModuleAdminInfo.settingId(module));
            check(helper, setting != null, () -> "no switch for " + module);
            AdminSettingDescriptor descriptor = AdminSettingDescriptor.of(setting, CONSOLE);
            ApplyMode expected = module == Module.AZKABAN || module == Module.CHAMBER_OF_SECRETS
                    ? ApplyMode.NEW_CHUNKS : ApplyMode.RUNTIME;
            check(helper, descriptor.applyMode() == expected && !descriptor.restartRequired(),
                    () -> module + " is " + descriptor.applyMode() + ", expected " + expected);
            check(helper, descriptor.category() == ModuleAdminInfo.home(module), () -> module + " filed wrongly");
        }

        Map<Module, ModuleState> before = saved(server);
        ServerPlayer traveller = WizardTestSupport.placeMockPlayer(helper, "ModuleGateTraveller", GameType.SURVIVAL);
        try {
            // REQUIRES: Apparition refuses at the start while Player abilities is off.
            set(server, Module.APPARITION, ModuleState.ENABLED);
            set(server, Module.PLAYER_ABILITIES, ModuleState.DISABLED);
            check(helper, ApparitionServerLogic.evaluateStart(traveller) == ApparitionStartResult.REJECTED_MODULE_OFF,
                    () -> "Apparition ran without Player abilities");
            // PARTIAL: the Ministry keeps running but issues no fines without Gringotts.
            set(server, Module.MINISTRY, ModuleState.ENABLED);
            set(server, Module.GRINGOTTS, ModuleState.DISABLED);
            check(helper, !MinistryFines.isActive(), () -> "fines ran without Gringotts");
            set(server, Module.GRINGOTTS, ModuleState.ENABLED);
            check(helper, MinistryFines.isActive() || at.koopro.wizardsandbeasts.Config.ministryFineScalePercent == 0,
                    () -> "fines did not resume with Gringotts");
            // PARTIAL both ways: study-gated drops need the Bestiary and Magizoology.
            set(server, Module.BESTIARY, ModuleState.ENABLED);
            set(server, Module.MAGIZOOLOGY, ModuleState.DISABLED);
            check(helper, !BestiaryHarvestLootModifier.isActive(), () -> "study drops ran without Magizoology");
            set(server, Module.MAGIZOOLOGY, ModuleState.ENABLED);
            check(helper, BestiaryHarvestLootModifier.isActive(), () -> "study drops did not resume");
            helper.succeed();
        } finally {
            restore(server, before);
            WizardTestSupport.retire(helper, traveller);
        }
    }
}
