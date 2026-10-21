package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperation;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperationService;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperations;
import at.koopro.wizardsandbeasts.admin.report.AdminReport;
import at.koopro.wizardsandbeasts.admin.report.AdminReportService;
import at.koopro.wizardsandbeasts.admin.report.AdminReports;
import at.koopro.wizardsandbeasts.admin.report.ReportView;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb admin report …} and {@code /wandb admin op …}: the command side of the Control Center's report pages
 * and operation buttons. Both go through the same services the payloads use ({@link AdminReportService},
 * {@link AdminOperationService}), so the two interfaces cannot disagree about who may read or do what.
 *
 * <pre>
 * /wandb admin report list
 * /wandb admin report &lt;id&gt; [subject]           e.g. report creature_live, report ministry_licences Steve
 * /wandb admin op list
 * /wandb admin op &lt;id&gt; &lt;subject&gt; [argument] [confirm]   e.g. op creature_scale &lt;uuid&gt; 2
 * </pre>
 */
@NullMarked
public final class AdminReportCommands {

    private static final int SUBJECTS_SHOWN = 20;

    private AdminReportCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> report() {
        return Commands.literal("report")
                .then(Commands.literal("list").executes(ctx -> listReports(ctx.getSource())))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                AdminReports.all().stream().map(AdminReport::id), builder))
                        .executes(ctx -> report(ctx, ""))
                        .then(Commands.argument("subject", StringArgumentType.greedyString())
                                .executes(ctx -> report(ctx, StringArgumentType.getString(ctx, "subject")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> operation() {
        var argument = Commands.argument("argument", StringArgumentType.word())
                .executes(ctx -> op(ctx, StringArgumentType.getString(ctx, "argument"), false))
                .then(Commands.literal("confirm")
                        .executes(ctx -> op(ctx, StringArgumentType.getString(ctx, "argument"), true)));
        return Commands.literal("op")
                .then(Commands.literal("list").executes(ctx -> listOperations(ctx.getSource())))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                AdminOperations.all().stream().map(AdminOperation::id), builder))
                        .then(Commands.argument("subject", StringArgumentType.string())
                                .executes(ctx -> op(ctx, "", false))
                                .then(Commands.literal("confirm").executes(ctx -> op(ctx, "", true)))
                                .then(argument)));
    }

    private static int listReports(CommandSourceStack source) {
        AdminContext actor = AdminContext.of(source);
        ChatReport out = ChatReport.of("Admin reports");
        for (AdminReport report : AdminReports.all()) {
            if (actor.canModify(report.capability())) {
                out.action(report.id() + " · " + report.section().id(), "/wandb admin report " + report.id());
            }
        }
        out.send(source);
        return 1;
    }

    private static int listOperations(CommandSourceStack source) {
        AdminContext actor = AdminContext.of(source);
        ChatReport out = ChatReport.of("Admin operations");
        for (AdminOperation operation : AdminOperations.all()) {
            if (actor.canModify(operation.capability())) {
                out.item(Component.literal(operation.id() + " · " + operation.section().id()
                        + (operation.destructive() ? " · needs confirm" : "") + " — ")
                        .append(Component.translatable("admin.wizards_and_beasts.operation." + operation.id() + ".desc")));
            }
        }
        out.send(source);
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> ctx, String subject) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        AdminReportService.Result result = AdminReportService.read(AdminContext.of(source), source.getServer(), id, subject);
        if (!result.ok()) {
            source.sendFailure(Component.translatable("admin.wizards_and_beasts.report_page.status." + result.status().id())
                    .append(" (" + id + ")"));
            return 0;
        }
        ReportView view = result.view();
        ChatReport out = ChatReport.of(Component.translatable("admin.wizards_and_beasts.report." + id + ".title")
                .append(subject.isEmpty() ? "" : " · " + subject));
        for (ReportView.Section section : view.sections()) {
            if (!section.heading().isEmpty()) {
                out.item(text(section.heading(), section.headingKey()).withStyle(ChatFormatting.GOLD));
            }
            for (ReportView.Line line : section.lines()) {
                MutableComponent text = text(line.label(), line.labelKey());
                if (!line.value().isEmpty()) {
                    text.append(": ").append(text(line.value(), line.valueKey()));
                }
                out.subItem(text.withStyle(colour(line.tone())));
            }
        }
        if (subject.isEmpty() && !view.subjects().isEmpty()) {
            out.subtitle("Subjects");
            view.subjects().stream().limit(SUBJECTS_SHOWN).forEach(s -> out.action(s.label()
                    + (s.note().isEmpty() ? "" : " · " + s.note()), "/wandb admin report " + id + " " + s.id()));
        }
        for (ReportView.Action action : view.actions()) {
            out.action(Component.translatable(action.labelKey(), action.argument()).getString(),
                    "/wandb admin op " + action.operation() + " " + action.subject()
                            + (action.argument().isEmpty() ? "" : " " + action.argument())
                            + (action.destructive() ? " confirm" : ""));
        }
        out.send(source);
        return 1;
    }

    private static int op(CommandContext<CommandSourceStack> ctx, String argument, boolean confirmed) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        String subject = StringArgumentType.getString(ctx, "subject");
        AdminOperation.Outcome outcome = AdminOperationService.perform(AdminContext.of(source), source.getServer(), id,
                subject, argument, confirmed);
        if (outcome.success()) {
            source.sendSuccess(() -> Component.literal("Done: " + id + " · " + outcome.detail())
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        if (outcome.code().equals("confirm_required")) {
            source.sendFailure(Component.translatable("admin.wizards_and_beasts.operation." + id + ".warning")
                    .append(" Repeat with confirm: /wandb admin op " + id + " " + subject
                            + (argument.isEmpty() ? "" : " " + argument) + " confirm"));
            return 0;
        }
        source.sendFailure(Component.literal("Refused (" + outcome.code() + "): " + id
                + (outcome.detail().isEmpty() ? "" : " · " + outcome.detail())));
        return 0;
    }

    private static MutableComponent text(String text, boolean key) {
        return key ? Component.translatable(text) : Component.literal(text);
    }

    private static ChatFormatting colour(ReportView.Tone tone) {
        return switch (tone) {
            case NORMAL -> ChatFormatting.WHITE;
            case GOOD -> ChatFormatting.GREEN;
            case WARN -> ChatFormatting.GOLD;
            case BAD -> ChatFormatting.RED;
            case MUTED -> ChatFormatting.GRAY;
        };
    }
}
