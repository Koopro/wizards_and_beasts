package at.koopro.wizardsandbeasts.command.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public interface DebugModule {
    String name();

    /** One-line description for {@code /wandb debug} overview. */
    default String summary() {
        return "";
    }

    LiteralArgumentBuilder<CommandSourceStack> register();

    /** What a debug node does once it knows which player it is talking about. */
    @FunctionalInterface
    interface PlayerAction {
        int run(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException;
    }

    /**
     * Builds the shape every debug node in this package wanted: {@code <literal>} acts on whoever
     * ran it, {@code <literal> <player>} acts on someone else.
     *
     * <p>Thirteen nodes across seven modules spelled this out by hand, which is thirteen chances to
     * name the argument something other than {@code target} and thirteen places to fix when the
     * shape changes.
     */
    static LiteralArgumentBuilder<CommandSourceStack> onSelfOrTarget(String literal, PlayerAction action) {
        return Commands.literal(literal)
                .executes(ctx -> action.run(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> action.run(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
    }
}
