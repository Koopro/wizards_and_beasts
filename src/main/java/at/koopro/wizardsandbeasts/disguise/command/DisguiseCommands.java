package at.koopro.wizardsandbeasts.disguise.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.disguise.DisguiseState;
import at.koopro.wizardsandbeasts.disguise.DisguiseSystemAPI;
import at.koopro.wizardsandbeasts.disguise.DisguiseTargetResolver;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;

/**
 * {@code /wandb player disguise …} — making somebody look like somebody else, on purpose.
 *
 * <h2>What this is for</h2>
 * <p>Testing the appearance layer without brewing a potion: checking that a skin resolves, that a
 * nameplate follows it, that a late-joining client sees the same thing everyone else does. It is also
 * the only path that can disguise a player as somebody who has <em>never joined this server</em>,
 * because it takes a name rather than an entity selector — see {@link DisguiseTargetResolver} for why
 * that distinction is the feature rather than a convenience.
 *
 * <h2>What it deliberately does not do</h2>
 * <p>No duration. An admin disguise is {@link DisguiseState#INDEFINITE} and comes off when somebody
 * takes it off or the wearer dies. A timed one is what the potion is for, and giving the command a
 * duration argument would make it a second, worse Polyjuice.
 *
 * <p>No refusals either. Unlike a dose, this overwrites whatever face is already on — an operator
 * fixing a stuck disguise must not be told the player is already disguised.
 *
 * <h2>Where it sits</h2>
 * <p>Under {@code player}, beside {@code appearance}, {@code heritage} and {@code stats}. The
 * {@code /wandb} root is eight categories by rule and a ninth is not available; a disguise is a
 * property of a player, so this is the category that already describes it.
 */
@NullMarked
public final class DisguiseCommands {

    private static final String KEY = "disguise.wizards_and_beasts.command.";

    private DisguiseCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("disguise")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.literal("set")
                        .then(Commands.argument("who", EntityArgument.players())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(DisguiseCommands::suggestNames)
                                        .executes(ctx -> set(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "who"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("self")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(DisguiseCommands::suggestNames)
                                .executes(ctx -> set(ctx.getSource(),
                                        List.of(ctx.getSource().getPlayerOrException()),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource(),
                                List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("who", EntityArgument.players())
                                .executes(ctx -> clear(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "who")))))
                .then(Commands.literal("query")
                        .executes(ctx -> query(ctx.getSource(),
                                ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("who", EntityArgument.player())
                                .executes(ctx -> query(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "who")))));
    }

    /**
     * Suggestions are the online players, and nothing more.
     *
     * <p>Offline and never-seen names are the point of the command and cannot be suggested — there is
     * no list of them to draw from. Suggesting the online ones still saves the common case from being
     * typed out, and an operator typing a name that appears in no suggestion is doing the thing this
     * command exists for rather than making a mistake.
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestNames(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                         com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder);
    }

    /**
     * Disguise everybody selected, once the name has resolved.
     *
     * <p>Returns before the work happens when the name needs a profile lookup: that lookup is a
     * blocking HTTP call and doing it inline would stall the whole server. The command therefore
     * reports success on <em>dispatch</em>, and the per-player confirmation arrives from the callback
     * — which is why each disguised player gets their own message rather than one summary line.
     */
    private static int set(CommandSourceStack source, Collection<ServerPlayer> targets, String rawName) {
        String name = rawName.strip();
        if (!DisguiseTargetResolver.isUsableName(name)) {
            source.sendFailure(Component.translatable(KEY + "bad_name", name));
            return 0;
        }
        MinecraftServer server = source.getServer();
        // Snapshot the selection now. By the time the lookup returns, an entity selector re-evaluated
        // against a changed player list would disguise a different set of people than the operator saw.
        List<ServerPlayer> chosen = List.copyOf(targets);

        DisguiseTargetResolver.resolve(server, name, target -> {
            for (ServerPlayer player : chosen) {
                // Re-fetched rather than reused: the snapshot is of UUIDs that were online when the
                // command ran, and a player who logged out during the lookup must not have a disguise
                // written to a stale entity that will never be saved.
                ServerPlayer live = server.getPlayerList().getPlayer(player.getUUID());
                if (live == null) {
                    continue;
                }
                DisguiseSystemAPI.applyIndefinite(live, target.id(), target.name());
                source.sendSuccess(() -> Component.translatable(
                        target.authentic() ? KEY + "set" : KEY + "set_unknown",
                        live.getDisplayName(), target.name()), true);
            }
        });
        return chosen.size();
    }

    private static int clear(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int cleared = 0;
        for (ServerPlayer player : targets) {
            if (DisguiseSystemAPI.clear(player, false)) {
                cleared++;
                source.sendSuccess(() -> Component.translatable(KEY + "cleared",
                        player.getDisplayName()), true);
            }
        }
        if (cleared == 0) {
            source.sendFailure(Component.translatable(KEY + "not_disguised"));
        }
        return cleared;
    }

    private static int query(CommandSourceStack source, ServerPlayer who) {
        DisguiseState state = DisguiseSystemAPI.get(who);
        if (!state.isDisguised()) {
            source.sendSuccess(() -> Component.translatable(KEY + "query_none",
                    who.getDisplayName()), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                state.isIndefinite() ? KEY + "query_indefinite" : KEY + "query_timed",
                who.getDisplayName(),
                state.targetName(),
                state.ticksRemaining() / 20), false);
        return 1;
    }
}
