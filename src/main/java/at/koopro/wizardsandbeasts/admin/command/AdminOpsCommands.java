package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.access.AdminPolicy;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets;
import at.koopro.wizardsandbeasts.admin.profile.ProfileApplier;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.PresetOp;
import at.koopro.wizardsandbeasts.currency.billing.GringottsLedger;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads;
import at.koopro.wizardsandbeasts.util.ChatReport;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Command equivalents of the Control Center's Performance presets and Visuals preset library, so every mutation the
 * screen can make can also be made, scripted and audited from a console.
 *
 * <pre>
 * /wandb admin performance preset &lt;low|medium|high&gt; [confirm]
 * /wandb admin visual preset list
 * /wandb admin visual preset duplicate &lt;id&gt; &lt;name&gt;
 * /wandb admin visual preset rename &lt;id&gt; &lt;name&gt;
 * /wandb admin visual preset delete &lt;id&gt; [confirm]
 * /wandb admin economy ledger
 * /wandb admin economy market
 * /wandb admin economy market reset &lt;location|all&gt; [confirm]
 * /wandb admin economy journal &lt;player&gt;
 * </pre>
 *
 * <p>Both call the same service entry point as the screen ({@link PerformancePresets#apply},
 * {@link BeamVisualAdminService#perform}), which checks authority itself. Creating or overwriting a look needs the
 * look's values, which the screen's editor supplies; from a console, duplicate a preset and then change the look's
 * settings with {@code /wandb admin config set}.
 */
@NullMarked
public final class AdminOpsCommands {

    private AdminOpsCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> performance() {
        var preset = Commands.argument("preset", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(PerformancePresets.Preset.values())
                        .map(p -> p.name().toLowerCase(Locale.ROOT)), builder))
                .executes(ctx -> applyPreset(ctx, false))
                .then(Commands.literal("confirm").executes(ctx -> applyPreset(ctx, true)));
        return Commands.literal("performance").then(Commands.literal("preset").then(preset));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> visual() {
        var id = Commands.argument("id", StringArgumentType.string()).suggests((ctx, builder) ->
                SharedSuggestionProvider.suggest(BeamVisualService.presets(ctx.getSource().getServer()).stream()
                        .map(BeamPreset::id), builder));
        return Commands.literal("visual").then(Commands.literal("preset")
                .then(Commands.literal("list").executes(ctx -> listPresets(ctx.getSource())))
                .then(Commands.literal("duplicate").then(id.then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> preset(ctx, PresetOp.DUPLICATE, true)))))
                .then(Commands.literal("rename").then(Commands.argument("id", StringArgumentType.string())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> preset(ctx, PresetOp.RENAME, true)))))
                .then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.string())
                        .executes(ctx -> preset(ctx, PresetOp.DELETE, false))
                        .then(Commands.literal("confirm").executes(ctx -> preset(ctx, PresetOp.DELETE, true))))));
    }

    /**
     * {@code /wandb admin economy ledger}: Gringotts' totals per money flow, and sources against sinks — whether this
     * world's money is being made faster than it is spent (documentation/ECONOMY.md §5). Read-only; needs the
     * Economy section's capability.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> economy() {
        var location = Commands.argument("location", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("all"), at.koopro.wizardsandbeasts.location.Locations.all().entrySet().stream()
                                .filter(e -> e.getValue().market().isPresent()).map(e -> e.getKey().getPath())), builder))
                .executes(ctx -> resetMarket(ctx, false))
                .then(Commands.literal("confirm").executes(ctx -> resetMarket(ctx, true)));
        return Commands.literal("economy")
                .requires(AdminPolicy.requires(AdminCategory.ECONOMY.defaultCapability()))
                .then(Commands.literal("ledger").executes(ctx -> ledger(ctx.getSource())))
                .then(Commands.literal("market").executes(ctx -> market(ctx.getSource()))
                        .then(Commands.literal("reset").then(location)))
                .then(Commands.literal("journal").then(Commands.argument("player",
                                net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> journal(ctx.getSource(),
                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player")))));
    }

    /**
     * {@code /wandb admin economy market}: every open trader's book now — price of the next unit, saturation (how far
     * trade has moved it), today's short good, and the most a good can pay the server per day at any volume.
     */
    private static int market(CommandSourceStack source) {
        at.koopro.wizardsandbeasts.market.EconomyReport.Snapshot snapshot =
                at.koopro.wizardsandbeasts.market.EconomyReport.build(source.getServer());
        ChatReport report = ChatReport.of("Markets").subtitle("Price of the next unit · saturation · ceiling per day");
        String current = "";
        for (at.koopro.wizardsandbeasts.market.EconomyReport.MarketRow row : snapshot.markets()) {
            if (!row.location().equals(current)) {
                current = row.location();
                report.divider().row(current, "");
            }
            String price = row.unitKnuts() > 0 ? CurrencyHelper.formatFromKnuts(row.unitKnuts()) : "by worth";
            report.subItem((row.buying() ? "buys " : "sells ") + row.good() + " · " + price + " · " + row.saturation()
                    + "%" + (row.inDemand() ? " · in demand" : "")
                    + (row.ceilingPerDay() > 0 ? " · ≤" + CurrencyHelper.formatFromKnuts(row.ceilingPerDay()) + "/day" : "")
                    + (row.deal() == at.koopro.wizardsandbeasts.market.DealClass.LEGAL ? "" : " · " + row.deal().name().toLowerCase(Locale.ROOT)));
        }
        if (snapshot.markets().isEmpty()) {
            report.divider().row("no open market", "");
        }
        report.send(source);
        return 1;
    }

    /**
     * {@code /wandb admin economy market reset <location|all> [confirm]}: forgets recent trade, restoring list prices.
     * Runs the audited {@code economy_market_reset} operation, the same one the Markets report's button sends.
     */
    private static int resetMarket(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "location");
        at.koopro.wizardsandbeasts.admin.operation.AdminOperation.Outcome outcome =
                at.koopro.wizardsandbeasts.admin.operation.AdminOperationService.perform(
                        at.koopro.wizardsandbeasts.admin.access.AdminContext.of(source), source.getServer(),
                        at.koopro.wizardsandbeasts.admin.operation.EconomyOperations.MARKET_RESET, name, "", confirmed);
        if (outcome.code().equals("confirm_required")) {
            source.sendSuccess(() -> Component.literal("This restores list prices at " + name
                    + " for everyone. Add 'confirm' to do it.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        if (!outcome.success()) {
            source.sendFailure(Component.literal("Refused (" + outcome.code() + "): " + outcome.detail()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(Character.toUpperCase(outcome.detail().charAt(0))
                + outcome.detail().substring(1) + "."), true);
        return 1;
    }

    /** {@code /wandb admin economy journal <player>}: the player's last money movements, newest last. */
    private static int journal(CommandSourceStack source, net.minecraft.server.level.ServerPlayer player) {
        at.koopro.wizardsandbeasts.currency.billing.MoneyJournal journal =
                player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.MONEY_JOURNAL.get());
        ChatReport report = ChatReport.of("Money journal").subtitle(player.getName().getString() + " · last "
                + at.koopro.wizardsandbeasts.currency.billing.MoneyJournal.CAPACITY + " movements");
        long now = source.getServer().overworld().getGameTime();
        for (at.koopro.wizardsandbeasts.currency.billing.MoneyJournal.Entry entry : journal.entries()) {
            MoneyFlow flow = MoneyFlow.byId(entry.flow());
            Component name = flow == null ? Component.literal(entry.flow()) : Component.translatable(flow.translationKey());
            String sign = flow != null && flow.credits() == entry.knuts() >= 0 ? "+" : "−";
            long ago = Math.max(0L, (now - entry.gameTime()) / 20L);
            report.subItem(name.copy().append(" · " + sign + CurrencyHelper.formatFromKnuts(Math.abs(entry.knuts()))
                    + " · " + ago + " s ago"));
        }
        if (journal.entries().isEmpty()) {
            report.divider().row("no movements yet", "");
        }
        report.send(source);
        return journal.entries().size();
    }

    private static int ledger(CommandSourceStack source) {
        GringottsLedger ledger = GringottsLedger.get(source.getServer());
        ChatReport report = ChatReport.of("Gringotts ledger").subtitle("Every money flow since this world began");
        for (MoneyFlow.Kind kind : MoneyFlow.Kind.values()) {
            report.divider().row(kind.id(), CurrencyHelper.formatFromKnuts(ledger.knutsOf(kind)));
            for (MoneyFlow flow : MoneyFlow.values()) {
                GringottsLedger.Totals totals = ledger.totals(flow);
                if (flow.kind() == kind && totals.count() > 0) {
                    report.subItem(Component.translatable(flow.translationKey()).append(" · " + totals.count() + "× · "
                            + CurrencyHelper.formatFromKnuts(totals.knuts())));
                }
            }
        }
        long net = ledger.knutsOf(MoneyFlow.Kind.SOURCE) - ledger.knutsOf(MoneyFlow.Kind.SINK);
        report.divider().row("made − spent", CurrencyHelper.formatFromKnuts(net));
        report.send(source);
        return 1;
    }

    private static int applyPreset(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "preset");
        PerformancePresets.Preset preset = PerformancePresets.Preset.byId(name.toLowerCase(Locale.ROOT));
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown preset " + name + " (low, medium, high)"));
            return 0;
        }
        AdminContext actor = AdminContext.of(source);
        if (!AdminOpsPayloads.mayReadPerformance(actor)) {
            source.sendFailure(Component.literal("You may not change performance settings."));
            return 0;
        }
        if (!confirmed) {
            source.sendFailure(Component.literal("Applying " + preset.id() + " changes " + preset.settings().size()
                    + " server settings at once (" + String.join(", ", preset.settings().keySet())
                    + "). Repeat with 'confirm' at the end.").withStyle(ChatFormatting.GOLD));
            return 0;
        }
        ProfileApplier.Outcome outcome = PerformancePresets.apply(AdminSettings.service(), actor, preset, source.getServer());
        if (!outcome.applied()) {
            source.sendFailure(Component.literal("Preset " + preset.id() + " not applied: "
                    + (outcome.failures().isEmpty() ? "refused" : outcome.failures().get(0).code() + " "
                    + outcome.failures().get(0).subject())));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Preset " + preset.id() + " applied: " + outcome.changed()
                + " setting(s) changed.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listPresets(CommandSourceStack source) {
        if (!BeamVisualAdminService.authorised(AdminContext.of(source))) {
            source.sendFailure(Component.literal("You may not change visuals."));
            return 0;
        }
        var presets = BeamVisualService.presets(source.getServer());
        source.sendSuccess(() -> Component.literal(presets.size() + " beam presets:"), false);
        for (BeamPreset preset : presets) {
            source.sendSuccess(() -> Component.literal(" · " + preset.id() + " \"" + preset.name() + "\""
                    + (BeamVisualAdminService.isBuiltin(preset.id()) ? " (built in)" : "")).withStyle(ChatFormatting.GRAY), false);
        }
        return presets.size();
    }

    private static int preset(CommandContext<CommandSourceStack> ctx, PresetOp op, boolean confirmed) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        if (op == PresetOp.DELETE && !confirmed) {
            source.sendFailure(Component.literal("Deleting preset " + id + " cannot be undone; beams using it keep their look. "
                    + "Repeat with 'confirm' at the end.").withStyle(ChatFormatting.GOLD));
            return 0;
        }
        String name = op == PresetOp.DELETE ? "" : StringArgumentType.getString(ctx, "name");
        BeamVisualAdminService.Performed result = BeamVisualAdminService.perform(AdminContext.of(source),
                source.getServer(), op, id, name, Map.of());
        if (!result.ok()) {
            source.sendFailure(Component.literal("Preset " + op.name().toLowerCase(Locale.ROOT) + " " + id + " refused: "
                    + result.outcome()));
            return 0;
        }
        for (var player : source.getServer().getPlayerList().getPlayers()) {
            if (BeamVisualAdminService.authorised(AdminContext.of(player))) {
                at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads.sendList(player);
            }
        }
        source.sendSuccess(() -> Component.literal("Preset " + op.name().toLowerCase(Locale.ROOT) + " done: "
                + result.presetId()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
