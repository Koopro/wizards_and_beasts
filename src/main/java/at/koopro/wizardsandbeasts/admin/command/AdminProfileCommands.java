package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.profile.ProfileCodec;
import at.koopro.wizardsandbeasts.admin.profile.ProfileDocument;
import at.koopro.wizardsandbeasts.admin.profile.ProfileService;
import at.koopro.wizardsandbeasts.admin.profile.ProfileValidator;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * {@code /wandb admin profile …} — profiles, snapshots, export/import and group reverts from the console. The same
 * {@link ProfileService} the Control Center uses: validation, all-or-nothing apply and history are identical.
 * Changing anything takes {@code confirm}; {@code preview} shows what would change first.
 */
@NullMarked
public final class AdminProfileCommands {

    private static final SuggestionProvider<CommandSourceStack> PROFILES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ProfileService.all(ctx.getSource().getServer()).stream()
                    .map(d -> d.meta().id()), builder);
    private static final SuggestionProvider<CommandSourceStack> FILES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ProfileService.importable(ctx.getSource().getServer()), builder);

    private AdminProfileCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("profile")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("preview").then(Commands.argument("id", StringArgumentType.word()).suggests(PROFILES)
                        .executes(ctx -> preview(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("apply").then(Commands.argument("id", StringArgumentType.word()).suggests(PROFILES)
                        .executes(ctx -> needConfirm(ctx.getSource(), "apply " + StringArgumentType.getString(ctx, "id")))
                        .then(Commands.literal("confirm").executes(ctx -> report(ctx.getSource(),
                                ProfileService.apply(ctx.getSource().getServer(), AdminContext.of(ctx.getSource()),
                                        StringArgumentType.getString(ctx, "id")))))))
                .then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> report(ctx.getSource(), ProfileService.saveAs(ctx.getSource().getServer(),
                                AdminContext.of(ctx.getSource()), StringArgumentType.getString(ctx, "name"),
                                ProfileDocument.Kind.CUSTOM)))))
                .then(Commands.literal("snapshot")
                        .executes(ctx -> report(ctx.getSource(), ProfileService.saveAs(ctx.getSource().getServer(),
                                AdminContext.of(ctx.getSource()), "", ProfileDocument.Kind.SNAPSHOT)))
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(ctx -> report(ctx.getSource(), ProfileService.saveAs(ctx.getSource().getServer(),
                                        AdminContext.of(ctx.getSource()), StringArgumentType.getString(ctx, "label"),
                                        ProfileDocument.Kind.SNAPSHOT)))))
                .then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).suggests(PROFILES)
                        .executes(ctx -> needConfirm(ctx.getSource(), "delete " + StringArgumentType.getString(ctx, "id")))
                        .then(Commands.literal("confirm").executes(ctx -> report(ctx.getSource(),
                                ProfileService.delete(ctx.getSource().getServer(), AdminContext.of(ctx.getSource()),
                                        StringArgumentType.getString(ctx, "id")))))))
                .then(Commands.literal("export").then(Commands.argument("id", StringArgumentType.word()).suggests(PROFILES)
                        .executes(ctx -> report(ctx.getSource(), ProfileService.export(ctx.getSource().getServer(),
                                AdminContext.of(ctx.getSource()), StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("import").then(Commands.argument("file", StringArgumentType.string()).suggests(FILES)
                        .executes(ctx -> report(ctx.getSource(), ProfileService.importFile(ctx.getSource().getServer(),
                                AdminContext.of(ctx.getSource()), StringArgumentType.getString(ctx, "file"))))))
                .then(Commands.literal("revert").then(Commands.argument("sequence", LongArgumentType.longArg(1))
                        .executes(ctx -> AdminCommandFeedback.report(ctx.getSource(), AdminSettings.service().revert(
                                AdminContext.of(ctx.getSource()), LongArgumentType.getLong(ctx, "sequence"))))))
                .then(Commands.literal("revert_group").then(Commands.argument("group", StringArgumentType.greedyString())
                        .executes(ctx -> report(ctx.getSource(), ProfileService.revertGroup(ctx.getSource().getServer(),
                                AdminContext.of(ctx.getSource()), StringArgumentType.getString(ctx, "group"))))));
    }

    private static int needConfirm(CommandSourceStack source, String what) {
        source.sendFailure(Component.literal("Add 'confirm' to go ahead: /wandb admin profile " + what + " confirm")
                .withStyle(ChatFormatting.GOLD));
        return 0;
    }

    private static int list(CommandSourceStack source) {
        ChatReport report = ChatReport.of(Component.literal("Profiles"));
        for (ProfileDocument document : ProfileService.all(source.getServer())) {
            report.item(Component.literal(document.meta().id() + " — " + document.meta().name() + " ("
                    + document.meta().kind().id() + ", " + document.size() + " values)"));
        }
        report.send(source);
        return 1;
    }

    private static int preview(CommandSourceStack source, String id) {
        ProfileDocument document = ProfileService.find(source.getServer(), id);
        if (document == null) {
            source.sendFailure(Component.literal("No profile " + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        ProfileValidator.Plan plan = ProfileService.preview(source.getServer(), AdminContext.of(source), document);
        ChatReport report = ChatReport.of(Component.literal(document.meta().name() + ": " + plan.changes().size()
                + " setting(s) will change" + (plan.needsRestart() ? " · RESTART REQUIRED" : "")
                + (plan.touchesWorldgen() ? " · NEW CHUNKS ONLY" : "")));
        for (ProfileValidator.Change change : plan.changes()) {
            report.item(Component.literal(change.id().getPath() + ": " + change.from() + " → " + change.to()));
        }
        issues(report, "✖ ", plan.errors());
        issues(report, "⚠ ", plan.warnings());
        report.send(source);
        return plan.applicable() ? 1 : 0;
    }

    private static void issues(ChatReport report, String mark, List<ProfileCodec.Issue> issues) {
        for (ProfileCodec.Issue issue : issues) {
            report.item(Component.literal(mark + issue.code() + " " + issue.subject()
                    + (issue.detail().isEmpty() ? "" : " (" + issue.detail() + ")")));
        }
    }

    private static int report(CommandSourceStack source, ProfileService.Result result) {
        if (result.ok()) {
            source.sendSuccess(() -> Component.literal("Done: " + result.id()
                    + (result.changed() > 0 ? " · " + result.changed() + " change(s)" : "")).withStyle(ChatFormatting.GREEN), true);
            for (ProfileCodec.Issue issue : result.issues()) {
                source.sendSuccess(() -> Component.literal("⚠ " + issue.code() + " " + issue.subject())
                        .withStyle(ChatFormatting.GOLD), false);
            }
            return 1;
        }
        source.sendFailure(Component.literal("Refused (" + result.outcome() + "): " + result.id()).withStyle(ChatFormatting.RED));
        for (ProfileCodec.Issue issue : result.issues()) {
            source.sendFailure(Component.literal("✖ " + issue.code() + " " + issue.subject()
                    + (issue.detail().isEmpty() ? "" : " (" + issue.detail() + ")")));
        }
        return 0;
    }
}
