package at.koopro.wizardsandbeasts.currency.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class VaultCommands {

    private VaultCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("vault")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "No vault commands are currently implemented.").withStyle(ChatFormatting.GRAY), false);
                    return 0;
                });
    }
}
