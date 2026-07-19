package at.koopro.wizardsandbeasts.bestiary.command;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.entity.niffler.command.NifflerCommands;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Locale;

public final class BestiaryCommands {
    private BestiaryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("bestiary")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("entry_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BestiaryEntryRegistry.getAll().stream().map(e -> e.id().toString()), b))
                                        .executes(ctx -> setTier(EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "entry_id"), DiscoveryTier.MASTERED)))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("entry_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BestiaryEntryRegistry.getAll().stream().map(e -> e.id().toString()), b))
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(DiscoveryTier.values()).map(Enum::name), b))
                                                .executes(ctx -> setTier(EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "entry_id"), DiscoveryTier.valueOf(StringArgumentType.getString(ctx, "tier").toUpperCase(Locale.ROOT))))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("entry_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BestiaryEntryRegistry.getAll().stream().map(e -> e.id().toString()), b))
                                        .executes(ctx -> resetOne(EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "entry_id"))))
                                .executes(ctx -> resetAll(EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(EntityArgument.getPlayer(ctx, "player"), ctx.getSource()))))
                .then(Commands.literal("sync")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> sync(EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("force")
                        .then(Commands.literal("unlock_all")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> forceUnlockAll(EntityArgument.getPlayers(ctx, "targets"), ctx.getSource()))))
                        .then(Commands.literal("reset_all")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> forceResetAll(EntityArgument.getPlayers(ctx, "targets"), ctx.getSource()))))
                        .then(Commands.literal("set_all")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(DiscoveryTier.values()).map(Enum::name), b))
                                                .executes(ctx -> forceSetAll(
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        DiscoveryTier.valueOf(StringArgumentType.getString(ctx, "tier").toUpperCase(Locale.ROOT)),
                                                        ctx.getSource())))))
                        .then(Commands.literal("sync_all")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> forceSyncAll(EntityArgument.getPlayers(ctx, "targets"), ctx.getSource()))))
                        .then(Commands.literal("list_all")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> forceListAll(EntityArgument.getPlayers(ctx, "targets"), ctx.getSource())))))
                .then(NifflerCommands.register());
    }

    private static int setTier(ServerPlayer player, String id, DiscoveryTier tier) {
        BestiaryDataHelper.setTier(player, Identifier.parse(id), tier);
        return 1;
    }

    private static int resetOne(ServerPlayer player, String id) {
        BestiaryDataHelper.forceSetTier(player, Identifier.parse(id), DiscoveryTier.UNDISCOVERED);
        return 1;
    }

    private static int resetAll(ServerPlayer player) {
        BestiaryEntryRegistry.getAll().forEach(entry -> BestiaryDataHelper.forceSetTier(player, entry.id(), DiscoveryTier.UNDISCOVERED));
        return 1;
    }

    private static int list(ServerPlayer player, CommandSourceStack source) {
        var list = player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.BESTIARY_DATA.get()).tiers();
        source.sendSuccess(() -> Component.literal("Unlocked entries: " + list.size()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int sync(ServerPlayer player) {
        BestiaryDataSyncPayload.syncToPlayer(player);
        return 1;
    }

    private static int forceUnlockAll(Collection<ServerPlayer> players, CommandSourceStack source) {
        for (ServerPlayer player : players) {
            BestiaryEntryRegistry.getAll().forEach(entry -> BestiaryDataHelper.forceSetTier(player, entry.id(), DiscoveryTier.MASTERED));
        }
        source.sendSuccess(() -> Component.literal("Force unlocked all bestiary entries for " + players.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int forceResetAll(Collection<ServerPlayer> players, CommandSourceStack source) {
        for (ServerPlayer player : players) {
            BestiaryEntryRegistry.getAll().forEach(entry -> BestiaryDataHelper.forceSetTier(player, entry.id(), DiscoveryTier.UNDISCOVERED));
        }
        source.sendSuccess(() -> Component.literal("Force reset all bestiary entries for " + players.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int forceSetAll(Collection<ServerPlayer> players, DiscoveryTier tier, CommandSourceStack source) {
        for (ServerPlayer player : players) {
            BestiaryEntryRegistry.getAll().forEach(entry -> BestiaryDataHelper.forceSetTier(player, entry.id(), tier));
        }
        source.sendSuccess(() -> Component.literal("Force set all bestiary entries to " + tier + " for " + players.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int forceSyncAll(Collection<ServerPlayer> players, CommandSourceStack source) {
        for (ServerPlayer player : players) {
            BestiaryDataSyncPayload.syncToPlayer(player);
        }
        source.sendSuccess(() -> Component.literal("Force synced bestiary data for " + players.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int forceListAll(Collection<ServerPlayer> players, CommandSourceStack source) {
        for (ServerPlayer player : players) {
            int count = player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.BESTIARY_DATA.get()).tiers().size();
            source.sendSuccess(() -> Component.literal(player.getName().getString() + ": " + count + " unlocked")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return players.size();
    }
}
