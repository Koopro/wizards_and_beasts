package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.access.AdminPermissions;
import at.koopro.wizardsandbeasts.admin.access.AdminPolicy;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets;
import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualService;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The 2026-10-07 additions to the Control Center's server boundary: one permission node per capability, and command
 * equivalents for the mutations that had none (player actions, performance presets, beam presets).
 *
 * <p>Commands run from the console source with their output collected, so a refusal's text is in the failure
 * message. Every scenario that changes a server value restores it in the same synchronous step.
 */
final class AdminControlTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));

    private AdminControlTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_permission_nodes_default_to_the_admin_rule",
                "admin: one permission node per capability, registered; an outsider holds none, an administrator all",
                AdminControlTests::permissionNodes);
        tests.add("admin_players_act_command_uses_the_service",
                "admin: /wandb admin players act holds a destructive action until confirmed, then applies and logs it",
                AdminControlTests::playersActCommand);
        tests.add("admin_ops_commands_use_the_services",
                "admin: performance and beam preset commands confirm first, apply through the services, refuse outsiders",
                AdminControlTests::opsCommands);
    }

    private static List<String> console(MinecraftServer server, String command) {
        List<String> said = new ArrayList<>();
        CommandSource listener = WizardTestSupport.recordingSource(said);
        CommandSourceStack source = server.createCommandSourceStack().withSource(listener);
        server.getCommands().performPrefixedCommand(source, command);
        return said;
    }

    private static void permissionNodes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "NodesOutsider", GameType.SURVIVAL);
        UUID listed = AdminFrameworkTests.firstAllowListedUuid();
        ServerPlayer admin = listed == null
                ? WizardTestSupport.placeMockPlayer(helper, "NodesOperator", GameType.SURVIVAL)
                : WizardTestSupport.placeMockPlayer(helper, "NodesListed", listed, GameType.SURVIVAL);
        boolean opped = false;
        try {
            if (listed == null) {
                server.getPlayerList().op(admin.nameAndId());
                opped = true;
            }
            var registered = PermissionAPI.getRegisteredNodes();
            for (AdminCapability capability : AdminCapability.values()) {
                PermissionNode<Boolean> node = AdminPermissions.node(capability);
                check(helper, registered.contains(node), () -> AdminPermissions.nodeName(capability) + " is not registered");
                check(helper, node.getNodeName().equals(AdminPermissions.nodeName(capability)),
                        () -> "node named " + node.getNodeName());
            }
            check(helper, AdminPolicy.capabilitiesOf(outsider.createCommandSourceStack()).isEmpty()
                            && !AdminPolicy.isAdministrator(outsider.createCommandSourceStack()),
                    () -> "an outsider holds an administrative capability");
            check(helper, AdminPolicy.capabilitiesOf(admin.createCommandSourceStack()).equals(EnumSet.allOf(AdminCapability.class)),
                    () -> "an administrator lacks a capability: " + AdminPolicy.capabilitiesOf(admin.createCommandSourceStack()));
            check(helper, AdminPolicy.capabilitiesOf(server.createCommandSourceStack()).equals(EnumSet.allOf(AdminCapability.class)),
                    () -> "the console lacks a capability");
            helper.succeed();
        } finally {
            if (opped) {
                server.getPlayerList().deop(admin.nameAndId());
            }
            WizardTestSupport.retire(helper, outsider);
            WizardTestSupport.retire(helper, admin);
        }
    }

    private static void playersActCommand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "ActCmdSubject", GameType.SURVIVAL);
        try {
            // By name, as an administrator types it (a UUID parses as an entity selector, which a player argument refuses).
            String who = subject.getName().getString();
            subject.addEffect(new MobEffectInstance(MobEffects.SPEED, 2000, 1));
            int logged = PlayerActionLog.get(server).recent(subject.getUUID(), 100).size();

            List<String> held = console(server, "wandb admin players act " + who + " effects_clear");
            check(helper, subject.hasEffect(MobEffects.SPEED) && String.join(" ", held).contains("confirm"),
                    () -> "an unconfirmed clear was not held: " + held);

            List<String> unknown = console(server, "wandb admin players act " + who + " no_such_action confirm");
            check(helper, String.join(" ", unknown).contains("Unknown player action"), () -> "unknown action: " + unknown);

            List<String> done = console(server, "wandb admin players act " + who + " effects_clear confirm");
            check(helper, subject.getActiveEffects().isEmpty() && String.join(" ", done).contains("Done"),
                    () -> "a confirmed clear did not apply: " + done);

            List<String> invalid = console(server, "wandb admin players act " + who + " money_deposit dragots 1 confirm");
            check(helper, String.join(" ", invalid).contains("invalid_argument"), () -> "unknown coin: " + invalid);

            // The held attempt, the applied one and the refused coin are all in the world's log.
            int now = PlayerActionLog.get(server).recent(subject.getUUID(), 100).size();
            check(helper, now >= logged + 3, () -> "the command's attempts were not logged: " + logged + " -> " + now);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static void opsCommands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "OpsOutsider", GameType.SURVIVAL);
        Map<String, String> original = new HashMap<>();
        for (String path : PerformancePresets.Preset.LOW.settings().keySet()) {
            AdminSetting<?> setting = AdminSettings.service().registry().get(id(path));
            if (setting != null) {
                original.put(path, setting.currentText());
            }
        }
        // A preset that differs from what is in force now, so applying it is a real change.
        PerformancePresets.Preset now = PerformancePresets.current(AdminSettings.service().registry());
        PerformancePresets.Preset target = now == PerformancePresets.Preset.LOW
                ? PerformancePresets.Preset.HIGH : PerformancePresets.Preset.LOW;
        String copyName = "gametest copy " + UUID.randomUUID().toString().substring(0, 6);
        String copyId = null;
        try {
            // An outsider cannot reach the group at all.
            console(server, "execute as " + outsider.getUUID() + " run wandb admin performance preset " + target.id() + " confirm");
            check(helper, PerformancePresets.current(AdminSettings.service().registry()) == now,
                    () -> "an outsider's preset command changed the settings");

            List<String> held = console(server, "wandb admin performance preset " + target.id());
            check(helper, PerformancePresets.current(AdminSettings.service().registry()) == now
                    && String.join(" ", held).contains("confirm"), () -> "an unconfirmed preset applied: " + held);
            List<String> applied = console(server, "wandb admin performance preset " + target.id() + " confirm");
            check(helper, PerformancePresets.current(AdminSettings.service().registry()) == target,
                    () -> "a confirmed preset did not apply: " + applied);

            // Beam presets: a built-in cannot be deleted; a copy can, but only confirmed.
            List<BeamPreset> presets = BeamVisualService.presets(server);
            BeamPreset builtin = presets.stream().filter(BeamPreset::builtin).findFirst().orElse(null);
            check(helper, builtin != null, () -> "no built-in beam preset to work from");
            List<String> refused = console(server, "wandb admin visual preset delete \"" + builtin.id() + "\" confirm");
            check(helper, BeamVisualService.presets(server).stream().anyMatch(p -> p.id().equals(builtin.id())),
                    () -> "a built-in preset was deleted: " + refused);
            console(server, "wandb admin visual preset duplicate \"" + builtin.id() + "\" \"" + copyName + "\"");
            BeamPreset copy = BeamVisualService.presets(server).stream().filter(p -> p.name().equals(copyName))
                    .findFirst().orElse(null);
            check(helper, copy != null, () -> "duplicate made no preset");
            copyId = copy.id();
            String made = copyId;
            List<String> unconfirmed = console(server, "wandb admin visual preset delete \"" + made + "\"");
            check(helper, BeamVisualService.presets(server).stream().anyMatch(p -> p.id().equals(made)),
                    () -> "an unconfirmed delete removed the preset: " + unconfirmed);
            console(server, "wandb admin visual preset delete \"" + made + "\" confirm");
            check(helper, BeamVisualService.presets(server).stream().noneMatch(p -> p.id().equals(made)),
                    () -> "a confirmed delete left the preset");
            copyId = null;
            helper.succeed();
        } finally {
            original.forEach((path, value) -> AdminSettings.service().change(CONSOLE, id(path), value, true));
            if (copyId != null) {
                BeamVisualService.delete(server, copyId);
            }
            WizardTestSupport.retire(helper, outsider);
        }
    }
}
