package at.koopro.wizardsandbeasts.owl.post;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb player post …} — the player-facing entry to the owl post.
 *
 * <p>Ungated on purpose. Sending costs the sender the stack in their hand and can only ever move it
 * to another player, so there is nothing here an operator needs to hold back; the module gate in
 * {@link OwlPostService#send} is the switch that matters.
 *
 * <p>A command rather than an item for the alpha, matching the brief's "creative summon OK": the
 * delivery loop is the thing being proved, and an owl-post item or a rookery block can sit on top of
 * this service later without any of it changing.
 */
@NullMarked
public final class OwlPostCommands {

    private OwlPostCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        // "post", not "owl": `/wandb player owls` is already the O.W.L. *examination* command, and two
        // nodes a letter apart doing unrelated things is a trap for anyone using tab-completion.
        return Commands.literal("post")
                .then(Commands.literal("send")
                        .then(Commands.argument("recipient", EntityArgument.player())
                                .executes(context -> send(
                                        context.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(context, "recipient")))))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException())));
    }

    private static int send(ServerPlayer sender, ServerPlayer recipient) {
        OwlPostService.SendResult result = OwlPostService.send(sender, recipient.getUUID());
        if (!result.ok()) {
            sender.sendSystemMessage(result.message().copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        sender.sendSystemMessage(Component.translatable("owl.wizards_and_beasts.sent_to",
                recipient.getDisplayName(),
                OwlPostService.FLIGHT_TICKS / 20L).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int status(ServerPlayer player) {
        var overworld = player.level().getServer().overworld();
        player.sendSystemMessage(OwlPostService.status(overworld, player).copy()
                .withStyle(ChatFormatting.GOLD));
        Long next = OwlPostService.ticksUntilNextArrival(overworld, player);
        if (next != null) {
            player.sendSystemMessage(Component.translatable(
                    "owl.wizards_and_beasts.next_arrival", next / 20L).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }
}
