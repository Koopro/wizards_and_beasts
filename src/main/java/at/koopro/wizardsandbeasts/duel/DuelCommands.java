package at.koopro.wizardsandbeasts.duel;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/** {@code /wandb magic duel challenge <player> | accept | decline | yield}: the formal duel (B5). */
@NullMarked
public final class DuelCommands {

    private static final String KEY = "duel.wizards_and_beasts.";

    private DuelCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("duel")
                .then(Commands.literal("challenge")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DuelCommands::challenge)))
                .then(Commands.literal("accept").executes(ctx -> answer(ctx.getSource(),
                        FormalDuels.accept(ctx.getSource().getPlayerOrException()), "no_challenge")))
                .then(Commands.literal("decline").executes(ctx -> answer(ctx.getSource(),
                        FormalDuels.decline(ctx.getSource().getPlayerOrException()), "no_challenge")))
                .then(Commands.literal("yield").executes(ctx -> answer(ctx.getSource(),
                        FormalDuels.yieldDuel(ctx.getSource().getPlayerOrException()), "not_duelling")));
    }

    private static int challenge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer challenger = ctx.getSource().getPlayerOrException();
        FormalDuels.ChallengeResult result = FormalDuels.challenge(challenger, EntityArgument.getPlayer(ctx, "player"));
        if (result == FormalDuels.ChallengeResult.OK) {
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable(KEY + "refused." + result.name().toLowerCase(Locale.ROOT)));
        return 0;
    }

    private static int answer(CommandSourceStack source, boolean done, String refusal) {
        if (!done) {
            source.sendFailure(Component.translatable(KEY + "refused." + refusal));
            return 0;
        }
        return 1;
    }
}
