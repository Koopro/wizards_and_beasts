package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.ministry.command.MinistryCommandsImpl;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * Thin delegate kept at the existing registration site; the tree itself lives with the rest of the Ministry
 * in {@code ministry.command}.
 */
public final class MinistryCommands {

    private MinistryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return MinistryCommandsImpl.register();
    }
}
