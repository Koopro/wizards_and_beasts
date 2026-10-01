package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /wandb admin heritage …} — the Heritages section's player tools from chat or the console.
 *
 * <p>An adapter over {@link HeritageAdminService}, the class the panel's payloads reach: the same capability check,
 * the same confirmation rule, the same assignment routine. Destructive verbs do nothing without a trailing
 * {@code confirm} — the first call explains what would happen. Heritage <em>rules</em> are settings:
 * {@code /wandb admin config set wizards_and_beasts:heritage/<id>/selectable false}.
 *
 * <p>The older {@code /wandb player heritage set|reset} commands remain and run the same
 * {@code HeritageAssignment} routine.
 */
@NullMarked
public final class AdminHeritageCommands {

    private static final String KEY = "command.wizards_and_beasts.admin.heritage.";

    private static final SuggestionProvider<CommandSourceStack> HERITAGES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Heritage.values()).map(Heritage::getId), builder);
    private static final SuggestionProvider<CommandSourceStack> VARIANTS = (ctx, builder) -> {
        Heritage heritage = Heritage.byId(StringArgumentType.getString(ctx, "heritage"));
        return SharedSuggestionProvider.suggest(heritage == null ? List.<String>of()
                : heritage.getSubtypes().stream().map(HeritageVariant::getId).toList(), builder);
    };

    private AdminHeritageCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("heritage")
                .then(Commands.literal("players").executes(ctx -> players(ctx.getSource())))
                .then(Commands.literal("inspect").then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("assign").then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("heritage", StringArgumentType.word()).suggests(HERITAGES)
                                .then(Commands.argument("variant", StringArgumentType.word()).suggests(VARIANTS)
                                        .executes(ctx -> assign(ctx, false))
                                        .then(Commands.literal("confirm").executes(ctx -> assign(ctx, true)))))))
                .then(Commands.literal("reset_onboarding").then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> reset(ctx, false))
                        .then(Commands.literal("confirm").executes(ctx -> reset(ctx, true)))));
    }

    private static boolean refuseUnauthorised(CommandSourceStack source, AdminContext actor) {
        if (HeritageAdminService.authorised(actor)) {
            return false;
        }
        source.sendFailure(Component.translatable("admin.wizards_and_beasts.heritage_action.unauthorized", ""));
        return true;
    }

    private static int players(CommandSourceStack source) {
        AdminContext actor = AdminContext.of(source);
        if (refuseUnauthorised(source, actor)) {
            return 0;
        }
        List<HeritageAdminService.PlayerRow> rows = HeritageAdminService.players(actor, source.getServer());
        ChatReport report = ChatReport.of(Component.translatable(KEY + "players.title"));
        for (HeritageAdminService.PlayerRow row : rows) {
            Heritage heritage = Heritage.byId(row.heritageId());
            HeritageVariant variant = HeritageVariant.byId(row.variantId());
            report.row(row.name(), heritage == null
                    ? Component.translatable("admin.wizards_and_beasts.heritage_fact.onboarding_pending")
                    : Component.literal(heritage.getDisplayName()).append(variant == null ? Component.empty()
                    : Component.literal(" / ").append(Component.literal(variant.getDisplayName()))));
        }
        report.send(source);
        return rows.size();
    }

    private static int inspect(CommandSourceStack source, ServerPlayer target) {
        AdminContext actor = AdminContext.of(source);
        if (refuseUnauthorised(source, actor)) {
            return 0;
        }
        HeritageAdminService.Inspection inspection = HeritageAdminService.inspect(actor, source.getServer(), target.getUUID());
        if (inspection == null) {
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(KEY + "inspect.title", inspection.name()));
        facts(report, inspection.identity());
        report.subtitle(Component.translatable("admin.wizards_and_beasts.heritage_players.stats"));
        facts(report, inspection.stats());
        report.subtitle(Component.translatable("admin.wizards_and_beasts.heritage_players.derived"));
        facts(report, inspection.derived());
        report.send(source);
        return 1;
    }

    private static void facts(ChatReport report, List<AdminSpellFact> facts) {
        for (AdminSpellFact fact : facts) {
            report.row(Component.translatable(fact.labelKey()).getString(),
                    fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value()));
        }
    }

    private static int assign(CommandContext<CommandSourceStack> ctx, boolean confirmed) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String heritage = StringArgumentType.getString(ctx, "heritage");
        String variant = StringArgumentType.getString(ctx, "variant");
        return report(source, HeritageAdminService.assign(AdminContext.of(source), source.getServer(),
                target.getUUID(), heritage, variant, confirmed),
                "/wandb admin heritage assign " + target.getName().getString() + " " + heritage + " " + variant + " confirm");
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, boolean confirmed) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        return report(source, HeritageAdminService.resetOnboarding(AdminContext.of(source), source.getServer(),
                target.getUUID(), confirmed),
                "/wandb admin heritage reset_onboarding " + target.getName().getString() + " confirm");
    }

    /** Success in green; an unconfirmed request explains itself and names the command that confirms it. */
    private static int report(CommandSourceStack source, HeritageAdminService.Outcome outcome, String confirmCommand) {
        Component message = Component.translatable(outcome.messageKey(), outcome.detail());
        if (outcome.success()) {
            source.sendSuccess(() -> message.copy().withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        if (outcome.messageKey().endsWith("confirm_required")) {
            source.sendSystemMessage(message.copy().withStyle(ChatFormatting.GOLD));
            source.sendSystemMessage(Component.translatable(KEY + "confirm_with", confirmCommand).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        source.sendFailure(message);
        return 0;
    }
}
