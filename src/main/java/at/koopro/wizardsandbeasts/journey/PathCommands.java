package at.koopro.wizardsandbeasts.journey;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb player path [on|off]}: a player's own path. Without an argument it repeats the next step; {@code off}
 * stops the hints and rewards for a wizard who knows the way, {@code on} brings them back. Self only, so no gate.
 */
@NullMarked
public final class PathCommands {

    private PathCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("path")
                .executes(ctx -> show(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)));
    }

    private static int show(ServerPlayer player) {
        if (!ApprenticePathService.record(player).guided()) {
            player.sendSystemMessage(Component.translatable("journey.wizards_and_beasts.command.off"));
            return 0;
        }
        player.sendSystemMessage(ApprenticePathService.nextLine(ApprenticePathService.current(player)));
        return 1;
    }

    private static int set(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ApprenticePathService.setGuided(player, on);
        source.sendSuccess(() -> Component.translatable(on ? "journey.wizards_and_beasts.command.on"
                : "journey.wizards_and_beasts.command.off"), false);
        return 1;
    }
}
