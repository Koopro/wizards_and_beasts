package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
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
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)

                // /wandb stats get <player>
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> getAll(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))
                                // /wandb stats get <player> <stat>
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(STAT_SUGGESTIONS, builder))
                                        .executes(ctx -> getSingle(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "stat"))))))

                // /wandb stats set <player> <stat> <value>
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

                // /wandb stats grant_growth <player> <amount>
                .then(Commands.literal("grant_growth")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 15))
                                        .executes(ctx -> grantGrowth(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))

                // /wandb stats reroll_power <player>
                .then(Commands.literal("reroll_power")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> rerollPower(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))

                // /wandb stats set_prodigy <player> <true|false>
                .then(Commands.literal("set_prodigy")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setProdigy(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                BoolArgumentType.getBool(ctx, "value"))))))

                // /wandb stats reset <player>
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    // -------------------------------------------------------------------------

    private static int getAll(CommandSourceStack source, ServerPlayer target) {
        String name = target.getName().getString();
        for (PlayerStat stat : PlayerStat.values()) {
            int value = PlayerStatsAPI.getStat(target, stat);
            source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.stats.get_success",
                    name, stat.displayName(), value), false);
        }
        if (PlayerStatsAPI.isProdigy(target)) {
            source.sendSuccess(() -> Component.literal("[Prodigy]").withStyle(
                    net.minecraft.ChatFormatting.GOLD), false);
        }
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
        // Wipe existing stats to allow re-initialization
        target.setData(ModAttachments.PLAYER_STATS.get(), PlayerStatsData.EMPTY);
        PlayerStatsAPI.initializeStatsForNewPlayer(target, variant, target.getRandom());
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

    private static PlayerStat resolveStat(CommandSourceStack source, String statId) {
        PlayerStat stat = PlayerStat.fromId(statId);
        if (stat == null) {
            source.sendFailure(Component.literal("Unknown stat '" + statId + "'. Valid: "
                    + STAT_SUGGESTIONS));
            return null;
        }
        return stat;
    }
}
