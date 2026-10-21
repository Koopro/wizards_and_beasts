package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /wandb admin players …}: the Players section's reads from the console — who is online, one facet of one
 * player, the administrative action log. Read-only on purpose: changes to a player go through the panel (one
 * confirmed, logged action at a time) or the existing per-system commands.
 */
@NullMarked
public final class AdminPlayerCommands {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private AdminPlayerCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("players")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ""))
                        .then(Commands.argument("query", StringArgumentType.word())
                                .executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "query")))))
                .then(Commands.literal("inspect").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("facet", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(PlayerAdminService.Facet.values()).map(PlayerAdminService.Facet::id), builder))
                                .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "facet"))))))
                .then(act())
                .then(Commands.literal("log")
                        .executes(ctx -> log(ctx.getSource(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> log(ctx.getSource(), EntityArgument.getPlayer(ctx, "player").getUUID()))));
    }

    /**
     * {@code act <player> <action> [argument] [amount] [confirm]} -- every player action the Control Center offers,
     * through the same {@link PlayerAdminService#perform} pipeline (permission, target, argument, confirmation,
     * snapshot, rollback, the world's action log). Without {@code confirm} a destructive action is held and says so;
     * nothing is decided here.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> act() {
        var amount = Commands.argument("amount", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                .executes(ctx -> act(ctx, true, true, false))
                .then(Commands.literal("confirm").executes(ctx -> act(ctx, true, true, true)));
        var argument = Commands.argument("argument", StringArgumentType.string())
                .executes(ctx -> act(ctx, true, false, false))
                .then(Commands.literal("confirm").executes(ctx -> act(ctx, true, false, true)))
                .then(amount);
        var action = Commands.argument("action", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction.values())
                                .map(at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction::id), builder))
                .executes(ctx -> act(ctx, false, false, false))
                .then(Commands.literal("confirm").executes(ctx -> act(ctx, false, false, true)))
                .then(argument);
        return Commands.literal("act").then(Commands.argument("player", EntityArgument.player()).then(action));
    }

    private static int act(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean hasArgument,
                           boolean hasAmount, boolean confirmed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        AdminContext actor = AdminContext.of(source);
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String actionId = StringArgumentType.getString(ctx, "action");
        var action = at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction.byId(actionId);
        if (action == null) {
            source.sendFailure(Component.literal("Unknown player action " + actionId));
            return 0;
        }
        String argument = hasArgument ? StringArgumentType.getString(ctx, "argument") : "";
        long amount = hasAmount ? com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "amount") : 0L;
        PlayerAdminService.Outcome outcome = PlayerAdminService.perform(actor, source.getServer(),
                new PlayerAdminService.Request(target.getUUID(), action, argument, amount, confirmed));
        String subject = action.id() + (argument.isEmpty() ? "" : " " + argument) + (hasAmount ? " " + amount : "")
                + " on " + target.getName().getString();
        if (outcome.success()) {
            source.sendSuccess(() -> Component.literal("Done: " + subject + (outcome.detail().isEmpty() ? "" : " - " + outcome.detail())
                    + " (log #" + outcome.logSequence() + ")").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        if (outcome.code().equals("confirm_required")) {
            source.sendFailure(Component.literal(subject + " changes the player's state and cannot be undone here. "
                    + "Repeat with 'confirm' at the end to apply it.").withStyle(ChatFormatting.GOLD));
            return 0;
        }
        source.sendFailure(Component.literal("Refused (" + outcome.code() + "): " + subject
                + (outcome.detail().isEmpty() ? "" : " - " + outcome.detail())));
        return 0;
    }

    private static boolean refuse(CommandSourceStack source, AdminContext actor) {
        if (PlayerAdminService.authorised(actor)) {
            return false;
        }
        source.sendFailure(Component.literal("You may not administer players.").withStyle(ChatFormatting.RED));
        return true;
    }

    private static int list(CommandSourceStack source, String query) {
        AdminContext actor = AdminContext.of(source);
        if (refuse(source, actor)) {
            return 0;
        }
        ChatReport report = ChatReport.of("Players online");
        for (PlayerAdminService.PlayerRow row : PlayerAdminService.search(actor, source.getServer(), query)) {
            report.item(row.name() + " · " + (row.heritage().isEmpty() ? "no heritage" : row.heritage())
                    + String.format(Locale.ROOT, " · ♥ %.0f/%.0f · %s", row.health(), row.maxHealth(), row.wanted())
                    + (row.location().isEmpty() ? "" : " · " + row.location()));
        }
        report.send(source);
        return 1;
    }

    private static int inspect(CommandSourceStack source, ServerPlayer target, String facetId) {
        AdminContext actor = AdminContext.of(source);
        if (refuse(source, actor)) {
            return 0;
        }
        PlayerAdminService.Facet facet = PlayerAdminService.Facet.byId(facetId);
        if (facet == null) {
            source.sendFailure(Component.literal("Unknown facet " + facetId));
            return 0;
        }
        PlayerAdminService.FacetView view = PlayerAdminService.facet(actor, source.getServer(), target.getUUID(), facet);
        if (view == null) {
            source.sendFailure(Component.literal("Not online: " + target.getName().getString()));
            return 0;
        }
        ChatReport report = ChatReport.of(view.playerName() + " · " + facet.id());
        for (AdminSpellFact fact : view.facts()) {
            report.item(Component.translatable(fact.labelKey()).append(": ").append(fact.valueTranslatable()
                    ? Component.translatable(fact.value()) : Component.literal(fact.value())));
        }
        for (PlayerAdminService.Item item : view.items()) {
            report.item(Component.literal("· ").append(item.labelKey() ? Component.translatable(item.label())
                    : Component.literal(item.label())).append(": " + item.value()));
        }
        report.send(source);
        return 1;
    }

    private static int log(CommandSourceStack source, @Nullable UUID player) {
        AdminContext actor = AdminContext.of(source);
        if (refuse(source, actor)) {
            return 0;
        }
        ChatReport report = ChatReport.of("Player action log");
        for (PlayerActionLog.Entry entry : PlayerActionLog.get(source.getServer()).recent(player, 20)) {
            report.item(Component.literal(TIME.format(Instant.ofEpochMilli(entry.timeMillis())) + " " + entry.adminName()
                    + " → " + entry.playerName() + " · " + entry.action()
                    + (entry.argument().isEmpty() ? "" : " (" + entry.argument() + ")") + " · " + entry.result()
                    + (entry.detail().isEmpty() ? "" : " — " + entry.detail()))
                    .withStyle(entry.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        report.send(source);
        return 1;
    }
}
