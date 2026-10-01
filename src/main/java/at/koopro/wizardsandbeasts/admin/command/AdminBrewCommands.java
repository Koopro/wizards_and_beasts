package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.brew.BrewAdminService;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads.BrewSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * {@code /wandb admin brew …} — read the Brewing section from chat. Every brew and recipe value is a setting, so
 * editing is {@code /wandb admin config set <id> <value>}; {@code info} prints each editable value's id to copy.
 * The existing cauldron debug commands are untouched.
 */
@NullMarked
public final class AdminBrewCommands {

    private static final String KEY = "command.wizards_and_beasts.admin.brew.";

    private static final SuggestionProvider<CommandSourceStack> BREWS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Brews.all().stream().map(brew -> brew.id()), builder);

    private AdminBrewCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("brew")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ""))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("info").then(Commands.argument("brew", StringArgumentType.greedyString())
                        .suggests(BREWS).executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "brew")))));
    }

    private static boolean refuse(CommandSourceStack source) {
        if (BrewAdminService.authorised(AdminContext.of(source))) {
            return false;
        }
        source.sendFailure(Component.translatable("admin.wizards_and_beasts.creature_action.unauthorized", ""));
        return true;
    }

    private static int list(CommandSourceStack source, String filter) {
        if (refuse(source)) {
            return 0;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        ChatReport report = ChatReport.of(Component.translatable(KEY + "list.title"));
        int shown = 0;
        for (BrewSummary row : BrewAdminService.list()) {
            if (!needle.isEmpty() && !row.id().contains(needle) && row.effects().stream().noneMatch(e -> e.contains(needle))) {
                continue;
            }
            report.row(row.id(), Component.translatable(KEY + "list.row", row.enabled() ? "on" : "off",
                    row.difficulty().toLowerCase(Locale.ROOT), row.effectText().isEmpty() ? "—" : row.effectText()));
            shown++;
        }
        report.send(source);
        return shown;
    }

    private static int info(CommandSourceStack source, String brewId) {
        if (refuse(source)) {
            return 0;
        }
        String id = brewId.contains(":") ? brewId : "wizards_and_beasts:" + brewId;
        BrewAdminService.Detail detail = BrewAdminService.detail(id, AdminContext.of(source));
        if (detail == null) {
            source.sendFailure(Component.translatable(KEY + "unknown", brewId));
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(detail.summary().nameKey()));
        report.row("effects", detail.summary().effectText().isEmpty() ? "—" : detail.summary().effectText());
        for (AdminSpellFact fact : detail.recipe()) {
            report.row(Component.translatable(fact.labelKey()).getString(),
                    fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value()));
        }
        report.subtitle(Component.translatable(KEY + "settings"));
        for (AdminSettingDescriptor setting : detail.settings()) {
            report.row(setting.id().toString(), setting.value());
        }
        report.send(source);
        return 1;
    }
}
