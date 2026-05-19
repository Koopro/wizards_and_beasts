package at.koopro.wizardsandbeasts.command.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

public interface DebugModule {
    String name();

    /** One-line description for {@code /wandb debug} overview. */
    default String summary() {
        return "";
    }

    LiteralArgumentBuilder<CommandSourceStack> register();
}
