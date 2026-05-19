package at.koopro.wizardsandbeasts.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class MinistryCommands {

    private MinistryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("ministry")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "No Ministry commands are currently implemented.").withStyle(ChatFormatting.GRAY), false);
                    return 0;
                });
    }
}
