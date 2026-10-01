package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminConfirmations;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The command adapter of the administration framework: {@code /wandb admin panel} and
 * {@code /wandb admin config …}.
 *
 * <p>An adapter and nothing more. Every mutation is one call into {@link AdminSettingService} with the
 * source's {@link AdminContext}; parsing, bounds, cross-setting rules and history all happen there, exactly
 * as they do for the Control Center's packets. This class only turns arguments into that call and the
 * {@link AdminResult} back into chat.
 *
 * <p>The {@code admin} group above already requires {@code AdminAccess}; the service checks again anyway,
 * because it must not depend on having been reached through a gated node.
 */
@NullMarked
public final class AdminConfigCommands {

    private AdminConfigCommands() {}

    /** {@code /wandb admin panel} — opens the Control Center on the caller's screen. */
    public static LiteralArgumentBuilder<CommandSourceStack> panel() {
        return Commands.literal("panel").executes(ctx -> openPanel(ctx.getSource()));
    }

    /** {@code /wandb admin config …} — the same operations the panel offers, from chat or the console. */
    public static LiteralArgumentBuilder<CommandSourceStack> config() {
        return Commands.literal("config")
                .executes(ctx -> list(ctx.getSource(), null))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), null))
                        .then(section().executes(ctx -> list(ctx.getSource(), sectionArg(ctx)))))
                .then(Commands.literal("get")
                        .then(setting().executes(ctx -> get(ctx.getSource(), settingArg(ctx)))))
                .then(Commands.literal("set")
                        .then(setting()
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> set(ctx.getSource(), settingArg(ctx),
                                                StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("reset")
                        .then(setting().executes(ctx -> reset(ctx.getSource(), settingArg(ctx)))))
                .then(Commands.literal("reset_section")
                        .then(section().executes(ctx -> resetSection(ctx.getSource(), sectionArg(ctx)))))
                .then(Commands.literal("reset_all")
                        .executes(ctx -> {
                            // Two steps on purpose: this is the one command that can undo a whole server's tuning.
                            ctx.getSource().sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.reset_all.confirm"));
                            return 0;
                        })
                        .then(Commands.literal("confirm").executes(ctx -> resetAll(ctx.getSource()))))
                .then(Commands.literal("undo").executes(ctx -> undo(ctx.getSource())))
                // Applies the dangerous change this actor was last asked to confirm.
                .then(Commands.literal("confirm").executes(ctx -> confirm(ctx.getSource())))
                .then(Commands.literal("history")
                        .executes(ctx -> history(ctx.getSource(), 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> history(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    // ── arguments ──

    private static RequiredArgumentBuilder<CommandSourceStack, String> setting() {
        return Commands.argument("setting", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        AdminSettings.registry().all().stream().map(s -> s.id().getPath()), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> section() {
        return Commands.argument("section", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(AdminCategory.values()).map(AdminCategory::id), builder));
    }

    private static String settingArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "setting");
    }

    private static String sectionArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "section");
    }

    // ── executors ──

    private static int openPanel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.panel.players_only"));
            return 0;
        }
        if (!AdminNetworkService.openFor(player)) {
            source.sendFailure(Component.translatable("admin.wizards_and_beasts.rejection.unauthorized"));
            return 0;
        }
        return 1;
    }

    private static int list(CommandSourceStack source, @Nullable String rawSection) {
        AdminCategory only = null;
        if (rawSection != null) {
            only = AdminCategory.byId(rawSection);
            if (only == null) {
                source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.unknown_section", rawSection));
                return 0;
            }
        }
        List<AdminSetting<?>> settings = only == null
                ? List.copyOf(AdminSettings.registry().all())
                : AdminSettings.registry().inCategory(only);
        long changed = settings.stream().filter(s -> s.binding().available() && !s.isDefault()).count();
        ChatReport report = ChatReport.of(Component.translatable("command.wizards_and_beasts.admin.config.list.title"))
                .subtitle(Component.translatable("command.wizards_and_beasts.admin.config.list.subtitle",
                        settings.size(), changed));
        AdminCategory current = null;
        for (AdminSetting<?> setting : settings) {
            if (setting.category() != current) {
                current = setting.category();
                report.divider().item(Component.translatable(current.nameKey()));
            }
            report.state(setting.id().getPath(), valueText(setting), tone(setting),
                    "/wandb admin config set " + setting.id().getPath() + " ",
                    Component.translatable(setting.descriptionKey()));
        }
        report.send(source);
        return 1;
    }

    private static int get(CommandSourceStack source, String raw) {
        AdminSetting<?> setting = AdminSettings.registry().find(raw);
        if (setting == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.unknown", raw));
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(setting.nameKey()))
                .subtitle(setting.id().getPath())
                .row("value", valueText(setting))
                .row("default", setting.binding().available() ? setting.defaultText() : "?");
        if (!Double.isNaN(setting.type().min())) {
            report.row("range", formatBound(setting, setting.type().min())
                    + " – " + formatBound(setting, setting.type().max()));
        }
        if (!setting.type().options().isEmpty()) {
            report.row("options", String.join(", ", setting.type().options()));
        }
        report.note(Component.translatable(setting.descriptionKey()));
        if (setting.scope() == SettingScope.CLIENT) {
            report.note(Component.translatable("admin.wizards_and_beasts.scope.client.hint"));
        }
        report.send(source);
        return 1;
    }

    private static int set(CommandSourceStack source, String raw, String value) {
        AdminSetting<?> setting = AdminSettings.registry().find(raw);
        if (setting == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.unknown", raw));
            return 0;
        }
        return AdminCommandFeedback.report(source,
                AdminSettings.service().change(AdminContext.of(source), setting.id(), value),
                new AdminConfirmations.Action(setting.id(), value));
    }

    private static int reset(CommandSourceStack source, String raw) {
        AdminSetting<?> setting = AdminSettings.registry().find(raw);
        if (setting == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.unknown", raw));
            return 0;
        }
        return AdminCommandFeedback.report(source,
                AdminSettings.service().reset(AdminContext.of(source), setting.id()),
                new AdminConfirmations.Action(setting.id(), null));
    }

    private static int resetSection(CommandSourceStack source, String rawSection) {
        AdminCategory section = AdminCategory.byId(rawSection);
        if (section == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.unknown_section", rawSection));
            return 0;
        }
        return reportBatch(source, AdminSettings.service().resetSection(AdminContext.of(source), section));
    }

    private static int resetAll(CommandSourceStack source) {
        return reportBatch(source, AdminSettings.service().resetAll(AdminContext.of(source)));
    }

    private static int confirm(CommandSourceStack source) {
        List<AdminResult> results = AdminConfirmations.confirm(AdminContext.of(source), AdminSettings.service(),
                net.minecraft.util.Util.getMillis());
        if (results.isEmpty()) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.config.confirm_none"));
            return 0;
        }
        int ok = 0;
        for (AdminResult result : results) {
            ok += report(source, result);
        }
        return ok;
    }

    private static int undo(CommandSourceStack source) {
        return report(source, AdminSettings.service().undoLast(AdminContext.of(source)));
    }

    private static int history(CommandSourceStack source, int count) {
        List<AdminChangeRecord> records = AdminSettings.service().history().recent(count);
        ChatReport report = ChatReport.of(Component.translatable("command.wizards_and_beasts.admin.config.history.title"));
        if (records.isEmpty()) {
            report.note(Component.translatable("command.wizards_and_beasts.admin.config.history.empty"));
        }
        for (AdminChangeRecord record : records) {
            String line = "#" + record.sequence() + " " + record.actorName() + " "
                    + record.kind().name().toLowerCase(Locale.ROOT) + " " + record.settingId().getPath()
                    + ": " + record.oldValue() + " → " + record.newValue();
            MutableComponent entry = Component.literal(line).withColor(
                    record.applied() ? (record.undone() ? ChatPalette.MUTED : ChatPalette.TEXT) : ChatPalette.BAD);
            if (record.rejection() != null) {
                entry.append(Component.literal(" (").withColor(ChatPalette.MUTED))
                        .append(Component.translatable(record.rejection().translationKey()).withColor(ChatPalette.MUTED))
                        .append(Component.literal(")").withColor(ChatPalette.MUTED));
            }
            report.item(entry);
        }
        report.send(source);
        return 1;
    }

    // ── feedback ──

    private static int report(CommandSourceStack source, AdminResult result) {
        return AdminCommandFeedback.report(source, result);
    }

    private static int reportBatch(CommandSourceStack source, List<AdminResult> results) {
        return AdminCommandFeedback.reportBatch(source, results);
    }

    private static String valueText(AdminSetting<?> setting) {
        if (!setting.binding().available()) {
            return "?";
        }
        return setting.scope() == SettingScope.CLIENT
                ? setting.currentText() + " (client)"
                : setting.currentText();
    }

    private static int tone(AdminSetting<?> setting) {
        if (setting.scope() == SettingScope.CLIENT) {
            return ChatPalette.MUTED;
        }
        return setting.binding().available() && setting.isDefault() ? ChatPalette.TEXT : ChatPalette.WARN;
    }

    /** Formats a numeric bound through the setting's own type, so an int range prints without ".0". */
    @SuppressWarnings("unchecked")
    private static <T> String formatBound(AdminSetting<T> setting, double bound) {
        Object boxed = setting.type().kind() == SettingKind.INTEGER ? (Object) (int) bound : (Object) bound;
        return setting.type().format((T) boxed);
    }
}
