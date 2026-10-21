package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.operation.AdminAuditLog;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperation;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperationService;
import at.koopro.wizardsandbeasts.admin.report.AdminReport;
import at.koopro.wizardsandbeasts.admin.report.AdminReportService;
import at.koopro.wizardsandbeasts.admin.report.AdminReports;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The report and operation framework on a live server: every registered report builds against real world data, the
 * live-creature operations act through the entity and are confirmed and audited, outsiders are refused and not
 * logged, and the commands print the same reports and run the same operations.
 */
final class AdminReportTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    private static final AdminContext OUTSIDER = AdminContext.detached(null, "outsider", Set.of());

    private AdminReportTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_reports_build_on_a_live_server",
                "admin reports: every registered report builds without failing, with and without a subject",
                AdminReportTests::everyReportBuilds);
        tests.add("admin_creature_operations_are_confirmed_and_audited",
                "admin operations: scale/heal/freeze act on the creature, discard needs confirmation, all are audited",
                AdminReportTests::creatureOperations);
        tests.add("admin_report_commands_use_the_services",
                "admin commands: report list/print and op run through the same services as the Control Center",
                AdminReportTests::commands);
    }

    private static NifflerEntity niffler(GameTestHelper helper) {
        return helper.spawn(ModEntities.NIFFLER.get(), new BlockPos(1, 2, 1));
    }

    private static void everyReportBuilds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "ReportSubject", GameType.SURVIVAL);
        NifflerEntity creature = niffler(helper);
        try {
            for (AdminReport report : AdminReports.all()) {
                AdminReportService.Result bare = AdminReportService.read(CONSOLE, server, report.id(), "");
                check(helper, bare.ok(), () -> report.id() + " failed without a subject: " + bare.status());
                check(helper, !bare.view().sections().isEmpty(), () -> report.id() + " built no sections");
            }
            for (String id : List.of("ministry_licences", "ministry_standing")) {
                AdminReportService.Result read = AdminReportService.read(CONSOLE, server, id, subject.getName().getString());
                check(helper, read.ok() && read.view().lineCount() > 3, () -> id + " on a player: " + read.status()
                        + " " + read.view().lineCount());
            }
            AdminReportService.Result live = AdminReportService.read(CONSOLE, server, "creature_live", creature.getStringUUID());
            check(helper, live.ok() && live.view().sections().size() >= 4, () -> "creature_live on a niffler: " + live.status()
                    + " " + live.view().sections().size());
            check(helper, live.view().subjects().stream().anyMatch(s -> s.id().equals(creature.getStringUUID())),
                    () -> "the niffler is not offered as a subject");
            check(helper, live.view().actions().stream().anyMatch(a -> a.operation().equals("creature_discard") && a.destructive()),
                    () -> "creature_live offers no destructive discard");
            AdminReportService.Result refused = AdminReportService.read(OUTSIDER, server, "audit", "");
            check(helper, refused.status() == AdminReportService.Status.UNAUTHORIZED && refused.view().lineCount() == 0,
                    () -> "an outsider read the audit: " + refused.status());
            helper.succeed();
        } finally {
            creature.discard();
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static void creatureOperations(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        NifflerEntity creature = niffler(helper);
        String id = creature.getStringUUID();
        try {
            AdminAuditLog log = AdminAuditLog.get(server);
            long before = log.recent(1).isEmpty() ? 0 : log.recent(1).get(0).sequence();
            float baseScale = creature.getScale();

            AdminOperation.Outcome outsider = AdminOperationService.perform(OUTSIDER, server, "creature_discard", id, "", true);
            check(helper, !outsider.success() && creature.isAlive(), () -> "an outsider discarded the creature");
            long afterOutsider = log.recent(1).isEmpty() ? 0 : log.recent(1).get(0).sequence();
            check(helper, afterOutsider == before, () -> "an outsider's attempt reached the audit log");

            AdminOperation.Outcome scaled = AdminOperationService.perform(CONSOLE, server, "creature_scale", id, "2", false);
            check(helper, scaled.success() && Math.abs(creature.getScale() - baseScale * 2f) < 0.01f,
                    () -> "scale ×2: " + scaled + " scale " + creature.getScale() + " from " + baseScale);
            AdminOperation.Outcome tooBig = AdminOperationService.perform(CONSOLE, server, "creature_scale", id, "40", false);
            check(helper, tooBig.code().equals("invalid_argument"), () -> "an out-of-range scale was accepted: " + tooBig);
            AdminOperation.Outcome reset = AdminOperationService.perform(CONSOLE, server, "creature_scale", id, "1", false);
            check(helper, reset.success() && Math.abs(creature.getScale() - baseScale) < 0.01f,
                    () -> "scale ×1 did not restore the creature's own size: " + creature.getScale());

            creature.setHealth(1f);
            AdminOperation.Outcome healed = AdminOperationService.perform(CONSOLE, server, "creature_heal", id, "", false);
            check(helper, healed.success() && creature.getHealth() == creature.getMaxHealth(), () -> "heal: " + healed);

            AdminOperation.Outcome frozen = AdminOperationService.perform(CONSOLE, server, "creature_freeze", id, "on", false);
            check(helper, frozen.success() && creature.isNoAi(), () -> "freeze: " + frozen);

            AdminOperation.Outcome held = AdminOperationService.perform(CONSOLE, server, "creature_discard", id, "", false);
            check(helper, held.code().equals("confirm_required") && creature.isAlive(),
                    () -> "an unconfirmed discard was not held: " + held);
            AdminOperation.Outcome gone = AdminOperationService.perform(CONSOLE, server, "creature_discard", id, "", true);
            check(helper, gone.success() && creature.isRemoved(), () -> "a confirmed discard did not remove it: " + gone);

            AdminOperation.Outcome missing = AdminOperationService.perform(CONSOLE, server, "creature_heal", id, "", false);
            check(helper, missing.code().equals("invalid_argument"), () -> "an operation on a removed creature: " + missing);

            List<AdminAuditLog.Entry> entries = log.recent(20);
            long logged = entries.stream().filter(e -> e.sequence() > before && e.subject().equals(id)).count();
            check(helper, logged >= 8, () -> "expected every attempt in the audit log, found " + logged);
            check(helper, entries.stream().anyMatch(e -> e.operation().equals("creature_discard")
                    && e.result().equals("refused:confirm_required")), () -> "the held discard is not in the log");
            helper.succeed();
        } finally {
            if (!creature.isRemoved()) {
                creature.discard();
            }
        }
    }

    private static List<String> console(MinecraftServer server, String command) {
        List<String> said = new ArrayList<>();
        CommandSource listener = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = server.createCommandSourceStack().withSource(listener);
        server.getCommands().performPrefixedCommand(source, command);
        return said;
    }

    private static void commands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        NifflerEntity creature = niffler(helper);
        try {
            List<String> bare = console(server, "wandb admin");
            check(helper, String.join(" ", bare).contains("report list"), () -> "bare /wandb admin from the console: " + bare);

            List<String> list = console(server, "wandb admin report list");
            check(helper, String.join(" ", list).contains("audit"), () -> "report list: " + list);

            List<String> printed = console(server, "wandb admin report creature_live " + creature.getStringUUID());
            check(helper, String.join(" ", printed).contains(creature.getStringUUID()), () -> "report print: " + printed);

            List<String> unknown = console(server, "wandb admin report no_such_report");
            check(helper, !unknown.isEmpty(), () -> "an unknown report printed nothing");

            creature.setHealth(1f);
            List<String> healed = console(server, "wandb admin op creature_heal " + creature.getStringUUID());
            check(helper, creature.getHealth() == creature.getMaxHealth() && String.join(" ", healed).contains("Done"),
                    () -> "op creature_heal: " + healed);

            List<String> held = console(server, "wandb admin op creature_discard " + creature.getStringUUID());
            check(helper, creature.isAlive() && String.join(" ", held).contains("confirm"), () -> "op discard unconfirmed: " + held);

            List<String> gone = console(server, "wandb admin op creature_discard " + creature.getStringUUID() + " confirm");
            check(helper, creature.isRemoved(), () -> "op discard confirmed: " + gone);
            helper.succeed();
        } finally {
            if (!creature.isRemoved()) {
                creature.discard();
            }
        }
    }
}
