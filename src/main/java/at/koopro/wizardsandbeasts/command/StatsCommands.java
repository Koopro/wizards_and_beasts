package at.koopro.wizardsandbeasts.command;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.util.ChatReport;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class StatsCommands {

    private static final Iterable<String> STAT_SUGGESTIONS =
            Arrays.stream(PlayerStat.values())
                    .map(PlayerStat::getId)
                    .collect(Collectors.toList());

    private StatsCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("stats")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)

                // /wandb player stats get <player>
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> getAll(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))
                                // /wandb player stats get <player> <stat>
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(STAT_SUGGESTIONS, builder))
                                        .executes(ctx -> getSingle(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "stat"))))))

                // /wandb player stats set <player> <stat> <value>
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(STAT_SUGGESTIONS, builder))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> set(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "stat"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))))

                // /wandb player stats grant_growth <player> <amount>
                .then(Commands.literal("grant_growth")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 15))
                                        .executes(ctx -> grantGrowth(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))

                // /wandb player stats reroll_power <player>
                .then(Commands.literal("reroll_power")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> rerollPower(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))

                // /wandb player stats set_prodigy <player> <true|false>
                .then(Commands.literal("set_prodigy")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setProdigy(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                BoolArgumentType.getBool(ctx, "value"))))))

                // /wandb player stats reset <player>
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    // -------------------------------------------------------------------------

    /**
     * The whole stat block as one report, a meter per stat.
     *
     * <p>Was one flat line each — {@code Steve · Power: 42} — which made a five-line answer that could
     * not be compared at a glance, the one thing a stat block is for. The loop is over
     * {@link PlayerStat#values()}, so a new stat appears here without this method being touched.
     */
    private static int getAll(CommandSourceStack source, ServerPlayer target) {
        ChatReport report = ChatReport.of(Component.translatable(
                "command.wizards_and_beasts.stats.report.title", target.getName().getString()));
        if (PlayerStatsAPI.isProdigy(target)) {
            report.subtitle(Component.translatable("command.wizards_and_beasts.stats.report.prodigy"));
        }
        for (PlayerStat stat : PlayerStat.values()) {
            // Derived stats have no training track and cannot be set, so say which kind this is on
            // hover rather than leaving a player to wonder why Knowledge ignores /stats set.
            Component hover = Component.translatable(stat.isDerived()
                    ? "command.wizards_and_beasts.stats.report.derived"
                    : stat.isTrainable()
                            ? "command.wizards_and_beasts.stats.report.trainable"
                            : "command.wizards_and_beasts.stats.report.fixed");
            report.meter(stat.displayName().getString(), PlayerStatsAPI.getStat(target, stat),
                    PlayerStatsData.MAX_VALUE, hover);
        }

        // The Power ceiling and what is left of the growth allowance. Both are server-authoritative
        // and neither was visible from anywhere: an operator checking whether a cap was doing its job
        // could see the number but not the limit it was being held against.
        report.divider();
        report.row("Power cap", Component.translatable(
                "command.wizards_and_beasts.stats.report.power_cap",
                PlayerStatsAPI.getPowerCap(target),
                PlayerStatsAPI.getRemainingPowerGrowth(target)));
        if (PlayerStatsAPI.isPowerCapped(target)) {
            report.note(Component.translatable("command.wizards_and_beasts.stats.report.power_capped"));
        }

        report.send(source);
        return 1;
    }

    private static int getSingle(CommandSourceStack source, ServerPlayer target, String statId) {
        PlayerStat stat = resolveStat(source, statId);
        if (stat == null) return 0;
        int value = PlayerStatsAPI.getStat(target, stat);
        String name = target.getName().getString();
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.get_success",
                name, stat.displayName(), value), false);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer target, String statId, int value) {
        PlayerStat stat = resolveStat(source, statId);
        if (stat == null) return 0;
        if (stat.isDerived()) {
            source.sendFailure(Component.translatable(
                    "command.wizards_and_beasts.stats.set_knowledge_warning"));
            return 0;
        }
        PlayerStatsAPI.setStat(target, stat, value);
        String name = target.getName().getString();
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.set_success",
                name, stat.displayName(), value), true);
        return 1;
    }

    private static int grantGrowth(CommandSourceStack source, ServerPlayer target, int amount) {
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(target);
        if (variant != null && "squib".equals(variant.getId())) {
            source.sendFailure(Component.translatable(
                    "command.wizards_and_beasts.stats.grant_growth_squib",
                    target.getName().getString()));
            return 0;
        }
        int remaining = PlayerStatsAPI.getRemainingPowerGrowth(target);
        PlayerStatsAPI.grantPowerGrowth(target, amount);
        int newRemaining = PlayerStatsAPI.getRemainingPowerGrowth(target);
        String name = target.getName().getString();
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.grant_growth_success",
                amount, name, newRemaining), true);
        return 1;
    }

    private static int rerollPower(CommandSourceStack source, ServerPlayer target) {
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(target);
        if (variant == null) {
            source.sendFailure(Component.literal("Player has no heritage selected yet."));
            return 0;
        }
        // Rolls POWER and the prodigy flag against the heritage band and nothing else. This used to wipe
        // the block and run the new-player initialiser over it, because that was the only method that
        // rolled — so a command named reroll_power also reset PRECISION, REFLEXES, WILLPOWER and every
        // training accumulator to zero.
        PlayerStatsAPI.rerollHeritagePower(target, variant, target.getRandom());
        int newPower = PlayerStatsAPI.getStat(target, PlayerStat.POWER);
        boolean prodigy = PlayerStatsAPI.isProdigy(target);
        String prodigyStr = prodigy ? " [Prodigy]" : "";
        String name = target.getName().getString();
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.reroll_success",
                name, newPower, prodigyStr), true);
        return 1;
    }

    private static int setProdigy(CommandSourceStack source, ServerPlayer target, boolean value) {
        PlayerStatsData old = PlayerStatsAPI.getData(target);
        PlayerStatsAPI.setAndSync(target, old.withProdigy(value));
        String name = target.getName().getString();
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.set_prodigy_success",
                name, value), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target) {
        target.setData(ModAttachments.PLAYER_STATS.get(), PlayerStatsData.EMPTY);
        // Sync the cleared data to client
        at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload.syncToPlayer(target);
        String name = target.getName().getString();
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.reset_success", name), true);
        return 1;
    }

    private static @Nullable PlayerStat resolveStat(CommandSourceStack source, String statId) {
        PlayerStat stat = PlayerStat.fromId(statId);
        if (stat == null) {
            source.sendFailure(Component.literal("Unknown stat '" + statId + "'. Valid: "
                    + STAT_SUGGESTIONS));
            return null;
        }
        return stat;
    }
}
