package at.koopro.wizardsandbeasts.heritage.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /wandb player appearance …} — what a player looks like and how big they are.
 *
 * <p>{@code form} and {@code size} are one subject split across two commands: a form carries a size
 * profile id, and {@code size reset} reads the current form to decide what "default" means. They
 * were siblings of {@code heritage info} before, which hid that relationship.
 */
public final class AppearanceCommands {

    private AppearanceCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("appearance")
                .then(WizFormCommands.register())
                .then(WizSizeCommands.register());
    }
}
